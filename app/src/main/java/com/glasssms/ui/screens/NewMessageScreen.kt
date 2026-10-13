package com.glasssms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasssms.AppViewModel
import com.glasssms.ui.glass.GlassCapsule
import com.glasssms.ui.glass.edgeBlur
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun NewMessageScreen(vm: AppViewModel, initialAddress: String, initialBody: String) {
    val p = LocalPalette.current
    val haze = rememberHazeState()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var to by rememberSaveable { mutableStateOf(initialAddress) }
    var body by rememberSaveable { mutableStateOf(initialBody) }
    val focus = remember { FocusRequester() }
    val insets = WindowInsets.systemBars.asPaddingValues()

    val suggestions = remember(conversations, to) {
        if (to.isBlank()) conversations.orEmpty().take(8)
        else conversations.orEmpty().filter {
            it.displayName.contains(to, ignoreCase = true) || it.address.filter(Char::isDigit).contains(to.filter(Char::isDigit).ifEmpty { "\u0000" })
        }
    }

    LaunchedEffect(Unit) { if (initialAddress.isBlank()) focus.requestFocus() }

    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 60.dp, bottom = 120.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("To:", style = IosType.body, color = p.secondaryLabel)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = to,
                        onValueChange = { to = it },
                        singleLine = true,
                        textStyle = IosType.body.copy(color = p.accent),
                        cursorBrush = SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = p.separator)
            }
            items(suggestions, key = { it.threadId }) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.openThread(c.threadId, c.address) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.displayName, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.displayName, style = IosType.body, color = p.label, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(c.address, style = IosType.footnote, color = p.secondaryLabel, maxLines = 1)
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
            onSend = { vm.startConversation(to.trim(), body) },
            haze = haze,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
