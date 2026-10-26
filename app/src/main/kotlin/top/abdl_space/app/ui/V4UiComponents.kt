package top.abdl_space.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.abdl_space.app.core.designsystem.component.V4Card
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text

/** Aliases keep this feature shell on core tokens; the profile size is specified in UI spec §我的. */
internal object V4UiDimens {
    val minTouchTarget = V4Dimens.minimumTouchTarget
    val icon = V4Dimens.navIcon
    val profileAvatar = 64.dp
    val navBottomPadding = V4Dimens.navBarBottomPad
    val tabIndicatorHeight = V4Dimens.glassBorder * 2
}

internal enum class V4Glyph {
    Search, Back, Forward, Close, Settings, Bell, Person, Map, Card, Diaper, Book, Nbw, Scan,
    City, Topic, Bookmark, History, Album, Shield, Heart, Edit, Empty, Sun, Moon, System,
}

/** Small original line drawings; no reference APK assets or material-icons bundle. */
@Composable
internal fun V4Symbol(
    glyph: V4Glyph,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    description: String? = null,
) {
    Canvas(
        modifier = modifier
            .size(V4UiDimens.icon)
            .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description }),
    ) {
        withTransform({ scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) }) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.8f, cap = StrokeCap.Round)
            fun circle(x: Float, y: Float, radius: Float) = drawCircle(tint, radius, Offset(x, y), style = stroke)
            fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float = 2f) =
                drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(radius), style = stroke)
            fun path(vararg points: Pair<Float, Float>) {
                drawPath(Path().apply {
                    points.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.first, point.second) else lineTo(point.first, point.second)
                    }
                }, tint, style = stroke)
            }
            when (glyph) {
                V4Glyph.Search -> { circle(10f, 10f, 6f); line(14.5f, 14.5f, 21f, 21f) }
                V4Glyph.Back -> { path(14f to 5f, 7f to 12f, 14f to 19f); line(7f, 12f, 21f, 12f) }
                V4Glyph.Forward -> path(9f to 5f, 16f to 12f, 9f to 19f)
                V4Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                V4Glyph.Person -> { circle(12f, 8f, 4f); drawArc(tint, 180f, 180f, false, Offset(4f, 14f), Size(16f, 12f), style = stroke) }
                V4Glyph.Settings -> {
                    circle(12f, 12f, 7f); circle(12f, 12f, 2.5f)
                    line(12f, 2f, 12f, 5f); line(12f, 19f, 12f, 22f)
                    line(2f, 12f, 5f, 12f); line(19f, 12f, 22f, 12f)
                }
                V4Glyph.Bell -> { path(5f to 17f, 7f to 14f, 7f to 9f, 9f to 5f, 15f to 5f, 17f to 9f, 17f to 14f, 19f to 17f, 5f to 17f); line(10f, 21f, 14f, 21f) }
                V4Glyph.Map -> { path(3f to 6f, 9f to 3f, 15f to 6f, 21f to 3f, 21f to 18f, 15f to 21f, 9f to 18f, 3f to 21f, 3f to 6f); line(9f, 3f, 9f, 18f); line(15f, 6f, 15f, 21f) }
                V4Glyph.Card -> { rect(2f, 5f, 20f, 14f); circle(8f, 10f, 2f); line(5f, 15f, 11f, 15f); line(15f, 10f, 19f, 10f); line(15f, 14f, 19f, 14f) }
                V4Glyph.Diaper -> { path(3f to 5f, 21f to 5f, 19f to 15f, 15f to 20f, 9f to 20f, 5f to 15f, 3f to 5f); path(4f to 10f, 9f to 12f, 10f to 19f); path(20f to 10f, 15f to 12f, 14f to 19f) }
                V4Glyph.Book -> { path(12f to 6f, 8f to 4f, 3f to 4f, 3f to 19f, 8f to 19f, 12f to 21f, 16f to 19f, 21f to 19f, 21f to 4f, 16f to 4f, 12f to 6f, 12f to 21f) }
                V4Glyph.Nbw -> { rect(3f, 3f, 18f, 18f, 5f); path(7f to 17f, 7f to 7f, 17f to 17f, 17f to 7f) }
                V4Glyph.Scan -> { path(3f to 9f, 3f to 3f, 9f to 3f); path(15f to 3f, 21f to 3f, 21f to 9f); path(21f to 15f, 21f to 21f, 15f to 21f); path(9f to 21f, 3f to 21f, 3f to 15f); line(7f, 12f, 17f, 12f) }
                V4Glyph.City -> { rect(4f, 10f, 7f, 11f, 1f); rect(11f, 3f, 9f, 18f, 1f); line(15f, 7f, 16f, 7f); line(15f, 11f, 16f, 11f); line(15f, 15f, 16f, 15f) }
                V4Glyph.Topic -> { line(9f, 3f, 7f, 21f); line(17f, 3f, 15f, 21f); line(3f, 9f, 22f, 9f); line(2f, 16f, 21f, 16f) }
                V4Glyph.Bookmark -> path(6f to 3f, 18f to 3f, 18f to 21f, 12f to 17f, 6f to 21f, 6f to 3f)
                V4Glyph.History -> { circle(12f, 12f, 9f); path(12f to 6f, 12f to 12f, 16f to 14f) }
                V4Glyph.Album -> { rect(3f, 3f, 18f, 18f); circle(16f, 8f, 2f); path(3f to 17f, 9f to 11f, 16f to 21f) }
                V4Glyph.Shield -> { path(12f to 2f, 21f to 6f, 19f to 16f, 12f to 22f, 5f to 16f, 3f to 6f, 12f to 2f); path(8f to 11f, 11f to 14f, 16f to 9f) }
                V4Glyph.Heart -> path(12f to 21f, 3f to 12f, 3f to 6f, 7f to 3f, 12f to 7f, 17f to 3f, 21f to 6f, 21f to 12f, 12f to 21f)
                V4Glyph.Edit -> { path(4f to 20f, 5f to 14f, 16f to 3f, 21f to 8f, 10f to 19f, 4f to 20f); line(13f, 6f, 18f, 11f) }
                V4Glyph.Empty -> { rect(4f, 5f, 16f, 16f, 3f); line(8f, 2f, 16f, 2f); line(8f, 11f, 16f, 11f); line(8f, 15f, 13f, 15f) }
                V4Glyph.Sun -> { circle(12f, 12f, 4f); line(12f, 2f, 12f, 5f); line(12f, 19f, 12f, 22f); line(2f, 12f, 5f, 12f); line(19f, 12f, 22f, 12f); line(5f, 5f, 7f, 7f); line(17f, 17f, 19f, 19f); line(5f, 19f, 7f, 17f); line(17f, 7f, 19f, 5f) }
                V4Glyph.Moon -> path(16f to 3f, 10f to 4f, 5f to 9f, 4f to 15f, 8f to 20f, 14f to 21f, 20f to 17f, 14f to 16f, 10f to 12f, 10f to 7f, 16f to 3f)
                V4Glyph.System -> { rect(3f, 4f, 18f, 13f); line(12f, 17f, 12f, 21f); line(8f, 21f, 16f, 21f) }
            }
        }
    }
}

