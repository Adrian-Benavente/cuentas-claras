package com.cuentasclaras.app.util

import android.content.Intent
import android.net.Uri

object InviteShare {
    const val SCHEME = "cuentasclaras"
    const val HOST_JOIN = "join"

    fun normalizeCode(raw: String): String =
        raw.trim().uppercase().replace(Regex("[\\s\\-]"), "")

    fun deepLinkUri(code: String): String =
        "$SCHEME://$HOST_JOIN/${normalizeCode(code)}"

    fun shareText(groupName: String, code: String): String {
        val normalized = normalizeCode(code)
        return buildString {
            append("Unite a \"")
            append(groupName)
            append("\" en Cuentas Claras.\n\n")
            append("1. Abrí la app\n")
            append("2. Tocá \"Unirme a un grupo\"\n")
            append("3. Ingresá el código: ")
            append(normalized)
            append("\n\n")
            append("O abrí: ")
            append(deepLinkUri(normalized))
        }
    }

    fun parseJoinCode(intent: Intent?): String? =
        parseJoinCode(intent?.data)

    fun parseJoinCode(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != SCHEME) return null
        if (uri.host != HOST_JOIN) return null
        val raw = uri.pathSegments.firstOrNull().orEmpty()
        val normalized = normalizeCode(raw)
        return normalized.takeIf { it.isNotBlank() }
    }
}
