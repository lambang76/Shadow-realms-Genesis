package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NocturneDarkColorScheme = darkColorScheme(
    primary = EmberGold,
    onPrimary = AbyssalObsidian,
    primaryContainer = DeepBronze,
    onPrimaryContainer = EmberGoldBright,
    secondary = CrimsonBlood,
    onSecondary = AgedParchment,
    secondaryContainer = DriedBloodDark,
    onSecondaryContainer = AgedParchment,
    tertiary = SpectralTeal,
    onTertiary = AbyssalObsidian,
    tertiaryContainer = SpectralTealDark,
    onTertiaryContainer = AgedParchment,
    background = AbyssalObsidian,
    onBackground = AgedParchment,
    surface = CryptSlate,
    onSurface = AgedParchment,
    surfaceVariant = WeatheredBasalt,
    onSurfaceVariant = AshSilver,
    surfaceContainer = WeatheredBasalt,
    surfaceContainerHigh = ColorTokens.ElevatedStone,
    outline = CarvedStoneBorder,
    outlineVariant = MutedStone,
    error = CrimsonBright,
    onError = AbyssalObsidian
)

private object ColorTokens {
    val ElevatedStone = androidx.compose.ui.graphics.Color(0xFF262230)
}

private val GothicShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun NocturneAbyssTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NocturneDarkColorScheme,
        typography = Typography,
        shapes = GothicShapes,
        content = content
    )
}
