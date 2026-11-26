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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tick.kimai.MainViewModel
import app.tick.kimai.R
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
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.week_start)) },
                supportingContent = { Text(weekStartLabel(ui.firstWeekday)) },
            )
            HorizontalDivider()
            SettingRow(stringResource(R.string.time_format), timeLabel(ui.timeMode), onTime)
            SettingRow(stringResource(R.string.week_goals_view), stripLabel(ui.weekStrip), onStrip)
            SettingRow(stringResource(R.string.weekly_target), null, onTarget)
            SettingRow(stringResource(R.string.pay_rate_tax), null, onPay)
            SettingRow(stringResource(R.string.start_presets), null, onPresets)
            ListItem(
                headlineContent = { Text(stringResource(R.string.description_field)) },
                supportingContent = { Text(stringResource(R.string.description_field_hint)) },
                trailingContent = {
                    Switch(checked = !ui.hideDescription, onCheckedChange = { vm.setHideDescription(!it) })
                },
                modifier = Modifier.clickable { vm.setHideDescription(!ui.hideDescription) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.check_updates)) },
                supportingContent = { Text(stringResource(R.string.check_updates_hint)) },
                trailingContent = {
                    Switch(checked = ui.updateCheck, onCheckedChange = vm::setUpdateCheck)
                },
                modifier = Modifier.clickable { vm.setUpdateCheck(!ui.updateCheck) },
            )
            SettingRow(
                stringResource(R.string.check_now),
                ui.update?.let { stringResource(R.string.update_version_available, it.version) },
            ) { if (ui.update != null) vm.openUpdate() else vm.checkForUpdate(manual = true) }
            HorizontalDivider()
            TextButton(onClick = vm::logout, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
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

@Composable
private fun timeLabel(mode: Int) =
    when (mode) {
        TimeMode.H12 -> stringResource(R.string.time_12h)
        TimeMode.H24 -> stringResource(R.string.time_24h)
        else -> stringResource(R.string.time_follow_system)
    }

@Composable
private fun stripLabel(mode: Int) =
    when (mode) {
        1 -> stringResource(R.string.strip_compact)
        2 -> stringResource(R.string.strip_hidden)
        else -> stringResource(R.string.strip_full)
    }
