package de.taschenwerk.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

enum class Scheme(val label: String, val swatch: Color) {
    GELB("Gelb", Color(0xFFF1D67C)),
    FLIEDER("Flieder", Color(0xFF8B8CC9)),
    ROSA("Rosa", Color(0xFFEBA88C)),
    SALBEI("Salbei", Color(0xFF9DB596)),
    TUERKIS("Türkis", Color(0xFF7DBDB6)),
    HELLBLAU("Hellblau", Color(0xFF7DB8E8))
}

data class Palette(
    val bg: Color,
    val card: Color,
    val cardRing: Color,
    val ink: Color,
    val sub: Color,
    val muted: Color,
    val accent: Color,
    val fill: Color,
    val onFill: Color,
    val line: Color,
    val track: Color,
    val nav: Color,
    val dark: Boolean
)

fun palette(s: Scheme, dark: Boolean): Palette {
    if (dark) {
        val accent = when (s) {
            Scheme.GELB -> Color(0xFFF1D67C)
            Scheme.FLIEDER -> Color(0xFFABACEB)
            Scheme.ROSA -> Color(0xFFF0B49B)
            Scheme.SALBEI -> Color(0xFFB3C9AC)
            Scheme.TUERKIS -> Color(0xFF8FD0C9)
            Scheme.HELLBLAU -> Color(0xFF9CCBF2)
        }
        val fill = when (s) {
            Scheme.GELB -> Color(0xFFF1D67C)
            Scheme.FLIEDER -> Color(0xFF8B8CC9)
            Scheme.ROSA -> Color(0xFFEBA88C)
            Scheme.SALBEI -> Color(0xFF9DB596)
            Scheme.TUERKIS -> Color(0xFF7DBDB6)
            Scheme.HELLBLAU -> Color(0xFF7DB8E8)
        }
        return Palette(
            bg = Color(0xFF141518), card = Color(0xFF1F2025), cardRing = Color.Transparent,
            ink = Color(0xFFF2F2F4), sub = Color(0xFFA0A1A8), muted = Color(0xFF6E6F76),
            accent = accent, fill = fill, onFill = Color(0xFF161618),
            line = Color(0xFF2E2F35), track = Color(0xFF2A2B31), nav = Color(0xFF18191D), dark = true
        )
    }
    return when (s) {
        Scheme.GELB -> Palette(
            bg = Color(0xFFFAF9F4), card = Color.White, cardRing = Color(0xFFEDEAE0),
            ink = Color(0xFF161618), sub = Color(0xFF6B6B72), muted = Color(0xFF8E8E95),
            accent = Color(0xFFF1D67C), fill = Color(0xFFF1D67C), onFill = Color(0xFF161618),
            line = Color(0xFFECEAE1), track = Color(0xFFF0EEE6), nav = Color(0xFFFEFEFB), dark = false
        )
        Scheme.FLIEDER -> Palette(
            bg = Color(0xFFF2F3F5), card = Color.White, cardRing = Color.Transparent,
            ink = Color(0xFF161618), sub = Color(0xFF6B6B72), muted = Color(0xFF8E8E95),
            accent = Color(0xFF6667B0), fill = Color(0xFF8B8CC9), onFill = Color.White,
            line = Color(0xFFE3E4E8), track = Color(0xFFE6E7EB), nav = Color(0xFFFAFAFB), dark = false
        )
        Scheme.ROSA -> Palette(
            bg = Color(0xFFF6F2F0), card = Color.White, cardRing = Color.Transparent,
            ink = Color(0xFF161618), sub = Color(0xFF706864), muted = Color(0xFF978E8A),
            accent = Color(0xFFA4523A), fill = Color(0xFFEBA88C), onFill = Color(0xFF161618),
            line = Color(0xFFEBE3DF), track = Color(0xFFEFE8E4), nav = Color(0xFFFCFAF9), dark = false
        )
        Scheme.SALBEI -> Palette(
            bg = Color(0xFFF1F2EE), card = Color.White, cardRing = Color.Transparent,
            ink = Color(0xFF161618), sub = Color(0xFF6A6D64), muted = Color(0xFF8F938A),
            accent = Color(0xFF56704F), fill = Color(0xFF9DB596), onFill = Color(0xFF161618),
            line = Color(0xFFE2E5DE), track = Color(0xFFE7E9E3), nav = Color(0xFFFAFAF8), dark = false
        )
        Scheme.TUERKIS -> Palette(
            bg = Color(0xFFEEF3F2), card = Color.White, cardRing = Color.Transparent,
            ink = Color(0xFF161618), sub = Color(0xFF63706E), muted = Color(0xFF8B9593),
            accent = Color(0xFF2A7770), fill = Color(0xFF7DBDB6), onFill = Color(0xFF161618),
            line = Color(0xFFDDE6E4), track = Color(0xFFE3EBE9), nav = Color(0xFFF8FBFA), dark = false
        )
        Scheme.HELLBLAU -> Palette(
            bg = Color(0xFFF0F4F8), card = Color.White, cardRing = Color.Transparent,
            ink = Color(0xFF161618), sub = Color(0xFF636B74), muted = Color(0xFF8A929B),
            accent = Color(0xFF2E6FA7), fill = Color(0xFF7DB8E8), onFill = Color(0xFF161618),
            line = Color(0xFFDDE4EB), track = Color(0xFFE3E9EF), nav = Color(0xFFF8FAFC), dark = false
        )
    }
}

val LocalPal = staticCompositionLocalOf { palette(Scheme.GELB, false) }

@Composable
fun AppTheme(scheme: Scheme, darkMode: Int, content: @Composable () -> Unit) {
    val dark = when (darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = palette(scheme, dark)
    val cs = if (dark) darkColorScheme(
        primary = p.fill, onPrimary = p.onFill, background = p.bg, surface = p.card,
        onBackground = p.ink, onSurface = p.ink
    ) else lightColorScheme(
        primary = p.fill, onPrimary = p.onFill, background = p.bg, surface = p.card,
        onBackground = p.ink, onSurface = p.ink
    )
    MaterialTheme(colorScheme = cs) {
        CompositionLocalProvider(LocalPal provides p) { content() }
    }
}

fun num(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontSize = size,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.02).em
)
