package com.qrcommunication.ploipanel

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val panelLightColors = lightColorScheme(
    primary = Color(0xFF006B5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5E9), onPrimaryContainer = Color(0xFF00382F),
    secondary = Color(0xFF435976), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8FA), onSecondaryContainer = Color(0xFF172C47),
    tertiary = Color(0xFF805C24), tertiaryContainer = Color(0xFFFFE3B4),
    background = Color(0xFFF5F8F7), onBackground = Color(0xFF172722),
    surface = Color(0xFFFEFFFC), onSurface = Color(0xFF172722),
    surfaceVariant = Color(0xFFE5EDE9), onSurfaceVariant = Color(0xFF465852),
    outlineVariant = Color(0xFFCCD9D2),
    error = Color(0xFFB32632)
)

internal val panelDarkColors = darkColorScheme(
    primary = Color(0xFF83D9BE), onPrimary = Color(0xFF003B30),
    primaryContainer = Color(0xFF155848), onPrimaryContainer = Color(0xFFD5F5E9),
    secondary = Color(0xFFACC7ED), secondaryContainer = Color(0xFF344D6D),
    tertiary = Color(0xFFF4C77F), tertiaryContainer = Color(0xFF604118),
    background = Color(0xFF0E1715), onBackground = Color(0xFFE1ECE6),
    surface = Color(0xFF15211D), onSurface = Color(0xFFE1ECE6),
    surfaceVariant = Color(0xFF26352F), onSurfaceVariant = Color(0xFFC1D2C9),
    outlineVariant = Color(0xFF40564D),
    error = Color(0xFFFFB3B7)
)

internal val panelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

internal val panelTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
)
