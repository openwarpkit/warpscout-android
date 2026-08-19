package io.github.openwarpkit.warpscout.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

private val LightColors = lightColorScheme(
    primary = Color(0xFF1956A3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E7FF),
    onPrimaryContainer = Color(0xFF061B36),
    secondary = Color(0xFF4D5F76),
    secondaryContainer = Color(0xFFDCE4EF),
    onSecondaryContainer = Color(0xFF101820),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF161A20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF161A20),
    surfaceVariant = Color(0xFFE5E9EF),
    onSurfaceVariant = Color(0xFF414750),
    outline = Color(0xFF737A84),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF062E62),
    primaryContainer = Color(0xFF174780),
    onPrimaryContainer = Color(0xFFD8E7FF),
    secondary = Color(0xFFBBC8D9),
    secondaryContainer = Color(0xFF35465A),
    onSecondaryContainer = Color(0xFFDCE4EF),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE2E5EA),
    surface = Color(0xFF101318),
    onSurface = Color(0xFFE2E5EA),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFC2C7D0),
    outline = Color(0xFF8C929C),
    error = Color(0xFFFFB4AB)
)

private val WarpScoutShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun WarpScoutTheme(
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, shapes = WarpScoutShapes, content = content)
}
