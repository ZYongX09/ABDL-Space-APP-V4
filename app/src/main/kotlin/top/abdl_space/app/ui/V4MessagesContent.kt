package top.abdl_space.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.abdl_space.app.core.designsystem.component.V4Card
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text
import top.abdl_space.app.core.network.dto.ChatConversation
import top.abdl_space.app.core.network.dto.Notification
import top.abdl_space.app.core.network.dto.NotificationKind

/**
 * Read-only messages data. Every list is caller-provided and session-isolated; the UI never
 * fabricates conversations, badges, or notification types, and no polling happens this round.
 */
data class V4NotificationsUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val hasLoaded: Boolean = false,
)

data class V4ConversationsUiState(
    val conversations: List<ChatConversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasLoaded: Boolean = false,
)

data class V4MessagesUiState(
    val notifications: V4NotificationsUiState = V4NotificationsUiState(),
    val conversations: V4ConversationsUiState = V4ConversationsUiState(),
    val lease: top.abdl_space.app.core.data.session.SessionLease? = null,
)

@Composable
internal fun V4MessagesScreen(
    session: V4SessionUiState,
    bottomPadding: Dp,
    messagesState: V4MessagesUiState,
    onTabSelected: (V4MessageTab) -> Unit,
    onRefresh: (V4MessageTab) -> Unit,
    onLoadMoreNotifications: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onSignIn: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val signedIn = session is V4SessionUiState.SignedIn
    // Selection notification is composition-local like the home feeds: a fresh ViewModel may have
    // no cache after process death, and the caller deduplicates loaded or in-flight requests.
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    LaunchedEffect(selected, signedIn) {
        if (signedIn) currentOnTabSelected(V4MessageTab.entries[selected])
    }
    Column(Modifier.fillMaxSize().testTag(V4TestTags.Messages).semantics { paneTitle = "消息" }) {
        V4PageTopBar("消息")
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            V4MessageTab.entries.forEachIndexed { index, tab ->
                Column(
                    Modifier.weight(1f).selectable(selected = selected == index, role = Role.Tab, onClick = { selected = index })
                        .heightIn(min = V4UiDimens.minTouchTarget).padding(V4Dimens.cardGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tab.label, style = V4Text.body, color = if (selected == index) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(V4Dimens.cardGap / 2))
                    Box(Modifier.fillMaxWidth().height(V4UiDimens.tabIndicatorHeight).background(if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background))
                }
            }
        }
        when (session) {
            V4SessionUiState.Guest -> V4MessagesGate(
                title = "登录后查看消息",
                explanation = "私信与通知属于你的私人内容。当前没有已登录账号，不会展示示例会话、虚构通知或未读角标，也不会代你发起任何请求。",
                onSignIn = onSignIn,
                bottomPadding = bottomPadding,
            )
            V4SessionUiState.Restoring -> V4MessagesGate(
                title = "正在确认登录状态",
                explanation = "确认完成后才能决定是否读取消息。当前没有加载任何私人内容。",
                onSignIn = onSignIn,
                bottomPadding = bottomPadding,
            )
            is V4SessionUiState.SignedIn -> when (V4MessageTab.entries[selected]) {
                V4MessageTab.Direct -> V4ConversationsList(
                    state = messagesState.conversations,
                    bottomPadding = bottomPadding,
                    onRefresh = { onRefresh(V4MessageTab.Direct) },
                    onOpenConversation = onOpenConversation,
                )
                V4MessageTab.Notifications -> V4NotificationsList(
                    state = messagesState.notifications,
                    bottomPadding = bottomPadding,
                    onRefresh = { onRefresh(V4MessageTab.Notifications) },
                    onLoadMore = onLoadMoreNotifications,
                )
            }
        }
    }
}

