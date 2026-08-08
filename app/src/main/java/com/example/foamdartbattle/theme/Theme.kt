package com.example.foamdartbattle.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class GameTheme {
    CYBERPUNK,
    TACTICAL,
    PLAYFUL
}

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    secondary = CyberpunkSecondary,
    tertiary = CyberpunkTertiary,
    background = CyberpunkBackground,
    surface = CyberpunkSurface,
    onPrimary = CyberpunkBackground,
    onSecondary = CyberpunkOnSurface,
    onTertiary = CyberpunkOnSurface,
    onBackground = CyberpunkOnBackground,
    onSurface = CyberpunkOnSurface,
    surfaceVariant = CyberpunkSurface.copy(alpha = 0.9f)
)

private val TacticalColorScheme = darkColorScheme(
    primary = TacticalPrimary,
    secondary = TacticalSecondary,
    tertiary = TacticalTertiary,
    background = TacticalBackground,
    surface = TacticalSurface,
    onPrimary = TacticalBackground,
    onSecondary = TacticalOnSurface,
    onTertiary = TacticalOnSurface,
    onBackground = TacticalOnBackground,
    onSurface = TacticalOnSurface,
    surfaceVariant = TacticalSurface.copy(alpha = 0.9f)
)

private val PlayfulColorScheme = lightColorScheme(
    primary = PlayfulPrimary,
    secondary = PlayfulSecondary,
    tertiary = PlayfulTertiary,
    background = PlayfulBackground,
    surface = PlayfulSurface,
    onPrimary = PlayfulBackground,
    onSecondary = PlayfulOnSurface,
    onTertiary = PlayfulOnSurface,
    onBackground = PlayfulOnBackground,
    onSurface = PlayfulOnSurface,
    surfaceVariant = PlayfulSurface.copy(alpha = 0.9f)
)

@Composable
fun FoamDartBattleTheme(
    theme: GameTheme = GameTheme.CYBERPUNK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        GameTheme.CYBERPUNK -> CyberpunkColorScheme
        GameTheme.TACTICAL -> TacticalColorScheme
        GameTheme.PLAYFUL -> PlayfulColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
