package app.tick.kimai.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.tick.kimai.MainActivity
import app.tick.kimai.TimerRepository
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.RecentEntry
import app.tick.kimai.data.TimerState
import app.tick.kimai.util.Fmt

private val SMALL = DpSize(90.dp, 60.dp)
private val MEDIUM = DpSize(180.dp, 110.dp)
private val LARGE = DpSize(250.dp, 180.dp)
private val XLARGE = DpSize(250.dp, 300.dp)

class TickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE, XLARGE))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val prefs = Prefs(context)
        val state = prefs.timerState()
        val since = if (state.running) Fmt.clock(context, state.beginMillis, prefs.timeMode) else ""
        val weekTotals = prefs.weekTotals()
        val week =
            weekTotals?.let {
                val total = Fmt.hoursMinutes(it.total(state))
                if (it.targetSeconds > 0) "Week: $total / ${Fmt.hoursMinutes(it.targetSeconds)}" else "Week: $total"
            }
        val weekProgress =
            weekTotals?.takeIf { it.targetSeconds > 0 }
                ?.let { (it.total(state).toFloat() / it.targetSeconds).coerceIn(0f, 1f) }
        val rows = dayRows(prefs.recentEntries(), context, prefs.timeMode)
        provideContent {
            GlanceTheme {
                WidgetContent(state, since, week, weekProgress, rows)
            }
        }
    }
}

@Composable
private fun WidgetContent(
    s: TimerState,
    since: String,
    week: String?,
    weekProgress: Float?,
    rows: List<DayRow>,
) {
    val size = LocalSize.current
    val title =
        when {
            s.running -> s.project.ifBlank { "Timer running" }
            else -> "No timer running"
        }
    val subtitle =
        when {
            s.running -> listOf(s.activity, "since $since").filter { it.isNotBlank() }.joinToString(" · ")
            s.lastLabel.isNotBlank() -> "Last: ${s.lastLabel}"
            else -> "Tap to open Tick"
        }

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(if (size.height <= SMALL.height) 8.dp else 16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (size.height <= SMALL.height) {
            // Too short for title/subtitle text; the button alone carries the state.
            Button(
                text = if (s.running) "Stop" else "Start",
                onClick = actionRunCallback<ToggleAction>(),
                modifier = GlanceModifier.fillMaxSize(),
            )
            return@Column
        }

        Text(
            text = title,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (size.width >= LARGE.width) 18.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
        Text(
            text = subtitle,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                ),
        )
        if (week != null && size.height >= LARGE.height) {
            Text(
                text = week,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                    ),
            )
        }
        if (weekProgress != null && size.height >= MEDIUM.height) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = weekProgress,
                modifier = GlanceModifier.fillMaxWidth(),
                // Glance's default indicator color is a hardcoded static purple, not theme-aware.
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        Button(
            text = if (s.running) "Stop" else "Start last",
            onClick = actionRunCallback<ToggleAction>(),
            modifier = GlanceModifier.fillMaxWidth(),
        )
        if (size.height >= XLARGE.height && rows.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(10.dp))
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(rows) { row ->
                    when (row) {
                        is DayRow.Header ->
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.Vertical.CenterVertically,
                            ) {
                                Text(
                                    text = row.label,
                                    maxLines = 1,
                                    style =
                                        TextStyle(
                                            color = GlanceTheme.colors.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                                Text(
                                    text = row.total,
                                    maxLines = 1,
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                                )
                            }
                        is DayRow.Item ->
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Vertical.CenterVertically,
                            ) {
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        text = row.label,
                                        maxLines = 1,
                                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                                    )
                                    Text(
                                        text = row.timeRange,
                                        maxLines = 1,
                                        style =
                                            TextStyle(
                                                color = GlanceTheme.colors.onSurfaceVariant,
                                                fontSize = 10.sp,
                                            ),
                                    )
                                }
                                Text(
                                    text = row.duration,
                                    maxLines = 1,
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                                )
                            }
                    }
                }
            }
        }
    }
}

private sealed class DayRow {
    data class Header(val label: String, val total: String) : DayRow()

    data class Item(val label: String, val timeRange: String, val duration: String) : DayRow()
}

/** Groups recent entries by local day, most recent day first, with a header carrying that day's total. */
private fun dayRows(
    recent: List<RecentEntry>,
    context: Context,
    timeMode: Int,
): List<DayRow> =
    recent.groupBy { Fmt.localDate(it.beginMillis) }
        .toList()
        .sortedByDescending { (date, _) -> date }
        .flatMap { (date, entries) ->
            listOf(DayRow.Header(Fmt.dayLabel(date), Fmt.hoursMinutes(entries.sumOf { it.seconds }))) +
                entries.map {
                    val begin = Fmt.clock(context, it.beginMillis, timeMode)
                    val end = Fmt.clock(context, it.endMillis, timeMode)
                    DayRow.Item(it.label.ifBlank { "Entry" }, "$begin – $end", Fmt.hoursMinutes(it.seconds))
                }
        }

class ToggleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        TimerRepository(context).toggle()
    }
}

class TickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TickWidget()
}
