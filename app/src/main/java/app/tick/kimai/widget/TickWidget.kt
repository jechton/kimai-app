package app.tick.kimai.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.tick.kimai.MainActivity
import app.tick.kimai.R
import app.tick.kimai.TimerRepository
import app.tick.kimai.data.PaceState
import app.tick.kimai.data.PaceStatus
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.RecentEntry
import app.tick.kimai.data.TimerState
import app.tick.kimai.data.WeekTotals
import app.tick.kimai.data.status
import app.tick.kimai.util.Fmt
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

private val SMALL = DpSize(90.dp, 60.dp)
private val MEDIUM = DpSize(180.dp, 110.dp)
private val TALL = DpSize(180.dp, 140.dp)
private val TALL_WIDE = DpSize(250.dp, 140.dp)
private val LARGE = DpSize(250.dp, 180.dp)
private val XLARGE = DpSize(250.dp, 300.dp)

class TickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, TALL, TALL_WIDE, LARGE, XLARGE))

    companion object {
        /** 1f is fully opaque (the old default), 0f is fully transparent. */
        val OPACITY_KEY = floatPreferencesKey("opacity")
        val SHOW_WEEK_KEY = booleanPreferencesKey("show_week")
        val SHOW_RECENT_KEY = booleanPreferencesKey("show_recent")
        val SHOW_PACE_KEY = booleanPreferencesKey("show_pace")
        val SHOW_PAY_KEY = booleanPreferencesKey("show_pay")

        /** Week goals bars under the pace line, off by default so existing widgets look the same. */
        val BARS_KEY = booleanPreferencesKey("bars")

        /** How many recent entries to list; 0 means all that are cached. */
        val RECENT_COUNT_KEY = intPreferencesKey("recent_count")
        val TAP_TOGGLE_KEY = booleanPreferencesKey("tap_toggle")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        const val THEME_MODE_AUTO = "auto"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
    }

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
                if (it.targetSeconds > 0) {
                    context.getString(R.string.widget_week_target, total, Fmt.hoursMinutes(it.targetSeconds))
                } else {
                    context.getString(R.string.widget_week, total)
                }
            }
        val weekFraction =
            weekTotals?.takeIf { it.targetSeconds > 0 }?.let { it.total(state).toFloat() / it.targetSeconds }
        val info = weekTotals?.let { weekInfo(context, prefs, state, it) }
        provideContent {
            val opacity = currentState(OPACITY_KEY) ?: 1f
            val showWeek = currentState(SHOW_WEEK_KEY) ?: true
            val showRecent = currentState(SHOW_RECENT_KEY) ?: true
            val showPace = currentState(SHOW_PACE_KEY) ?: true
            val showPay = currentState(SHOW_PAY_KEY) ?: false
            val bars = currentState(BARS_KEY) ?: false
            val recentCount = currentState(RECENT_COUNT_KEY) ?: 0
            val tapToggle = currentState(TAP_TOGGLE_KEY) ?: false
            val themeMode = currentState(THEME_MODE_KEY) ?: THEME_MODE_AUTO
            val recent = prefs.recentEntries().let { if (recentCount > 0) it.take(recentCount) else it }
            GlanceTheme(colors = ColorProviders(colorScheme(context, themeMode))) {
                WidgetContent(
                    state,
                    since,
                    week.takeIf { showWeek },
                    weekFraction.takeIf { showWeek },
                    info?.pace.takeIf { showWeek && showPace },
                    info?.pay.takeIf { showWeek && showPay },
                    info?.days.takeIf { showWeek && bars },
                    rows = if (showRecent) dayRows(recent, context, prefs.timeMode) else emptyList(),
                    opacity = opacity,
                    tapToggle = tapToggle,
                )
            }
        }
    }

    /** Matches TickTheme's own light/dark + dynamic-color logic, but with the dark/light choice forced when requested. */
    private fun colorScheme(
        context: Context,
        themeMode: String,
    ): ColorScheme {
        val systemDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val dark =
            when (themeMode) {
                THEME_MODE_LIGHT -> false
                THEME_MODE_DARK -> true
                else -> systemDark
            }
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
}

