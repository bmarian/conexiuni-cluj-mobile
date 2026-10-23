package online.bus.bmarian.conexiuniclujmobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDateTime
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.formatMinutesFromNow
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLiveColors
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalLineLooks

@Composable
fun rememberNow(): LocalDateTime {
    val clock by Services.clock.collectAsStateWithLifecycle()
    return remember(clock) { LocalDateTime.ofInstant(Instant.ofEpochMilli(clock), CLUJ_ZONE) }
}

/** Port of formatMinutes. */
@Composable
fun minutesLabel(minutes: Int, now: LocalDateTime): String =
    formatMinutesFromNow(minutes, now, stringResource(R.string.w_now))

@Composable
fun LineBadge(
    shortName: String,
    color: String,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    minWidth: Dp = 40.dp,
    corner: Dp = 8.dp,
) {
    val looks = LocalLineLooks.current
    val morph = rememberMorph(shortName, looks.color(shortName, color), looks.favorite(shortName))
    FavoriteMorph(morph, RoundedCornerShape(corner), modifier.widthIn(min = minWidth).heightIn(min = height)) {
        Text(
            shortName,
            Modifier.padding(horizontal = 6.dp),
            color = Color.White,
            fontSize = (height.value * 0.45f).sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
fun RealtimeIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.width * 0.13f
        val corner = Offset(stroke, this.size.height - stroke)
        drawCircle(color, stroke * 1.1f, corner)
        for (step in 1..2) {
            val radius = this.size.width * (0.32f + 0.3f * (step - 1))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(corner.x - radius, corner.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun liveColor(): Color = LocalLiveColors.current.content

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = iconTint)
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
fun SubLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun LoadingRow(modifier: Modifier = Modifier, text: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(Modifier.size(56.dp))
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MessageText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

@Composable
fun RowIcon(icon: ImageVector, container: Color, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

@Composable
fun TimePill(text: String, live: Boolean, modifier: Modifier = Modifier, extra: Boolean = false) {
    val container = when {
        live -> LocalLiveColors.current.container
        extra -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        live -> LocalLiveColors.current.onContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (live) RealtimeIcon(content, size = 10.dp)
        Ticker(
            text,
            style = TextStyle(fontSize = 13.sp, fontWeight = if (extra) FontWeight.Medium else FontWeight.Bold),
            color = content,
        )
    }
}

@Composable
fun TruncatedText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified, maxLines: Int = 1) {
    Text(text, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Lazy list keys must be unique: repeats get a suffix. */
fun <T> withUniqueKeys(items: List<T>, key: (T) -> String): List<Pair<String, T>> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val k = key(item)
        val n = seen.merge(k, 1, Int::plus)!!
        (if (n == 1) k else "$k#$n") to item
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))
