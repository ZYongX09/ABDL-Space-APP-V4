package top.abdl_space.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import top.abdl_space.app.core.designsystem.component.V4Card
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text

@Composable
internal fun V4SearchScreen(bottomPadding: Dp, onBack: () -> Unit, onFeature: (V4Feature) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = V4FeatureCatalog.search(query)
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().testTag(V4TestTags.Search).semantics { paneTitle = "搜索" }) {
        V4PageTopBar("搜索", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(V4DraftRules.MaxCharacters) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = V4Dimens.pagePaddingH).testTag(V4TestTags.SearchInput),
            label = { Text("搜索功能", style = V4Text.bodySecondary) },
            placeholder = { Text("例如：地图、相册、NBW", style = V4Text.bodySecondary) },
            textStyle = V4Text.body,
            leadingIcon = { V4Symbol(V4Glyph.Search) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { V4Symbol(V4Glyph.Close, description = "清空搜索") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            singleLine = true,
        )
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.weight(1f).testTag(V4TestTags.SearchResults),
            contentPadding = PaddingValues(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            item("scope") {
                Text("仅搜索本地功能入口。帖子、用户与话题的线上搜索尚未接入，不会向服务器发送关键词。", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                query.isBlank() -> {
                    item("empty") { V4StatusCard("输入关键词，查找功能入口", "本地搜索可直接打开功能说明。没有推荐账号、热搜榜或搜索历史上传。", glyph = V4Glyph.Search) }
                    item("quick-links") {
                        V4Card {
                            V4ActionRow("交友地图", "开发中 · 查看定位与地图接入范围", V4Glyph.Map) { onFeature(V4Feature.Map) }
                            V4ActionRow("话题", "开发中 · 查看话题接入范围", V4Glyph.Topic) { onFeature(V4Feature.Topics) }
                        }
                    }
                }
                results.isEmpty() -> {
                    item("no-results") { V4StatusCard("没有匹配的功能入口", "试试“地图”“相册”或“NBW”，也可以清空关键词重新查找。此结果不代表线上没有相关内容。", glyph = V4Glyph.Search) }
                }
                else -> {
                    item("results-title") { V4SectionTitle("本地功能入口") }
                    items(results, key = { it.id }) { feature ->
                        V4Card { V4ActionRow(feature.title, "开发中 · ${feature.description}", feature.glyph()) { keyboard?.hide(); onFeature(feature) } }
                    }
                }
            }
        }
    }
}

@Composable
internal fun V4FeatureScreen(
    feature: V4Feature?,
    session: V4SessionUiState,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    if (feature == V4Feature.Nbw) {
        // The NBW entry reads the real capability snapshot instead of the static explanation shell.
        V4NbwEntry(session = session, bottomPadding = bottomPadding, onBack = onBack, onSignIn = onSignIn)
        return
    }
    val title = feature?.title ?: "未找到入口"
    Column(Modifier.fillMaxSize().testTag(V4TestTags.Feature).semantics { paneTitle = title }) {
        V4PageTopBar(title, onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            if (feature == null) {
                V4StatusCard("这个入口暂不可用", "未识别的功能路由，没有执行任何操作。请返回选择已有入口。")
            } else {
                V4StatusCard(if (feature == V4Feature.About) "V4 正在重构" else "开发中 · 尚未接入", feature.description, glyph = feature.glyph())
                V4Card {
                    Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                        V4SectionTitle("当前范围")
                        Text(feature.boundary, style = V4Text.body, color = MaterialTheme.colorScheme.onSurface)
                        Text("这是功能说明页，不是已上线业务。不会生成示例用户、帖子、评分或成功结果。", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (feature.requiresAccount && session == V4SessionUiState.Guest) {
                    V4StatusCard("此功能需要登录", "登录服务由应用层接入。登录后仍须等待本功能的业务服务完成，不会用本地状态冒充线上权限。", glyph = V4Glyph.Shield, actionLabel = "登录或了解登录进度", onAction = onSignIn)
                }
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                    Text("返回上一页", style = V4Text.body, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
internal fun V4AccountScreen(
    session: V4SessionUiState,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSignIn: (() -> Unit)?,
    onSignOut: (() -> Unit)?,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().semantics { paneTitle = "账号" }) {
        V4PageTopBar("账号", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            when (session) {
                V4SessionUiState.Guest -> V4StatusCard(
                    title = if (onSignIn == null) "登录服务尚未接入" else "当前尚未登录",
                    explanation = if (onSignIn == null) "当前主壳没有接入认证流程，不能创建或验证账号。你仍可浏览功能说明、修改外观与编辑本地文字草稿。" else "登录由应用层提供。确认登录后，主壳才会接收真实会话状态；本地草稿不会随登录自动发布。",
                    glyph = V4Glyph.Shield, actionLabel = if (onSignIn == null) null else "前往登录", onAction = onSignIn,
                )
                V4SessionUiState.Restoring -> V4StatusCard("正在恢复会话", "请等待应用层完成会话校验，暂不提供登录或退出操作。", glyph = V4Glyph.Shield)
                is V4SessionUiState.SignedIn -> {
                    V4StatusCard("已登录", "显示名称：${session.displayName.ifBlank { "未提供" }}。此状态由应用层提供。资料编辑与社区统计尚未接入。", glyph = V4Glyph.Person)
                    if (onSignOut != null) {
                        Button(onClick = { confirmSignOut = true }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) { Text("退出登录", style = V4Text.bodySecondary) }
                    }
                }
            }
        }
    }
    if (confirmSignOut && session is V4SessionUiState.SignedIn && onSignOut != null) {
        V4SignOutDialog(onDismiss = { confirmSignOut = false }, onConfirm = { confirmSignOut = false; onSignOut() })
    }
}

@Composable
internal fun V4SignOutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出当前账号？", style = V4Text.title) },
        text = { Text("将请求应用层清理登录会话。本地文字草稿不会自动发送。", style = V4Text.body) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) { Text("确认退出", style = V4Text.bodySecondary) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) { Text("取消", style = V4Text.bodySecondary) } },
    )
}

@Composable
internal fun V4SettingsScreen(
    theme: V4ThemePreference,
    onTheme: (V4ThemePreference) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparency: (Boolean) -> Unit,
    hostOwnsAppearance: Boolean,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onFeature: (V4Feature) -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag(V4TestTags.Settings).semantics { paneTitle = "设置" }) {
        V4PageTopBar("设置", onBack)
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            item("appearance-title") { V4SectionTitle("外观") }
            item("theme") {
                V4Card {
                    Column(Modifier.fillMaxWidth().selectableGroup()) {
                        V4ThemePreference.entries.forEach { preference ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = V4UiDimens.minTouchTarget)
                                    .selectable(selected = theme == preference, role = Role.RadioButton, onClick = { onTheme(preference) })
                                    .padding(V4Dimens.cardPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
                            ) {
                                V4Symbol(when (preference) { V4ThemePreference.System -> V4Glyph.System; V4ThemePreference.Light -> V4Glyph.Sun; V4ThemePreference.Dark -> V4Glyph.Moon })
                                Text(preference.label, style = V4Text.body, modifier = Modifier.weight(1f))
                                RadioButton(selected = theme == preference, onClick = null)
                            }
                        }
                    }
                }
            }
            item("accessibility") {
                V4Card {
                    V4PreferenceSwitch("减弱动画", "关闭主壳页面淡入与点击页签时的滚动动画", reduceMotion, onReduceMotion)
                    V4PreferenceSwitch("减少透明效果", "使用不透明底栏；关闭后仅支持的设备可显示玻璃效果", reduceTransparency, onReduceTransparency)
                }
            }
            item("scope") {
                Text(
                    if (hostOwnsAppearance) "外观偏好由应用层管理；持久化状态以应用层实现为准。" else "外观偏好仅使用 rememberSaveable 保存在本轮界面状态；尚未写入永久设置，清除应用状态后不会保留。",
                    style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item("account-title") { V4SectionTitle("账户与社区") }
            item("account") {
                V4Card {
                    V4ActionRow("账号", "查看会话状态与登录接入范围", V4Glyph.Person, onAccount)
                    V4ActionRow("隐私与安全", "尚未接入线上设置", V4Glyph.Shield) { onFeature(V4Feature.Privacy) }
                    V4ActionRow("通知设置", "推送与通知偏好尚未接入", V4Glyph.Bell) { onFeature(V4Feature.NotificationSettings) }
                    V4ActionRow("关于 V4", "当前可用范围与开发状态", V4Glyph.Empty) { onFeature(V4Feature.About) }
                }
            }
        }
    }
}

@Composable
private fun V4PreferenceSwitch(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = V4UiDimens.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(V4Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap / 2)) {
            Text(title, style = V4Text.body)
            Text(summary, style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