internal fun V4Feature.glyph(): V4Glyph = when (this) {
    V4Feature.Map -> V4Glyph.Map
    V4Feature.FriendCard, V4Feature.FriendRequest -> V4Glyph.Card
    V4Feature.Diaper -> V4Glyph.Diaper
    V4Feature.Novel -> V4Glyph.Book
    V4Feature.Nbw, V4Feature.MyNbw -> V4Glyph.Nbw
    V4Feature.Scan -> V4Glyph.Scan
    V4Feature.City -> V4Glyph.City
    V4Feature.Topics -> V4Glyph.Topic
    V4Feature.Favorites -> V4Glyph.Bookmark
    V4Feature.History -> V4Glyph.History
    V4Feature.Album -> V4Glyph.Album
    V4Feature.Verification, V4Feature.Privacy -> V4Glyph.Shield
    V4Feature.Sponsor -> V4Glyph.Heart
    V4Feature.NotificationSettings, V4Feature.Announcement -> V4Glyph.Bell
    V4Feature.About -> V4Glyph.Empty
}

@Composable
internal fun V4SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier.semantics { heading() }, style = V4Text.title, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
internal fun V4PageTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = V4UiDimens.minTouchTarget).padding(horizontal = V4Dimens.pagePaddingH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(V4UiDimens.minTouchTarget)) {
                    V4Symbol(V4Glyph.Back, description = "返回")
                }
            }
            V4SectionTitle(title, Modifier.weight(1f).padding(vertical = V4Dimens.cardGap))
            action?.invoke()
        }
    }
}

@Composable
internal fun V4SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "打开搜索", onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.heightIn(min = V4UiDimens.minTouchTarget).padding(horizontal = V4Dimens.cardPadding, vertical = V4Dimens.cardGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            V4Symbol(V4Glyph.Search)
            Text("搜索社区与功能", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun V4StatusCard(
    title: String,
    explanation: String,
    modifier: Modifier = Modifier,
    glyph: V4Glyph = V4Glyph.Empty,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    V4Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
            Box(Modifier.size(V4Dimens.fabSize).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                V4Symbol(glyph, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            V4SectionTitle(title)
            Text(explanation, style = V4Text.body, color = MaterialTheme.colorScheme.onSurface)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                    Text(actionLabel, style = V4Text.bodySecondary)
                }
            }
        }
    }
}

/** Four columns normally, two when large type or a narrow window requires more room. */
@Composable
internal fun V4FeatureGrid(features: List<V4Feature>, onFeature: (V4Feature) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val normalWidth = (V4UiDimens.minTouchTarget + V4Dimens.cardGap) * 4 + V4Dimens.cardGap * 3
        val columns = if (fontScale > 1.3f || maxWidth < normalWidth) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
            features.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    row.forEach { feature ->
                        Column(
                            modifier = Modifier.weight(1f)
                                .heightIn(min = V4UiDimens.minTouchTarget)
                                .clip(RoundedCornerShape(V4Dimens.chipRadius))
                                .clickable(role = Role.Button, onClickLabel = "打开${feature.title}功能说明", onClick = { onFeature(feature) })
                                .padding(vertical = V4Dimens.cardPadding, horizontal = V4Dimens.cardGap),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
                        ) {
                            V4Symbol(feature.glyph(), tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(V4Dimens.chipRadius)).padding(V4Dimens.cardGap))
                            Text(feature.title, style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun V4ActionRow(title: String, summary: String, glyph: V4Glyph, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = V4UiDimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(V4Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
    ) {
        V4Symbol(glyph)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap / 2)) {
            Text(title, style = V4Text.body, color = MaterialTheme.colorScheme.onSurface)
            Text(summary, style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        V4Symbol(V4Glyph.Forward)
    }
}
