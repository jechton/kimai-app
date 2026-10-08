package app.tick.kimai.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
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
import app.tick.kimai.data.TimerState
import app.tick.kimai.util.Fmt

class TickWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val prefs = Prefs(context)
        val state = prefs.timerState()
        val since = if (state.running) Fmt.clock(context, state.beginMillis, prefs.timeMode) else ""
        val week =
            prefs.weekTotals()?.let {
                val total = Fmt.hoursMinutes(it.total(state))
                if (it.targetSeconds > 0) "Week: $total / ${Fmt.hoursMinutes(it.targetSeconds)}" else "Week: $total"
            }
        provideContent {
            GlanceTheme {
                WidgetContent(state, since, week)
            }
        }
    }
}

@Composable
private fun WidgetContent(
    s: TimerState,
    since: String,
    week: String?,
) {
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
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
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
        if (week != null) {
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
        Spacer(modifier = GlanceModifier.height(10.dp))
        Button(
            text = if (s.running) "Stop" else "Start last",
            onClick = actionRunCallback<ToggleAction>(),
            modifier = GlanceModifier.fillMaxWidth(),
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
