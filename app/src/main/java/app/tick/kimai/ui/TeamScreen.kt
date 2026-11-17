package app.tick.kimai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tick.kimai.MainViewModel
import app.tick.kimai.UiState
import app.tick.kimai.data.User
import app.tick.kimai.util.Fmt

/** Everyone's finished hours for a week, most first. Tap a person to open their week. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    vm: MainViewModel,
    ui: UiState,
    onBack: () -> Unit,
    onOpen: (User) -> Unit,
) {
    BackHandler(onBack = onBack)
    var byTime by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    LaunchedEffect(byTime) { listState.scrollToItem(0) }
    val weekLabel =
        when (ui.teamOffset) {
            0 -> "This week"
            -1 -> "Last week"
            else -> "${-ui.teamOffset} weeks ago"
        }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Team") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.loadTeam(ui.teamOffset - 1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                }
                Text(weekLabel, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.loadTeam(ui.teamOffset + 1) }, enabled = ui.teamOffset < 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = byTime, onClick = { byTime = true }, label = { Text("Most hours") })
                FilterChip(selected = !byTime, onClick = { byTime = false }, label = { Text("A to Z") })
            }
            HorizontalDivider()
            val total = ui.team.sumOf { it.doneSeconds }
            ListItem(
                headlineContent = { Text("Everyone") },
                trailingContent = { Text(Fmt.hoursMinutes(total), style = MaterialTheme.typography.titleMedium) },
            )
            HorizontalDivider()
            val rows =
                if (byTime) {
                    ui.team.sortedByDescending { it.doneSeconds }
                } else {
                    ui.team.sortedBy { it.user.display.lowercase() }
                }
            LazyColumn(state = listState) {
                items(rows, key = { it.user.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.user.display) },
                        supportingContent = if (row.running) ({ Text("Timer running") }) else null,
                        trailingContent = { Text(Fmt.hoursMinutes(row.doneSeconds)) },
                        modifier = Modifier.clickable { onOpen(row.user) },
                    )
                }
            }
        }
    }
}
