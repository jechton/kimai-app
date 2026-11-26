package app.tick.kimai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tick.kimai.MainViewModel
import app.tick.kimai.R
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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun TickRoot(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (ui.loggedIn) HomeScreen(vm, ui) else LoginScreen(vm, ui)
    UpdateDialog(vm, ui)
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
    val notQr = stringResource(R.string.login_qr_invalid)
    val scanPrompt = stringResource(R.string.login_scan_prompt)
    val scope = rememberCoroutineScope()

    fun useQr(raw: String?) {
        val qr = raw?.let { KimaiQr.parse(it) }
        if (qr == null) {
            scope.launch { snackbar.showSnackbar(notQr) }
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
                stringResource(R.string.login_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        scanQr.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(scanPrompt)
                                .setBeepEnabled(false),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.login_scan_qr))
                }
                OutlinedButton(
                    onClick = { pickQrImage.launch("image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.login_upload_qr))
                }
            }
            Text(
                stringResource(R.string.login_manual),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.login_server_url)) },
                placeholder = { Text("https://time.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.login_api_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                supportingText = { Text(stringResource(R.string.login_username_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.login(url, token, username) },
                enabled = url.isNotBlank() && token.isNotBlank() && !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (ui.busy) R.string.login_connecting else R.string.login_connect))
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
            title = { Text(stringResource(R.string.battery_title)) },
            text = {
                Text(
                    stringResource(R.string.battery_text),
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
                }) { Text(stringResource(R.string.battery_turn_off)) }
            },
            dismissButton = { TextButton(onClick = ::closeBatteryDialog) { Text(stringResource(R.string.not_now)) } },
        )
    }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        vm.checkForUpdate(manual = false)
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
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (ui.viewing == null) {
                            IconButton(onClick = { adding = true }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_past_entry))
                            }
                        }
                        if (ui.canViewOthers) {
                            IconButton(
                                onClick = {
                                    pickingUser = true
                                    vm.loadTeam(0)
                                },
                            ) {
                                Icon(Icons.Default.People, contentDescription = stringResource(R.string.team_overview))
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (ui.busy && !ui.refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (ui.updateBanner && ui.update != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable(onClick = vm::openUpdate)
                                .padding(start = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.update_available, ui.update?.version.orEmpty()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::openUpdate) { Text(stringResource(R.string.update)) }
                        IconButton(onClick = vm::dismissUpdate) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.dismiss),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                if (ui.pending > 0) {
                    Text(
                        pluralStringResource(R.plurals.pending_changes, ui.pending, ui.pending),
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
                                        Fmt.dayLabel(context, day),
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
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(stringResource(R.string.delete_entry_message, entry.label, Fmt.elapsed(entry.seconds))) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(entry)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
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
            title = { Text(stringResource(R.string.week_goals_view)) },
            text = {
                Column {
                    listOf(
                        0 to stringResource(R.string.strip_full),
                        1 to stringResource(R.string.strip_compact),
                        2 to stringResource(R.string.strip_hidden),
                    ).forEach { (mode, label) ->
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
            confirmButton = {
                TextButton(
                    onClick = { showStripDialog = false },
                ) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (showTimeDialog) {
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text(stringResource(R.string.time_format)) },
            text = {
                Column {
                    listOf(
                        TimeMode.SYSTEM to stringResource(R.string.time_follow_system),
                        TimeMode.H12 to stringResource(R.string.time_12h),
                        TimeMode.H24 to stringResource(R.string.time_24h),
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
            confirmButton = {
                TextButton(
                    onClick = { showTimeDialog = false },
                ) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

/** Shows which day Kimai says the week starts on, so a wrong week is easy to trace. */
@Composable
internal fun weekStartLabel(firstWeekday: String): String {
    val day = runCatching { DayOfWeek.valueOf(firstWeekday.uppercase()) }.getOrNull()
    val name = (day ?: DayOfWeek.MONDAY).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return stringResource(if (day != null) R.string.week_starts_kimai else R.string.week_starts_default, name)
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
                    if (goals[i] == 0L && done[i] == 0L) stringResource(R.string.goal_off) else Fmt.narrow(done[i]),
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
    val context = LocalContext.current
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
            0 -> stringResource(R.string.this_week)
            -1 -> stringResource(R.string.last_week)
            else -> pluralStringResource(R.plurals.weeks_ago, -offset, -offset)
        }
    val range =
        start?.let {
            val f =
                DateTimeFormatter.ofPattern(
                    DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"),
                    Locale.getDefault(),
                )
            "${f.format(it)} \u2013 ${f.format(it.plusDays(6))}"
        }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onShow(offset - 1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_week))
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
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.next_week))
            }
        }
        val text =
            if (week.targetSeconds > 0) {
                stringResource(R.string.week_progress, Fmt.hoursMinutes(total), Fmt.hoursMinutes(week.targetSeconds))
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
                    stringResource(R.string.est_short, currency.format(netPay)),
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
                val message = Fmt.paceText(context, pace.message)
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
                    Text(
                        pluralStringResource(
                            R.plurals.pace_later,
                            plan.laterDays,
                            Fmt.hoursMinutes(plan.laterPerDay),
                            plan.laterDays,
                        ),
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
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_running_entry))
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
                    stringResource(R.string.started_at, Fmt.clock(context, t.beginMillis, ui.timeMode)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { vm.stop() },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stop))
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
                    DescriptionField(ui.description, onChange = { vm.setDescription(it) })
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
                        Text(stringResource(R.string.start))
                    }
                    // One tap starts the timer that many minutes in the past.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.startPresets.forEach { min ->
                            OutlinedButton(
                                onClick = { vm.start(min) },
                                enabled = canStart,
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.minutes_ago_short, min), maxLines = 1, softWrap = false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProjectActivityPickers(
    vm: MainViewModel,
    ui: UiState,
) {
    Picker(
        label = stringResource(R.string.project),
        items = ui.projects,
        selected = ui.selectedProject,
        text = { it.display },
        onSelect = { vm.selectProject(it) },
    )
    Picker(
        label = stringResource(R.string.activity),
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
                    entry.project.name ?: stringResource(R.string.project_fallback, entry.project.id),
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
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.entry_actions),
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.start_again)) },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onRestart()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onEdit()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
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
