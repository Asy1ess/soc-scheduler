package com.soc.scheduler.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soc.scheduler.Graph
import com.soc.scheduler.data.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Navy = Color(0xFF0F2740)
private val NavyLight = Color(0xFF1D3E5F)
private val Cyan = Color(0xFF00ACC1)
private val Amber = Color(0xFFFFA000)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F0),
    onPrimaryContainer = Navy,
    secondary = Cyan,
    onSecondary = Color.White,
    tertiary = Amber,
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC5E8),
    onPrimary = Navy,
    primaryContainer = NavyLight,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4DD0E1),
    tertiary = Color(0xFFFFCA28),
    background = Color(0xFF111417),
    surface = Color(0xFF191C1F),
    surfaceVariant = Color(0xFF2A2F34),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SocSchedulerTheme(
    content: @Composable () -> Unit,
) {
    val mode by Graph.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            // 내비게이션 바도 같이 맞춰야 밝은 테마에서 회색 스크림이 남지 않는다.
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
