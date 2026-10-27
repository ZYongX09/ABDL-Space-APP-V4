package top.abdl_space.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.abdl_space.app.core.data.auth.AuthOutcome
import top.abdl_space.app.core.data.auth.AuthRepository
import top.abdl_space.app.core.data.preferences.AppearancePreferences
import top.abdl_space.app.core.data.preferences.AppearanceRepository
import top.abdl_space.app.core.data.preferences.ThemePreference
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import top.abdl_space.app.core.data.timeline.TimelineKind
import top.abdl_space.app.core.data.session.SessionLease
import top.abdl_space.app.core.network.NetworkResult
import top.abdl_space.app.core.network.NetworkFailureKind
import top.abdl_space.app.ui.V4TimelineKind
import top.abdl_space.app.ui.V4TimelineUiState

data class LoginUiState(
    val visible: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class V4AppViewModel @Inject constructor(
    private val appearances: AppearanceRepository,
    private val auth: AuthRepository,
    private val sessions: SessionRepository,
    private val timelineRepository: top.abdl_space.app.core.data.timeline.TimelineRepository,
    private val nbwRepository: top.abdl_space.app.core.data.nbw.NbwRepository,
) : ViewModel() {
    val appearance = appearances.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppearancePreferences(),
    )
    val session = sessions.state
    private val mutableLogin = MutableStateFlow(LoginUiState())
    val login = mutableLogin.asStateFlow()
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice = mutableNotice.asStateFlow()
    private val mutableTimelines = MutableStateFlow(emptyTimelines())
    val timelines = mutableTimelines.asStateFlow()
    private val timelineJobs = mutableMapOf<V4TimelineKind, Job>()
    private val timelineCursors = mutableMapOf<V4TimelineKind, String?>()
    private val timelineOffsets = mutableMapOf<V4TimelineKind, Int>()
    private var timelineLease: SessionLease? = null

    init {
        viewModelScope.launch { sessions.restore() }
        viewModelScope.launch {
            sessions.state.collectLatest { state ->
                val lease = (state as? SessionState.Ready)?.lease
                if (lease != timelineLease) {
                    timelineJobs.values.forEach(Job::cancel)
                    timelineJobs.clear()
                    timelineCursors.clear()
                    timelineOffsets.clear()
                    timelineLease = lease
                    mutableTimelines.value = emptyTimelines()
                }
            }
        }
    }

    fun selectTimeline(kind: V4TimelineKind) {
        val current = timelines.value[kind] ?: return
        if (!current.hasLoaded && !current.isLoading && current.error == null) loadTimeline(kind, append = false)
    }

    fun refreshTimeline(kind: V4TimelineKind) = loadTimeline(kind, append = false)
    fun loadMoreTimeline(kind: V4TimelineKind) = loadTimeline(kind, append = true)

    private fun loadTimeline(kind: V4TimelineKind, append: Boolean) {
        val lease = (sessions.state.value as? SessionState.Ready)?.lease ?: return
        if (lease.accountId == null || timelineJobs[kind]?.isActive == true) return
        val current = timelines.value[kind] ?: return
        if (append && (!current.hasMore || current.isLoading || current.isLoadingMore)) return
        mutableTimelines.value = timelines.value + (kind to current.copy(
            isLoading = !append,
            isLoadingMore = append,
            error = if (append) current.error else null,
            loadMoreError = null,
        ))
        timelineJobs[kind] = viewModelScope.launch {
            val repositoryKind = when (kind) {
                V4TimelineKind.Recommended -> TimelineKind.RECOMMENDED
                V4TimelineKind.Following -> TimelineKind.FOLLOWING
                V4TimelineKind.Popular -> TimelineKind.POPULAR
            }
            val result = timelineRepository.page(
                kind = repositoryKind,
                lease = lease,
                maxId = if (append) timelineCursors[kind] else null,
                offset = if (append) timelineOffsets[kind] ?: 0 else 0,
            )
            if (!sessions.isCurrent(lease)) return@launch
            val latest = timelines.value.getValue(kind)
            val next = when (result) {
                is NetworkResult.Success -> {
                    val page = result.value
                    timelineCursors[kind] = page.nextCursor
                    timelineOffsets[kind] = page.nextOffset ?: 0
                    val statuses = if (append) (latest.statuses + page.statuses).distinctBy { it.id } else page.statuses
                    latest.copy(statuses = statuses, isLoading = false, isLoadingMore = false,
                        error = null, loadMoreError = null, hasMore = page.hasMore, hasLoaded = true)
                }
                is NetworkResult.Failure -> {
                    val message = when (result.error.kind) {
                        NetworkFailureKind.UNAUTHORIZED -> "登录状态无法验证，请重新登录或稍后重试。"
                        NetworkFailureKind.RATE_LIMITED -> "请求过于频繁，请稍后重试。"
                        NetworkFailureKind.PROTOCOL -> "时间线格式不兼容，未把错误响应当作空列表。"
                        else -> "时间线加载失败，请检查网络后重试。"
                    }
                    latest.copy(isLoading = false, isLoadingMore = false,
                        error = if (append) latest.error else message,
                        loadMoreError = if (append) message else null)
                }
            }
            mutableTimelines.value = timelines.value + (kind to next)
        }
    }

    private fun emptyTimelines() = V4TimelineKind.entries.associateWith { V4TimelineUiState() }

    fun openLogin() {
        mutableLogin.value = LoginUiState(visible = true)
    }

    fun dismissLogin() {
        if (!login.value.busy) mutableLogin.value = LoginUiState()
    }

    fun signIn(login: String, password: String) {
        if (this.login.value.busy) return
        if (login.isBlank() || password.isEmpty()) {
            mutableLogin.value = LoginUiState(visible = true, message = "请填写账号和密码。")
            return
        }
        mutableLogin.value = LoginUiState(visible = true, busy = true)
        viewModelScope.launch {
            when (val result = auth.passwordSignIn(login, password)) {
                AuthOutcome.SignedIn -> mutableLogin.value = LoginUiState()
                is AuthOutcome.Failure -> mutableLogin.value = LoginUiState(visible = true, message = result.message)
                else -> mutableLogin.value = LoginUiState(visible = true, message = "登录未完成。")
            }
        }
    }

    fun signOut() {
        val accountId = (session.value as? SessionState.Ready)?.stored?.activeAccountId ?: return
        viewModelScope.launch {
            if (!sessions.signOut(accountId)) mutableNotice.value = "退出未能完整保存。请修复账号存储；如果无法写入隔离标记，重启仍可能恢复旧账号。"
        }
    }

    fun retrySessionStorage() {
        viewModelScope.launch { sessions.restore() }
    }

    fun resetSessionStorageAfterConfirmation() {
        viewModelScope.launch {
            if (!sessions.resetAfterConfirmation()) mutableNotice.value = "账号存储重置未完成，请重试；没有恢复旧账号。"
        }
    }

    fun setTheme(preference: ThemePreference) = updateAppearance { appearances.setTheme(preference) }
    fun setReduceTransparency(reduce: Boolean) = updateAppearance { appearances.setReduceTransparency(reduce) }
    fun setReduceMotion(reduce: Boolean) = updateAppearance { appearances.setReduceMotion(reduce) }
    fun clearNotice() { mutableNotice.value = null }

    private fun updateAppearance(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: IOException) {
                mutableNotice.value = "外观偏好未能保存，请重试。"
            }
        }
    }
}
