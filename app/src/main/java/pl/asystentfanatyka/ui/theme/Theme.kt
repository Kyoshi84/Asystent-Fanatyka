package pl.asystentfanatyka.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.asystentfanatyka.core.rules.Verdict

// Paleta o wysokim kontraście – czytelna w mocnym słońcu nad wodą.
private val Teal = Color(0xFF0E4A5C)
private val TealLight = Color(0xFF8FD0E0)
private val Sand = Color(0xFFF6F1E4)
private val Ink = Color(0xFF14191C)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE8EF),
    onPrimaryContainer = Color(0xFF04232C),
    secondary = Color(0xFF5B4A1E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E3B8),
    onSecondaryContainer = Color(0xFF2A2000),
    background = Sand,
    onBackground = Ink,
    surface = Color(0xFFFFFCF5),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6E0D0),
    onSurfaceVariant = Color(0xFF3D3A33),
    outline = Color(0xFF6F6A5E),
    error = Color(0xFFB3261E)
)

private val Dark = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00313D),
    primaryContainer = Color(0xFF0E4A5C),
    onPrimaryContainer = Color(0xFFCBE8EF),
    secondary = Color(0xFFE2C77F),
    onSecondary = Color(0xFF3B2F00),
    background = Color(0xFF101415),
    onBackground = Color(0xFFE8E4DA),
    surface = Color(0xFF171C1E),
    onSurface = Color(0xFFE8E4DA),
    surfaceVariant = Color(0xFF2A3133),
    onSurfaceVariant = Color(0xFFC9C5BA)
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = t.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun AsystentFanatykaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content
    )
}

/** Kolory werdyktu: tło i tekst o kontraście ≥ 7:1. */
data class VerdictColors(val container: Color, val content: Color)

fun verdictColors(v: Verdict): VerdictColors = when (v) {
    Verdict.NOT_ALLOWED -> VerdictColors(Color(0xFF9C1C14), Color.White)
    Verdict.MEETS_CHECKED -> VerdictColors(Color(0xFF145A26), Color.White)
    Verdict.CANNOT_CONFIRM -> VerdictColors(Color(0xFFFFD866), Color(0xFF231A00))
}

val BigButtonText = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
