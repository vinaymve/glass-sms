package com.glasssms.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.glasssms.AppViewModel
import com.glasssms.MainActivity
import com.glasssms.Screen
import com.glasssms.data.SmsRepository
import com.glasssms.ui.glass.GlassCapsule
import com.glasssms.ui.glass.glass
import com.glasssms.ui.screens.CodesScreen
import com.glasssms.ui.screens.InboxScreen
import com.glasssms.ui.screens.NewMessageScreen
import com.glasssms.ui.screens.SettingsScreen
import com.glasssms.ui.screens.ThreadScreen
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import com.glasssms.ui.theme.Mta
import com.glasssms.ui.theme.SubwayFont
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private data class NavEntry(val depth: Int, val screen: Screen)

@Composable
fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var isDefault by remember { mutableStateOf(SmsRepository.isDefaultSmsApp(context)) }
    var canRead by remember { mutableStateOf(hasPermission(context, Manifest.permission.READ_SMS)) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refresh()
    }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = SmsRepository.isDefaultSmsApp(context)
        canRead = hasPermission(context, Manifest.permission.READ_SMS)
        vm.refresh()
        val wanted = buildList {
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterNot { hasPermission(context, it) }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }
    val requestDefault = { roleRequest.launch(MainActivity.defaultSmsRoleIntent(activity)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = SmsRepository.isDefaultSmsApp(context)
        canRead = hasPermission(context, Manifest.permission.READ_SMS)
    }

    if (!isDefault && !canRead) {
        Onboarding(onContinue = requestDefault)
        return
    }

    val stack = vm.backStack
    BackHandler(enabled = stack.size > 1) { vm.pop() }

    AnimatedContent(
        targetState = NavEntry(stack.size, stack.last()),
        contentKey = { it.screen },
        transitionSpec = {
            val forward = targetState.depth >= initialState.depth
            val slide = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.9f, stiffness = 420f)
            if (forward) {
                (slideInHorizontally(slide) { it } + fadeIn()) togetherWith (slideOutHorizontally(slide) { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally(slide) { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally(slide) { it } + fadeOut())
            }
        },
        label = "nav",
    ) { entry ->
        when (val s = entry.screen) {
            Screen.Inbox -> InboxScreen(vm)
            is Screen.Thread -> ThreadScreen(vm, s.threadId, s.address)
            is Screen.NewMessage -> NewMessageScreen(vm, s.address, s.body)
            Screen.Codes -> CodesScreen(vm)
            Screen.Settings -> SettingsScreen(vm, isDefault, requestDefault)
        }
    }
}

private fun hasPermission(context: android.content.Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** First run: a strip of route bullets behind a glass card, so the glass has something to refract. */
@Composable
private fun Onboarding(onContinue: () -> Unit) {
    val p = LocalPalette.current
    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize().background(p.background)) {
        RouteBackdrop(haze)
        Column(
            Modifier
                .align(Alignment.Center)
                .systemBarsPadding()
                .padding(24.dp)
                .fillMaxWidth()
                .glass(haze, RoundedCornerShape(32.dp), elevation = 24.dp)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(p.label))
            Spacer(Modifier.height(14.dp))
            Text("Glass SMS", style = IosType.largeTitle, color = p.label)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your texts, with one-time codes pulled out, copied in one tap and cleaned up after.",
                style = IosType.callout,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            GlassCapsule(haze, Modifier.fillMaxWidth(), onClick = onContinue, tint = p.accent.copy(alpha = 0.85f)) {
                Text("Set as Default SMS App", style = IosType.headline, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Android only lets the default SMS app receive and send messages.",
                style = IosType.footnote,
                color = p.tertiaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RouteBackdrop(haze: HazeState) {
    val lines = listOf("1", "4", "7", "A", "B", "G", "J", "L", "N", "S", "2", "5", "C", "D", "Q", "6", "E", "F", "R", "Z")
    Column(
        Modifier.fillMaxSize().hazeSource(haze).padding(top = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        lines.chunked(5).forEachIndexed { row, chunk ->
            Row(
                Modifier.fillMaxWidth().offset(x = if (row % 2 == 0) 0.dp else 36.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                chunk.forEach { label ->
                    val (bullet, ink) = Mta.forKey(label)
                    Box(Modifier.size(64.dp).clip(CircleShape).background(bullet), contentAlignment = Alignment.Center) {
                        Text(label, color = ink, fontFamily = SubwayFont, fontWeight = FontWeight.Bold, fontSize = 36.sp)
                    }
                }
            }
        }
    }
}
