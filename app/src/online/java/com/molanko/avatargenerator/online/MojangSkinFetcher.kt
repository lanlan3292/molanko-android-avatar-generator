package com.molanko.avatargenerator.online

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Fetches a Minecraft skin PNG via Mojang session / profile APIs.
 * Accepts a player name or UUID (with or without dashes).
 */
object MojangSkinFetcher {

    data class Result(
        val bitmap: Bitmap,
        val resolvedName: String?,
        val uuid: String
    )

    sealed class FetchError(message: String) : Exception(message) {
        class InvalidInput : FetchError("Invalid username or UUID")
        class PlayerNotFound : FetchError("Player not found")
        class NoSkin : FetchError("No skin texture on profile")
        class Network(cause: Throwable) : FetchError(cause.message ?: "Network error")
    }

    suspend fun fetch(usernameOrUuid: String): Result = withContext(Dispatchers.IO) {
        val input = usernameOrUuid.trim()
        if (input.isEmpty()) throw FetchError.InvalidInput()

        val uuidNoDash = resolveUuid(input)
        val (skinUrl, name) = fetchSkinUrl(uuidNoDash)
        val bmp = downloadBitmap(skinUrl)
            ?: throw FetchError.NoSkin()
        Result(bitmap = bmp, resolvedName = name, uuid = formatUuid(uuidNoDash))
    }

    private fun resolveUuid(input: String): String {
        val stripped = input.replace("-", "")
        if (stripped.length == 32 && stripped.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return stripped.lowercase()
        }
        if (!input.matches(Regex("^[A-Za-z0-9_]{1,16}$"))) {
            throw FetchError.InvalidInput()
        }
        val conn = open("https://api.mojang.com/users/profiles/minecraft/$input")
        try {
            when (conn.responseCode) {
                200 -> {
                    val body = conn.inputStream.bufferedReader().readText()
                    val id = JSONObject(body).optString("id", "")
                    if (id.length != 32) throw FetchError.PlayerNotFound()
                    return id.lowercase()
                }
                204, 404 -> throw FetchError.PlayerNotFound()
                else -> throw FetchError.Network(Exception("HTTP ${conn.responseCode}"))
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchSkinUrl(uuidNoDash: String): Pair<String, String?> {
        val conn = open("https://sessionserver.mojang.com/session/minecraft/profile/$uuidNoDash")
        try {
            when (conn.responseCode) {
                200 -> {
                    val body = conn.inputStream.bufferedReader().readText()
                    val root = JSONObject(body)
                    val name = root.optString("name", null)
                    val props = root.optJSONArray("properties") ?: throw FetchError.NoSkin()
                    var texturesB64: String? = null
                    for (i in 0 until props.length()) {
                        val p = props.getJSONObject(i)
                        if (p.optString("name") == "textures") {
                            texturesB64 = p.optString("value")
                            break
                        }
                    }
                    if (texturesB64.isNullOrBlank()) throw FetchError.NoSkin()
                    val decoded = String(Base64.decode(texturesB64, Base64.DEFAULT))
                    val textures = JSONObject(decoded)
                        .optJSONObject("textures")
                        ?.optJSONObject("SKIN")
                        ?: throw FetchError.NoSkin()
                    val url = textures.optString("url", "")
                    if (url.isBlank()) throw FetchError.NoSkin()
                    return url to name
                }
                204, 404 -> throw FetchError.PlayerNotFound()
                else -> throw FetchError.Network(Exception("HTTP ${conn.responseCode}"))
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val conn = open(url)
        try {
            if (conn.responseCode != 200) return null
            return BitmapFactory.decodeStream(conn.inputStream)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MolankoAvatarGenerator/1.0")
            setRequestProperty("Accept", "application/json, image/png, */*")
        }
    }

    private fun formatUuid(noDash: String): String {
        return try {
            UUID.fromString(
                "${noDash.substring(0, 8)}-${noDash.substring(8, 12)}-" +
                    "${noDash.substring(12, 16)}-${noDash.substring(16, 20)}-${noDash.substring(20)}"
            ).toString()
        } catch (_: Exception) {
            noDash
        }
    }
}
