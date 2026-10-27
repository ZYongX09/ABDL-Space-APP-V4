package top.abdl_space.app.ui

import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.abdl_space.app.core.designsystem.component.V4Card
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text
import top.abdl_space.app.core.model.Status

@Composable
internal fun V4ReadOnlyTimelinePage(
    kind: V4TimelineKind,
    label: String,
    state: V4TimelineUiState?,
    listState: LazyListState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // The caller should deduplicate pages too. Keep Lazy keys valid if overlapping pages arrive.
    val statuses = remember(state?.statuses) { state?.statuses.orEmpty().distinctBy { it.id } }
    // Latch a tap until the caller acknowledges it with new state; recomposition alone cannot send
    // the same request twice. No scroll observer automatically fetches pages or bypasses a failure.
    var pendingAction by remember(state) { mutableStateOf<String?>(null) }
    val busy = state?.isLoading == true || state?.isLoadingMore == true || pendingAction != null
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("v4.feed.${kind.name}"),
        contentPadding = PaddingValues(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
    ) {
        item(key = "timeline-header", contentType = "timeline-controls") {
            V4Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    V4SectionTitle("$label · 只读时间线")
                    Text("文字与计数可读 · 媒体与互动迁移中", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state != null) {
                        TextButton(
                            enabled = !busy,
                            onClick = { pendingAction = "refresh"; onRefresh() },
                            modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget),
                        ) { Text(if (state.isLoading && statuses.isNotEmpty()) "正在刷新…" else "刷新时间线", style = V4Text.body, color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
        when {
            state == null -> item(key = "not-connected", contentType = "timeline-state") {
                V4StatusCard("${label}内容尚未接入", "本轮调用方没有提供这条时间线的数据。没有示例帖子，也不会由 UI 自行联网。")
            }
            statuses.isEmpty() -> item(key = "initial-state", contentType = "timeline-state") {
                when {
                    state.isLoading -> V4TimelineNotice("正在加载$label", "首次请求尚未完成，不会把加载中当作没有帖子。")
                    state.error != null -> V4TimelineNotice("$label 加载失败", state.error, "重试加载", !busy) {
                        pendingAction = "refresh"; onRefresh()
                    }
                    state.hasLoaded -> V4TimelineNotice("暂时没有${label}内容", "这次请求已完成，服务暂未返回帖子。可以稍后刷新。")
                    else -> V4TimelineNotice("等待加载$label", "登录状态已确认，等待应用层提供这条时间线的首屏结果。")
                }
            }
            else -> {
                if (state.isLoading) {
                    item(key = "refreshing", contentType = "timeline-state") { V4TimelineNotice("正在刷新$label", "仍保留上次已加载的内容。") }
                } else if (state.error != null) {
                    item(key = "refresh-error", contentType = "timeline-state") {
                        V4TimelineNotice("刷新失败，已保留原内容", state.error, "重试刷新", !busy) { pendingAction = "refresh"; onRefresh() }
                    }
                }
                items(statuses, key = { "status:${it.id}" }, contentType = { "status-text" }) { status ->
                    V4ReadOnlyStatusCard(status)
                }
            }
        }
        if (state != null && (statuses.isNotEmpty() || state.hasLoaded) && !state.isLoading) {
            item(key = "timeline-footer", contentType = "timeline-pagination") {
                when {
                    state.isLoadingMore -> V4TimelineNotice("正在加载更多", "已有内容保持可读。")
                    state.loadMoreError != null -> V4TimelineNotice("更多内容加载失败", state.loadMoreError, "重试加载更多", !busy) {
                        pendingAction = "append"; onLoadMore()
                    }
                    state.hasMore -> V4TimelineNotice("还有更多内容", "点击后才会请求下一页。", "加载更多", !busy) {
                        pendingAction = "append"; onLoadMore()
                    }
                    else -> Text("已显示本次可用内容", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun V4TimelineNotice(title: String, detail: String, action: String? = null, enabled: Boolean = true, onAction: () -> Unit = {}) {
    V4Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(V4Dimens.cardPadding).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
            V4SectionTitle(title)
            Text(detail, style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Button(onClick = onAction, enabled = enabled, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) { Text(action, style = V4Text.bodySecondary) }
            }
        }
    }
}

@Composable
private fun V4ReadOnlyStatusCard(envelope: Status) {
    val status = envelope.reblog ?: envelope
    val plainBody = remember(status.content, status.text) {
        if (status.content.isNotBlank()) htmlToPlainText(status.content) else status.text.orEmpty()
    }
    val warning = remember(status.spoilerText) { htmlToPlainText(status.spoilerText) }
    val hasWarning = status.sensitive || status.mentalCrisis || warning.isNotBlank()
    var revealed by rememberSaveable(envelope.id, status.id, status.sensitive, status.mentalCrisis, status.spoilerText) { mutableStateOf(false) }
    var expanded by rememberSaveable(envelope.id, status.id, status.content, status.text) { mutableStateOf(false) }
    var overflowing by remember(status.content, status.text) { mutableStateOf(false) }
    val date = remember(status.createdAt) {
        runCatching { OffsetDateTime.parse(status.createdAt).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm xxx", Locale.ROOT)) }
            .getOrDefault(status.createdAt)
    }
    V4Card(modifier = Modifier.fillMaxWidth().testTag("v4.status.${envelope.id}")) {
        Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
            if (envelope.reblog != null) {
                Text("${envelope.account.preferredDisplayName.ifBlank { envelope.account.id }} 转发", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(status.account.preferredDisplayName.ifBlank { status.account.id }, style = V4Text.title, color = MaterialTheme.colorScheme.onSurface)
            val accountHandle = status.account.acct.ifBlank { status.account.username }
            if (accountHandle.isNotBlank()) Text("@$accountHandle", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date, style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasWarning) {
                V4SectionTitle("内容提示（CW）")
                Text(warning.ifBlank { if (status.mentalCrisis) "心理健康相关内容，正文默认折叠。" else "作者已标记为敏感内容，正文默认折叠。" }, style = V4Text.body)
                TextButton(onClick = { revealed = !revealed }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                    Text(if (revealed) "收起正文" else "展开正文", style = V4Text.body, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (!hasWarning || revealed) {
                Text(
                    plainBody.ifBlank { "本帖没有文字正文" }, style = V4Text.body, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
                )
                if (overflowing || expanded) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                        Text(if (expanded) "收起长文" else "展开全文", style = V4Text.body, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (status.emojis.isNotEmpty()) {
                    Text("自定义表情暂以文字标记显示", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (status.mediaAttachments.isNotEmpty()) {
                Text("${status.mediaAttachments.size} 个媒体附件 · 媒体浏览迁移中", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status.poll != null) {
                Text("此帖含投票 · 投票查看与参与尚未接入", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("转发 ${status.reblogsCount} · 评论 ${status.repliesCount} · 点赞 ${status.favouritesCount}（只读）", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Html.fromHtml without an ImageGetter performs no fetch. Strip image placeholders, keep text. */
internal fun htmlToPlainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    .toString().replace("\uFFFC", "").trim()
