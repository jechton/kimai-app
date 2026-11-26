package app.tick.kimai.wear

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/** Tile: running timer or the week's hours, with Stop / Start last. */
class TimerTile : TileService() {
    override fun onTileRequest(request: RequestBuilders.TileRequest) =
        SuspendToFutureAdapter.launchFuture {
            if (request.currentState.lastClickableId == TOGGLE) {
                // The phone pushes the new state afterwards, which refreshes the tile again.
                withTimeoutOrNull(10_000) { runCatching { Phone.request(this@TimerTile, Phone.TOGGLE) } }
            }
            val state = runCatching { Phone.state(this@TimerTile) }.getOrNull() ?: TickState()
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES)
                .setTileTimeline(
                    TimelineBuilders.Timeline.fromLayoutElement(
                        layout(this@TimerTile, state, request.deviceConfiguration),
                    ),
                )
                .build()
        }

    override fun onTileResourcesRequest(request: RequestBuilders.ResourcesRequest) =
        SuspendToFutureAdapter.launchFuture { ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build() }

    private fun layout(
        context: Context,
        s: TickState,
        device: DeviceParameters,
    ) = PrimaryLayout.Builder(device)
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(
            text(
                context,
                if (s.running) {
                    context.getString(
                        R.string.since,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.begin)),
                    )
                } else {
                    context.getString(R.string.not_running)
                },
                Typography.TYPOGRAPHY_CAPTION1,
            ),
        )
        .setContent(
            if (s.running) elapsed(s) else text(context, hoursMinutes(s.week()), Typography.TYPOGRAPHY_DISPLAY1),
        )
        .setSecondaryLabelTextContent(
            text(
                context,
                if (s.running) s.label else context.getString(R.string.week_value, hoursMinutes(s.week())),
                Typography.TYPOGRAPHY_CAPTION2,
            ),
        )
        .setPrimaryChipContent(
            CompactChip.Builder(
                context,
                context.getString(if (s.running) R.string.stop else R.string.start),
                if (s.running || s.lastLabel.isNotBlank()) click(LoadAction) else openApp(context),
                device,
            ).build(),
        )
        .build()

    private fun text(
        context: Context,
        value: String,
        typography: Int,
    ) = Text.Builder(context, value)
        .setTypography(typography)
        .setColor(ColorBuilders.argb(Colors.DEFAULT.onSurface))
        .setMaxLines(1)
        .build()

    /** Running time with seconds, counted by the watch itself so the tile needs no refreshes. */
    private fun elapsed(s: TickState): LayoutElementBuilders.LayoutElement {
        val duration =
            DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(s.begin))
                .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
        val twoDigits = DynamicInt32.IntFormatter.Builder().setMinIntegerDigits(2).build()
        val dynamic =
            duration.toIntHours().format()
                .concat(DynamicString.constant(":"))
                .concat(duration.minutesPart.format(twoDigits))
                .concat(DynamicString.constant(":"))
                .concat(duration.secondsPart.format(twoDigits))
        val sec = s.runningSeconds()
        return LayoutElementBuilders.Text.Builder()
            .setText(
                TypeBuilders.StringProp.Builder("%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60))
                    .setDynamicValue(dynamic)
                    .build(),
            )
            .setLayoutConstraintsForDynamicText(TypeBuilders.StringLayoutConstraint.Builder("00:00:00").build())
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(40f))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                    .setColor(ColorBuilders.argb(Colors.DEFAULT.onSurface))
                    .build(),
            )
            .build()
    }

    private fun click(action: ActionBuilders.Action) =
        ModifiersBuilders.Clickable.Builder().setId(TOGGLE).setOnClick(action).build()

    private fun openApp(context: Context) =
        ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    ).build(),
            ).build()

    private companion object {
        const val RESOURCES = "1"
        const val TOGGLE = "toggle"
        val LoadAction: ActionBuilders.LoadAction = ActionBuilders.LoadAction.Builder().build()
    }
}

/** Refreshes the tile whenever the phone pushes a new state. */
class StateListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        TileService.getUpdater(this).requestUpdate(TimerTile::class.java)
    }
}
