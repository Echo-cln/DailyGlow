package com.echo.dailyglow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DailyGlowCloudSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val expiresAt: Long
)

/**
 * Direct, user-scoped Supabase access for Android.
 * Only the publishable key is embedded in the client; row access relies on the signed-in
 * user's access token and database RLS. Session tokens are encrypted with Android Keystore.
 */
class DailyGlowCloud(private val context: Context) {
    private val sessionPrefs = context.getSharedPreferences("dailyglow_cloud_session", Context.MODE_PRIVATE)
    private val apiUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun hasStoredSession(): Boolean = loadSession() != null

    suspend fun signIn(email: String, password: String): DailyGlowCloudSession = withContext(Dispatchers.IO) {
        val result = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            authenticated = false,
            body = JSONObject().put("email", email.trim()).put("password", password)
        )
        val user = result.optJSONObject("user") ?: throw IllegalStateException("登录响应缺少用户信息")
        val session = DailyGlowCloudSession(
            accessToken = result.getString("access_token"),
            refreshToken = result.getString("refresh_token"),
            userId = user.getString("id"),
            email = user.optString("email").takeIf(String::isNotBlank),
            expiresAt = System.currentTimeMillis() / 1000 + result.optLong("expires_in", 3600)
        )
        storeSession(session)
        session
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = loadSession()
        if (session != null) {
            runCatching { request("POST", "/auth/v1/logout", session.accessToken, JSONObject()) }
        }
        sessionPrefs.edit().remove("session").apply()
    }

    suspend fun fetchDailyHub(date: String): JSONArray = withContext(Dispatchers.IO) {
        val session = requireSession()
        val encodedDate = java.net.URLEncoder.encode(date, Charsets.UTF_8.name())
        requestArray(
            "/rest/v1/daily_hub_items?select=id,content_date,content_type,title,summary,payload,updated_at&content_date=eq.$encodedDate&order=updated_at.desc",
            session.accessToken
        )
    }

    suspend fun downloadLifeSnapshot(date: String): JSONObject? = withContext(Dispatchers.IO) {
        val session = requireSession()
        val encodedDate = java.net.URLEncoder.encode(date, Charsets.UTF_8.name())
        requestArray(
            "/rest/v1/daily_life_records?select=payload&record_date=eq.$encodedDate&record_type=eq.daily_life&limit=1",
            session.accessToken
        ).optJSONObject(0)?.optJSONObject("payload")
    }

    suspend fun uploadLifeSnapshot(date: String, payload: JSONObject) = withContext(Dispatchers.IO) {
        val session = requireSession()
        val row = JSONObject()
            .put("user_id", session.userId)
            .put("record_date", date)
            .put("record_type", "daily_life")
            .put("schema_version", 1)
            .put("payload", payload)
            .put("updated_at", java.time.Instant.now().toString())
        request(
            method = "POST",
            path = "/rest/v1/daily_life_records?on_conflict=user_id,record_date,record_type",
            token = session.accessToken,
            body = row,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    private suspend fun requireSession(): DailyGlowCloudSession {
        val current = loadSession() ?: throw IllegalStateException("请先登录 DailyGlow 账号")
        if (current.expiresAt > System.currentTimeMillis() / 1000 + 90) return current
        val refreshed = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=refresh_token",
            authenticated = false,
            body = JSONObject().put("refresh_token", current.refreshToken)
        )
        val user = refreshed.optJSONObject("user")
        val session = DailyGlowCloudSession(
            accessToken = refreshed.getString("access_token"),
            refreshToken = refreshed.optString("refresh_token", current.refreshToken),
            userId = user?.optString("id")?.takeIf(String::isNotBlank) ?: current.userId,
            email = user?.optString("email")?.takeIf(String::isNotBlank) ?: current.email,
            expiresAt = System.currentTimeMillis() / 1000 + refreshed.optLong("expires_in", 3600)
        )
        storeSession(session)
        return session
    }

    private fun requestArray(path: String, token: String): JSONArray {
        val response = openRequest("GET", path, token, null, null)
        val raw = response.inputStream.bufferedReader().use { it.readText() }
        return JSONArray(raw)
    }

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
        prefer: String? = null,
        authenticated: Boolean = token != null
    ): JSONObject {
        val response = openRequest(method, path, token, body, prefer, authenticated)
        val raw = (if (response.responseCode in 200..299) response.inputStream else response.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (response.responseCode !in 200..299) {
            val message = runCatching { JSONObject(raw).optString("msg").ifBlank { JSONObject(raw).optString("message") } }.getOrNull()
            throw IllegalStateException(message?.takeIf(String::isNotBlank) ?: "云端请求失败（${response.responseCode}）")
        }
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun openRequest(
        method: String,
        path: String,
        token: String?,
        body: JSONObject?,
        prefer: String?,
        authenticated: Boolean = token != null
    ): HttpURLConnection {
        val connection = (URL(apiUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            if (authenticated && token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                body.toString().toByteArray(Charsets.UTF_8).let { outputStream.use { stream -> stream.write(it) } }
            }
            if (prefer != null) setRequestProperty("Prefer", prefer)
        }
        return connection
    }

    private fun storeSession(session: DailyGlowCloudSession) {
        val raw = JSONObject()
            .put("access", session.accessToken)
            .put("refresh", session.refreshToken)
            .put("user", session.userId)
            .put("email", session.email ?: JSONObject.NULL)
            .put("expires", session.expiresAt)
            .toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(raw.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        sessionPrefs.edit().putString("session", Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    private fun loadSession(): DailyGlowCloudSession? {
        val encoded = sessionPrefs.getString("session", null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = 12
            require(combined.size > ivSize)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, combined.copyOfRange(0, ivSize)))
            val json = JSONObject(String(cipher.doFinal(combined.copyOfRange(ivSize, combined.size)), Charsets.UTF_8))
            DailyGlowCloudSession(
                json.getString("access"),
                json.getString("refresh"),
                json.getString("user"),
                json.optString("email").takeIf(String::isNotBlank),
                json.optLong("expires")
            )
        } catch (_: Exception) {
            sessionPrefs.edit().remove("session").apply()
            null
        }
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "dailyglow_cloud_session_key"
    }
}