@Composable
private fun V4MessagesGate(title: String, explanation: String, onSignIn: () -> Unit, bottomPadding: Dp) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
    ) {
        V4StatusCard(title, explanation, glyph = V4Glyph.Shield, actionLabel = "登录或了解登录进度", onAction = onSignIn)
        Text("隐私优先 · 未接入不等于没有消息", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun V4ConversationsList(
    state: V4ConversationsUiState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    // The caller deduplicates too; Lazy keys must stay valid if an overlapping page arrives.
    val conversations = remember(state.conversations) { state.conversations.distinctBy { it.userId } }
    var pendingRefresh by remember(state) { mutableStateOf(false) }
    val busy = state.isLoading || pendingRefresh
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag(V4MessagesTags.Conversations),
        contentPadding = PaddingValues(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
    ) {
        item(key = "conversations-header", contentType = "messages-controls") {
            V4Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    V4SectionTitle("私信 · 只读会话列表")
                    Text("会话详情与发送迁移中 · 仅手动刷新，没有轮询", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        enabled = !busy,
                        onClick = { pendingRefresh = true; onRefresh() },
                        modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget),
                    ) { Text(if (state.isLoading && conversations.isNotEmpty()) "正在刷新…" else "刷新会话列表", style = V4Text.body, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        when {
            state.isLoading && conversations.isEmpty() -> item(key = "conversations-loading", contentType = "messages-state") {
                V4MessagesNotice("正在加载会话", "首次请求尚未完成，不会把加载中当作没有会话。")
            }
            state.error != null && conversations.isEmpty() -> item(key = "conversations-error", contentType = "messages-state") {
                V4MessagesNotice("会话列表加载失败", state.error, "重试加载", !busy) { pendingRefresh = true; onRefresh() }
            }
            state.hasLoaded && conversations.isEmpty() -> item(key = "conversations-empty", contentType = "messages-state") {
                V4MessagesNotice("暂时没有会话", "这次请求已完成，服务暂未返回会话。可以稍后手动刷新。")
            }
            else -> {
                if (state.isLoading) {
                    item(key = "conversations-refreshing", contentType = "messages-state") {
                        V4MessagesNotice("正在刷新会话", "仍保留上次已加载的内容。")
                    }
                } else if (state.error != null) {
                    item(key = "conversations-refresh-error", contentType = "messages-state") {
                        V4MessagesNotice("刷新失败，已保留原内容", state.error, "重试刷新", !busy) { pendingRefresh = true; onRefresh() }
                    }
                }
                items(conversations, key = { "conversation:${it.userId}" }, contentType = { "conversation-row" }) { conversation ->
                    V4ConversationRow(conversation) { onOpenConversation(conversation.userId) }
                }
            }
        }
    }
}

@Composable
private fun V4NotificationsList(
    state: V4NotificationsUiState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val notifications = remember(state.notifications) { state.notifications.distinctBy { it.id } }
    var pendingAction by remember(state) { mutableStateOf<String?>(null) }
    val busy = state.isLoading || state.isLoadingMore || pendingAction != null
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag(V4MessagesTags.Notifications),
        contentPadding = PaddingValues(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
    ) {
        item(key = "notifications-header", contentType = "messages-controls") {
            V4Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    V4SectionTitle("通知 · 只读")
                    Text("未知类型原样保留 · 推送注册未接入 · 仅手动刷新", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        enabled = !busy,
                        onClick = { pendingAction = "refresh"; onRefresh() },
                        modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget),
                    ) { Text(if (state.isLoading && notifications.isNotEmpty()) "正在刷新…" else "刷新通知", style = V4Text.body, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        when {
            state.isLoading && notifications.isEmpty() -> item(key = "notifications-loading", contentType = "messages-state") {
                V4MessagesNotice("正在加载通知", "首次请求尚未完成，不会把加载中当作没有通知。")
            }
            state.error != null && notifications.isEmpty() -> item(key = "notifications-error", contentType = "messages-state") {
                V4MessagesNotice("通知加载失败", state.error, "重试加载", !busy) { pendingAction = "refresh"; onRefresh() }
            }
            state.hasLoaded && notifications.isEmpty() -> item(key = "notifications-empty", contentType = "messages-state") {
                V4MessagesNotice("暂时没有通知", "这次请求已完成，服务暂未返回通知。可以稍后手动刷新。")
            }
            else -> {
                if (state.isLoading) {
                    item(key = "notifications-refreshing", contentType = "messages-state") {
                        V4MessagesNotice("正在刷新通知", "仍保留上次已加载的内容。")
                    }
                } else if (state.error != null) {
                    item(key = "notifications-refresh-error", contentType = "messages-state") {
                        V4MessagesNotice("刷新失败，已保留原内容", state.error, "重试刷新", !busy) { pendingAction = "refresh"; onRefresh() }
                    }
                }
                items(notifications, key = { "notification:${it.id}" }, contentType = { "notification-row" }) { notification ->
                    V4NotificationRow(notification)
                }
            }
        }
        if (!state.isLoading && (notifications.isNotEmpty() || state.hasLoaded)) {
            item(key = "notifications-footer", contentType = "messages-pagination") {
                when {
                    state.isLoadingMore -> V4MessagesNotice("正在加载更多", "已有内容保持可读。")
                    state.loadMoreError != null -> V4MessagesNotice("更多通知加载失败", state.loadMoreError, "重试加载更多", !busy) {
                        pendingAction = "append"; onLoadMore()
                    }
                    state.hasMore -> V4MessagesNotice("还有更多通知", "点击后才会请求下一页。", "加载更多", !busy) {
                        pendingAction = "append"; onLoadMore()
                    }
                    else -> Text("已显示本次可用内容", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun V4NotificationRow(notification: Notification) {
    val actor = notification.account?.let { it.preferredDisplayName.ifBlank { "账号 ${it.id}" } }
    V4Card(Modifier.fillMaxWidth().testTag("v4.notification.${notification.id}")) {
        Row(
            Modifier.padding(V4Dimens.cardPadding),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            V4TextAvatar(actor.orEmpty())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap / 2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    Column(Modifier.weight(1f)) {
                        Text(actor ?: "账号资料未提供", style = V4Text.body, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(notificationKindLabel(notification.kind, notification.rawType), style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatNotificationTime(notification.createdAt), style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                notification.status?.let { status ->
                    val contentWarning = status.sensitive || status.mentalCrisis || status.spoilerText.isNotBlank()
                    val summary = remember(status.content, status.spoilerText, contentWarning) {
                        if (contentWarning) "敏感内容已折叠" else htmlToPlainText(status.content).ifBlank { null }
                    }
                    if (summary != null) {
                        Text(summary, style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun V4ConversationRow(conversation: ChatConversation, onOpen: () -> Unit) {
    val title = conversation.username.ifBlank { "用户 ${conversation.userId}" }
    V4Card(Modifier.fillMaxWidth().testTag("v4.conversation.${conversation.userId}")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = V4UiDimens.minTouchTarget)
                .clickable(role = Role.Button, onClickLabel = "打开会话", onClick = onOpen)
                .padding(V4Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            V4TextAvatar(conversation.username)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap / 2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    Text(title, style = V4Text.body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    formatConversationTime(conversation.lastMessageAt)?.let { time ->
                        Text(time, style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    Text(
                        conversation.lastMessage?.takeIf { it.isNotBlank() } ?: "还没有消息",
                        style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // The count is the server's own unread_count; no badge exists without it.
                    if (conversation.unreadCount > 0) {
                        Text(
                            "未读 ${conversation.unreadCount}",
                            style = V4Text.bodySecondary,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(V4Dimens.chipRadius)).padding(horizontal = V4Dimens.cardGap / 2, vertical = V4Dimens.cardGap / 4),
                        )
                    }
                }
            }
        }
    }
}

/** Text-only avatar placeholder; network avatars are deliberately not loaded this round. */
@Composable
private fun V4TextAvatar(name: String) {
    Box(
        Modifier.size(V4Dimens.avatarFeed).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().take(1)
        if (initial.isEmpty()) {
            V4Symbol(V4Glyph.Person, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            Text(initial, style = V4Text.body, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun V4MessagesNotice(title: String, detail: String, action: String? = null, enabled: Boolean = true, onAction: () -> Unit = {}) {
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

/** Honest placeholder: conversation detail migration is explicitly not part of this round. */
@Composable
internal fun V4ConversationDetailPlaceholder(bottomPadding: Dp, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().testTag(V4MessagesTags.ConversationDetail).semantics { paneTitle = "会话" }) {
        V4PageTopBar("会话", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            V4StatusCard(
                "会话详情迁移中",
                "旧版聊天历史依靠服务端同步。本轮只接入会话列表的只读展示；这里不会读取、发送或标记任何消息。",
                glyph = V4Glyph.Empty,
            )
            Text("发送、已读回执与同步事件尚未接入。", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun notificationKindLabel(kind: NotificationKind, rawType: String): String = when (kind) {
    NotificationKind.Follow -> "关注了你"
    NotificationKind.FollowRequest -> "请求关注你"
    NotificationKind.Mention -> "提及了你"
    NotificationKind.Reblog -> "转发了你的帖子"
    NotificationKind.Favourite -> "喜欢了你的帖子"
    NotificationKind.Poll -> "你参与的投票已结束"
    NotificationKind.Status -> "发布了新帖子"
    NotificationKind.Update -> "编辑了提到的帖子"
    NotificationKind.SeveredRelationships -> "关注关系被服务端解除"
    NotificationKind.ModerationWarning -> "收到管理警告"
    NotificationKind.Quote -> "引用了你的帖子"
    NotificationKind.QuotedUpdate -> "编辑了引用你的帖子"
    NotificationKind.ExplicitUnknown -> if (rawType.isBlank()) "未知通知类型" else "未知通知类型 · $rawType"
}

private val TimelineTimeFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm xxx", Locale.ROOT)
private val MinuteFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
private val SqlUtcFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)

/** Mastodon created_at is ISO 8601; an unparseable value stays verbatim instead of vanishing. */
internal fun formatNotificationTime(isoTimestamp: String): String =
    runCatching { OffsetDateTime.parse(isoTimestamp).format(TimelineTimeFormat) }.getOrDefault(isoTimestamp)

/**
 * Conversation timestamps are the legacy self-hosted shapes: SQL UTC strings or epoch seconds
 * (ChatMessage/ChatConversation retain them verbatim). Anything else is shown unchanged.
 */
internal fun formatConversationTime(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    if (value.matches(Regex("[0-9]+"))) {
        val epochSeconds = value.toLongOrNull() ?: return value
        return MinuteFormat.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epochSeconds))
    }
    runCatching { return OffsetDateTime.parse(value).format(TimelineTimeFormat) }
    runCatching { return LocalDateTime.parse(value, SqlUtcFormat).format(MinuteFormat) }
    return value
}

/** Test tags owned by the messages feature so tests do not rely on display copy. */
internal object V4MessagesTags {
    const val Notifications = "v4.messages.notifications"
    const val Conversations = "v4.messages.conversations"
    const val ConversationDetail = "v4.messages.conversation-detail"
}
