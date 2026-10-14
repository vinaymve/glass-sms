package com.glasssms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.glasssms.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** iOS system colours, named after their UIKit counterparts. */
@Immutable
data class Palette(
    val isDark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val secondaryGroupedBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val accent: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val incomingBubble: Color,
    val outgoingTop: Color,
    val outgoingBottom: Color,
    val avatarTop: Color,
    val avatarBottom: Color,
    // Liquid glass
    val glassTint: Color,
    val glassFallback: Color,
    val glassRimStrong: Color,
    val glassRimWeak: Color,
    val glassHighlight: Color,
)

val LightPalette = Palette(
    isDark = false,
    background = Color.White,
    groupedBackground = Color(0xFFF2F2F7),
    secondaryGroupedBackground = Color.White,
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0xFFC6C6C8),
    fill = Color(0x1F767680),
    accent = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    incomingBubble = Color(0xFFE9E9EB),
    outgoingTop = Color(0xFF3B9BFF),
    outgoingBottom = Color(0xFF007AFF),
    avatarTop = Color(0xFFA5ABB8),
    avatarBottom = Color(0xFF858994),
    glassTint = Color(0x8CFFFFFF),
    glassFallback = Color(0xF0F9F9F9),
    glassRimStrong = Color(0xE6FFFFFF),
    glassRimWeak = Color(0x26FFFFFF),
    glassHighlight = Color(0x59FFFFFF),
)

val DarkPalette = Palette(
    isDark = true,
    background = Color.Black,
    groupedBackground = Color.Black,
    secondaryGroupedBackground = Color(0xFF1C1C1E),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xFF38383A),
    fill = Color(0x3D767680),
    accent = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    incomingBubble = Color(0xFF26252A),
    outgoingTop = Color(0xFF2E8FFF),
    outgoingBottom = Color(0xFF0A84FF),
    avatarTop = Color(0xFF6E727B),
    avatarBottom = Color(0xFF4F525A),
    glassTint = Color(0x732C2C2E),
    glassFallback = Color(0xF21C1C1E),
    glassRimStrong = Color(0x59FFFFFF),
    glassRimWeak = Color(0x0FFFFFFF),
    glassHighlight = Color(0x1AFFFFFF),
)

/**
 * NYC Subway signage typeface. The MTA uses Helvetica (licensed), so this ships TeX Gyre Heros,
 * a free Helvetica clone under the GUST Font License (see licenses/). Swap the files in res/font
 * to change it everywhere.
 */
val SubwayFont: FontFamily = FontFamily(
    Font(R.font.subway_regular, FontWeight.Normal),
    Font(R.font.subway_bold, FontWeight.Bold),
    Font(R.font.subway_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.subway_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

/** MTA route colours, used for "route bullet" avatars. Pairs of (bullet, text). */
object Mta {
    val routes = listOf(
        Color(0xFFEE352E) to Color.White, // 1 2 3
        Color(0xFF00933C) to Color.White, // 4 5 6
        Color(0xFFB933AD) to Color.White, // 7
        Color(0xFF0039A6) to Color.White, // A C E
        Color(0xFFFF6319) to Color.White, // B D F M
        Color(0xFF6CBE45) to Color.White, // G
        Color(0xFF996633) to Color.White, // J Z
        Color(0xFFA7A9AC) to Color.White, // L
        Color(0xFFFCCC0A) to Color.Black, // N Q R W
        Color(0xFF808183) to Color.White, // S
    )

    private val lines = listOf("123", "456", "7", "ACE", "BDFM", "G", "JZ", "L", "NQRW", "S")

    /** The real colour for a route label like "A" or "7", else a stable pick from the key's hash. */
    fun forKey(key: String): Pair<Color, Color> {
        val i = lines.indexOfFirst { key.length == 1 && key.uppercase() in it }
        return routes[if (i >= 0) i else Math.floorMod(key.hashCode(), routes.size)]
    }
}

/** iOS Dynamic Type sizes at the default "Large" setting, set in the subway face. */
object IosType {
    val largeTitle = TextStyle(fontFamily = SubwayFont, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, lineHeight = 41.sp)
    val title2 = TextStyle(fontFamily = SubwayFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.26).sp, lineHeight = 28.sp)
    val title3 = TextStyle(fontFamily = SubwayFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.45).sp, lineHeight = 25.sp)
    val headline = TextStyle(fontFamily = SubwayFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.43).sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = SubwayFont, fontSize = 17.sp, letterSpacing = (-0.43).sp, lineHeight = 22.sp)
    val callout = TextStyle(fontFamily = SubwayFont, fontSize = 16.sp, letterSpacing = (-0.31).sp, lineHeight = 21.sp)
    val subhead = TextStyle(fontFamily = SubwayFont, fontSize = 15.sp, letterSpacing = (-0.23).sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontFamily = SubwayFont, fontSize = 13.sp, letterSpacing = (-0.08).sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = SubwayFont, fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontFamily = SubwayFont, fontSize = 11.sp, letterSpacing = 0.06.sp, lineHeight = 13.sp)
}

/** Material's own components (dialogs, menus) in the subway face too. */
private val SubwayTypography = Typography().let { t ->
    fun TextStyle.subway() = copy(fontFamily = SubwayFont)
    Typography(
        displayLarge = t.displayLarge.subway(), displayMedium = t.displayMedium.subway(), displaySmall = t.displaySmall.subway(),
        headlineLarge = t.headlineLarge.subway(), headlineMedium = t.headlineMedium.subway(), headlineSmall = t.headlineSmall.subway(),
        titleLarge = t.titleLarge.subway(), titleMedium = t.titleMedium.subway(), titleSmall = t.titleSmall.subway(),
        bodyLarge = t.bodyLarge.subway(), bodyMedium = t.bodyMedium.subway(), bodySmall = t.bodySmall.subway(),
        labelLarge = t.labelLarge.subway(), labelMedium = t.labelMedium.subway(), labelSmall = t.labelSmall.subway(),
    )
}

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun GlassTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = palette.accent, background = palette.background, surface = palette.secondaryGroupedBackground)
    } else {
        lightColorScheme(primary = palette.accent, background = palette.background, surface = palette.secondaryGroupedBackground)
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = SubwayTypography, content = content)
    }
}
