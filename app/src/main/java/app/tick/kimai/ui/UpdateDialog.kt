package app.tick.kimai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tick.kimai.MainViewModel
import app.tick.kimai.UiState
import app.tick.kimai.data.Updater

/** Offers a new release: download it, then hand it to Android's installer. Also the "up to date" reply. */
@Composable
fun UpdateDialog(
    vm: MainViewModel,
    ui: UiState,
) {
    val context = LocalContext.current
    var needsPermission by remember { mutableStateOf(false) }

    if (ui.upToDate) {
        AlertDialog(
            onDismissRequest = vm::clearUpToDate,
            title = { Text("You're up to date") },
            confirmButton = { TextButton(onClick = vm::clearUpToDate) { Text("OK") } },
        )
    }
    val info = ui.update?.takeIf { ui.showUpdate } ?: return

    // Once the download finishes, hand the file straight to Android's installer.
    LaunchedEffect(ui.updateFile) {
        ui.updateFile?.let {
            context.startActivity(Updater.installIntent(context, it))
            vm.clearUpdateFile()
        }
    }

    AlertDialog(
        onDismissRequest = vm::closeUpdate,
        title = { Text("Update available: ${info.version}") },
        text = {
            val scroll = rememberScrollState()
            Column {
                Text(
                    if (needsPermission) {
                        "Android needs your OK to install apps from Tick. " +
                            "Allow it on the page that opened, go back, then tap Install again."
                    } else {
                        "Tap Install. Android will ask you to confirm. Your data and settings are kept."
                    },
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    // A line shows only while there is more to scroll to in that direction.
                    if (scroll.canScrollBackward) HorizontalDivider()
                    Column(Modifier.weight(1f, fill = false).heightIn(max = 280.dp).verticalScroll(scroll)) {
                        Notes(info.notes)
                    }
                    if (scroll.canScrollForward) HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !ui.updateDownloading,
                onClick = {
                    if (Updater.canInstall(context)) {
                        needsPermission = false
                        vm.downloadUpdate()
                    } else {
                        needsPermission = true
                        context.startActivity(Updater.installPermissionIntent(context))
                    }
                },
            ) { Text(if (ui.updateDownloading) "Downloading..." else "Install") }
        },
        dismissButton = {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Release page") }
        },
    )
}

/** Renders the release notes, which are markdown with "### Heading" and "- item" lines. */
@Composable
private fun Notes(notes: String) {
    notes.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        when {
            line.startsWith("#") ->
                Text(
                    line.trimStart('#').trim(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            line.startsWith("- ") || line.startsWith("* ") ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(line.drop(2), style = MaterialTheme.typography.bodyMedium)
                }
            else -> Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
