package app.tick.kimai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.tick.kimai.MainViewModel
import app.tick.kimai.R
import app.tick.kimai.UiState
import app.tick.kimai.data.Entry
import app.tick.kimai.util.Fmt
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Reads "1.5" or "1,5". Null when the text is empty or not a number. */
private fun parseDecimal(text: String): Double? = text.replace(',', '.').trim().toDoubleOrNull()

private fun LocalDateTime.toMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()

/** The dialog shell shared by every form here: a title, the fields, and Cancel / Save. */
@Composable
private fun SaveDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    saveLabel: String = stringResource(R.string.save),
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) },
        confirmButton = { TextButton(enabled = saveEnabled, onClick = onSave) { Text(saveLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DecimalField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The optional description box, shared by the timer card and the add and edit dialogs. */
@Composable
internal fun DescriptionField(
    value: String,
    onChange: (String) -> Unit,
    label: String = stringResource(R.string.description_optional),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

// Week target, start presets and pay

@Composable
internal fun WeekTargetDialog(
    currentSeconds: Long,
    currentDays: Int,
    firstWeekday: String,
    onDismiss: () -> Unit,
    onSave: (Double?, Int) -> Unit,
) {
    var text by remember {
        mutableStateOf(if (currentSeconds > 0) (currentSeconds / 3600.0).toString().removeSuffix(".0") else "")
    }
    var days by remember { mutableIntStateOf(currentDays) }
    SaveDialog(stringResource(R.string.weekly_target), onDismiss, onSave = { onSave(parseDecimal(text), days) }) {
        DecimalField(
            text,
            { text = it },
            stringResource(R.string.hours_per_week),
            stringResource(R.string.weekly_target_hint),
        )
        Text(stringResource(R.string.days_you_work), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // Start on the week's first day, like the goals under the pace line.
            val first =
                runCatching { DayOfWeek.valueOf(firstWeekday.uppercase()) }
                    .getOrDefault(DayOfWeek.MONDAY)
            (0L..6L).map { first.plus(it) }.forEach { d ->
                val bit = 1 shl (d.value - 1)
                FilterChip(
                    selected = days and bit != 0,
                    onClick = { days = days xor bit },
                    label = { Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            stringResource(R.string.days_you_work_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PresetsDialog(
    current: List<Int>,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
) {
    var text by remember { mutableStateOf(current.joinToString(", ")) }
    SaveDialog(
        stringResource(R.string.start_presets),
        onDismiss,
        onSave = {
            onSave(
                Regex("\\D+").split(text).mapNotNull { it.toIntOrNull() }.filter { it > 0 }.distinct().sorted().take(3),
            )
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.minutes_ago)) },
            supportingText = { Text(stringResource(R.string.presets_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun PayDialog(
    currentRate: Float,
    currentTaxPercent: Float,
    onDismiss: () -> Unit,
    onSave: (rate: Double?, taxPercent: Double?) -> Unit,
) {
    var rateText by remember { mutableStateOf(if (currentRate > 0) currentRate.toString() else "") }
    var taxText by remember { mutableStateOf(if (currentTaxPercent > 0) currentTaxPercent.toString() else "") }
    SaveDialog(
        stringResource(R.string.pay_rate_tax),
        onDismiss,
        onSave = { onSave(parseDecimal(rateText), parseDecimal(taxText)) },
    ) {
        DecimalField(
            rateText,
            { rateText = it },
            stringResource(R.string.hourly_rate),
            stringResource(R.string.hourly_rate_hint),
        )
        DecimalField(
            taxText,
            { taxText = it },
            stringResource(R.string.tax_withheld),
            stringResource(R.string.tax_hint),
        )
    }
}

// Edit entry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditEntryDialog(
    entry: Entry,
    showDescription: Boolean,
    mode: Int,
    onDismiss: () -> Unit,
    onSave: (description: String, beginMillis: Long, endMillis: Long?) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val running = entry.isRunning

    var description by remember(entry.id) { mutableStateOf(entry.description.orEmpty()) }
    var begin by remember(entry.id) {
        mutableStateOf(Instant.ofEpochMilli(entry.beginMillis).atZone(zone).toLocalDateTime())
    }
    var end by remember(entry.id) {
        mutableStateOf(
            Instant.ofEpochMilli(entry.endMillis ?: entry.beginMillis).atZone(zone).toLocalDateTime(),
        )
    }
    val valid = running || end.isAfter(begin)

    SaveDialog(
        stringResource(R.string.edit_entry),
        onDismiss,
        onSave = { onSave(description, begin.toMillis(zone), if (running) null else end.toMillis(zone)) },
        saveEnabled = valid,
    ) {
        if (showDescription) DescriptionField(description, { description = it }, stringResource(R.string.description))
        StartEndFields(begin, end, showEnd = !running, zone, mode, onBegin = { begin = it }, onEnd = { end = it })
    }
}

/**
 * The Start and End rows (date and time buttons) with their pickers, shared by the add and edit dialogs.
 * Shows a warning when End is not after Start.
 */
@Composable
private fun StartEndFields(
    begin: LocalDateTime,
    end: LocalDateTime,
    showEnd: Boolean,
    zone: ZoneId,
    mode: Int,
    onBegin: (LocalDateTime) -> Unit,
    onEnd: (LocalDateTime) -> Unit,
) {
    // Which picker is open: first = editing the start (true) or end (false), second = date (true) or time (false)
    var picking by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }

    // The Box keeps the picker dialog from adding a gap in the caller's Column.
    Box {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTimeRow(
                label = stringResource(R.string.start),
                value = begin,
                zone = zone,
                mode = mode,
                onDate = { picking = true to true },
                onTime = { picking = true to false },
            )
            if (showEnd) {
                DateTimeRow(
                    label = stringResource(R.string.end),
                    value = end,
                    zone = zone,
                    mode = mode,
                    onDate = { picking = false to true },
                    onTime = { picking = false to false },
                )
                if (!end.isAfter(begin)) {
                    Text(
                        stringResource(R.string.end_after_start),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        picking?.let { (isStart, isDate) ->
            DateOrTimePicker(
                current = if (isStart) begin else end,
                isDate = isDate,
                mode = mode,
                onDismiss = { picking = null },
                onPick = { picked ->
                    if (isStart) onBegin(picked) else onEnd(picked)
                    picking = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOrTimePicker(
    current: LocalDateTime,
    isDate: Boolean,
    mode: Int,
    onDismiss: () -> Unit,
    onPick: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    if (isDate) {
        val state =
            rememberDatePickerState(
                // The date picker works in UTC midnight millis
                initialSelectedDateMillis =
                    current.toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis
                    if (ms != null) {
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onPick(current.with(d))
                    } else {
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = state)
        }
    } else {
        val state =
            rememberTimePickerState(
                initialHour = current.hour,
                initialMinute = current.minute,
                is24Hour = Fmt.is24(context, mode),
            )
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(16.dp),
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onPick(current.withHour(state.hour).withMinute(state.minute))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    value: LocalDateTime,
    zone: ZoneId,
    mode: Int,
    onDate: () -> Unit,
    onTime: () -> Unit,
) {
    val context = LocalContext.current
    val date = value.toLocalDate()
    // Short year form so it fits the button; omitted for the current year.
    val datePattern = if (date.year == LocalDate.now(zone).year) "MMM d" else "MMM d ''yy"
    val dateText = date.format(DateTimeFormatter.ofPattern(datePattern, Locale.getDefault()))
    val timeText = Fmt.clock(context, value.toMillis(zone), mode)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = onDate,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { Text(dateText, maxLines = 1, softWrap = false) }
        OutlinedButton(
            onClick = onTime,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { Text(timeText, maxLines = 1) }
    }
}

// Add entry (logging past time without starting/stopping a timer)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEntryDialog(
    vm: MainViewModel,
    ui: UiState,
    onDismiss: () -> Unit,
    onSave: (projectId: Int, activityId: Int, description: String, beginMillis: Long, endMillis: Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var description by remember { mutableStateOf("") }
    var begin by remember { mutableStateOf(LocalDateTime.now(zone).minusHours(1)) }
    var end by remember { mutableStateOf(LocalDateTime.now(zone)) }
    val valid = ui.selectedProject != null && ui.selectedActivity != null && end.isAfter(begin)

    LaunchedEffect(Unit) { vm.selectLastUsed() }

    SaveDialog(
        stringResource(R.string.add_entry),
        onDismiss,
        onSave = {
            val project = ui.selectedProject!!
            val activity = ui.selectedActivity!!
            onSave(project.id, activity.id, description, begin.toMillis(zone), end.toMillis(zone))
        },
        saveEnabled = valid,
        saveLabel = stringResource(R.string.add),
    ) {
        ProjectActivityPickers(vm, ui)
        if (!ui.hideDescription) DescriptionField(description, { description = it })
        StartEndFields(begin, end, showEnd = true, zone, ui.timeMode, onBegin = { begin = it }, onEnd = { end = it })
    }
}
