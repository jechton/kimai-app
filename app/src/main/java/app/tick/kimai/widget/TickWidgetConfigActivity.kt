package app.tick.kimai.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.tick.kimai.ui.TickTheme
import kotlinx.coroutines.launch

private data class WidgetConfig(
    val opacity: Float,
    val showWeek: Boolean,
    val showRecent: Boolean,
    val themeMode: String,
)

/** Launched by the system when a widget is placed or (on launchers that support it) long-press "Edit". */
class TickWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val appWidgetId =
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)

        setContent {
            TickTheme {
                Surface {
                    var initial by remember { mutableStateOf<WidgetConfig?>(null) }
                    LaunchedEffect(Unit) {
                        val prefs =
                            getAppWidgetState(this@TickWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                        initial =
                            WidgetConfig(
                                opacity = prefs[TickWidget.OPACITY_KEY] ?: 1f,
                                showWeek = prefs[TickWidget.SHOW_WEEK_KEY] ?: true,
                                showRecent = prefs[TickWidget.SHOW_RECENT_KEY] ?: true,
                                themeMode = prefs[TickWidget.THEME_MODE_KEY] ?: TickWidget.THEME_MODE_AUTO,
                            )
                    }
                    initial?.let { config ->
                        ConfigScreen(
                            initial = config,
                            onSave = { updated ->
                                lifecycleScope.launch {
                                    updateAppWidgetState(
                                        this@TickWidgetConfigActivity,
                                        PreferencesGlanceStateDefinition,
                                        glanceId,
                                    ) {
                                        it.toMutablePreferences().apply {
                                            this[TickWidget.OPACITY_KEY] = updated.opacity
                                            this[TickWidget.SHOW_WEEK_KEY] = updated.showWeek
                                            this[TickWidget.SHOW_RECENT_KEY] = updated.showRecent
                                            this[TickWidget.THEME_MODE_KEY] = updated.themeMode
                                        }
                                    }
                                    TickWidget().update(this@TickWidgetConfigActivity, glanceId)

                                    val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    setResult(RESULT_OK, result)
                                    finish()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    initial: WidgetConfig,
    onSave: (WidgetConfig) -> Unit,
) {
    var opacity by remember { mutableStateOf(initial.opacity) }
    var showWeek by remember { mutableStateOf(initial.showWeek) }
    var showRecent by remember { mutableStateOf(initial.showRecent) }
    var themeMode by remember { mutableStateOf(initial.themeMode) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Widget settings") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Background opacity: ${(opacity * 100).toInt()}%")
            Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..1f, steps = 9)

            Spacer(modifier = Modifier.height(8.dp))
            SettingSwitch("Show week summary", showWeek) { showWeek = it }
            SettingSwitch("Show recent entries", showRecent) { showRecent = it }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Widget theme")
            listOf(
                TickWidget.THEME_MODE_AUTO to "Auto (follow system)",
                TickWidget.THEME_MODE_LIGHT to "Light",
                TickWidget.THEME_MODE_DARK to "Dark",
            ).forEach { (mode, label) ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = themeMode == mode,
                                onClick = { themeMode = mode },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = themeMode == mode, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSave(WidgetConfig(opacity, showWeek, showRecent, themeMode)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
