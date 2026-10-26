package top.abdl_space.app.core.data.timeline

import kotlinx.serialization.json.Json
import top.abdl_space.app.core.data.session.SessionLease
import top.abdl_space.app.core.data.session.SessionRepository
import top.abdl_space.app.core.model.ProtocolViolation
import top.abdl_space.app.core.model.Status
import top.abdl_space.app.core.network.AuthorizationHeader
import top.abdl_space.app.core.network.LinkPagination
import top.abdl_space.app.core.network.NetworkCall
import top.abdl_space.app.core.network.NetworkFailure
import top.abdl_space.app.core.network.NetworkFailureKind
import top.abdl_space.app.core.network.NetworkResult
import javax.inject.Inject
import javax.inject.Singleton
import top.abdl_space.app.core.network.api.MastodonApi

enum class TimelineKind { RECOMMENDED, FOLLOWING, POPULAR }

data class TimelinePage(
    val statuses: List<Status>,
    val nextCursor: String?,
    val nextOffset: Int?,
) {
    val hasMore: Boolean get() = nextCursor != null || nextOffset != null
}

@Singleton
class TimelineRepository @Inject constructor(
    private val api: MastodonApi,
    private val sessions: SessionRepository,
    private val json: Json,
) {
    suspend fun page(
        kind: TimelineKind,
        lease: SessionLease,
        maxId: String? = null,
        offset: Int = 0,
    ): NetworkResult<TimelinePage> {
        val credential = sessions.activeCredential(lease)
            ?: return NetworkResult.Failure(NetworkFailure(NetworkFailureKind.UNAUTHORIZED))
        val authorization = AuthorizationHeader.bearer(credential.accessToken)
        val result = NetworkCall.response(json) {
            when (kind) {
                TimelineKind.RECOMMENDED -> api.allTimeline(authorization, maxId = maxId, limit = PAGE_SIZE)
                TimelineKind.FOLLOWING -> api.homeTimeline(authorization, maxId = maxId, limit = PAGE_SIZE)
                TimelineKind.POPULAR -> api.popularTimeline(authorization, offset = offset, limit = PAGE_SIZE)
            }
        }
        if (!sessions.isCurrent(lease)) {
            return NetworkResult.Failure(NetworkFailure(NetworkFailureKind.UNAUTHORIZED))
        }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> try {
                val response = result.value
                val body = checkNotNull(response.body())
                val next = if (kind == TimelineKind.POPULAR) null else LinkPagination.from(response).nextMaxId
                val nextOffset = if (kind == TimelineKind.POPULAR && body.size == PAGE_SIZE) offset + body.size else null
                NetworkResult.Success(TimelinePage(body.distinctBy { it.id }, next, nextOffset))
            } catch (_: ProtocolViolation) {
                NetworkResult.Failure(NetworkFailure(NetworkFailureKind.PROTOCOL))
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
