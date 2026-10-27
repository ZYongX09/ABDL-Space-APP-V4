package top.abdl_space.app.core.data.compose

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import top.abdl_space.app.core.data.session.SessionLease
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.model.Status
import top.abdl_space.app.core.model.StatusVisibility
import top.abdl_space.app.core.network.AuthorizationHeader
import top.abdl_space.app.core.network.NetworkCall
import top.abdl_space.app.core.network.NetworkFailure
import top.abdl_space.app.core.network.NetworkFailureKind
import top.abdl_space.app.core.network.NetworkResult
import top.abdl_space.app.core.network.api.MastodonApi
import top.abdl_space.app.core.network.dto.CreateStatusRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Faithful port of the legacy client's local mental-crisis heuristic (ComposeFragment
 * containsCrisisKeyword + CRISIS_KEYWORDS). It flips the typed `mental_crisis` request field;
 * it is not a moderation decision and never blocks or rewrites the text.
 */
object MentalCrisisRules {
    val Keywords = listOf(
        "自杀", "自残", "死亡", "抑郁", "双向", "双相", "ADHD", "PTSD", "童年创伤", "不想活",
        "不想活了", "不想再活", "活不下去", "活着没意思", "活着没有意义", "想死", "去死", "求死",
        "寻死", "轻生", "结束生命", "结束自己的生命", "了结自己", "离开这个世界", "永远消失",
        "绝望", "没必要活", "没有活下去的理由", "不值得活", "死了算了", "一死了之", "结束一切",
        "解脱了", "撑不下去了", "不想醒来", "希望永远睡过去", "割腕", "割脉", "拿刀割自己",
        "划伤自己", "跳楼", "跳河", "撞车", "卧轨", "吞药", "服毒", "上吊", "自我伤害",
        "伤害自己", "伤害我自己",
    )

    fun containsCrisisKeyword(text: String): Boolean {
        if (text.isEmpty()) return false
        val normalized = text.lowercase()
        return Keywords.any { normalized.contains(it.lowercase()) }
    }
}

sealed interface ComposePublishResult {
    /** The server accepted the POST and returned the real created Status. */
    data class Published(val status: Status) : ComposePublishResult

    /**
     * The POST did not complete successfully. The draft text and its idempotency key stay
     * valid so the caller can retry instead of silently losing the post.
     */
    data class Failed(val error: NetworkFailure) : ComposePublishResult

    /**
     * The session lease changed mid-flight (logout/account switch/storage failure). The
     * outcome belongs to a previous account and is dropped by design; the caller's draft
     * text stays wherever the user left it.
     */
    data object SessionChanged : ComposePublishResult
}

/**
 * Real text posting over POST /api/v1/statuses.
 *
 * Contract parity verified against the legacy ComposeFragment/CreateStatus:
 * - New posts go out with visibility "public" (the legacy fork hardcodes it; no picker is wired).
 * - Language, media, poll, CW, scheduling, NBW classification and geo fields are absent until
 *   their own migration lands; nothing is invented to fill them.
 * - Idempotency-Key is minted per unique payload: a retry of the identical payload reuses the
 *   same in-process key, any content change gets a fresh one, success clears it. The key lives
 *   in process memory only (same as the legacy field), never persisted.
 */
@Singleton
class ComposeRepository @Inject constructor(
    private val api: MastodonApi,
    private val sessions: SessionRepository,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var pendingLease: SessionLease? = null
    private var pendingPayload: String? = null
    private var pendingKey: String? = null

    suspend fun publish(lease: SessionLease, text: String): ComposePublishResult = mutex.withLock {
        val credential = sessions.activeCredential(lease)
            ?: return@withLock ComposePublishResult.Failed(NetworkFailure(NetworkFailureKind.UNAUTHORIZED))
        val request = textStatusRequest(text)
        val payload = json.encodeToString(CreateStatusRequest.serializer(), request)
        val key = pendingKey?.takeIf { pendingLease == lease && pendingPayload == payload }
            ?: UUID.randomUUID().toString()
        pendingLease = lease
        pendingPayload = payload
        pendingKey = key
        val result = NetworkCall.body(json) {
            api.createStatus(AuthorizationHeader.bearer(credential.accessToken), key, request)
        }
        // Cancellation is an unknown outcome even if a transport finishes non-cooperatively.
        // Keep the key so an explicit retry can safely resolve the same write.
        currentCoroutineContext().ensureActive()
        if (!sessions.isCurrent(lease)) {
            // A new lease generation must never inherit a key minted for the previous account.
            clearPending()
            return@withLock ComposePublishResult.SessionChanged
        }
        when (result) {
            is NetworkResult.Success -> {
                clearPending()
                ComposePublishResult.Published(result.value)
            }
            is NetworkResult.Failure -> ComposePublishResult.Failed(result.error)
        }
    }

    private fun clearPending() {
        pendingLease = null
        pendingPayload = null
        pendingKey = null
    }

    companion object {
        /** Verified text-post subset of the legacy CreateStatus.Request; extensions stay absent. */
        fun textStatusRequest(text: String): CreateStatusRequest = CreateStatusRequest(
            status = text,
            visibility = StatusVisibility.PUBLIC,
            mentalCrisis = MentalCrisisRules.containsCrisisKeyword(text),
        )
    }
}
