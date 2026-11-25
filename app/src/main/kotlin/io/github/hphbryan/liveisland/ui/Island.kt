package io.github.hphbryan.liveisland.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.AppCategory
import io.github.hphbryan.liveisland.core.KnownApps
import java.util.Date

/**
 * The status bar chip as One UI draws it: a dark pill with the app's icon and a line of text. [live]
 * pulses the dot when there is no icon; [muted] is for an island with nothing on it.
 */
@Composable
fun IslandPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageBitmap? = null,
    live: Boolean = false,
    muted: Boolean = false,
    small: Boolean = false,
) {
    val border = if (isSystemInDarkTheme()) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // A minimum, not a fixed height, so large system font sizes are not clipped.
            .heightIn(min = if (small) 26.dp else 40.dp)
            .clip(CircleShape)
            .background(IslandColors.Ink)
            .then(border)
            .padding(start = if (small) 5.dp else 7.dp, end = if (small) 10.dp else 16.dp),
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(if (small) 16.dp else 26.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(if (small) 16.dp else 26.dp), contentAlignment = Alignment.Center) { LiveDot(live) }
        }
        Spacer(Modifier.width(if (small) 6.dp else 9.dp))
        Text(
            text,
            color = if (muted) IslandColors.OnInkMuted else IslandColors.OnInk,
            style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveDot(live: Boolean) {
    val alpha = if (live) {
        val pulse by rememberInfiniteTransition(label = "live").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulse",
        )
        pulse
    } else {
        1f
    }
    Box(
        Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(if (live) IslandColors.Live else IslandColors.OnInkMuted),
    )
}

/**
 * A slice of the status bar (the clock, then the island) so the pill reads as the real thing, on a
 * rounded stage. [content] goes below it: what the island means right now and what to do about it.
 */
@Composable
fun IslandStage(
    pill: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clearAndSetSemantics { description?.let { contentDescription = it } },
            ) {
                Text(
                    DateFormat.getTimeFormat(context).format(Date()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                pill()
            }
            content()
        }
    }
}

/** What this kind of app puts in the chip, as a sample; null for apps of no known kind. */
@Composable
fun sampleChip(packageName: String): String? = when (KnownApps.categoryOf(packageName)) {
    AppCategory.AI -> stringResource(R.string.chip_replied)
    AppCategory.MESSAGING -> stringResource(
        R.string.chip_message,
        stringResource(R.string.example_sender),
        stringResource(R.string.example_message),
    )
    null -> null
    else -> stringResource(categorySample(KnownApps.categoryOf(packageName)!!))
}

fun categorySample(c: AppCategory): Int = when (c) {
    AppCategory.AI, AppCategory.MESSAGING -> R.string.example_message
    AppCategory.DELIVERY -> R.string.example_delivery
    AppCategory.RIDES -> R.string.example_ride
    AppCategory.TRANSIT -> R.string.example_transit
    AppCategory.WORKOUT -> R.string.example_workout
    AppCategory.TRANSFERS -> R.string.example_transfer
    AppCategory.CHARGING_PARKING -> R.string.example_charging
}

fun categoryTitle(c: AppCategory): Int = when (c) {
    AppCategory.AI -> R.string.apps_ai
    AppCategory.MESSAGING -> R.string.cat_messaging
    AppCategory.DELIVERY -> R.string.cat_delivery
    AppCategory.RIDES -> R.string.cat_rides
    AppCategory.TRANSIT -> R.string.cat_transit
    AppCategory.WORKOUT -> R.string.cat_workout
    AppCategory.TRANSFERS -> R.string.cat_transfers
    AppCategory.CHARGING_PARKING -> R.string.cat_charging
}
