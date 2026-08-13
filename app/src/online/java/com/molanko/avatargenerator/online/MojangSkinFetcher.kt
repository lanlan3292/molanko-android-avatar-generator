package com.molanko.avatargenerator.online

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
        class RateLimited : FetchError("Mojang API rate limit exceeded. Please try again later")
        class ServerError(code: Int) : FetchError("Mojang server error: HTTP $code")
        class Network(val detail: String, cause: Throwable? = null) : FetchError(detail)
    }

    suspend fun fetch(usernameOrUuid: String): Result = withContext(Dispatchers.IO) {
        val input = usernameOrUuid.trim()
        if (input.isEmpty()) throw FetchError.InvalidInput()

        try {
            ensureActive()
            val uuidNoDash = resolveUuid(input)

            ensureActive()
            val (skinUrl, name) = fetchSkinUrl(uuidNoDash)

            ensureActive()
            val bmp = downloadBitmap(skinUrl)
                ?: throw FetchError.NoSkin()

            Result(bitmap = bmp, resolvedName = name, uuid = formatUuid(uuidNoDash))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FetchError) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw FetchError.Network("DNS: ${e.message}", e)
        } catch (e: java.net.SocketTimeoutException) {
            throw FetchError.Network("Timeout: ${e.message}", e)
        } catch (e: javax.net.ssl.SSLException) {
            throw FetchError.Network("SSL: ${e.message}", e)
        } catch (e: java.io.IOException) {
            currentCoroutineContext().ensureActive()
            throw FetchError.Network("IO: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private suspend fun resolveUuid(input: String): String {
        val stripped = input.replace("-", "")
        if (stripped.length == 32 && stripped.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return stripped.lowercase()
        }
        if (!input.matches(Regex("^[A-Za-z0-9_]{1,16}$"))) {
            throw FetchError.InvalidInput()
        }

        val encodedName = URLEncoder.encode(input, "UTF-8")
        val conn = open("https://api.mojang.com/users/profiles/minecraft/$encodedName")

        try {
            when (conn.responseCode) {
                200 -> {
                    val body = conn.inputStream.bufferedReader().readText()
                    val id = JSONObject(body).optString("id", "")
                    if (id.length != 32 || !id.matches(Regex("^[0-9a-fA-F]{32}$"))) {
                        throw FetchError.PlayerNotFound()
                    }
                    return id.lowercase()
                }
                204, 404 -> throw FetchError.PlayerNotFound()
                429 -> throw FetchError.RateLimited()
                in 500..599 -> throw FetchError.ServerError(conn.responseCode)
                else -> {
                    val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                    throw FetchError.Network("HTTP ${conn.responseCode}" + (errBody?.take(120)?.let { ": $it" } ?: ""))
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun fetchSkinUrl(uuidNoDash: String): Pair<String, String?> {
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

                    val decoded = try {
                        String(
                            Base64.decode(texturesB64, Base64.NO_WRAP),
                            Charsets.UTF_8
                        )
                    } catch (e: IllegalArgumentException) {
                        throw FetchError.Network("Invalid Mojang texture Base64", e)
                    }

                    val textures = JSONObject(decoded)
                        .optJSONObject("textures")
                        ?.optJSONObject("SKIN")
                        ?: throw FetchError.NoSkin()
                    val url = textures.optString("url", "")
                    if (url.isBlank()) throw FetchError.NoSkin()
                    return url to name
                }
                204, 404 -> throw FetchError.PlayerNotFound()
                429 -> throw FetchError.RateLimited()
                in 500..599 -> throw FetchError.ServerError(conn.responseCode)
                else -> {
                    val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                    throw FetchError.Network("HTTP ${conn.responseCode}" + (errBody?.take(120)?.let { ": $it" } ?: ""))
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadBitmap(url: String): Bitmap? {
        val conn = open(url)
        try {
            if (conn.responseCode != 200) {
                if (conn.responseCode == 429) {
                    throw FetchError.RateLimited()
                }

                if (conn.responseCode in 500..599) {
                    throw FetchError.ServerError(conn.responseCode)
                }

                val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                throw FetchError.Network(
                    "Skin download HTTP ${conn.responseCode}" +
                        (errBody?.take(80)?.let { ": $it" } ?: "") + " url=$url"
                )
            }
            return BitmapFactory.decodeStream(conn.inputStream)
                ?: throw FetchError.Network("Failed to decode skin PNG from $url")
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun open(url: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MolankoAvatarGenerator/1.0")
            setRequestProperty("Accept", "application/json, image/png, */*")
        }

        currentCoroutineContext().job.invokeOnCompletion {
            conn.disconnect()
        }

        return conn
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
