package com.glasssms

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glasssms.data.Conversation
import com.glasssms.data.Message
import com.glasssms.data.OtpEntry
import com.glasssms.data.Categorizer
import com.glasssms.data.Settings
import com.glasssms.data.StoredMessage
import com.glasssms.otp.CodeActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

sealed interface Screen {
    data object Inbox : Screen
    data class Thread(val threadId: Long, val address: String) : Screen
    data class NewMessage(val address: String = "", val body: String = "") : Screen
    data object Codes : Screen
    data object Settings : Screen
    data object Trash : Screen
    data object Spam : Screen
    data object Blocked : Screen
}

/** Something that just happened and can be reversed from the snackbar for a few seconds. */
data class UndoEvent(val id: Long, val label: String, val undo: () -> Unit)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx = application
    private val repo = application.app.repo
    private val settingsStore = application.app.settings
    private val manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val changes = merge(repo.changes(), manualRefresh)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val settings: StateFlow<Settings> = settingsStore.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** null while the first load is running. */
    val conversations: StateFlow<List<Conversation>?> = changes
        .map { repo.loadConversations() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val codes: StateFlow<List<OtpEntry>> = changes
        .map { repo.recentCodes(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bin and spam live in the app's own database, so they refresh off both kinds of change. */
    val trash: StateFlow<List<StoredMessage>> = changes
        .map { repo.trashed() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val spam: StateFlow<List<StoredMessage>> = changes
        .map { repo.spam() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _undo = MutableStateFlow<UndoEvent?>(null)
    val undo: StateFlow<UndoEvent?> = _undo

    fun dismissUndo(id: Long) {
        if (_undo.value?.id == id) _undo.value = null
    }

    private fun offerUndo(label: String, undo: () -> Unit) {
        _undo.value = UndoEvent(System.nanoTime(), label, undo)
    }

    init {
        // Recently Deleted keeps things for 30 days.
        viewModelScope.launch(Dispatchers.IO) { repo.purgeTrash(TimeUnit.DAYS.toMillis(30)) }
    }

    fun messages(threadId: Long): Flow<List<Message>> = changes
        .map { repo.loadMessages(threadId) }
        .flowOn(Dispatchers.IO)

    // --- Navigation --------------------------------------------------------------------------

    val backStack = mutableStateListOf<Screen>(Screen.Inbox)

    fun push(screen: Screen) {
        if (backStack.last() != screen) backStack.add(screen)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun openThread(threadId: Long, address: String) {
        backStack.removeAll { it is Screen.Thread || it is Screen.NewMessage }
        push(Screen.Thread(threadId, address))
    }

    fun openNewMessage(address: String = "", body: String = "") {
        viewModelScope.launch {
            // Jump straight into an existing thread when a link names someone we already talk to.
            val existing = if (address.isNotBlank()) {
                withContext(Dispatchers.IO) { runCatching { repo.threadIdFor(address) }.getOrNull() }
            } else null
            val hasHistory = existing != null && conversations.value?.any { it.threadId == existing } == true
            if (hasHistory && body.isBlank()) openThread(existing!!, address) else push(Screen.NewMessage(address, body))
        }
    }

    // --- Actions -----------------------------------------------------------------------------

    fun refresh() {
        repo.contacts.clear()
        manualRefresh.tryEmit(Unit)
    }

    fun displayName(address: String) = repo.contacts.displayName(address)

    suspend fun searchContacts(query: String) = withContext(Dispatchers.IO) { repo.contacts.search(query) }

    fun send(address: String, body: String) {
        if (body.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { repo.send(address, body.trim()) }
    }

    fun startConversation(address: String, body: String) {
        if (address.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            val threadId = withContext(Dispatchers.IO) {
                val id = repo.threadIdFor(address)
                repo.send(address, body.trim())
                id
            }
            openThread(threadId, address)
        }
    }

    fun markRead(threadId: Long) = viewModelScope.launch(Dispatchers.IO) { repo.markRead(threadId) }

    fun markAllRead() = viewModelScope.launch(Dispatchers.IO) { repo.markAllRead() }

    fun togglePin(threadId: Long) = updateSettings { s ->
        s.copy(pinnedThreads = if (threadId in s.pinnedThreads) s.pinnedThreads - threadId else s.pinnedThreads + threadId)
    }

    fun toggleRead(c: Conversation) = viewModelScope.launch(Dispatchers.IO) {
        if (c.unreadCount > 0) repo.markRead(c.threadId) else repo.markUnread(c.threadId)
    }

    fun deleteThread(threadId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val batch = repo.trashThread(threadId)
        if (threadId in settings.value.pinnedThreads) settingsStore.update { it.copy(pinnedThreads = it.pinnedThreads - threadId) }
        bump()
        if (batch != null) offerUndo("Conversation deleted") { restoreBatch(batch) }
    }

    fun deleteMessages(ids: Collection<Long>) = viewModelScope.launch(Dispatchers.IO) {
        val batch = repo.trashMessages(ids)
        bump()
        if (batch != null) offerUndo(if (ids.size == 1) "Message deleted" else "${ids.size} messages deleted") { restoreBatch(batch) }
    }

    private fun restoreBatch(batch: Long) = viewModelScope.launch(Dispatchers.IO) {
        repo.restoreBatch(batch)
        bump()
    }

    fun restoreTrash(items: List<StoredMessage>) = viewModelScope.launch(Dispatchers.IO) {
        repo.restoreTrash(items)
        bump()
    }

    fun deleteFromTrash(items: List<StoredMessage>) = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteFromTrash(items.map { it.id })
        bump()
    }

    fun emptyTrash() = viewModelScope.launch(Dispatchers.IO) {
        repo.emptyTrash()
        bump()
    }

    // --- Mute, archive, spam -------------------------------------------------------------------

    fun toggleMute(threadId: Long) = updateSettings { s ->
        s.copy(mutedThreads = if (threadId in s.mutedThreads) s.mutedThreads - threadId else s.mutedThreads + threadId)
    }

    fun toggleArchive(threadId: Long) {
        val archiving = threadId !in settings.value.archivedThreads
        updateSettings { s ->
            s.copy(archivedThreads = if (archiving) s.archivedThreads + threadId else s.archivedThreads - threadId)
        }
        if (archiving) offerUndo("Conversation archived") { toggleArchive(threadId) }
    }

    /** Blocks the sender and moves the whole conversation to Spam. */
    fun blockAndReport(c: Conversation) = viewModelScope.launch(Dispatchers.IO) {
        val key = Categorizer.senderKey(c.address)
        settingsStore.update { it.copy(blockedSenders = it.blockedSenders + key, pinnedThreads = it.pinnedThreads - c.threadId) }
        repo.moveThreadToSpam(c.threadId, "Reported by you")
        bump()
    }

    fun unblock(key: String) = updateSettings { it.copy(blockedSenders = it.blockedSenders - key) }

    fun blockSender(address: String) = updateSettings {
        it.copy(blockedSenders = it.blockedSenders + Categorizer.senderKey(address))
    }

    fun addBlockedKeyword(word: String) {
        val w = word.trim()
        if (w.isNotEmpty()) updateSettings { it.copy(blockedKeywords = it.blockedKeywords + w) }
    }

    fun removeBlockedKeyword(word: String) = updateSettings { it.copy(blockedKeywords = it.blockedKeywords - word) }

    /** "Not spam": puts the messages back in the inbox and unblocks the sender. */
    fun notSpam(items: List<StoredMessage>) = viewModelScope.launch(Dispatchers.IO) {
        val keys = items.map { Categorizer.senderKey(it.address) }.toSet()
        settingsStore.update { it.copy(blockedSenders = it.blockedSenders - keys) }
        repo.restoreSpam(items)
        bump()
    }

    fun deleteSpam(items: List<StoredMessage>) = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteSpam(items.map { it.id })
        bump()
    }

    // --- Inbox cleaner -------------------------------------------------------------------------

    suspend fun findCleanable(otpAgeDays: Int?, offerAgeDays: Int?) = withContext(Dispatchers.IO) {
        repo.findCleanable(otpAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) }, offerAgeDays?.let { TimeUnit.DAYS.toMillis(it.toLong()) })
    }

    fun clean(messages: List<Message>) = viewModelScope.launch(Dispatchers.IO) {
        val batch = repo.trashAll(messages)
        bump()
        if (batch != null) offerUndo("${messages.size} messages cleaned up") { restoreBatch(batch) }
    }

    private fun bump() {
        manualRefresh.tryEmit(Unit)
    }

    fun copyCode(code: String, messageId: Long) = viewModelScope.launch {
        CodeActions.copyAndMaybeCleanUp(ctx, code, messageId)
    }

    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch {
        settingsStore.update(transform)
    }
}
