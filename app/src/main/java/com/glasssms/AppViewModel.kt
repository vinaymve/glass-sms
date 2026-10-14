package com.glasssms

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glasssms.data.Conversation
import com.glasssms.data.Message
import com.glasssms.data.OtpEntry
import com.glasssms.data.Settings
import com.glasssms.otp.CodeActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
}

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

    fun toggleRead(c: Conversation) = viewModelScope.launch(Dispatchers.IO) {
        if (c.unreadCount > 0) repo.markRead(c.threadId) else repo.markUnread(c.threadId)
    }

    fun deleteThread(threadId: Long) = viewModelScope.launch(Dispatchers.IO) { repo.deleteThread(threadId) }

    fun deleteMessages(ids: Collection<Long>) = viewModelScope.launch(Dispatchers.IO) { repo.deleteMessages(ids) }

    fun copyCode(code: String, messageId: Long) = viewModelScope.launch {
        CodeActions.copyAndMaybeCleanUp(ctx, code, messageId)
    }

    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch {
        settingsStore.update(transform)
    }
}
