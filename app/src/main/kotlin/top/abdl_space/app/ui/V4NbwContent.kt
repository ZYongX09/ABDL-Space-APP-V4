package top.abdl_space.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.abdl_space.app.V4NbwViewModel
import top.abdl_space.app.core.data.nbw.NbwSnapshot
import top.abdl_space.app.core.data.nbw.NbwSnapshotState
import top.abdl_space.app.core.designsystem.component.V4Card
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text
import top.abdl_space.app.core.model.NbwAuthorizationState
import top.abdl_space.app.core.model.NbwBindingState
import top.abdl_space.app.core.model.NbwRemoteMode

/**
 * Real NBW capability entry. It renders only what the repository verified this session: the
 * three remote modes (full/read_only/local_only), plus the forum catalog (fid/name/required)
 * after a verified full grant. Nothing here posts content or touches fallback consent.
 */
@Composable
internal fun V4NbwEntry(
    session: V4SessionUiState,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    val viewModel: V4NbwViewModel = hiltViewModel()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    // Entering the page re-verifies only after the TTL; the repository owns every other guard.
    LaunchedEffect(viewModel) { viewModel.refreshIfExpired() }
    V4NbwScreen(
        snapshot = snapshot,
        session = session,
        bottomPadding = bottomPadding,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSignIn = onSignIn,
    )
}

@Composable
internal fun V4NbwScreen(
    snapshot: NbwSnapshot,
    session: V4SessionUiState,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag(V4TestTags.Feature).semantics { paneTitle = "NBW" }) {
        V4PageTopBar("NBW", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = V4Dimens.pagePaddingH, end = V4Dimens.pagePaddingH, top = V4Dimens.cardGap, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            when (session) {
                V4SessionUiState.Guest -> V4StatusCard(
                    title = "此功能需要登录",
                    explanation = "NBW 能力只有已登录会话才能验证。未登录时按 local_only 处理：不会发起能力检测，也不读取或发布任何 NBW 内容。",
                    glyph = V4Glyph.Shield,
                    actionLabel = "登录或了解登录进度",
                    onAction = onSignIn,
                )
                V4SessionUiState.Restoring -> V4StatusCard(
                    title = "正在确认登录状态",
                    explanation = "会话由应用层恢复中；确认前不会发起 NBW 能力检测。",
                    glyph = V4Glyph.Shield,
                )
                is V4SessionUiState.SignedIn -> Unit
            }
            val capabilities = snapshot.capabilities
            V4Card(Modifier.fillMaxWidth().testTag(V4TestTags.NbwStatus)) {
                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    V4SectionTitle(statusTitle(snapshot))
                    Text(statusExplanation(snapshot), style = V4Text.body, color = MaterialTheme.colorScheme.onSurface)
                    if (capabilities != null) {
                        Text(
                            "远端声明：${modeLabel(capabilities.remoteMode)} · 绑定 ${bindingLabel(capabilities.binding.state)} · 授权 ${authorizationLabel(capabilities.authorization.state)}",
                            style = V4Text.bodySecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (session is V4SessionUiState.SignedIn) {
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget).testTag(V4TestTags.NbwRefresh),
                        ) { Text("重新检测能力", style = V4Text.bodySecondary) }
                    }
                }
            }
            val options = snapshot.options
            if (snapshot.state == NbwSnapshotState.READY &&
                capabilities?.remoteMode == NbwRemoteMode.FULL &&
                options != null
            ) {
                V4Card(Modifier.fillMaxWidth().testTag(V4TestTags.NbwCatalog)) {
                    Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                        V4SectionTitle("板块目录")
                        Text(
                            "目录修订 ${options.configRevision}；仅展示 fid / 名称 / 是否强制分类，这份快照不是发布权限。",
                            style = V4Text.bodySecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        options.forums.forEach { forum ->
                            Column(Modifier.fillMaxWidth().heightIn(min = V4UiDimens.minTouchTarget)) {
                                Text("${forum.name}（fid ${forum.fid}）", style = V4Text.body, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    if (forum.required) "强制分类" else "不强制分类 · 可仅选板块",
                                    style = V4Text.bodySecondary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "本轮范围：只读取能力与板块目录，不做发帖与跨站动作；回退同意（fallback-consent）未接入界面。",
                style = V4Text.bodySecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusTitle(snapshot: NbwSnapshot): String = when (snapshot.state) {
    NbwSnapshotState.UNAVAILABLE -> "NBW 能力尚未验证"
    NbwSnapshotState.LOADING -> "正在验证 NBW 能力"
    NbwSnapshotState.STALE -> "NBW 目录刷新中"
    NbwSnapshotState.READY -> when (snapshot.capabilities?.remoteMode) {
        NbwRemoteMode.FULL -> "NBW 已连接 · full"
        NbwRemoteMode.READ_ONLY -> "NBW 已连接 · read_only"
        else -> "NBW 本地模式 · local_only"
    }
    NbwSnapshotState.LOCAL_ONLY -> "NBW 本地模式 · local_only"
}

private fun statusExplanation(snapshot: NbwSnapshot): String = when (snapshot.state) {
    NbwSnapshotState.UNAVAILABLE -> "当前没有可验证的已登录会话，还没有发起能力检测；一切按 local_only 处理。"
    NbwSnapshotState.LOADING -> "已按当前会话请求 capabilities；确认之前不展示板块目录，也不发送任何内容。"
    NbwSnapshotState.STALE -> "重新验证仍在进行；如展示目录，仅为该账号上次可信快照，失败即整体失效。"
    NbwSnapshotState.READY -> when (snapshot.capabilities?.remoteMode) {
        NbwRemoteMode.FULL -> "远端声明可读可写。本轮仅读取目录，不发布内容。"
        NbwRemoteMode.READ_ONLY -> "远端声明只读；写入能力未获声明，本轮也不展示跨站内容。"
        else -> "远端声明 local_only，已按 fail-closed 处理。"
    }
    NbwSnapshotState.LOCAL_ONLY -> "能力不可验证或远端声明 local_only，已按 fail-closed 处理：不请求目录、不发起跨站动作。"
}

private fun modeLabel(mode: NbwRemoteMode): String = when (mode) {
    NbwRemoteMode.FULL -> "可读可写（full）"
    NbwRemoteMode.READ_ONLY -> "只读（read_only）"
    NbwRemoteMode.LOCAL_ONLY -> "本地（local_only）"
}

private fun bindingLabel(state: NbwBindingState): String = when (state) {
    NbwBindingState.BOUND -> "已绑定"
    NbwBindingState.UNBOUND -> "未绑定"
    NbwBindingState.UNKNOWN -> "未知（fail-closed）"
}

private fun authorizationLabel(state: NbwAuthorizationState): String = when (state) {
    NbwAuthorizationState.READY -> "就绪"
    NbwAuthorizationState.MISSING -> "缺失"
    NbwAuthorizationState.REVOKED -> "已撤销"
    NbwAuthorizationState.UNAVAILABLE -> "不可用"
}
