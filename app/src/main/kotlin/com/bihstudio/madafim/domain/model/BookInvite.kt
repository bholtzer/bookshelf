package com.bihstudio.madafim.domain.model

import java.security.MessageDigest
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale

private const val BOOK_INVITE_PREFIX = "BK"
private const val BOOK_INVITE_LENGTH = 12
private const val BOOK_INVITE_WEB_HOST = "bookshelf-4e90f.web.app"
private val bookInviteRegex = Regex("""(?i)\bBK[- ]?[A-Z0-9]{12}\b""")

fun String.toBookInviteCode(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val code = buildString {
        digest.forEach { byte ->
            if (length < BOOK_INVITE_LENGTH) {
                append(alphabet[Math.floorMod(byte.toInt(), alphabet.length)])
            }
        }
    }
    return "$BOOK_INVITE_PREFIX-$code"
}

fun String.extractBookInviteCode(): String? {
    val match = bookInviteRegex.find(this)?.value ?: return null
    val compact = match
        .uppercase(Locale.US)
        .replace("-", "")
        .replace(" ", "")
    return "${compact.take(2)}-${compact.drop(2)}"
}

data class BookInviteTarget(
    val ownerId: String,
    val bookId: String,
)

fun String.toBookInviteLink(ownerId: String, bookId: String): String =
    "madafim://book-invite/$this?t=${encodeInviteTarget(ownerId, bookId)}"

fun String.toBookInviteWebLink(ownerId: String, bookId: String): String =
    "https://$BOOK_INVITE_WEB_HOST/book-invite/$this?t=${encodeInviteTarget(ownerId, bookId)}"

fun String.toBookInvitePlayStoreLink(packageName: String, inviteLink: String): String =
    "https://play.google.com/store/apps/details?id=$packageName&referrer=${urlEncode("book_invite_link=$inviteLink")}"

fun String.toBookInviteIntentLink(packageName: String): String {
    val fallback = toBookInvitePlayStoreLink(packageName, this)
    val schemeSpecificPart = removePrefix("madafim://")
    return "intent://$schemeSpecificPart#Intent;scheme=madafim;package=$packageName;S.browser_fallback_url=${urlEncode(fallback)};end"
}

fun String.extractBookInviteTarget(): BookInviteTarget? {
    val token = substringAfter("?t=", missingDelimiterValue = "")
        .substringBefore("&")
        .takeIf { it.isNotBlank() }
        ?: return null
    val decoded = runCatching {
        String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8)
    }.getOrNull() ?: return null
    val ownerId = decoded.substringBefore(":", missingDelimiterValue = "")
    val bookId = decoded.substringAfter(":", missingDelimiterValue = "")
    if (ownerId.isBlank() || bookId.isBlank()) return null
    return BookInviteTarget(ownerId = ownerId, bookId = bookId)
}

private fun encodeInviteTarget(ownerId: String, bookId: String): String =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString("$ownerId:$bookId".toByteArray(Charsets.UTF_8))

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name())
