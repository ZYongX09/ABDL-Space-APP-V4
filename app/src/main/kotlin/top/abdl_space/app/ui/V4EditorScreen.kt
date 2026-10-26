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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import top.abdl_space.app.core.designsystem.component.V4Card
import top.abdl_space.app.core.designsystem.theme.V4Dimens
import top.abdl_space.app.core.designsystem.theme.V4Text

@Composable
internal fun V4EditorScreen(savedDraft: String, onSaveDraft: (String) -> Unit, onExit: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(savedDraft) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var showPublishBoundary by rememberSaveable { mutableStateOf(false) }

    fun validate(): Boolean {
        error = when (V4DraftRules.validate(text)) {
            V4DraftValidation.Empty -> "请先输入非空白的正文。"
            V4DraftValidation.TooLong -> "正文超出本地草稿长度上限。"
            V4DraftValidation.Valid -> null
        }
        return error == null
    }
    fun requestExit() {
        if (V4DraftRules.needsExitConfirmation(text, savedDraft)) confirmExit = true else onExit()
    }
    fun save(): Boolean {
        if (!validate()) return false
        onSaveDraft(text)
        feedback = "草稿已保存在本轮本地状态，未发送到服务器。"
        return true
    }

    // Always intercept the editor's system/predictive Back before Navigation pops its destination.
    BackHandler { requestExit() }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().imePadding().testTag(V4TestTags.Editor)
            .semantics { paneTitle = "本地文字编辑器" },
    ) {
        V4PageTopBar("本地文字编辑器", onBack = ::requestExit, action = {
            TextButton(
                onClick = { if (validate()) showPublishBoundary = true },
                modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget),
            ) { Text("发布", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(V4Dimens.pagePaddingH),
            verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap),
        ) {
            V4Card {
                Column(Modifier.padding(V4Dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(V4Dimens.cardGap)) {
                    V4SectionTitle("开发中 · 仅本地文字草稿")
                    Text("发布、媒体上传、可见范围与 NBW 均尚未接入。这里可以编辑文字并保存本轮草稿，不会发布到生产环境。", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().heightIn(min = V4UiDimens.minTouchTarget)) {
                Text("保存本地草稿", style = V4Text.bodySecondary)
            }
            if (feedback != null) {
                Text(feedback.orEmpty(), style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(V4TestTags.DraftFeedback).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Text("草稿使用 rememberSaveable 保存，不是文件或永久草稿箱。系统可能恢复本轮界面状态，但清除应用数据、强制结束任务或卸载后不保证保留。不会读取旧版 App 数据。", style = V4Text.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (showPublishBoundary) {
        AlertDialog(
            onDismissRequest = { showPublishBoundary = false },
            title = { Text("发布服务尚未接入", style = V4Text.title) },
            text = { Text("内容仍在本地编辑器中，没有发送到服务器。你可以保存本地草稿后继续编辑；不会显示发布成功或创建线上帖子。", style = V4Text.body) },
            confirmButton = {
                TextButton(onClick = { if (save()) showPublishBoundary = false }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                    Text("保存为本地草稿", style = V4Text.bodySecondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublishBoundary = false }, modifier = Modifier.heightIn(min = V4UiDimens.minTouchTarget)) {
                    Text("继续编辑", style = V4Text.bodySecondary)
                }
            },
        )
    }
}
