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
import dev.companionremote.app.ui.DeviceListScreen
import dev.companionremote.app.ui.PairingScreen
import dev.companionremote.app.ui.RemoteScreen

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompanionTheme {
                val screen by viewModel.screen.collectAsState()
                Surface {
                    when (val current = screen) {
                        is Screen.DeviceList -> DeviceListScreen(viewModel)
                        is Screen.Pairing -> PairingScreen(viewModel, current.device)
                        is Screen.Remote -> RemoteScreen(viewModel, current.device)
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
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF9AA6B2),
    surface = Color(0xFF14181D),
    background = Color(0xFF101418),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5FD0),
    secondary = Color(0xFF5B6770),
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
