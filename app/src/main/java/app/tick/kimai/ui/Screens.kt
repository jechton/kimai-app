package app.tick.kimai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tick.kimai.MainViewModel
import app.tick.kimai.UiState
import app.tick.kimai.data.Entry
import app.tick.kimai.util.Fmt
import app.tick.kimai.util.TimeMode
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TickRoot(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (ui.loggedIn) HomeScreen(vm, ui) else LoginScreen(vm, ui)
}

// Sign in

@Composable
private fun LoginScreen(
    vm: MainViewModel,
    ui: UiState,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("Tick", style = MaterialTheme.typography.displayMedium)
            Text(
                "Sign in to your Kimai server with an API token from your Kimai profile.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://time.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("API token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (old servers only)") },
                supportingText = { Text("Leave empty unless your server uses an API password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.login(url, token, username) },
                enabled = url.isNotBlank() && token.isNotBlank() && !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (ui.busy) "Connecting..." else "Connect")
            }
        }
    }
}

// Home

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    vm: MainViewModel,
    ui: UiState,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var deleting by remember { mutableStateOf<Entry?>(null) }

    val notifPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tick") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Time format") },
                                onClick = {
                                    menuOpen = false
                                    showTimeDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = {
                                    menuOpen = false
                                    vm.logout()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (ui.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val finished = ui.entries.filter { !it.isRunning }
            val byDay = finished.groupBy { Fmt.localDate(it.beginMillis) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "timer") { TimerCard(vm, ui) }

                byDay.forEach { (day, list) ->
                    item(key = "day-$day") {
                        Text(
                            Fmt.dayLabel(day),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                        )
                    }
                    items(list, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            mode = ui.timeMode,
                            onRestart = { vm.restart(entry) },
                            onEdit = { editing = entry },
                            onDelete = { deleting = entry },
                        )
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            mode = ui.timeMode,
            onDismiss = { editing = null },
            onSave = { description, beginMillis, endMillis ->
                vm.updateEntry(entry, description, beginMillis, endMillis)
                editing = null
            },
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete entry?") },
            text = { Text("${entry.label} (${Fmt.elapsed(entry.seconds)}) will be removed from Kimai.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(entry)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    if (showTimeDialog) {
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text("Time format") },
            text = {
                Column {
                    listOf(
                        TimeMode.SYSTEM to "Follow system",
                        TimeMode.H12 to "12-hour",
                        TimeMode.H24 to "24-hour",
                    ).forEach { (mode, label) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = ui.timeMode == mode,
                                        onClick = {
                                            vm.setTimeMode(mode)
                                            showTimeDialog = false
                                        },
                                        role = Role.RadioButton,
                                    )
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = ui.timeMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimeDialog = false }) { Text("Close") } },
        )
    }
}

// Timer card

@Composable
private fun TimerCard(
    vm: MainViewModel,
    ui: UiState,
) {
    val t = ui.timer
    val context = LocalContext.current

    if (t.running) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(t.id) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(t.project, style = MaterialTheme.typography.titleLarge)
                val detail = listOf(t.activity, t.description).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
                Text(
                    Fmt.elapsed((now - t.beginMillis) / 1000),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(
                    "Started ${Fmt.clock(context, t.beginMillis, ui.timeMode)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { vm.stop() },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Picker(
                    label = "Project",
                    items = ui.projects,
                    selected = ui.selectedProject,
                    text = { it.display },
                    onSelect = { vm.selectProject(it) },
                )
                Picker(
                    label = "Activity",
                    items = ui.activities,
                    selected = ui.selectedActivity,
                    text = { it.name },
                    onSelect = { vm.selectActivity(it) },
                )
                OutlinedTextField(
                    value = ui.description,
                    onValueChange = { vm.setDescription(it) },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.start() },
                    enabled = !ui.busy && ui.selectedProject != null && ui.selectedActivity != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(
    label: String,
    items: List<T>,
    selected: T?,
    text: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(text).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

// Edit entry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntryDialog(
    entry: Entry,
    mode: Int,
    onDismiss: () -> Unit,
    onSave: (description: String, beginMillis: Long, endMillis: Long) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    var description by remember(entry.id) { mutableStateOf(entry.description.orEmpty()) }
    var begin by remember(entry.id) {
        mutableStateOf(Instant.ofEpochMilli(entry.beginMillis).atZone(zone).toLocalDateTime())
    }
    var end by remember(entry.id) {
        mutableStateOf(
            Instant.ofEpochMilli(entry.endMillis ?: entry.beginMillis).atZone(zone).toLocalDateTime(),
        )
    }
    // Which picker is open: first = editing the start (true) or end (false), second = date (true) or time (false)
    var picking by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    val valid = end.isAfter(begin)

    fun millis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateTimeRow(
                    label = "Start",
                    value = begin,
                    zone = zone,
                    mode = mode,
                    onDate = { picking = true to true },
                    onTime = { picking = true to false },
                )
                DateTimeRow(
                    label = "End",
                    value = end,
                    zone = zone,
                    mode = mode,
                    onDate = { picking = false to true },
                    onTime = { picking = false to false },
                )
                if (!valid) {
                    Text(
                        "End must be after start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(description, millis(begin), millis(end)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    picking?.let { (isStart, isDate) ->
        val current = if (isStart) begin else end

        fun assign(v: LocalDateTime) {
            if (isStart) begin = v else end = v
        }

        if (isDate) {
            val state =
                rememberDatePickerState(
                    // The date picker works in UTC midnight millis
                    initialSelectedDateMillis =
                        current.toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            assign(current.with(d))
                        }
                        picking = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
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
                onDismissRequest = { picking = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.padding(16.dp),
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        assign(current.withHour(state.hour).withMinute(state.minute))
                        picking = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            )
        }
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
    val dateText =
        value.toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val timeText = Fmt.clock(context, value.atZone(zone).toInstant().toEpochMilli(), mode)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = onDate,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { Text(dateText, maxLines = 1) }
        OutlinedButton(
            onClick = onTime,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) { Text(timeText, maxLines = 1) }
    }
}

// Entries

@Composable
private fun EntryRow(
    entry: Entry,
    mode: Int,
    onRestart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val begin = Fmt.clock(context, entry.beginMillis, mode)
    val end = entry.endMillis?.let { Fmt.clock(context, it, mode) }.orEmpty()
    val detail =
        listOfNotNull(
            entry.activity.name,
            entry.description?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            overlineContent = { Text("$begin – $end") },
            headlineContent = {
                Text(
                    entry.project.name ?: "Project ${entry.project.id}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                if (detail.isNotBlank()) {
                    Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.elapsed(entry.seconds), style = MaterialTheme.typography.titleMedium)
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Entry actions")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Start again") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onRestart()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}
