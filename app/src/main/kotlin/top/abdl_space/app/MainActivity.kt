package top.abdl_space.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import top.abdl_space.app.core.data.preferences.ThemePreference
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.designsystem.theme.V4Theme
import top.abdl_space.app.ui.V4App
import top.abdl_space.app.ui.V4SessionUiState
import top.abdl_space.app.ui.V4ThemePreference

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: V4AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { V4Root(viewModel) }
    }
}

@Composable
private fun V4Root(viewModel: V4AppViewModel) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val timelines by viewModel.timelines.collectAsStateWithLifecycle()
    val theme = when (appearance.theme) {
        ThemePreference.SYSTEM -> V4ThemePreference.System
        ThemePreference.LIGHT -> V4ThemePreference.Light
        ThemePreference.DARK -> V4ThemePreference.Dark
    }
    val uiSession = when (val current = session) {
        SessionState.Loading -> V4SessionUiState.Restoring
        is SessionState.StorageUnavailable -> V4SessionUiState.Guest
        is SessionState.Ready -> current.stored.activeCredential()?.let {
            V4SessionUiState.SignedIn(it.displayName.ifBlank { it.username })
        } ?: V4SessionUiState.Guest
    }
    val activeLease = (session as? SessionState.Ready)?.lease
    androidx.compose.runtime.key(activeLease?.accountId, activeLease?.generation) {
        V4App(
        themePreference = theme,
        onThemePreferenceChange = {
            viewModel.setTheme(
                when (it) {
                    V4ThemePreference.System -> ThemePreference.SYSTEM
                    V4ThemePreference.Light -> ThemePreference.LIGHT
                    V4ThemePreference.Dark -> ThemePreference.DARK
                }
            )
        },
        session = uiSession,
        onSignInRequested = viewModel::openLogin,
        onSignOutRequested = viewModel::signOut,
        reduceTransparency = appearance.reduceTransparency,
        onReduceTransparencyChange = viewModel::setReduceTransparency,
        reduceMotion = appearance.reduceMotion,
        onReduceMotionChange = viewModel::setReduceMotion,
        timelineState = timelines,
        onTimelineSelected = viewModel::selectTimeline,
        onTimelineRefresh = viewModel::refreshTimeline,
        onLoadMore = viewModel::loadMoreTimeline,
    )
    }
    val darkTheme = when (appearance.theme) {
        ThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    V4Theme(darkTheme = darkTheme) {
        if (login.visible) {
            LoginDialog(
                state = if (session is SessionState.StorageUnavailable) {
                    login.copy(message = "账号加密存储不可用。为保护凭据，本轮不会保存登录信息。")
                } else login,
                onDismiss = viewModel::dismissLogin,
                onSignIn = viewModel::signIn,
            )
        }
        if (session is SessionState.StorageUnavailable && notice == null) {
            SessionStorageRecovery(
                onRetry = viewModel::retrySessionStorage,
                onReset = viewModel::resetSessionStorageAfterConfirmation,
            )
        }
        if (notice != null) {
            AlertDialog(
                onDismissRequest = viewModel::clearNotice,
                title = { Text("操作未完成") },
                text = { Text(notice.orEmpty()) },
                confirmButton = { TextButton(onClick = viewModel::clearNotice) { Text("知道了") } },
            )
        }
    }
}

@Composable
private fun SessionStorageRecovery(onRetry: () -> Unit, onReset: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (confirmReset) "清除 V4 本地登录记录？" else "账号存储不可用") },
        text = {
            Text(
                if (confirmReset) {
                    "此操作只清除 V4 的本地账号凭据和加密密钥，不删除线上账号或内容。完成后需要重新登录。"
                } else {
                    "暂时无法安全读取或保存账号，当前会话已停止。可先重试；若密钥或记录损坏，再确认清除 V4 本地登录记录。退出保存失败时，重试不会自动重新启用旧凭据。"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (confirmReset) onReset() else onRetry() }) {
                Text(if (confirmReset) "确认清除并重新登录" else "重试读取")
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = !confirmReset }) {
                Text(if (confirmReset) "返回" else "重置本地登录记录")
            }
        },
    )
}

@Composable
private fun LoginDialog(
    state: LoginUiState,
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
) {
    var account by remember { mutableStateOf("") }
    // Passwords intentionally do not participate in saved instance state.
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text("登录 ABDL Space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("使用现有社区账号登录。不会迁移或读取旧版 App 的本地数据。")
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("邮箱或用户名") },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.busy) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                Text("验证码登录、注册与 QQ 登录界面仍在迁移，本轮仅接通密码登录。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.busy && account.isNotBlank() && password.isNotEmpty(),
                onClick = { onSignIn(account, password) },
            ) { Text("登录") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("取消") } },
    )
}
