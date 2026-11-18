package app.tick.kimai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tick.kimai.MainViewModel
import app.tick.kimai.UiState
import app.tick.kimai.util.TimeMode

/** Everything that used to crowd the overflow menu. Each row opens the same dialog it did there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    ui: UiState,
    onBack: () -> Unit,
    onTime: () -> Unit,
    onStrip: () -> Unit,
    onTarget: () -> Unit,
    onPay: () -> Unit,
    onPresets: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Week start") },
                supportingContent = { Text(weekStartLabel(ui.firstWeekday)) },
            )
            HorizontalDivider()
            SettingRow("Time format", timeLabel(ui.timeMode), onTime)
            SettingRow("Week goals view", stripLabel(ui.weekStrip), onStrip)
            SettingRow("Weekly target", null, onTarget)
            SettingRow("Pay rate & tax", null, onPay)
            SettingRow("Start presets", null, onPresets)
            ListItem(
                headlineContent = { Text("Description field") },
                supportingContent = { Text("Show it when starting a timer") },
                trailingContent = {
                    Switch(checked = !ui.hideDescription, onCheckedChange = { vm.setHideDescription(!it) })
                },
                modifier = Modifier.clickable { vm.setHideDescription(!ui.hideDescription) },
            )
            ListItem(
                headlineContent = { Text("Check for updates") },
                supportingContent = { Text("On open, looks for a new Tick release on GitHub") },
                trailingContent = {
                    Switch(checked = ui.updateCheck, onCheckedChange = vm::setUpdateCheck)
                },
                modifier = Modifier.clickable { vm.setUpdateCheck(!ui.updateCheck) },
            )
            SettingRow(
                "Check now",
                ui.update?.let { "${it.version} available" },
            ) { if (ui.update != null) vm.openUpdate() else vm.checkForUpdate(manual = true) }
            HorizontalDivider()
            TextButton(onClick = vm::logout, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
) = ListItem(
    headlineContent = { Text(title) },
    supportingContent = value?.let { { Text(it) } },
    modifier = Modifier.clickable(onClick = onClick),
)

private fun timeLabel(mode: Int) =
    when (mode) {
        TimeMode.H12 -> "12-hour"
        TimeMode.H24 -> "24-hour"
        else -> "Follow system"
    }

private fun stripLabel(mode: Int) =
    when (mode) {
        1 -> "Compact"
        2 -> "Hidden"
        else -> "Full"
    }
