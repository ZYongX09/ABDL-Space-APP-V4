package top.abdl_space.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import top.abdl_space.app.core.designsystem.R
import top.abdl_space.app.core.designsystem.glass.V4GlassBackdrop
import top.abdl_space.app.core.designsystem.glass.V4GlassSurface
import top.abdl_space.app.core.designsystem.icon.V4Icons
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Theme
import top.abdl_space.app.core.designsystem.theme.V4TypeTokens

/** A navigation destination. A null entry reserves the central publish action (not a Tab). */
@Immutable
data class V4PillNavItem(val icon: ImageVector, val label: String)

/**
 * Floating pill navigation: selected capsule, semantic Tabs and an independent publish Button.
 * [selectedIndex] indexes [items] including its null publish slot, matching the original API.
 * Without [backdrop] this is an opaque fallback. To enable glass pass the provider overlay handle.
 *
 * The bar grows with font scale. On exceptionally narrow windows it scrolls horizontally rather
 * than shrinking click targets below 48dp. Publish never calls [onSelect]. All new args are optional.
 */
@Composable
fun V4PillNavigationBar(
    items: List<V4PillNavItem?>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPublishClick: () -> Unit = {},
    onPublishLongClick: (() -> Unit)? = null,
    backdrop: V4GlassBackdrop? = null,
) {
    require(items.isNotEmpty()) { "Navigation requires at least one destination" }
    require(selectedIndex in items.indices && items[selectedIndex] != null) {
        "selectedIndex must identify a destination, not the publish action"
    }
    require(items.count { it == null } <= 1) { "Only one publish action is supported" }
    require(items.filterNotNull().all { it.label.isNotBlank() }) { "Tabs need accessible labels" }

    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = V4Dimens.navBarMarginH)
            .navigationBarsPadding()
            .padding(bottom = V4Dimens.navBarBottomPad)
            .fillMaxWidth(),
    ) {
        val minimumSlotWidth = if (items.any { it == null }) V4Dimens.fabSize else V4Dimens.minimumTouchTarget
        val slotWidth = if (constraints.hasBoundedWidth) {
            maxOf(
                minimumSlotWidth,
                (maxWidth - V4Dimens.navBarInnerPadding * 2) / items.size,
            )
        } else {
            V4Dimens.fabSize
        }
        // Only the background is clipped. The raised publish key is a sibling, not inside glass.
        V4GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.matchParentSize().padding(top = V4Dimens.fabLift),
            shape = CircleShape,
        ) {}
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .heightIn(min = V4Dimens.navBarHeight + V4Dimens.fabLift)
                .padding(
                    top = V4Dimens.fabLift,
                    start = V4Dimens.navBarInnerPadding,
                    end = V4Dimens.navBarInnerPadding,
                )
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                if (item == null) {
                    PublishButton(
                        onClick = onPublishClick,
                        onLongClick = onPublishLongClick,
                        modifier = Modifier.width(slotWidth),
                    )
                } else {
                    PillTab(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.width(slotWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun PillTab(
    item: V4PillNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = V4Theme.colors
    val contentColor = if (selected) colors.accentText else colors.textSecondary
    Column(
        modifier = modifier
            .heightIn(min = V4Dimens.minimumTouchTarget)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.navActivePill) else Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = V4Dimens.navItemPaddingH, vertical = V4Dimens.navItemPaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            // The visible label is merged into the Tab's single accessibility announcement.
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(V4Dimens.navIcon),
        )
        Text(text = item.label, style = V4TypeTokens.tabLabel, color = contentColor)
    }
}

@Composable
private fun PublishButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = V4Theme.colors
    val label = stringResource(R.string.v4_publish)
    val expandLabel = stringResource(R.string.v4_publish_expand)
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .offset(y = -V4Dimens.fabLift)
                .size(V4Dimens.fabSize)
                .clip(CircleShape)
                .background(colors.accent)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = if (onLongClick != null) expandLabel else null,
                )
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(V4Icons.Add),
                contentDescription = null,
                // Brand onPrimary (#0A141C), not white or the light-theme text-primary pair.
                tint = colors.onAccent,
                modifier = Modifier.size(V4Dimens.navIcon),
            )
        }
    }
}
