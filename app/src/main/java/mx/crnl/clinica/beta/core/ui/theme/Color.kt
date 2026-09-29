package mx.crnl.clinica.beta.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Color(0xFFB01E2D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD9),
    onPrimaryContainer = Color(0xFF410009),
    secondary = Color(0xFF4F5B66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE3EA),
    onSecondaryContainer = Color(0xFF0F1D2A),
    tertiary = Color(0xFF1F6F5F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0F0E1),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8F8),
    onBackground = Color(0xFF1F1A1B),
    surface = Color(0xFFFBF8F8),
    onSurface = Color(0xFF1F1A1B),
    surfaceVariant = Color(0xFFF1E9E9),
    onSurfaceVariant = Color(0xFF4F4445),
    outline = Color(0xFF857374),
    outlineVariant = Color(0xFFD8C2C2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1F1),
    surfaceContainer = Color(0xFFF1EBEB),
    surfaceContainerHigh = Color(0xFFEBE5E5),
    surfaceContainerHighest = Color(0xFFE5DFDF),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3B1),
    onPrimary = Color(0xFF650012),
    primaryContainer = Color(0xFF8C0F20),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFBBC7D4),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3B4957),
    onSecondaryContainer = Color(0xFFD8E4F1),
    tertiary = Color(0xFF88D6C2),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005142),
    onTertiaryContainer = Color(0xFFA4F2DD),
    // Un rojo más saturado que el primario rosado: así el error se distingue del foco y de los enlaces (5,8:1 sobre el fondo).
    error = Color(0xFFFF5449),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF171314),
    onBackground = Color(0xFFEBE0E0),
    surface = Color(0xFF171314),
    onSurface = Color(0xFFEBE0E0),
    surfaceVariant = Color(0xFF524343),
    onSurfaceVariant = Color(0xFFD7C1C1),
    outline = Color(0xFF9F8C8C),
    outlineVariant = Color(0xFF524343),
    surfaceContainerLowest = Color(0xFF120E0F),
    surfaceContainerLow = Color(0xFF1F1A1B),
    surfaceContainer = Color(0xFF231E1F),
    surfaceContainerHigh = Color(0xFF2E2829),
    surfaceContainerHighest = Color(0xFF393334),
)

/** Colores de estado que Material 3 no define (éxito y advertencia). */
@Immutable
class StatusColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightStatusColors = StatusColors(
    successContainer = Color(0xFFCFEFD6),
    onSuccessContainer = Color(0xFF00210B),
    warningContainer = Color(0xFFFFE7B3),
    onWarningContainer = Color(0xFF3D2A00),
)

internal val DarkStatusColors = StatusColors(
    successContainer = Color(0xFF1F5130),
    onSuccessContainer = Color(0xFFB2F0C0),
    warningContainer = Color(0xFF5A4200),
    onWarningContainer = Color(0xFFFFDF9A),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
