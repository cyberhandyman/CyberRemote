package dev.companionremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import dev.companionremote.app.data.ThemeMode
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.i18n.currentSystemLanguage
import dev.companionremote.app.i18n.resolveStrings
import dev.companionremote.app.ui.DeviceListScreen
import dev.companionremote.app.ui.PairingScreen
import dev.companionremote.app.ui.RemoteScreen
import dev.companionremote.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            CompanionTheme(themeMode) {
                val screen by viewModel.screen.collectAsState()
                val language by viewModel.language.collectAsState()
                val strings = resolveStrings(language, currentSystemLanguage())
                CompositionLocalProvider(LocalAppStrings provides strings) {
                    Surface {
                        when (val current = screen) {
                            is Screen.DeviceList -> DeviceListScreen(viewModel)
                            is Screen.Settings -> SettingsScreen(viewModel)
                            is Screen.Pairing -> PairingScreen(viewModel, current.device)
                            is Screen.Remote -> RemoteScreen(viewModel, current.device)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF0A1022),
    primaryContainer = Color(0xFF283452),
    onPrimaryContainer = Color(0xFFD6E0FF),
    secondary = Color(0xFF9AA5CE),
    onSecondary = Color(0xFF10141F),
    secondaryContainer = Color(0xFF232A3B),
    onSecondaryContainer = Color(0xFFD3DAF0),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF11141B),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1B2029),
    onSurfaceVariant = Color(0xFF9CA3B4),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF14171F),
    surfaceContainer = Color(0xFF161A22),
    surfaceContainerHigh = Color(0xFF1E232D),
    surfaceContainerHighest = Color(0xFF252B37),
    outline = Color(0xFF2E3542),
    outlineVariant = Color(0xFF222834),
    error = Color(0xFFF7768E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0A1B4D),
    secondary = Color(0xFF5A6478),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E7F3),
    onSecondaryContainer = Color(0xFF1A2233),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF1A1C22),
    surface = Color(0xFFF7F8FB),
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFE6E9F0),
    onSurfaceVariant = Color(0xFF5A6072),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3F8),
    surfaceContainer = Color(0xFFEEF1F6),
    surfaceContainerHigh = Color(0xFFE8ECF3),
    surfaceContainerHighest = Color(0xFFE1E6EF),
    outline = Color(0xFFC4CAD6),
    outlineVariant = Color(0xFFDCE0E9),
    error = Color(0xFFD03A56),
)

@Composable
fun CompanionTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
