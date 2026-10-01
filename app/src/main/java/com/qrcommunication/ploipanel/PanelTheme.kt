package com.qrcommunication.ploipanel

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Ploi Panel design system.
 *
 * Identity: an operator console for servers — calm cool-slate neutrals so dense lists stay
 * readable, one deep teal brand colour (kept from the launcher icon) for primary actions and the
 * selected destination, and a separate set of semantic colours for server states. Every foreground
 * / container pair below was chosen for WCAG AA (≥ 4.5:1 for body text). A state is never carried
 * by colour alone: pills always show a label and an icon.
 */

internal val panelLightColors = lightColorScheme(
    primary = Color(0xFF0B6E62), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE8), onPrimaryContainer = Color(0xFF00201B),
    inversePrimary = Color(0xFF7DD8C7),
    secondary = Color(0xFF4A5A70), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE3EE), onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF7A5900), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08F), onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF7F9FA), onBackground = Color(0xFF161C22),
    surface = Color(0xFFF7F9FA), onSurface = Color(0xFF161C22),
    surfaceVariant = Color(0xFFDEE4E8), onSurfaceVariant = Color(0xFF414A53),
    surfaceTint = Color(0xFF0B6E62),
    surfaceBright = Color(0xFFF7F9FA), surfaceDim = Color(0xFFD7DBDF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F4F6),
    surfaceContainer = Color(0xFFEBEFF2), surfaceContainerHigh = Color(0xFFE5E9EC),
    surfaceContainerHighest = Color(0xFFDFE3E7),
    inverseSurface = Color(0xFF2B3137), inverseOnSurface = Color(0xFFEEF1F4),
    outline = Color(0xFF6F7982), outlineVariant = Color(0xFFC3CAD1),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    scrim = Color.Black
)

internal val panelDarkColors = darkColorScheme(
    primary = Color(0xFF7DD8C7), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046), onPrimaryContainer = Color(0xFF9EF3E1),
    inversePrimary = Color(0xFF0B6E62),
    secondary = Color(0xFFB4C4DA), onSecondary = Color(0xFF1F2F43),
    secondaryContainer = Color(0xFF36465A), onSecondaryContainer = Color(0xFFD6E2F5),
    tertiary = Color(0xFFF2C04D), onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200), onTertiaryContainer = Color(0xFFFFDF9C),
    background = Color(0xFF0F1418), onBackground = Color(0xFFDEE3E8),
    surface = Color(0xFF0F1418), onSurface = Color(0xFFDEE3E8),
    surfaceVariant = Color(0xFF3F484F), onSurfaceVariant = Color(0xFFBEC7CF),
    surfaceTint = Color(0xFF7DD8C7),
    surfaceBright = Color(0xFF353B40), surfaceDim = Color(0xFF0F1418),
    surfaceContainerLowest = Color(0xFF0A0F12), surfaceContainerLow = Color(0xFF171C21),
    surfaceContainer = Color(0xFF1B2126), surfaceContainerHigh = Color(0xFF252B31),
    surfaceContainerHighest = Color(0xFF30363C),
    inverseSurface = Color(0xFFDEE3E8), inverseOnSurface = Color(0xFF2B3137),
    outline = Color(0xFF88919A), outlineVariant = Color(0xFF3F484F),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    scrim = Color.Black
)

/** Semantic state colours; exposed through [PanelTheme.status] rather than raw hex in screens. */
@Immutable
internal data class PanelStatusColors(
    val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val onWarning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val info: Color, val onInfo: Color, val infoContainer: Color, val onInfoContainer: Color
)

internal val panelLightStatus = PanelStatusColors(
    success = Color(0xFF1B6E3A), onSuccess = Color.White,
    successContainer = Color(0xFFCFF0D9), onSuccessContainer = Color(0xFF07210F),
    warning = Color(0xFF7A4F00), onWarning = Color.White,
    warningContainer = Color(0xFFFFE1B0), onWarningContainer = Color(0xFF2A1800),
    info = Color(0xFF1F5FA8), onInfo = Color.White,
    infoContainer = Color(0xFFD6E4FF), onInfoContainer = Color(0xFF001B3E)
)

internal val panelDarkStatus = PanelStatusColors(
    success = Color(0xFF8BD9A2), onSuccess = Color(0xFF00391A),
    successContainer = Color(0xFF0F5129), onSuccessContainer = Color(0xFFC9F5D5),
    warning = Color(0xFFF5BD62), onWarning = Color(0xFF442B00),
    warningContainer = Color(0xFF5E3F00), onWarningContainer = Color(0xFFFFE1B0),
    info = Color(0xFFA8C8FF), onInfo = Color(0xFF003061),
    infoContainer = Color(0xFF0C4383), onInfoContainer = Color(0xFFD6E4FF)
)

internal val LocalPanelStatusColors = staticCompositionLocalOf { panelLightStatus }

/** Spacing scale (4 dp grid) and the minimum touch target used across the app. */
internal object PanelSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val touchTarget = 48.dp
    /** Readable line length for forms and settings on tablets and unfolded foldables. */
    val maxContentWidth = 720.dp
}

internal val panelShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Type scale on the platform sans (no bundled font: nothing to download, native hinting).
 * Hierarchy comes from size and weight; titles tighten slightly, labels stay neutral.
 */
internal val panelTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

/** Monospace for machine values (IPs, fingerprints, ports) so digits align and read unambiguously. */
internal val panelMonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

/** Root theme: Material colour scheme + semantic state colours for the chosen light/dark mode. */
@Composable
internal fun PloiPanelTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPanelStatusColors provides if (dark) panelDarkStatus else panelLightStatus) {
        MaterialTheme(
            colorScheme = if (dark) panelDarkColors else panelLightColors,
            typography = panelTypography,
            shapes = panelShapes,
            content = content
        )
    }
}

internal object PanelTheme {
    val status: PanelStatusColors
        @Composable @ReadOnlyComposable get() = LocalPanelStatusColors.current
}
