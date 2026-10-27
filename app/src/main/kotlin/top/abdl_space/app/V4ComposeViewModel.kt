package top.abdl_space.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.abdl_space.app.core.data.compose.ComposePublishResult
import top.abdl_space.app.core.data.compose.ComposeRepository
import top.abdl_space.app.core.data.session.SessionLease
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.network.NetworkFailureKind
import top.abdl_space.app.ui.V4DraftRules
import top.abdl_space.app.ui.V4DraftValidation
import javax.inject.Inject

/**
 * [publishedId] is a separately acknowledged, one-shot editor completion event; only a confirmed
 * Status can produce it. [feedback] is the home-screen notice and may outlive that event without
 * clearing a later draft on recreation. The editor owns the draft text, not this state machine.
 */
data class V4ComposeUiState(
    val sending: Boolean = false,
    val error: String? = null,
    val feedback: String? = null,
    val publishedId: String? = null,
    val lease: SessionLease? = null,
)

/** Classified copy only: neither an unconfirmed write nor raw server text is called a success. */
internal fun composeFailureMessage(kind: NetworkFailureKind): String = when (kind) {
    NetworkFailureKind.UNAUTHORIZED -> "登录状态无法验证，帖子未发出。请重新登录后重试；草稿已保留。"
    NetworkFailureKind.FORBIDDEN -> "当前账号没有发布权限，帖子未发出；草稿已保留。"
    NetworkFailureKind.VALIDATION -> "服务器没有接受这条内容（校验未通过）。请调整内容后重试；草稿已保留。"
    NetworkFailureKind.RATE_LIMITED -> "发布过于频繁，请稍等片刻再重试；草稿已保留。"
    NetworkFailureKind.SERVER -> "服务器返回错误，发布结果未知。请稍后在首页时间线核实，避免重复发布；草稿已保留。"
    NetworkFailureKind.PROTOCOL -> "服务器返回了无法确认的响应，发布结果未知。请稍后在首页时间线核实，避免重复发布；草稿已保留。"
    NetworkFailureKind.TIMEOUT -> "连接超时，发布结果未知。请稍后在首页时间线核实，避免重复发布；草稿已保留。"
    NetworkFailureKind.NETWORK -> "网络连接中断，发布结果未知。请稍后在首页时间线核实，避免重复发布；草稿已保留。"
    else -> "发布未能确认；草稿已保留，请检查后重试。"
}

@HiltViewModel
class V4ComposeViewModel @Inject constructor(
    private val composeRepository: ComposeRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    private var lease: SessionLease? = (sessions.state.value as? SessionState.Ready)?.lease
    private val mutableState = MutableStateFlow(V4ComposeUiState(lease = lease))
    val compose = mutableState.asStateFlow()
    private var publishJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.state.collect { synchronizeSession() }
        }
    }

    /**
     * Both the collector and UI entry points reconcile the current snapshot, so whichever actor
     * runs first owns the same lease. An old completion/cancellation cannot reset a newer attempt.
     */
    private fun synchronizeSession(): SessionLease? {
        val active = (sessions.state.value as? SessionState.Ready)?.lease
        if (active != lease) {
            val previous = publishJob
            publishJob = null
            lease = active
            mutableState.value = V4ComposeUiState(lease = active)
            previous?.cancel()
        }
        return active
    }

    /** Re-entry is ignored; only a confirmed response produces the editor completion event. */
    fun publishPost(text: String) {
        val active = synchronizeSession() ?: return
        if (active.accountId == null) return
        val current = mutableState.value
        if (current.sending || current.publishedId != null) return
        if (V4DraftRules.validate(text) != V4DraftValidation.Valid) return
        // A previous success banner must never leak into a failed or cancelled new attempt.
        mutableState.value = V4ComposeUiState(sending = true, lease = active)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val result = composeRepository.publish(active, text)
            currentCoroutineContext().ensureActive()
            if (!sessions.isCurrent(active) || lease != active) return@launch
            when (result) {
                is ComposePublishResult.Published -> mutableState.value = V4ComposeUiState(
                    feedback = "发帖成功。首页时间线刷新后可以看到这条帖子。",
                    publishedId = result.status.id,
                    lease = active,
                )
                is ComposePublishResult.Failed -> mutableState.value = V4ComposeUiState(
                    error = composeFailureMessage(result.error.kind),
                    lease = active,
                )
                ComposePublishResult.SessionChanged -> mutableState.value = V4ComposeUiState(lease = active)
            }
        }
        publishJob = job
        // Also runs if the queued coroutine is cancelled before its body starts. Cancellation
        // propagates normally; it never becomes a success and leaves the retry key in the repo.
        job.invokeOnCompletion {
            if (publishJob === job) {
                publishJob = null
                if (lease == active && sessions.isCurrent(active) && mutableState.value.sending) {
                    mutableState.value = V4ComposeUiState(
                        error = "发布已中断，结果未知。请稍后在首页时间线核实，避免重复发布；草稿已保留。",
                        lease = active,
                    )
                }
            }
        }
        job.start()
    }

    /** Acknowledge exactly the event handled by the shell, without dismissing its home notice. */
    fun consumePublishedPost(publishedId: String) {
        synchronizeSession()
        if (mutableState.value.publishedId == publishedId) {
            mutableState.value = mutableState.value.copy(publishedId = null)
        }
    }

    fun consumeComposeFeedback() {
        synchronizeSession()
        if (!mutableState.value.sending) mutableState.value = mutableState.value.copy(feedback = null)
    }

    /** Reopening must not replay an unconsumed completion against a new editor's draft. */
    fun onComposeEditorOpened() {
        synchronizeSession()
        if (!mutableState.value.sending) {
            mutableState.value = mutableState.value.copy(error = null, publishedId = null)
        }
    }
}
