package com.liubai.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal700,
    secondary = Amber500,
    onSecondary = Color(0xFF2A1B03),
    secondaryContainer = Amber100,
    onSecondaryContainer = Amber700,
    background = PaperPage,
    onBackground = PaperTextPrimary,
    surface = PaperCard,
    onSurface = PaperTextPrimary,
    surfaceVariant = PaperWindow,
    onSurfaceVariant = PaperTextSecondary,
    outline = PaperBorderStrong,
    outlineVariant = PaperBorder,
    error = Danger,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Teal300,
    onPrimary = Color(0xFF06231B),
    primaryContainer = Color(0xFF123328),
    onPrimaryContainer = Teal300,
    secondary = Amber400,
    onSecondary = Color(0xFF2A1B03),
    secondaryContainer = Color(0xFF3A2B10),
    onSecondaryContainer = Amber300,
    background = InkPage,
    onBackground = InkTextPrimary,
    surface = InkCard,
    onSurface = InkTextPrimary,
    surfaceVariant = InkCardElevated,
    onSurfaceVariant = InkTextSecondary,
    outline = InkBorderStrong,
    outlineVariant = InkBorder,
    error = Color(0xFFFF6B6E),
    onError = Color(0xFF3A0A0C),
)

/** 主题无法直接映射的补充 token（页面背景/图表色/文字层级） */
data class LiubaiExtraColors(
    val window: Color,
    val page: Color,
    val card: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val catScreen: Color,
    val catFocus: Color,
    val catInfo: Color,
    val catSocial: Color,
    val catEnt: Color,
    val success: Color,
    val warning: Color,
    val ringTrack: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    LiubaiExtraColors(
        window = PaperWindow, page = PaperPage, card = PaperCard, divider = PaperDivider,
        textPrimary = PaperTextPrimary, textSecondary = PaperTextSecondary, textTertiary = PaperTextTertiary,
        catScreen = CatScreen, catFocus = CatFocus, catInfo = CatInfo, catSocial = CatSocial, catEnt = CatEnt,
        success = Success, warning = Amber500, ringTrack = PaperDivider,
    )
}

private val LightExtras = LiubaiExtraColors(
    window = PaperWindow, page = PaperPage, card = PaperCard, divider = PaperDivider,
    textPrimary = PaperTextPrimary, textSecondary = PaperTextSecondary, textTertiary = PaperTextTertiary,
    catScreen = CatScreen, catFocus = CatFocus, catInfo = CatInfo, catSocial = CatSocial, catEnt = CatEnt,
    success = Success, warning = Amber600, ringTrack = PaperDivider,
)

private val DarkExtras = LiubaiExtraColors(
    window = InkWindow, page = InkPage, card = InkCard, divider = InkDivider,
    textPrimary = InkTextPrimary, textSecondary = InkTextSecondary, textTertiary = InkTextTertiary,
    catScreen = Teal300, catFocus = Amber400, catInfo = Color(0xFF8FA6F5), catSocial = Color(0xFFF08BB6), catEnt = Color(0xFFB3A9F0),
    success = Teal300, warning = Amber400, ringTrack = InkDivider,
)

/**
 * 应用主题。[darkTheme] 由上层按用户设置计算（domain 的 ThemeMode 在 feature 层翻译成布尔值），
 * 以此保持 designsystem 不依赖 domain。
 */
@Composable
fun LiubaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dark = darkTheme
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = LiubaiTypography,
            shapes = LiubaiShapes,
            content = content,
        )
    }
}
