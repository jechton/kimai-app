package app.tick.kimai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.tick.kimai.data.KimaiQr
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.TimerState
import app.tick.kimai.data.WeekTotals
import app.tick.kimai.util.Fmt
import app.tick.kimai.util.QrImage
import app.tick.kimai.util.TimeMode
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TickRoot(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (ui.loggedIn) HomeScreen(vm, ui) else LoginScreen(vm, ui)
}

// Small helpers shared by several screens

/** Shows the current error in the snackbar, then clears it. */
@Composable
private fun ShowErrors(
    ui: UiState,
    vm: MainViewModel,
    snackbar: SnackbarHostState,
) {
    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }
}

/** Reads "1.5" or "1,5". Null when the text is empty or not a number. */
private fun parseDecimal(text: String): Double? = text.replace(',', '.').trim().toDoubleOrNull()

private fun LocalDateTime.toMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()

// Sign in

@Composable
private fun LoginScreen(
    vm: MainViewModel,
    ui: UiState,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun useQr(raw: String?) {
        val qr = raw?.let { KimaiQr.parse(it) }
        if (qr == null) {
            scope.launch { snackbar.showSnackbar("That's not a Kimai login QR code") }
        } else {
            vm.login(qr.url, qr.token, "")
        }
    }

    val scanQr =
        rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(::useQr) }
    val pickQrImage =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { useQr(QrImage.decode(context, it)) }
        }

    ShowErrors(ui, vm, snackbar)

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        scanQr.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Scan Kimai login QR code")
                                .setBeepEnabled(false),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan QR")
                }
                OutlinedButton(
                    onClick = { pickQrImage.launch("image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload QR")
                }
            }
            Text(
                "or enter your details manually",
                style = MaterialTheme.typography.bodySmall,
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
    var showTargetDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }
    var showPresetsDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var adding by remember { mutableStateOf(false) }

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

    val prefs = remember { Prefs(context) }
    var showBatteryDialog by remember {
        mutableStateOf(
            !prefs.batteryPromptDismissed &&
                !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    fun closeBatteryDialog() {
        showBatteryDialog = false
        prefs.batteryPromptDismissed = true
    }
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = ::closeBatteryDialog,
            title = { Text("Keep background sync reliable") },
            text = {
                Text(
                    "Android's battery optimization can delay or skip the background check for " +
                        "timers started elsewhere. Exempting Tick keeps the notification and widget in sync.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    closeBatteryDialog()
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = ::closeBatteryDialog) { Text("Not now") } },
        )
    }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    ShowErrors(ui, vm, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tick") },
                actions = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add past entry")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(weekStartLabel(ui.firstWeekday)) },
                                enabled = false,
                                onClick = {},
                            )
                            DropdownMenuItem(
                                text = { Text("Time format") },
                                onClick = {
                                    menuOpen = false
                                    showTimeDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Weekly target") },
                                onClick = {
                                    menuOpen = false
                                    showTargetDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Pay rate & tax") },
                                onClick = {
                                    menuOpen = false
                                    showPayDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Start presets") },
                                onClick = {
                                    menuOpen = false
                                    showPresetsDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (ui.hideDescription) "Show description field" else "Hide description field",
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    vm.setHideDescription(!ui.hideDescription)
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
            if (ui.busy && !ui.refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (ui.pending > 0) {
                Text(
                    if (ui.pending == 1) "1 change waiting to sync" else "${ui.pending} changes waiting to sync",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            val finished = ui.entries.filter { !it.isRunning }
            val byDay = finished.groupBy { Fmt.localDate(it.beginMillis) }

            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "timer") { TimerCard(vm, ui, onEdit = { editing = it }) }

                    ui.week?.let { week ->
                        item(key = "week") {
                            WeekSummary(
                                week = week,
                                entries = ui.entries,
                                timer = ui.timer.takeIf { ui.weekOffset == 0 },
                                offset = ui.weekOffset,
                                start = ui.weekStart,
                                payRateOverride = ui.payRateOverride,
                                payTaxPercent = ui.payTaxPercent,
                                onShow = { vm.showWeek(it) },
                            )
                        }
                    }

                    byDay.forEach { (day, list) ->
                        item(key = "day-$day") {
                            val running = ui.running.takeIf { ui.weekOffset == 0 }
                            val runningSecs =
                                if (running != null && Fmt.localDate(running.beginMillis) == day) {
                                    ((System.currentTimeMillis() - running.beginMillis) / 1000).coerceAtLeast(0)
                                } else {
                                    0L
                                }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    Fmt.dayLabel(day),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    Fmt.hoursMinutes(list.sumOf { it.seconds } + runningSecs),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            mode = ui.timeMode,
            showDescription = !ui.hideDescription,
            onDismiss = { editing = null },
            onSave = { description, beginMillis, endMillis ->
                vm.updateEntry(entry, description, beginMillis, endMillis)
                editing = null
            },
        )
    }

    if (adding) {
        AddEntryDialog(
            vm = vm,
            ui = ui,
            onDismiss = { adding = false },
            onSave = { projectId, activityId, description, beginMillis, endMillis ->
                vm.addEntry(projectId, activityId, description, beginMillis, endMillis)
                adding = false
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

    if (showTargetDialog) {
        WeekTargetDialog(
            currentSeconds = ui.weekTargetOverride,
            currentDays = ui.workDaysMask,
            onDismiss = { showTargetDialog = false },
            onSave = { hours, days ->
                vm.setWeekTarget(hours, days)
                showTargetDialog = false
            },
        )
    }

    if (showPayDialog) {
        PayDialog(
            currentRate = ui.payRateOverride,
            currentTaxPercent = ui.payTaxPercent,
            onDismiss = { showPayDialog = false },
            onSave = { rate, tax ->
                vm.setPayRate(rate)
                vm.setPayTax(tax)
                showPayDialog = false
            },
        )
    }

    if (showPresetsDialog) {
        PresetsDialog(
            current = ui.startPresets,
            onDismiss = { showPresetsDialog = false },
            onSave = {
                vm.setStartPresets(it)
                showPresetsDialog = false
            },
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

/** Shows which day Kimai says the week starts on, so a wrong week is easy to trace. */
private fun weekStartLabel(firstWeekday: String): String {
    val day = runCatching { DayOfWeek.valueOf(firstWeekday.uppercase()) }.getOrNull()
    val name = (day ?: DayOfWeek.MONDAY).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return if (day != null) "Week starts on $name (from Kimai)" else "Week starts on $name (default)"
}

@Composable
private fun WeekTargetDialog(
    currentSeconds: Long,
    currentDays: Int,
    onDismiss: () -> Unit,
    onSave: (Double?, Int) -> Unit,
) {
    var text by remember {
        mutableStateOf(if (currentSeconds > 0) (currentSeconds / 3600.0).toString().removeSuffix(".0") else "")
    }
    var days by remember { mutableIntStateOf(currentDays) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Hours per week") },
                    supportingText = { Text("Leave empty to use your Kimai contract") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Days you work", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    DayOfWeek.entries.forEach { d ->
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
                    "Used for the on-pace line. None selected counts every day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parseDecimal(text), days) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PresetsDialog(
    current: List<Int>,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
) {
    var text by remember { mutableStateOf(current.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start presets") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Minutes ago") },
                supportingText = { Text("Up to 3 numbers, e.g. 5, 15, 30. Empty resets.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        text.split(
                            Regex("\\D+"),
                        ).mapNotNull { it.toIntOrNull() }.filter { it > 0 }.distinct().sorted().take(3),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PayDialog(
    currentRate: Float,
    currentTaxPercent: Float,
    onDismiss: () -> Unit,
    onSave: (rate: Double?, taxPercent: Double?) -> Unit,
) {
    var rateText by remember { mutableStateOf(if (currentRate > 0) currentRate.toString() else "") }
    var taxText by remember { mutableStateOf(if (currentTaxPercent > 0) currentTaxPercent.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay rate & tax") },
        text = {
            Column {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Hourly rate") },
                    supportingText = { Text("Leave empty to use Kimai's own rate for each entry") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = taxText,
                    onValueChange = { taxText = it },
                    label = { Text("Tax withheld (%)") },
                    supportingText = { Text("Leave empty for none") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parseDecimal(rateText), parseDecimal(taxText)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WeekSummary(
    week: WeekTotals,
    entries: List<Entry>,
    timer: TimerState?,
    offset: Int,
    start: LocalDate?,
    payRateOverride: Float,
    payTaxPercent: Float,
    onShow: (Int) -> Unit,
) {
    val total = if (timer != null) week.total(timer) else week.doneSeconds
    val title =
        when (offset) {
            0 -> "This week"
            -1 -> "Last week"
            else -> "${-offset} weeks ago"
        }
    val range =
        start?.let {
            val f = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            "${f.format(it)} \u2013 ${f.format(it.plusDays(6))}"
        }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onShow(offset - 1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous week")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (range != null) {
                    Text(
                        range,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onShow(offset + 1) }, enabled = offset < 0) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next week")
            }
        }
        val text =
            if (week.targetSeconds > 0) {
                "${Fmt.hoursMinutes(total)} of ${Fmt.hoursMinutes(week.targetSeconds)}"
            } else {
                Fmt.hoursMinutes(total)
            }
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (week.targetSeconds > 0) {
            val fraction = total.toFloat() / week.targetSeconds
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color =
                    when {
                        fraction >= 1f -> MaterialTheme.colorScheme.error
                        fraction >= 0.9f -> MaterialTheme.colorScheme.tertiary
                        fraction >= 0.5f -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                // M3 1.3 draws a "stop indicator" dot at the track's end by default; we don't want it.
                drawStopIndicator = {},
            )
            if (week.paceSeconds >= 0) {
                val gap = week.paceSeconds - total
                // Icon plus color, so behind/ahead isn't carried by color alone.
                val behind = gap > 0
                val tint = if (behind) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        if (behind) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (behind) {
                            "${Fmt.hoursMinutes(gap)} more today to stay on pace"
                        } else {
                            "${Fmt.hoursMinutes(-gap)} ahead of pace"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tint,
                    )
                }
            }
        }
        val grossPay =
            if (payRateOverride > 0) {
                payRateOverride * (total / 3600.0)
            } else {
                // Kimai's own per-entry rate; a currently running entry has none yet.
                entries.filter { !it.isRunning }.sumOf { it.rate ?: 0.0 }.takeIf { it > 0 }
            }
        if (grossPay != null && grossPay > 0) {
            val netPay = grossPay * (1 - payTaxPercent / 100.0)
            val currency = remember { NumberFormat.getCurrencyInstance() }
            val payText = "Est. pay: ${currency.format(netPay)}"
            Text(
                payText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Timer card

@Composable
private fun TimerCard(
    vm: MainViewModel,
    ui: UiState,
    onEdit: (Entry) -> Unit,
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
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.project, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ui.running?.let { entry ->
                        IconButton(onClick = { onEdit(entry) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit running entry")
                        }
                    }
                }
                val detail = listOf(t.activity, t.description).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
                Text(
                    Fmt.elapsed((now - t.beginMillis) / 1000),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    "Started ${Fmt.clock(context, t.beginMillis, ui.timeMode)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { vm.stop() },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LaunchedEffect(ui.entries.isNotEmpty(), ui.projects.isNotEmpty()) { vm.selectLastUsed() }
                ProjectActivityPickers(vm, ui)
                if (!ui.hideDescription) {
                    OutlinedTextField(
                        value = ui.description,
                        onValueChange = { vm.setDescription(it) },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val canStart = !ui.busy && ui.selectedProject != null && ui.selectedActivity != null
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.start() },
                        enabled = canStart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start")
                    }
                    // One tap starts the timer that many minutes in the past.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.startPresets.forEach { min ->
                            OutlinedButton(
                                onClick = { vm.start(min) },
                                enabled = canStart,
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text("${min}m ago", maxLines = 1, softWrap = false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectActivityPickers(
    vm: MainViewModel,
    ui: UiState,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showDescription) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StartEndFields(
                    begin,
                    end,
                    showEnd = !running,
                    zone,
                    mode,
                    onBegin = { begin = it },
                    onEnd = { end = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(description, begin.toMillis(zone), if (running) null else end.toMillis(zone)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
                label = "Start",
                value = begin,
                zone = zone,
                mode = mode,
                onDate = { picking = true to true },
                onTime = { picking = true to false },
            )
            if (showEnd) {
                DateTimeRow(
                    label = "End",
                    value = end,
                    zone = zone,
                    mode = mode,
                    onDate = { picking = false to true },
                    onTime = { picking = false to false },
                )
                if (!end.isAfter(begin)) {
                    Text(
                        "End must be after start",
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
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
                TextButton(onClick = { onPick(current.withHour(state.hour).withMinute(state.minute)) }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
private fun AddEntryDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProjectActivityPickers(vm, ui)
                if (!ui.hideDescription) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                StartEndFields(
                    begin,
                    end,
                    showEnd = true,
                    zone,
                    ui.timeMode,
                    onBegin = { begin = it },
                    onEnd = { end = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val project = ui.selectedProject!!
                    val activity = ui.selectedActivity!!
                    onSave(project.id, activity.id, description, begin.toMillis(zone), end.toMillis(zone))
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
            entry.activityLabel,
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
