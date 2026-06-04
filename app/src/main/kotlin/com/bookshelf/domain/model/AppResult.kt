package com.bookshelf.domain.model

/**
 * A lightweight result wrapper used across the domain and data layers.
 * Keeps the domain free of Android-specific Result / kotlin.Result quirks.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()

    val isSuccess get() = this is Success
    val isError   get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): String? = (this as? Error)?.message
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onError(block: (String, Throwable?) -> Unit): AppResult<T> {
    if (this is AppResult.Error) block(message, cause)
    return this
}