@Composable
private fun WidgetContent(
    s: TimerState,
    since: String,
    week: String?,
    weekFraction: Float?,
    pace: PaceStatus?,
    pay: String?,
    days: List<DayBar>?,
    rows: List<DayRow>,
    opacity: Float,
    tapToggle: Boolean,
) {
    val size = LocalSize.current
    val context = LocalContext.current
    val background = ColorProvider(GlanceTheme.colors.widgetBackground.getColor(context).copy(alpha = opacity))
    val title =
        when {
            s.running -> s.project.ifBlank { context.getString(R.string.timer_running) }
            else -> context.getString(R.string.no_timer_running)
        }
    val subtitle =
        when {
            s.running ->
                listOf(s.activity, context.getString(R.string.widget_since, since)).filter {
                    it.isNotBlank()
                }.joinToString(" · ")
            s.lastLabel.isNotBlank() -> context.getString(R.string.widget_last, s.lastLabel)
            else -> context.getString(R.string.widget_tap_to_open)
        }

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(background)
                .cornerRadius(24.dp)
                .padding(if (size.height <= SMALL.height) 6.dp else 16.dp)
                .clickable(if (tapToggle) actionRunCallback<ToggleAction>() else actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (size.height <= SMALL.height) {
            // Short widget: a one-line state above a normal-height button, not one giant button.
            Text(
                text = title,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
            Button(
                text = if (s.running) context.getString(R.string.stop) else context.getString(R.string.start),
                onClick = actionRunCallback<ToggleAction>(),
                modifier = GlanceModifier.fillMaxWidth().height(36.dp),
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
        // Medium-height widgets with the week line have no room for the "Last:" line too.
        if (week == null || size.height >= LARGE.height) {
            Text(
                text = subtitle,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                    ),
            )
        }
        if (week != null && size.height >= TALL.height) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    text = week,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (pay != null && size.width >= LARGE.width) {
                    Text(
                        text = context.getString(R.string.est_short, pay),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    )
                }
            }
        }
        if (weekFraction != null && size.height >= MEDIUM.height) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = weekFraction.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth(),
                // Glance's default indicator color is a hardcoded static purple, not theme-aware.
                color =
                    when {
                        weekFraction >= 0.9f -> GlanceTheme.colors.tertiary
                        weekFraction >= 0.5f -> GlanceTheme.colors.primary
                        else -> GlanceTheme.colors.secondary
                    },
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
        if (pace != null && size.height >= LARGE.height) {
            val tint =
                when (pace.state) {
                    PaceState.MISSED -> GlanceTheme.colors.error
                    PaceState.MET -> GlanceTheme.colors.primary
                    PaceState.PENDING -> GlanceTheme.colors.onSurfaceVariant
                }
            Text(
                text = Fmt.paceText(context, pace.message),
                maxLines = 1,
                style = TextStyle(color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.padding(top = 4.dp),
            )
        }
        if (days != null && size.height >= XLARGE.height) {
            WeekStrip(days)
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        Button(
            text =
                if (s.running) {
                    context.getString(R.string.stop)
                } else if (size.width >= LARGE.width) {
                    context.getString(R.string.start_last)
                } else {
                    context.getString(R.string.start)
                },
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

/** One day of the week goals strip; [state] MET also covers days still to come. */
private data class DayBar(
    val letter: String,
    val done: String,
    val goal: String?,
    val fill: Float,
    val state: PaceState,
    val today: Boolean,
)

private data class WeekInfo(val pace: PaceStatus?, val pay: String?, val days: List<DayBar>?)

/** Works out the pace line, estimated pay and per-day bars from what the last sync cached. */
private fun weekInfo(
    context: Context,
    prefs: Prefs,
    timer: TimerState,
    totals: WeekTotals,
): WeekInfo {
    val total = totals.total(timer)
    val gross =
        if (prefs.payRateOverride > 0) {
            prefs.payRateOverride * (total / 3600.0)
        } else {
            prefs.weekDetail()?.kimaiPay?.takeIf { it > 0 }
        }
    val pay = gross?.let { NumberFormat.getCurrencyInstance().format(it * (1 - prefs.payTaxPercent / 100.0)) }

    val detail = prefs.weekDetail()
    val zone = totals.zone
    val today = LocalDate.now(zone)
    val idx = detail?.let { ChronoUnit.DAYS.between(it.start, today).toInt() }
    if (detail == null || idx == null || idx !in 0..6 || totals.targetSeconds <= 0) return WeekInfo(null, pay, null)

    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val runningToday =
        if (timer.running) {
            ((System.currentTimeMillis() - maxOf(timer.beginMillis, dayStart)) / 1000).coerceAtLeast(
                0,
            )
        } else {
            0L
        }
    val done = detail.perDay.toMutableList().also { it[idx] += runningToday }
    val doneToday = done[idx]
    val plan = totals.pacePlan(detail.start, today, total - doneToday, doneToday)
    val goals = totals.dayGoals(detail.start, today, total - doneToday, doneToday)
    val pace = plan?.status(totals.targetSeconds - total, doneToday)
    val days =
        goals?.mapIndexed { i, goal ->
            val met = goal > 0 && done[i] >= goal - 59
            DayBar(
                letter =
                    detail.start.plusDays(
                        i.toLong(),
                    ).dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                done = if (goal == 0L && done[i] == 0L) "off" else Fmt.narrow(done[i]),
                goal = if (goal > 0) Fmt.narrow(goal) else null,
                fill =
                    when {
                        goal > 0 -> (done[i].toFloat() / goal).coerceIn(0f, 1f)
                        done[i] > 0 -> 1f
                        else -> 0f
                    },
                state =
                    when {
                        met -> PaceState.MET
                        i < idx && goal > 0 -> PaceState.MISSED
                        i == idx -> PaceState.PENDING
                        else -> PaceState.MET
                    },
                today = i == idx,
            )
        }
    return WeekInfo(pace, pay, days)
}

@Composable
private fun WeekStrip(days: List<DayBar>) {
    val barHeight = 22.dp
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
        days.forEach { d ->
            val color =
                when (d.state) {
                    PaceState.MISSED -> GlanceTheme.colors.error
                    PaceState.MET -> GlanceTheme.colors.primary
                    PaceState.PENDING -> GlanceTheme.colors.onSurfaceVariant
                }
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text(
                    d.letter,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = if (d.today) FontWeight.Bold else FontWeight.Normal,
                        ),
                )
                // A track with the filled part at the bottom: a spacer above, a block below.
                Column(
                    modifier =
                        GlanceModifier
                            .width(12.dp)
                            .height(barHeight)
                            .padding(vertical = 0.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .cornerRadius(3.dp),
                ) {
                    Spacer(GlanceModifier.height(barHeight * (1f - d.fill)))
                    if (d.fill > 0f) {
                        Spacer(GlanceModifier.fillMaxWidth().height(barHeight * d.fill).background(color))
                    }
                }
                Text(d.done, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 9.sp))
                if (d.goal != null) {
                    Text(
                        d.goal,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                    )
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
            listOf(DayRow.Header(Fmt.dayLabel(context, date), Fmt.hoursMinutes(entries.sumOf { it.seconds }))) +
                entries.map {
                    val begin = Fmt.clock(context, it.beginMillis, timeMode)
                    val end = Fmt.clock(context, it.endMillis, timeMode)
                    DayRow.Item(
                        it.label.ifBlank {
                            context.getString(R.string.entry_fallback)
                        },
                        "$begin – $end", Fmt.hoursMinutes(it.seconds),
                    )
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
