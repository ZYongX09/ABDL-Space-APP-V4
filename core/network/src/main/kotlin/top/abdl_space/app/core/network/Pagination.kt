package top.abdl_space.app.core.network

import okhttp3.HttpUrl
import retrofit2.Response
import top.abdl_space.app.core.model.ProtocolViolation
import top.abdl_space.app.core.model.StatusContext

/** A cursor is a query value, never a URL to follow with credentials. */
data class PageCursors(val nextMaxId: String? = null, val previousMinId: String? = null)

/**
 * Inspect every Link header/entry, quoted/unquoted/multi-value rel attributes, and
 * percent-encoded cursors. Only accept same-origin/same-path links; NEVER follow a Link URL.
 * Duplicate or conflicting continuations fail closed instead of looping or choosing arbitrarily.
 */
object LinkPagination {
    fun from(response: Response<*>): PageCursors = parse(
        response.headers().values("Link"), response.raw().request.url,
    )

    fun parse(headers: List<String>, requestUrl: HttpUrl): PageCursors {
        var next: String? = null
        var previous: String? = null
        for (header in headers) {
            for (entry in entries(header)) {
                val target = TARGET.find(entry) ?: continue
                val url = requestUrl.resolve(target.groupValues[1]) ?: continue
                if (!sameOrigin(requestUrl, url) || url.encodedPath != requestUrl.encodedPath) continue
                val attributes = entry.substring(target.range.last + 1)
                val rel = REL.find(attributes) ?: continue
                val relations = (rel.groups[1]?.value ?: rel.groups[2]?.value.orEmpty()).split(Regex("\\s+"))
                if ("next" in relations) {
                    val candidate = singleQuery(url, "max_id")
                    if (candidate != null) {
                        if (next != null && next != candidate) throw ProtocolViolation("Conflicting next cursors")
                        next = candidate
                    }
                }
                if ("prev" in relations) {
                    val candidate = singleQuery(url, "min_id")
                    if (candidate != null) {
                        if (previous != null && previous != candidate) throw ProtocolViolation("Conflicting previous cursors")
                        previous = candidate
                    }
                }
            }
        }
        if (next != null && next == requestUrl.queryParameter("max_id")) throw ProtocolViolation("Timeline cursor did not advance")
        return PageCursors(next, previous)
    }

    private fun singleQuery(url: HttpUrl, name: String): String? {
        val values = url.queryParameterValues(name)
        if (values.size > 1) throw ProtocolViolation("Duplicate pagination cursor")
        return values.singleOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun entries(header: String): List<String> {
        val result = mutableListOf<String>()
        var inTarget = false
        var quoted = false
        var start = 0
        header.forEachIndexed { index, char ->
            when (char) {
                '<' -> if (!quoted) inTarget = true
                '>' -> if (!quoted) inTarget = false
                '"' -> if (!inTarget) quoted = !quoted
                ',' -> if (!inTarget && !quoted) {
                    result += header.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        result += header.substring(start).trim()
        return result
    }

    private val TARGET = Regex("^\\s*<([^>]+)>")
    private val REL = Regex("(?:^|;)\\s*rel\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]+))", RegexOption.IGNORE_CASE)
}

/** Header and body signals are OR-ed, without ever converting an HTTP failure to empty context. */
fun Response<StatusContext>.contextWithDegradation(): StatusContext {
    if (!isSuccessful) throw ProtocolViolation("Context response is unsuccessful")
    val context = body() ?: throw ProtocolViolation("Missing status context")
    return context.copy(nbwUnavailable = context.nbwUnavailable || headers()["X-ABDL-Context-Degraded"].equals("nbw", ignoreCase = true))
}
