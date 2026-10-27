package top.abdl_space.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import top.abdl_space.app.core.data.session.SessionLease
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.data.session.SessionState
import top.abdl_space.app.core.model.ProtocolViolation
import top.abdl_space.app.core.network.AuthorizationHeader
import top.abdl_space.app.core.network.LinkPagination
import top.abdl_space.app.core.network.NetworkCall
import top.abdl_space.app.core.network.NetworkFailureKind
import top.abdl_space.app.core.network.NetworkResult
import top.abdl_space.app.core.network.api.MessagesApi
import top.abdl_space.app.core.network.api.NotificationApi
import top.abdl_space.app.ui.V4MessageTab
import top.abdl_space.app.ui.V4MessagesUiState
import top.abdl_space.app.ui.V4NotificationsUiState

/**
 * Read-only, lease-isolated messages. Refresh/append failures retain loaded rows and the last
 * successfully parsed cursor. Neither polling nor a send path exists this round.
 */
@HiltViewModel
class V4MessagesViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val notifications: NotificationApi,
    private val conversations: MessagesApi,
    private val json: Json,
) : ViewModel() {
    private var lease: SessionLease? = (sessions.state.value as? SessionState.Ready)?.lease
    private val mutableState = MutableStateFlow(V4MessagesUiState(lease = lease))
    val state: StateFlow<V4MessagesUiState> = mutableState.asStateFlow()
    private var notificationsJob: Job? = null
    private var conversationsJob: Job? = null
    private var notificationsCursor: String? = null

    init {
        viewModelScope.launch {
            sessions.state.collect { synchronizeSession() }
        }
    }

    /**
     * UI calls can precede the collector, including first injection into an already Ready session.
     * Reconcile the latest snapshot in both actors, never reset from a delayed collected value.
     */
    private fun synchronizeSession(): SessionLease? {
        val active = (sessions.state.value as? SessionState.Ready)?.lease
        if (active != lease) {
            val oldNotifications = notificationsJob
            val oldConversations = conversationsJob
            notificationsJob = null
            conversationsJob = null
            notificationsCursor = null
            lease = active
            mutableState.value = V4MessagesUiState(lease = active)
            // Detach before cancelling: old completion handlers must not clear a newer job/state.
            oldNotifications?.cancel()
            oldConversations?.cancel()
        }
        return active
    }

    private fun isCurrent(active: SessionLease): Boolean = lease == active && sessions.isCurrent(active)

    /** Initial load when a tab becomes visible; a failed tab waits for the explicit retry. */
    fun tabSelected(tab: V4MessageTab) {
        synchronizeSession()
        when (tab) {
            V4MessageTab.Direct -> {
                val current = mutableState.value.conversations
                if (!current.hasLoaded && !current.isLoading && current.error == null) loadConversations()
            }
            V4MessageTab.Notifications -> {
                val current = mutableState.value.notifications
                if (!current.hasLoaded && !current.isLoading && current.error == null) loadNotifications(append = false)
            }
        }
    }

    fun refresh(tab: V4MessageTab) {
        when (tab) {
            V4MessageTab.Direct -> loadConversations()
            V4MessageTab.Notifications -> loadNotifications(append = false)
        }
    }

    fun loadMoreNotifications() {
        synchronizeSession()
        val current = mutableState.value.notifications
        if (!current.hasMore || current.isLoading || current.isLoadingMore) return
        loadNotifications(append = true)
    }

    private fun loadNotifications(append: Boolean) {
        val active = synchronizeSession() ?: return
        if (active.accountId == null) return
        val credential = sessions.activeCredential(active) ?: return
        if (notificationsJob?.isActive == true) return
        val current = mutableState.value.notifications
        if (append && (!current.hasMore || current.isLoading || current.isLoadingMore)) return
        mutableState.value = mutableState.value.copy(
            notifications = current.copy(
                isLoading = !append,
                isLoadingMore = append,
                error = if (append) current.error else null,
                loadMoreError = null,
            ),
        )
        val cursor = notificationsCursor
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val result = NetworkCall.response(json) {
                notifications.notifications(
                    AuthorizationHeader.bearer(credential.accessToken),
                    maxId = if (append) cursor else null,
                    limit = NOTIFICATIONS_PAGE_SIZE,
                )
            }
            currentCoroutineContext().ensureActive()
            if (!isCurrent(active)) return@launch
            val next = when (result) {
                is NetworkResult.Success -> try {
                    val response = result.value
                    val nextCursor = LinkPagination.from(response).nextMaxId
                    val rows = checkNotNull(response.body())
                        .mapNotNull { it.toModel() }
                        .distinctBy { it.id }
                    val latest = latestNotifications()
                    val page = latest.copy(
                        notifications = if (append) (latest.notifications + rows).distinctBy { it.id } else rows,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        loadMoreError = null,
                        hasMore = nextCursor != null,
                        hasLoaded = true,
                    )
                    // Commit only after headers AND rows have been validated. HTTP 200 alone
                    // does not make a page successful, and parse failure must retain the cursor.
                    if (!isCurrent(active)) return@launch
                    notificationsCursor = nextCursor
                    page
                } catch (_: ProtocolViolation) {
                    failureNotifications(NetworkFailureKind.PROTOCOL, append)
                }
                is NetworkResult.Failure -> failureNotifications(result.error.kind, append)
            }
            if (!isCurrent(active)) return@launch
            mutableState.value = mutableState.value.copy(notifications = next)
        }
        notificationsJob = job
        job.invokeOnCompletion {
            if (notificationsJob === job) {
                notificationsJob = null
                if (isCurrent(active)) {
                    val latest = latestNotifications()
                    if (latest.isLoading || latest.isLoadingMore) {
                        mutableState.value = mutableState.value.copy(
                            notifications = latest.copy(isLoading = false, isLoadingMore = false),
                        )
                    }
                }
            }
        }
        job.start()
    }

    private fun failureNotifications(kind: NetworkFailureKind, append: Boolean): V4NotificationsUiState {
        val latest = latestNotifications()
        val message = when (kind) {
            NetworkFailureKind.UNAUTHORIZED -> "登录状态无法验证，请重新登录或稍后重试。"
            NetworkFailureKind.RATE_LIMITED -> "请求过于频繁，请稍后重试。"
            NetworkFailureKind.PROTOCOL -> "通知格式不兼容，未把错误响应当作空列表。"
            else -> "通知加载失败，请检查网络后重试。"
        }
        return latest.copy(
            isLoading = false,
            isLoadingMore = false,
            error = if (append) latest.error else message,
            loadMoreError = if (append) message else null,
        )
    }

    private fun loadConversations() {
        val active = synchronizeSession() ?: return
        if (active.accountId == null) return
        val credential = sessions.activeCredential(active) ?: return
        if (conversationsJob?.isActive == true) return
        mutableState.value = mutableState.value.copy(
            conversations = latestConversations().copy(isLoading = true, error = null),
        )
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val result = NetworkCall.body(json) {
                conversations.conversations(AuthorizationHeader.bearer(credential.accessToken))
            }
            currentCoroutineContext().ensureActive()
            if (!isCurrent(active)) return@launch
            val latest = latestConversations()
            val next = when (result) {
                is NetworkResult.Success -> latest.copy(
                    conversations = result.value.conversations.distinctBy { it.userId },
                    isLoading = false,
                    error = null,
                    hasLoaded = true,
                )
                is NetworkResult.Failure -> latest.copy(
                    isLoading = false,
                    error = when (result.error.kind) {
                        NetworkFailureKind.UNAUTHORIZED -> "登录状态无法验证，请重新登录或稍后重试。"
                        NetworkFailureKind.RATE_LIMITED -> "请求过于频繁，请稍后重试。"
                        NetworkFailureKind.PROTOCOL -> "会话格式不兼容，未把错误响应当作空列表。"
                        else -> "会话列表加载失败，请检查网络后重试。"
                    },
                )
            }
            if (!isCurrent(active)) return@launch
            mutableState.value = mutableState.value.copy(conversations = next)
        }
        conversationsJob = job
        job.invokeOnCompletion {
            if (conversationsJob === job) {
                conversationsJob = null
                if (isCurrent(active) && latestConversations().isLoading) {
                    mutableState.value = mutableState.value.copy(
                        conversations = latestConversations().copy(isLoading = false),
                    )
                }
            }
        }
        job.start()
    }

    private fun latestNotifications() = mutableState.value.notifications
    private fun latestConversations() = mutableState.value.conversations

    companion object {
        const val NOTIFICATIONS_PAGE_SIZE = 20
    }
}
