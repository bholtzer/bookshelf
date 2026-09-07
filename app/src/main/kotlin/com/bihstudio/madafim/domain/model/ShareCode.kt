package com.bihstudio.madafim.domain.model

import java.security.MessageDigest
import java.util.Locale

private const val SHARE_CODE_PREFIX = "BS"
private const val SHARE_CODE_LENGTH = 10
private val shareCodeRegex = Regex("""(?i)\bBS[- ]?[A-Z0-9]{10}\b""")

fun String.toEditorShareCode(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val code = buildString {
        digest.forEach { byte ->
            if (length < SHARE_CODE_LENGTH) {
                append(alphabet[Math.floorMod(byte.toInt(), alphabet.length)])
            }
        }
    }
    return "$SHARE_CODE_PREFIX-$code"
}

fun String.extractEditorShareCode(): String? {
    val match = shareCodeRegex.find(this)?.value ?: return null
    val compact = match
        .uppercase(Locale.US)
        .replace("-", "")
        .replace(" ", "")
    return "${compact.take(2)}-${compact.drop(2)}"
}
