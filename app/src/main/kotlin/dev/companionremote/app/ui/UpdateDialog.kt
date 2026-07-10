package dev.companionremote.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.companionremote.app.UpdateState
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.update.AppUpdater

/**
 * Renders the in-app update prompt for the current [state]. Silent for Idle
 * and background checks; shows a dialog for everything the user should see.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    fun openGitHub() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdater.RELEASES_PAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(s.updateAvailable) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(s.updateAvailableMsg.format(state.info.versionName))
                    if (state.info.notes.isNotBlank()) {
                        Text(
                            state.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDownload) { Text(s.download) } },
            dismissButton = {
                Column {
                    TextButton(onClick = { openGitHub() }) { Text(s.viewOnGitHub) }
                    TextButton(onClick = onDismiss) { Text(s.later) }
                }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = { /* keep visible while downloading */ },
            title = { Text(s.updateDownloading) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
        )

        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(s.updateAvailable) },
            text = { Text(s.updateReady) },
            confirmButton = { TextButton(onClick = onInstall) { Text(s.install) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(s.later) } },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(s.updateFailed) },
            text = { state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { openGitHub() }) { Text(s.viewOnGitHub) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(s.later) } },
        )

        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(s.updates) },
            text = { Text(s.upToDate) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(s.later) } },
        )

        is UpdateState.Checking -> if (state.manual) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(s.checkingUpdates) },
                text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
                confirmButton = {},
            )
        }

        UpdateState.Idle -> Unit
    }
}
