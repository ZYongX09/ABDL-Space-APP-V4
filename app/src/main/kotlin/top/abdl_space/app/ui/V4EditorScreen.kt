package top.abdl_space.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import top.abdl_space.app.V4ComposeUiState
import top.abdl_space.app.core.designsystem.component.V4Card
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text

/**
 * Real text posting. The draft text stays in rememberSaveable; send progress, classified
 * errors and the success outcome arrive through [compose]. On failure the draft is kept and
 * the same text can be retried; the shell clears it only after a confirmed publish.
 */
@Composable
internal fun V4EditorScreen(
    savedDraft: String,
    onSaveDraft: (String) -> Unit,
    onExit: () -> Unit,
    session: V4SessionUiState,
    onSignIn: () -> Unit,
    compose: V4ComposeUiState,
    onPublish: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(savedDraft) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var showLoginGate by rememberSaveable { mutableStateOf(false) }
    val sending = compose.sending

    fun validate(): Boolean {
        error = when (V4DraftRules.validate(text)) {
            V4DraftValidation.Empty -> "请先输入非空白的正文。"
            V4DraftValidation.TooLong -> "正文超出本地草稿长度上限。"
            V4DraftValidation.Valid -> null
        }
        return error == null
    }
    fun requestExit() {
        if (sending) return
        if (V4DraftRules.needsExitConfirmation(text, savedDraft)) confirmExit = true else onExit()
    }
    fun save(): Boolean {
        if (!validate()) return false
        onSaveDraft(text)
        feedback = "草稿已保存在本轮本地状态；发布需要点击右上角“发布”。"
        return true
    }
    fun requestPublish() {
        when (session) {
            V4SessionUiState.Guest, V4SessionUiState.Restoring -> showLoginGate = true
            is V4SessionUiState.SignedIn -> if (validate()) onPublish(text)
        }
    }

    // Always intercept the editor's system/predictive Back before Navigation pops its destination.
    BackHandler { requestExit() }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().imePadding().testTag(V4TestTags.Editor)
            .semantics { paneTitle = "发帖编辑器" },
    ) {
        V4PageTopBar("发帖", onBack = ::requestExit, action = {
            TextButton(
                onClick = ::requestPublish,
                enabled = !sending,
                modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget).testTag(V4TestTags.EditorPublish),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(V4UiDimens.icon))
                    Text("发布中", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text("发布", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(V4Dimens.pagePaddingH),
            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            V4Card {
                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    V4SectionTitle("发布范围：仅文字")
                    Text(
                        "登录后会公开发布文字；是否自动同步到 NBW 以当前服务端合作策略为准。媒体、投票、定时、板块分类及可见范围选择仍在迁移中。仅在服务端确认后显示成功，结果不明时保留正文供核实。",
                        style = V4Text.bodySecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    if (value.length <= V4DraftRules.MaxCharacters) {
                        text = value
                        error = null
                        feedback = null
                    } else {
                        error = "本地正文最多 ${V4DraftRules.MaxCharacters} 个字符，已保留原文。"
                    }
                },
                enabled = !sending,
                modifier = Modifier.fillMaxWidth().testTag(V4TestTags.EditorInput),
                label = { Text("正文", style = V4Text.bodySecondary) },
                placeholder = { Text("写下想分享的内容……", style = V4Text.body) },
                textStyle = V4Text.body,
                minLines = 6,
                isError = error != null,
                supportingText = { Text("${text.length} / ${V4DraftRules.MaxCharacters} · 本地编辑限制", style = V4Text.caption) },
            )
            if (error != null) {
                Text(error.orEmpty(), style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (sending) {
                Text("正在把这条内容提交到服务器；完成前不能编辑或再次发布。",
                    style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(V4TestTags.EditorSending).semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (compose.error != null) {
                // The draft text above is untouched; retrying resends the same content with the same key.
                Text(compose.error.orEmpty(), style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(V4TestTags.EditorError).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(
                onClick = { save() },
                enabled = !sending,
                modifier = Modifier.fillMaxWidth().heightIn(min = V4UiDimens.minTouchTarget),
            ) {
                Text("保存本地草稿", style = V4Text.bodySecondary)
            }
            if (feedback != null) {
                Text(feedback.orEmpty(), style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(V4TestTags.DraftFeedback).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Text("草稿使用 rememberSaveable 保存在本轮界面状态，不是文件或永久草稿箱。发布失败不会清除草稿；发布成功后由主壳清空草稿并关闭编辑器。系统可能恢复本轮界面状态，但清除应用数据、强制结束任务或卸载后不保证保留。不会读取旧版 App 数据。", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("保存修改后再退出？", style = V4Text.title) },
            text = { Text("还有未保存的本地修改。不保存退出会放弃本次修改，之前保存的草稿不变；任何选项都不会向服务器发送内容。", style = V4Text.body) },
            confirmButton = {
                if (V4DraftRules.validate(text) == V4DraftValidation.Valid) {
                    TextButton(onClick = { if (save()) { confirmExit = false; onExit() } }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                        Text("保存草稿并退出", style = V4Text.bodySecondary)
                    }
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { confirmExit = false; onExit() }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                        Text("不保存并退出", style = V4Text.bodySecondary)
                    }
                    TextButton(onClick = { confirmExit = false }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                        Text("继续编辑", style = V4Text.bodySecondary)
                    }
                }
            },
        )
    }
    if (showLoginGate) {
        AlertDialog(
            onDismissRequest = { showLoginGate = false },
            title = { Text("发布需要登录", style = V4Text.title) },
            text = { Text("访客不能发布内容。登录由主壳的登录界面完成，此时不会发送或上传你正在编辑的文字。登录成功会使本轮界面（包括编辑器草稿）重新开始，需要保留的文字请先自行备份。", style = V4Text.body) },
            confirmButton = {
                TextButton(
                    onClick = { showLoginGate = false; onSignIn() },
                    modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget).testTag(V4TestTags.EditorGateSignIn),
                ) { Text("登录", style = V4Text.bodySecondary) }
            },
            dismissButton = {
                TextButton(onClick = { showLoginGate = false }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                    Text("继续编辑", style = V4Text.bodySecondary)
                }
            },
        )
    }
}
