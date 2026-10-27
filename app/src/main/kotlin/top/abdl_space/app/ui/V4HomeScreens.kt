package top.abdl_space.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.abdl_space.app.core.designsystem.component.V4Card
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text

@Composable
internal fun V4HomeScreen(
    session: V4SessionUiState,
    bottomPadding: Dp,
    reduceMotion: Boolean,
    onSearch: () -> Unit,
    onFeature: (V4Feature) -> Unit,
    onSignIn: () -> Unit,
    timelineState: Map<V4TimelineKind, V4TimelineUiState>,
    onTimelineSelected: (V4TimelineKind) -> Unit,
    onTimelineRefresh: (V4TimelineKind) -> Unit,
    onLoadMore: (V4TimelineKind) -> Unit,
    publishFeedback: String? = null,
    onPublishFeedbackDismiss: () -> Unit = {},
) {
    val pagerState = rememberPagerState { V4Feed.entries.size }
    val scope = rememberCoroutineScope()
    // Each actual pager page owns a real LazyListState; Navigation's SaveableStateHolder retains it.
    val feedListStates = V4Feed.entries.map { rememberLazyListState() }
    val currentOnSelected by rememberUpdatedState(onTimelineSelected)
    // Request notification is composition-local, not restorable data: a new ViewModel may have
    // no timeline cache after process death. The caller deduplicates loaded/in-flight requests.
    var lastNotifiedPage by remember { mutableIntStateOf(-1) }
    val signedIn = session is V4SessionUiState.SignedIn
    LaunchedEffect(pagerState, signedIn) {
        if (!signedIn) {
            lastNotifiedPage = -1
            return@LaunchedEffect
        }
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { page ->
            if (lastNotifiedPage != page) {
                lastNotifiedPage = page
                V4Feed.entries[page].timelineKind?.let { currentOnSelected(it) }
            }
        }
    }
    Column(Modifier.fillMaxSize().testTag(V4TestTags.Home).semantics { paneTitle = "首页" }) {
        if (publishFeedback != null) {
            // The only "posted" claim in the app: the server returned the created Status.
            V4Card(Modifier.fillMaxWidth().padding(horizontal = V4Dimens.pagePaddingH).testTag(V4TestTags.PublishFeedback)) {
                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    V4SectionTitle("发帖成功")
                    Text(publishFeedback, style = V4Text.bodySecondary)
                    TextButton(
                        onClick = onPublishFeedbackDismiss,
                        modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget),
                    ) { Text("知道了", style = V4Text.bodySecondary) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = V4Dimens.pagePaddingH, vertical = V4Dimens.cardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            V4SearchEntry(onSearch, Modifier.weight(1f))
            IconButton(onClick = { onFeature(V4Feature.Announcement) }, modifier = Modifier.size(V4UiDimens.minTouchTarget)) {
                V4Symbol(V4Glyph.Bell, description = "社区公告")
            }
        }
        @Suppress("DEPRECATION")
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            edgePadding = V4Dimens.pagePaddingH,
        ) {
            V4Feed.entries.forEachIndexed { index, feed ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { if (reduceMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index) } },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget),
                    text = { Text(feed.label, style = V4Text.body) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), key = { V4Feed.entries[it].name }) { index ->
            val feed = V4Feed.entries[index]
            val kind = feed.timelineKind
            if (signedIn && kind != null) {
                V4ReadOnlyTimelinePage(
                    kind = kind,
                    label = feed.label,
                    state = timelineState[kind],
                    listState = feedListStates[index],
                    bottomPadding = bottomPadding,
                    onRefresh = { onTimelineRefresh(kind) },
                    onLoadMore = { onLoadMore(kind) },
                )
            } else {
                LazyColumn(
                    state = feedListStates[index],
                    modifier = Modifier.fillMaxSize().testTag("v4.feed.${feed.name}"),
                    contentPadding = PaddingValues(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
                ) {
                    item("session") {
                        when (session) {
                            V4SessionUiState.Guest -> V4StatusCard(
                                title = "以访客身份浏览",
                                explanation = "当前没有已登录账号。首页时间线只在登录后按需读取；访客不会自动联网，也不会看到示例帖子。",
                                glyph = V4Glyph.Person, actionLabel = "登录或了解登录进度", onAction = onSignIn,
                            )
                            V4SessionUiState.Restoring -> V4StatusCard("正在确认登录状态", "账户状态由应用层恢复中。在确认前，不会展示私人数据或假定你已登录。", glyph = V4Glyph.Shield)
                            is V4SessionUiState.SignedIn -> V4Card {
                                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                                    V4SectionTitle("社区首页")
                                    Text("账号状态已由应用提供。NBW 尚未接入，不会把未加载内容当作你的动态。", style = V4Text.bodySecondary)
                                }
                            }
                        }
                    }
                    item("feed-state") {
                        V4StatusCard(
                            if (feed == V4Feed.Nbw) "NBW 内容尚未接入" else "${feed.label}内容尚未加载",
                            feed.explanation, glyph = if (feed == V4Feed.Nbw) V4Glyph.Nbw else V4Glyph.Empty,
                        )
                    }
                    item("explore") {
                        V4Card {
                            V4ActionRow("探索社区入口", "功能导航可用，业务接入范围会明确说明", V4Glyph.Map) { onFeature(V4Feature.Map) }
                            V4ActionRow("发现话题", "话题服务开发中", V4Glyph.Topic) { onFeature(V4Feature.Topics) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun V4DiscoverScreen(bottomPadding: Dp, onSearch: () -> Unit, onFeature: (V4Feature) -> Unit) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag(V4TestTags.Discover).semantics { paneTitle = "发现" },
        contentPadding = PaddingValues(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
    ) {
        item("search") { V4SearchEntry(onSearch, Modifier.fillMaxWidth()) }
        item("title") {
            Column(verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                V4SectionTitle("发现社区")
                Text("八个入口，连接更多可能", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("features") {
            V4Card {
                Column(Modifier.padding(V4Dimens.cardGap)) { V4FeatureGrid(V4FeatureCatalog.discovery, onFeature) }
            }
        }
        item("boundary") {
            Text("NBW 入口已读取真实能力状态；其余宫格仍是功能说明，尚不能读取线上业务数据。", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item("recommendations") {
            V4StatusCard("推荐内容尚未接入", "没有可核实的推荐内容与热门话题。先了解功能入口，业务接入后才会呈现真实内容。", glyph = V4Glyph.Empty)
        }
    }
}

@Composable
internal fun V4MeScreen(
    session: V4SessionUiState,
    bottomPadding: Dp,
    hasDraft: Boolean,
    onAccount: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: (() -> Unit)?,
    onSettings: () -> Unit,
    onFeature: (V4Feature) -> Unit,
    onDraft: () -> Unit,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag(V4TestTags.Me).semantics { paneTitle = "我的" },
        contentPadding = PaddingValues(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
    ) {
        item("title") {
            V4PageTopBar("我的", action = {
                IconButton(onClick = onSettings, modifier = Modifier.size(V4UiDimens.minTouchTarget)) { V4Symbol(V4Glyph.Settings, description = "设置") }
            })
        }
        item("profile") {
            V4Card {
                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    Box(Modifier.size(V4UiDimens.profileAvatar).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        V4Symbol(V4Glyph.Person, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    V4SectionTitle(when (session) {
                        V4SessionUiState.Guest -> "尚未登录"
                        V4SessionUiState.Restoring -> "正在确认账号"
                        is V4SessionUiState.SignedIn -> session.displayName.ifBlank { "已登录账号" }
                    })
                    Text(when (session) {
                        V4SessionUiState.Guest -> "以访客身份探索社区；不会替你创建或登录账号。"
                        V4SessionUiState.Restoring -> "登录状态由应用层恢复中。"
                        is V4SessionUiState.SignedIn -> "身份由应用提供；资料、等级、动态与关注统计尚未加载。"
                    }, style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (session != V4SessionUiState.Restoring) {
                        TextButton(onClick = if (session is V4SessionUiState.SignedIn) onAccount else onSignIn, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                            Text(if (session is V4SessionUiState.SignedIn) "账号状态" else "登录或了解登录进度", style = V4Text.body, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
        item("stats-boundary") {
            Text("动态 / 关注 / 粉丝统计尚未接入，不展示占位数字。", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item("basics") {
            V4SectionTitle("基础功能")
            V4Card { V4FeatureGrid(V4FeatureCatalog.basics, onFeature) }
        }
        item("community") {
            V4SectionTitle("社区服务")
            V4Card { V4FeatureGrid(V4FeatureCatalog.community, onFeature) }
        }
        item("local") {
            V4SectionTitle("本地与外观")
            V4Card {
                V4ActionRow("本地草稿", if (hasDraft) "有一份本轮保存的文字草稿" else "尚无已保存草稿 · 可打开本地编辑器", V4Glyph.Edit, onDraft)
                V4ActionRow("设置", "外观可用 · 账户与隐私服务开发中", V4Glyph.Settings, onSettings)
            }
        }
        item("cards") {
            V4StatusCard("我的卡片尚未接入", "扩列卡与关注关系尚未加载；没有虚构的卡片或关注记录。", glyph = V4Glyph.Card)
        }
        if (session is V4SessionUiState.SignedIn && onSignOut != null) {
            item("sign-out") {
                V4Card { V4ActionRow("退出登录", "由应用层清理登录会话", V4Glyph.Shield) { confirmSignOut = true } }
            }
        }
    }
    if (confirmSignOut && session is V4SessionUiState.SignedIn && onSignOut != null) {
        V4SignOutDialog(onDismiss = { confirmSignOut = false }, onConfirm = { confirmSignOut = false; onSignOut() })
    }
}
