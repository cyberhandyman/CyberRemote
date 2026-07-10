package dev.companionremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.companionremote.app.data.AppSkin
import dev.companionremote.app.data.ThemeMode
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.i18n.currentSystemLanguage
import dev.companionremote.app.i18n.resolveStrings
import dev.companionremote.app.theme.LocalGlass
import dev.companionremote.app.theme.skinBackground
import dev.companionremote.app.theme.skinColorScheme
import dev.companionremote.app.theme.skinGlass
import dev.companionremote.app.ui.DeviceListScreen
import dev.companionremote.app.ui.PairingScreen
import dev.companionremote.app.ui.RemoteScreen
import dev.companionremote.app.ui.SettingsScreen
import dev.companionremote.app.ui.UpdateDialog

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val skin by viewModel.skin.collectAsState()
            CompanionTheme(themeMode, skin) {
                val screen by viewModel.screen.collectAsState()
                val language by viewModel.language.collectAsState()
                val strings = resolveStrings(language, currentSystemLanguage())
                CompositionLocalProvider(LocalAppStrings provides strings) {
                    Surface(color = Color.Transparent) {
                        when (val current = screen) {
                            is Screen.DeviceList -> DeviceListScreen(viewModel)
                            is Screen.Settings -> SettingsScreen(viewModel)
                            is Screen.Pairing -> PairingScreen(viewModel, current.device)
                            is Screen.Remote -> RemoteScreen(viewModel, current.device)
                        }
                    }
                    val updateState by viewModel.updateState.collectAsState()
                    UpdateDialog(
                        state = updateState,
                        onDownload = viewModel::downloadUpdate,
                        onInstall = viewModel::installUpdate,
                        onDismiss = viewModel::dismissUpdate,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }
}

@Composable
fun CompanionTheme(themeMode: ThemeMode, skin: AppSkin, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(colorScheme = skinColorScheme(skin, dark)) {
        CompositionLocalProvider(LocalGlass provides skinGlass(dark)) {
            Box(Modifier.fillMaxSize().background(skinBackground(skin, dark))) {
                content()
            }
        }
    }
}
