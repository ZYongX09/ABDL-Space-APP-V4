package top.abdl_space.app.core.common

/** 统一结果包装：成功 / 可重试失败 / 不可重试失败。 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
}

data class AppError(
    val kind: Kind,
    val message: String? = null,
    val cause: Throwable? = null,
) {
    enum class Kind { NETWORK, TIMEOUT, AUTH, FORBIDDEN, NOT_FOUND, SERVER, CLIENT, UNKNOWN }

    val retryable: Boolean
        get() = kind in setOf(Kind.NETWORK, Kind.TIMEOUT, Kind.SERVER)
}
