package dev.companionremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.DeviceVerify
import dev.companionremote.app.R
import dev.companionremote.app.data.AppSkin
import dev.companionremote.app.data.HapticStrength
import dev.companionremote.app.data.ThemeMode
import dev.companionremote.app.i18n.AppLanguage
import dev.companionremote.app.i18n.FEEDBACK_EMAIL
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.theme.skinAccentPreview
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val s = LocalAppStrings.current
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val skin by viewModel.skin.collectAsState()
    val fetchIcons by viewModel.fetchAppIcons.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val hapticStrength by viewModel.hapticStrength.collectAsState()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsState()
    val autoDownloadUpdates by viewModel.autoDownloadUpdates.collectAsState()
    val paired by viewModel.pairedDevices.collectAsState()
    val activeDevice by viewModel.activeDeviceName.collectAsState()
    val deviceVerify by viewModel.deviceVerify.collectAsState()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                title = { Text(s.settings, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeSettings() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Paired Apple TVs (top of the list)
            SectionTitle(s.pairedDevices)
            SettingsCard {
                if (paired.isEmpty()) {
                    Text(
                        s.noPairedDevices,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    paired.forEach { name ->
                        PairedDeviceRow(
                            name = name,
                            inUse = name == activeDevice,
                            verify = deviceVerify[name] ?: DeviceVerify.Idle,
                            onRefresh = { viewModel.verifyDevice(name) },
                            onForget = { viewModel.forgetDeviceByName(name) },
                        )
                    }
                }
            }

            // Appearance
            SectionTitle(s.theme)
            SettingsCard {
                OptionRow(s.themeSystem, themeMode == ThemeMode.System) { viewModel.setThemeMode(ThemeMode.System) }
                OptionRow(s.themeLight, themeMode == ThemeMode.Light) { viewModel.setThemeMode(ThemeMode.Light) }
                OptionRow(s.themeDark, themeMode == ThemeMode.Dark) { viewModel.setThemeMode(ThemeMode.Dark) }
            }

            // Skin
            SectionTitle(s.skin)
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SkinSwatch(s.skinMidnight, AppSkin.Midnight, skin) { viewModel.setSkin(AppSkin.Midnight) }
                    SkinSwatch(s.skinGraphite, AppSkin.Graphite, skin) { viewModel.setSkin(AppSkin.Graphite) }
                    SkinSwatch(s.skinAurora, AppSkin.Aurora, skin) { viewModel.setSkin(AppSkin.Aurora) }
                    SkinSwatch(s.skinSunset, AppSkin.Sunset, skin) { viewModel.setSkin(AppSkin.Sunset) }
                }
            }

            // Button feedback (haptics)
            SectionTitle(s.haptics)
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(s.hapticVibrate, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            s.hapticVibrateDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = hapticEnabled, onCheckedChange = { viewModel.setHapticEnabled(it) })
                }
                if (hapticEnabled) {
                    Text(
                        s.hapticStrength,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                    OptionRow(s.hapticLight, hapticStrength == HapticStrength.Light) { viewModel.setHapticStrength(HapticStrength.Light) }
                    OptionRow(s.hapticMedium, hapticStrength == HapticStrength.Medium) { viewModel.setHapticStrength(HapticStrength.Medium) }
                    OptionRow(s.hapticStrong, hapticStrength == HapticStrength.Strong) { viewModel.setHapticStrength(HapticStrength.Strong) }
                }
            }

            // Language
            SectionTitle(s.language)
            SettingsCard {
                OptionRow(s.languageSystem, language == AppLanguage.System) { viewModel.setLanguage(AppLanguage.System) }
                OptionRow(s.languageEnglish, language == AppLanguage.English) { viewModel.setLanguage(AppLanguage.English) }
                OptionRow(s.languageChinese, language == AppLanguage.Chinese) { viewModel.setLanguage(AppLanguage.Chinese) }
            }

            // App icons (network opt-in)
            SectionTitle(s.tabApps)
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(s.appIcons, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            s.appIconsDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = fetchIcons, onCheckedChange = { viewModel.setFetchAppIcons(it) })
                }
            }

            // Feedback
            SectionTitle(s.sendFeedback)
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        clipboard.setText(AnnotatedString(FEEDBACK_EMAIL))
                        scope.launch { snackbarHostState.showMessage(s.emailCopied) }
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.MailOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(FEEDBACK_EMAIL, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            s.feedbackDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Updates
            SectionTitle(s.updates)
            SettingsCard {
                ToggleRow(
                    title = s.autoCheckUpdates,
                    desc = s.autoCheckUpdatesDesc,
                    checked = autoCheckUpdates,
                    onCheckedChange = { viewModel.setAutoCheckUpdates(it) },
                )
                ToggleRow(
                    title = s.autoDownloadUpdates,
                    desc = s.autoDownloadUpdatesDesc,
                    checked = autoDownloadUpdates,
                    onCheckedChange = { viewModel.setAutoDownloadUpdates(it) },
                )
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.checkForUpdates(manual = true) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.checkNow, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            s.currentVersion.format(dev.companionremote.app.BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // About
            SectionTitle(s.about)
            Text(
                s.author,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Text(
                s.aboutText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private suspend fun SnackbarHostState.showMessage(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

private val PairedOkGreen = Color(0xFF34C759)

@Composable
private fun PairedDeviceRow(
    name: String,
    inUse: Boolean,
    verify: DeviceVerify,
    onRefresh: () -> Unit,
    onForget: () -> Unit,
) {
    val s = LocalAppStrings.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_apple),
            contentDescription = null,
            Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (verify == DeviceVerify.Ok) {
                    Spacer(Modifier.size(8.dp))
                    Box(Modifier.size(9.dp).clip(CircleShape).background(PairedOkGreen))
                }
            }
            when {
                inUse -> Text(
                    s.inUse,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                verify == DeviceVerify.Ok -> Text(
                    s.paired,
                    style = MaterialTheme.typography.labelSmall,
                    color = PairedOkGreen,
                )
                verify == DeviceVerify.Failed -> Text(
                    s.atvUnreachable,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (verify == DeviceVerify.Checking) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = s.checkNow,
                    tint = if (verify == DeviceVerify.Ok) PairedOkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onForget) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = s.forget, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SkinSwatch(label: String, skin: AppSkin, current: AppSkin, onSelect: () -> Unit) {
    val selected = skin == current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onSelect).padding(4.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(skinAccentPreview(skin))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
