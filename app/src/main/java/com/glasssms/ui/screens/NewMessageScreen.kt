package com.glasssms.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasssms.AppViewModel
import com.glasssms.data.ContactPhone
import com.glasssms.data.ContactResolver
import com.glasssms.ui.glass.GlassCapsule
import com.glasssms.ui.glass.edgeBlur
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private val PHONE_LIKE = Regex("""^\+?[\d\s()\-]{3,}$""")

@Composable
fun NewMessageScreen(vm: AppViewModel, initialAddress: String, initialBody: String) {
    val p = LocalPalette.current
    val haze = rememberHazeState()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialAddress) }
    // Chosen recipient; a contact's name with their number, or just a typed number.
    var recipientName by rememberSaveable { mutableStateOf<String?>(null) }
    var recipientNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var body by rememberSaveable { mutableStateOf(initialBody) }
    val focus = remember { FocusRequester() }
    val insets = WindowInsets.systemBars.asPaddingValues()

    val contacts by produceState(emptyList<ContactPhone>(), query) {
        delay(120) // let fast typing settle before hitting the provider
        value = vm.searchContacts(query)
    }
    val conversationMatches = remember(conversations, query) {
        val digits = query.filter(Char::isDigit)
        if (query.isBlank()) conversations.orEmpty().take(10)
        else conversations.orEmpty().filter {
            it.displayName.contains(query, ignoreCase = true) ||
                (digits.length >= 3 && it.address.filter(Char::isDigit).contains(digits))
        }.take(10)
    }
    val typedNumber = query.trim().takeIf { PHONE_LIKE.matches(it) }

    fun pick(name: String?, number: String) {
        recipientName = name
        recipientNumber = number
        query = ""
    }

    fun clearRecipient() {
        recipientName = null
        recipientNumber = null
        focus.requestFocus()
    }

    LaunchedEffect(Unit) { if (initialAddress.isBlank()) focus.requestFocus() }

    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 60.dp, bottom = 120.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("To:", style = IosType.body, color = p.secondaryLabel)
                    Spacer(Modifier.width(8.dp))
                    AnimatedContent(
                        targetState = recipientNumber,
                        transitionSpec = { (scaleIn(spring(0.6f, 600f), initialScale = 0.7f) + fadeIn()) togetherWith fadeOut() },
                        modifier = Modifier.weight(1f),
                        label = "recipient",
                    ) { number ->
                        if (number != null) {
                            // Box loosens the min-width AnimatedContent passes down, so the chip hugs its label.
                            Box(Modifier.fillMaxWidth()) {
                                RecipientChip(recipientName ?: ContactResolver.formatAddress(number), onClear = ::clearRecipient)
                            }
                        } else {
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = IosType.body.copy(color = p.label),
                                cursorBrush = SolidColor(p.accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next,
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                decorationBox = { inner ->
                                    Box {
                                        if (query.isEmpty()) Text("Name or number", style = IosType.body, color = p.tertiaryLabel)
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = p.separator)
            }

            if (recipientNumber == null) {
                if (typedNumber != null) {
                    item {
                        SuggestionRow(
                            title = "Send to ${ContactResolver.formatAddress(typedNumber)}",
                            subtitle = "New SMS",
                            avatarName = "",
                            icon = true,
                            onClick = { pick(null, typedNumber) },
                        )
                    }
                }
                if (contacts.isNotEmpty()) {
                    item { SectionLabel("Contacts") }
                    items(contacts, key = { "c:" + it.name + it.number }) { c ->
                        SuggestionRow(
                            title = c.name,
                            subtitle = "${c.label}  ${c.number}",
                            avatarName = c.name,
                            onClick = { pick(c.name, c.number) },
                        )
                    }
                }
                if (conversationMatches.isNotEmpty()) {
                    item { SectionLabel(if (query.isBlank()) "Recent" else "Conversations") }
                    items(conversationMatches, key = { "t:" + it.threadId }) { c ->
                        SuggestionRow(
                            title = c.displayName,
                            subtitle = ContactResolver.formatAddress(c.address),
                            avatarName = c.displayName,
                            onClick = { vm.openThread(c.threadId, c.address) },
                        )
                    }
                }
                if (query.isNotBlank() && typedNumber == null && contacts.isEmpty() && conversationMatches.isEmpty()) {
                    item {
                        Text(
                            "No contacts match “$query”.",
                            style = IosType.subhead,
                            color = p.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(insets.calculateTopPadding() + 64.dp).edgeBlur(haze))
        Box(Modifier.fillMaxWidth().padding(top = insets.calculateTopPadding() + 6.dp, start = 16.dp, end = 16.dp).height(44.dp)) {
            Text("New Message", style = IosType.headline, color = p.label, modifier = Modifier.align(Alignment.Center))
            GlassCapsule(haze, Modifier.align(Alignment.CenterEnd), onClick = { vm.pop() }) {
                Text("Cancel", style = IosType.body, fontWeight = FontWeight.Medium, color = p.accent)
            }
        }

        GlassInputBar(
            text = body,
            onTextChange = { body = it },
            onSend = {
                // A typed number counts even if the user never tapped the "Send to" row.
                val to = recipientNumber ?: typedNumber
                if (to != null) vm.startConversation(to, body)
            },
            haze = haze,
            modifier = Modifier.align(Alignment.BottomCenter),
            placeholder = if (recipientNumber == null && typedNumber == null) "Choose a recipient first" else "Text Message · SMS",
        )
    }
}

@Composable
private fun RecipientChip(label: String, onClear: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(p.accent)
            .clickable(onClick = onClear)
            .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = IosType.subhead, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.Close, "Remove recipient", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = IosType.footnote,
        fontWeight = FontWeight.Bold,
        color = LocalPalette.current.secondaryLabel,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SuggestionRow(title: String, subtitle: String, avatarName: String, onClick: () -> Unit, icon: Boolean = false) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Sms, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        } else {
            Avatar(avatarName, size = 40.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = IosType.body, color = if (icon) p.accent else p.label, maxLines = 1)
            Text(subtitle, style = IosType.footnote, color = p.secondaryLabel, maxLines = 1)
        }
    }
}
