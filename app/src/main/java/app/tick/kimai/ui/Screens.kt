package app.tick.kimai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import app.tick.kimai.data.PaceState
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.TimerState
import app.tick.kimai.data.WeekTotals
import app.tick.kimai.data.status
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
import java.time.temporal.ChronoUnit
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
    var showSettings by remember { mutableStateOf(false) }
    var pickingUser by remember { mutableStateOf(false) }
    // Back from someone else's week returns to the team list.
    val leaveViewing = {
        vm.viewUser(null)
        pickingUser = true
    }
    BackHandler(enabled = ui.viewing != null, onBack = leaveViewing)
    var showTimeDialog by remember { mutableStateOf(false) }
    var showStripDialog by remember { mutableStateOf(false) }
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

    if (showSettings) {
        SettingsScreen(
            vm = vm,
            ui = ui,
            onBack = { showSettings = false },
            onTime = { showTimeDialog = true },
            onStrip = { showStripDialog = true },
            onTarget = { showTargetDialog = true },
            onPay = { showPayDialog = true },
            onPresets = { showPresetsDialog = true },
        )
    } else if (pickingUser) {
        TeamScreen(
            vm = vm,
            ui = ui,
            onBack = { pickingUser = false },
            onOpen = {
                pickingUser = false
                vm.viewUser(it)
            },
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(ui.viewing?.display ?: "Tick") },
                    navigationIcon = {
                        if (ui.viewing != null) {
                            IconButton(onClick = leaveViewing) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (ui.viewing == null) {
                            IconButton(onClick = { adding = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add past entry")
                            }
                        }
                        if (ui.canViewOthers) {
                            IconButton(
                                onClick = {
                                    pickingUser = true
                                    vm.loadTeam(0)
                                },
                            ) {
                                Icon(Icons.Default.People, contentDescription = "Team overview")
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                        val viewing = ui.viewing
                        if (viewing == null) {
                            item(key = "timer") { TimerCard(vm, ui, onEdit = { editing = it }) }
                        } else {
                        }

                        ui.week?.let { week ->
                            item(key = "week") {
                                WeekSummary(
                                    week = week,
                                    entries = ui.entries,
                                    timer = ui.timer.takeIf { ui.weekOffset == 0 && ui.viewing == null },
                                    offset = ui.weekOffset,
                                    start = ui.weekStart,
                                    payRateOverride = ui.payRateOverride,
                                    payTaxPercent = ui.payTaxPercent,
                                    strip = ui.weekStrip,
                                    onShow = { vm.showWeek(it) },
                                )
                            }
                        }

                        byDay.forEach { (day, list) ->
                            item(key = "day-$day") {
                                val running = ui.running.takeIf { ui.weekOffset == 0 && ui.viewing == null }
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
                                    readOnly = ui.viewing != null,
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
            firstWeekday = ui.firstWeekday,
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

    if (showStripDialog) {
        AlertDialog(
            onDismissRequest = { showStripDialog = false },
            title = { Text("Week goals view") },
            text = {
                Column {
                    listOf(0 to "Full", 1 to "Compact", 2 to "Hidden").forEach { (mode, label) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = ui.weekStrip == mode,
                                        onClick = {
                                            vm.setWeekStrip(mode)
                                            showStripDialog = false
                                        },
                                        role = Role.RadioButton,
                                    )
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = ui.weekStrip == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStripDialog = false }) { Text("Close") } },
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
internal fun weekStartLabel(firstWeekday: String): String {
    val day = runCatching { DayOfWeek.valueOf(firstWeekday.uppercase()) }.getOrNull()
    val name = (day ?: DayOfWeek.MONDAY).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return if (day != null) "Week starts on $name (from Kimai)" else "Week starts on $name (default)"
}

@Composable
private fun WeekTargetDialog(
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
                    "Used for the on-pace line. Overrides the per-day hours in your Kimai contract. " +
                        "None selected uses those hours if Kimai has them, otherwise every day.",
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

/**
 * One column per day: a bar filled by hours tracked against that day's goal, the hours tracked,
 * and the goal. Past days show their even share; today and later days show the catch-up plan.
 */
@Composable
private fun WeekGoals(
    start: LocalDate,
    todayIdx: Int,
    done: List<Long>,
    goals: List<Long>,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0..6) {
            val day = start.plusDays(i.toLong())
            val missed = i < todayIdx && goals[i] > 0 && goals[i] - done[i] >= 60
            val fill =
                when {
                    goals[i] > 0 -> (done[i].toFloat() / goals[i]).coerceIn(0f, 1f)
                    done[i] > 0 -> 1f
                    else -> 0f
                }
            val met = goals[i] > 0 && done[i] >= goals[i] - 59
            val barColor =
                when {
                    met -> MaterialTheme.colorScheme.primary
                    missed -> MaterialTheme.colorScheme.error
                    i == todayIdx -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == todayIdx) FontWeight.Bold else FontWeight.Normal,
                    color =
                        if (i == todayIdx) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Box(
                    modifier =
                        Modifier
                            .padding(vertical = 2.dp)
                            .width(18.dp)
                            .height(if (compact) 28.dp else 48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fill).background(barColor))
                }
                // Hours worked over a divider, the goal underneath: reads as "worked of goal".
                Text(
                    if (goals[i] == 0L && done[i] == 0L) "off" else Fmt.narrow(done[i]),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == todayIdx) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
                if (goals[i] > 0) {
                    HorizontalDivider(Modifier.width(26.dp))
                    Text(
                        Fmt.narrow(goals[i]),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
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
    strip: Int,
    onShow: (Int) -> Unit,
) {
    // Ticks each minute so the total and "today" stay current while the screen is open, even past midnight.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val total = if (timer != null) week.total(timer, now) else week.doneSeconds
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
        val grossPay =
            if (payRateOverride > 0) {
                payRateOverride * (total / 3600.0)
            } else {
                // Kimai's own per-entry rate; a currently running entry has none yet.
                entries.filter { !it.isRunning }.sumOf { it.rate ?: 0.0 }.takeIf { it > 0 }
            }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            if (grossPay != null && grossPay > 0) {
                val netPay = grossPay * (1 - payTaxPercent / 100.0)
                val currency = remember { NumberFormat.getCurrencyInstance() }
                Text(
                    "Est. ${currency.format(netPay)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (week.targetSeconds > 0) {
            val fraction = total.toFloat() / week.targetSeconds
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color =
                    when {
                        fraction >= 0.9f -> MaterialTheme.colorScheme.tertiary
                        fraction >= 0.5f -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                // M3 1.3 draws a "stop indicator" dot at the track's end by default; we don't want it.
                drawStopIndicator = {},
            )
            val today = Instant.ofEpochMilli(now).atZone(week.zone).toLocalDate()
            val zone = week.zone
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            // Time tracked today, including a running timer that started earlier (counted from midnight).
            val finishedToday =
                entries.filter { !it.isRunning && Fmt.localDate(it.beginMillis, zone) == today }.sumOf { it.seconds }
            val runningToday =
                if (timer != null && timer.running) {
                    ((now - maxOf(timer.beginMillis, dayStart)) / 1000).coerceAtLeast(0)
                } else {
                    0L
                }
            val doneToday = finishedToday + runningToday
            val plan = if (start != null) week.pacePlan(start, today, total - doneToday, doneToday) else null
            if (plan != null) {
                val weekLeft = week.targetSeconds - total
                val pace = plan.status(weekLeft, doneToday)
                val message = pace.message
                val tint =
                    when (pace.state) {
                        PaceState.MISSED -> MaterialTheme.colorScheme.error
                        PaceState.MET -> MaterialTheme.colorScheme.primary
                        PaceState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                // Icon plus color, so the state isn't carried by color alone.
                val icon =
                    when (pace.state) {
                        PaceState.MISSED -> Icons.Default.Warning
                        PaceState.MET -> Icons.Default.CheckCircle
                        PaceState.PENDING -> Icons.Default.Schedule
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = tint)
                }
                if (plan.laterDays > 0 && weekLeft >= 60) {
                    val days = if (plan.laterDays == 1) "1 more work day" else "${plan.laterDays} more work days"
                    Text(
                        "Then about ${Fmt.hoursMinutes(plan.laterPerDay)} a day for $days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val goals = if (start != null) week.dayGoals(start, today, total - doneToday, doneToday) else null
                if (start != null && goals != null && strip != 2) {
                    val done = LongArray(7)
                    entries.filter { !it.isRunning }.forEach {
                        val i = ChronoUnit.DAYS.between(start, Fmt.localDate(it.beginMillis, zone)).toInt()
                        if (i in 0..6) done[i] += it.seconds
                    }
                    val todayIdx = ChronoUnit.DAYS.between(start, today).toInt()
                    done[todayIdx] += runningToday
                    WeekGoals(start, todayIdx, done.toList(), goals, compact = strip == 1)
                }
            }
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
    readOnly: Boolean,
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
                    softWrap = false,
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
                    if (!readOnly) {
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
                }
            },
        )
    }
}
