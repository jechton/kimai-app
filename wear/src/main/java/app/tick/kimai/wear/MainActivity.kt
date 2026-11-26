package app.tick.kimai.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private var state by mutableStateOf<TickState?>(null)
    private var error by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        lifecycleScope.launch {
            state = runCatching { Phone.state(this@MainActivity) }.getOrNull() ?: state
            run(Phone.SYNC)
        }
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.dataItem.uri.path == Phone.STATE }.lastOrNull()?.let {
            state = TickState.from(DataMapItem.fromDataItem(it.dataItem).dataMap)
        }
    }

    /** Asks the phone; the new state arrives through [onDataChanged]. */
    private suspend fun run(
        path: String,
        data: DataMap = DataMap(),
    ) {
        busy = true
        error =
            try {
                Phone.request(this, path, data)
                null
            } catch (e: PhoneException) {
                e.message
            }
        busy = false
    }

    @Composable
    private fun App() {
        val nav = rememberSwipeDismissableNavController()
        val scope = rememberCoroutineScope()
        var project by remember { mutableStateOf<Pair<Int, String>?>(null) }
        SwipeDismissableNavHost(navController = nav, startDestination = "main") {
            composable("main") {
                Main(
                    onToggle = { scope.launch { run(Phone.TOGGLE) } },
                    onNew = { nav.navigate("projects") },
                )
            }
            composable("projects") {
                Picker(stringResource(R.string.project), Phone.PROJECTS, DataMap()) {
                    project = it
                    nav.navigate("activities")
                }
            }
            composable("activities") {
                val p = project ?: return@composable
                Picker(
                    stringResource(R.string.activity),
                    Phone.ACTIVITIES,
                    DataMap().apply { putInt("project_id", p.first) },
                ) {
                    val data =
                        DataMap().apply {
                            putInt("project_id", p.first)
                            putString("project", p.second)
                            putInt("activity_id", it.first)
                            putString("activity", it.second)
                        }
                    scope.launch { run(Phone.START, data) }
                    nav.popBackStack("main", inclusive = false)
                }
            }
        }
    }

    @Composable
    private fun Main(
        onToggle: () -> Unit,
        onNew: () -> Unit,
    ) {
        val s = state ?: TickState()
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(s.running) {
            while (s.running) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
        Scaffold(timeText = { TimeText() }) {
            // No scrolling: everything fits on the round screen, centred below the clock.
            Column(
                Modifier.fillMaxSize().padding(start = 16.dp, top = 32.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (s.running) {
                    Text(s.label, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sec = s.runningSeconds(now)
                    Text(
                        "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60),
                        style = MaterialTheme.typography.display3,
                    )
                    WideChip(stringResource(R.string.stop), null, enabled = !busy, onClick = onToggle)
                } else {
                    if (s.lastLabel.isNotBlank()) {
                        WideChip(stringResource(R.string.start_last), s.lastLabel, enabled = !busy, onClick = onToggle)
                    }
                    WideChip(
                        stringResource(R.string.new_timer),
                        null,
                        enabled = !busy,
                        secondary = true,
                        onClick = onNew,
                    )
                }
                Text(
                    stringResource(R.string.today_value, hoursMinutes(s.today(now))),
                    style = MaterialTheme.typography.caption1,
                )
                val week = hoursMinutes(s.week(now))
                val text = if (s.weekTarget > 0) "$week / ${hoursMinutes(s.weekTarget)}" else week
                Text(stringResource(R.string.week_value, text), style = MaterialTheme.typography.caption1)
                if (s.pending > 0) {
                    Text(
                        pluralStringResource(R.plurals.pending_changes, s.pending, s.pending),
                        style = MaterialTheme.typography.caption2,
                    )
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }
        }
    }

    /** Project or activity list fetched from the phone, last used first. */
    @Composable
    private fun Picker(
        title: String,
        path: String,
        request: DataMap,
        onPick: (Pair<Int, String>) -> Unit,
    ) {
        val context = LocalContext.current
        var items by remember { mutableStateOf<List<Pair<Int, String>>?>(null) }
        var failed by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(path) {
            try {
                items =
                    Phone.request(context, path, request).getDataMapArrayList("items").orEmpty().map {
                        it.getInt("id") to it.getString("name", "")
                    }
            } catch (e: PhoneException) {
                failed = e.message
            }
        }
        Screen {
            item { ListHeader { Text(title) } }
            val list = items
            when {
                failed != null ->
                    item {
                        Text(
                            failed!!,
                            color = MaterialTheme.colors.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                list == null -> item { CircularProgressIndicator() }
                list.isEmpty() -> item { Text("–") }
                else -> items(list.size) { i -> WideChip(list[i].second, null, secondary = true) { onPick(list[i]) } }
            }
        }
    }

    @Composable
    private fun Screen(content: ScalingLazyListScope.() -> Unit) {
        val list = rememberScalingLazyListState()
        Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(list) }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = list,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = content,
                )
            }
        }
    }

    @Composable
    private fun WideChip(
        label: String,
        detail: String?,
        enabled: Boolean = true,
        secondary: Boolean = false,
        onClick: () -> Unit,
    ) {
        Chip(
            label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            secondaryLabel = detail?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            onClick = onClick,
            enabled = enabled,
            colors = if (secondary) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
