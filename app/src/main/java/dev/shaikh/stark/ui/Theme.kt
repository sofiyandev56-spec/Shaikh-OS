package dev.shaikh.stark.ui

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** STARK palette: matte black and dark titanium, cyan for live state, amber reserved for attention. */
object Stark {
    val Void = Color(0xFF07090C)
    val Titanium = Color(0xFF11151A)
    val TitaniumRaised = Color(0xFF181D24)
    val Line = Color(0xFF242C35)
    val Text = Color(0xFFE6EDF3)
    val TextDim = Color(0xFF8B98A5)
    val TextFaint = Color(0xFF56626E)
    val Cyan = Color(0xFF3FD8FF)
    val CyanDeep = Color(0xFF14708A)
    val Core = Color(0xFFDDF8FF)
    val Amber = Color(0xFFFFB547)
    val Alert = Color(0xFFFF6B5E)
}

private val StarkColors = darkColorScheme(
    primary = Stark.Cyan,
    onPrimary = Stark.Void,
    secondary = Stark.Amber,
    background = Stark.Void,
    onBackground = Stark.Text,
    surface = Stark.Titanium,
    onSurface = Stark.Text,
    surfaceVariant = Stark.TitaniumRaised,
    onSurfaceVariant = Stark.TextDim,
    outline = Stark.Line,
    error = Stark.Alert,
)

private val StarkType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 12.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.5.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
    ),
)

@Composable
fun StarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StarkColors, typography = StarkType, content = content)
}

/** Honours the system "Remove animations" setting. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
