package com.echo.dailyglow

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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


@Composable
fun DailyGlowLoginDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("登录 DailyGlow 云端", color = Color(0xFF496B80), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("使用网页版同一邮箱与密码，简报和生活记录即可在设备间同步。", color = Color(0xFF625F5B), fontSize = 13.sp)
                OutlinedTextField(email, { email = it; error = "" }, label = { Text("邮箱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it; error = "" }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = Color(0xFFB25C56), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (email.isBlank() || password.isBlank()) { error = "请输入邮箱和密码"; return@TextButton }
                busy = true
                scope.launch {
                    try {
                        DailyGlowCloud(context).signIn(email, password)
                        onSuccess()
                    } catch (e: Exception) { error = e.message ?: "登录失败，请检查网络和账号"; }
                    finally { busy = false }
                }
            }) { if (busy) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp) else Text("登录并继续", color = Color(0xFF496B80)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("稍后", color = Color(0xFF625F5B)) } },
        containerColor = Color(0xFFFFFCF7)
    )
}

@Composable
fun DailyGlowCloudContent(
    page: String,
    sessionVersion: Int,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isInsights = page == "insights"
    var selectedDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    var selectedType by remember { mutableStateOf("growth_brief") }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val signedIn = remember(sessionVersion) { DailyGlowCloud(context).hasStoredSession() }
    val accent = when (selectedType) {
        "fund_strategy" -> Color(0xFFDA8C7E)
        "market_intraday" -> Color(0xFF7FAF8C)
        else -> Color(0xFF85ABC0)
    }
    LaunchedEffect(page, selectedDate, selectedType, sessionVersion) {
        rows = emptyList()
        message = ""
        if (!signedIn) return@LaunchedEffect
        loading = true
        try {
            val result = DailyGlowCloud(context).fetchDailyHub(selectedDate.toString())
            rows = (0 until result.length()).mapNotNull { result.optJSONObject(it) }
                .filter { row ->
                    val type = row.optString("content_type")
                    if (isInsights) type in setOf("growth_brief", "fund_strategy", "market_intraday")
                    else type == "growth_brief"
                }
                .filter { !isInsights || it.optString("content_type") == selectedType }
            if (rows.isEmpty()) message = "这一天还没有同步的内容。"
        } catch (e: Exception) { message = e.message ?: "云端暂时无法连接" }
        finally { loading = false }
    }

    val header = if (isInsights) "洞察" else "每日成长"
    val kindTitle = when (selectedType) {
        "fund_strategy" -> "基金策略"
        "market_intraday" -> "盘中风控"
        else -> "成长简报"
    }
    Column(modifier.fillMaxSize().background(Color(0xFFFFFAF4))) {
        Column(Modifier.fillMaxWidth().background(accent.copy(alpha = .16f)).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(header, color = Color(0xFF496B80), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(if (isInsights) "成长、基金与盘中风险，一页查阅。" else "今日关注与学习灵感。", color = Color(0xFF625F5B), fontSize = 13.sp)
        }
        if (isInsights) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("growth_brief" to "成长洞察", "fund_strategy" to "基金策略", "market_intraday" to "盘中风险").forEach { (type, label) ->
                    val tint = when (type) { "fund_strategy" -> Color(0xFFDA8C7E); "market_intraday" -> Color(0xFF7FAF8C); else -> Color(0xFF85ABC0) }
                    Text(label, Modifier.weight(1f).background(if (selectedType == type) tint.copy(alpha = .22f) else Color.White, RoundedCornerShape(18.dp)).clickable { selectedType = type }.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center, color = Color(0xFF496B80), fontSize = 11.sp, fontWeight = if (selectedType == type) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 前一天", Modifier.clickable { selectedDate = selectedDate.minusDays(1) }.padding(8.dp), color = Color(0xFF496B80))
            Text("${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日", color = Color(0xFF496B80), fontWeight = FontWeight.SemiBold)
            Text("后一天 ›", Modifier.clickable { if (selectedDate < java.time.LocalDate.now()) selectedDate = selectedDate.plusDays(1) }.padding(8.dp), color = if (selectedDate < java.time.LocalDate.now()) Color(0xFF496B80) else Color.LightGray)
        }
        when {
            !signedIn -> Column(Modifier.weight(1f).padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("登录后查看你的云端内容", color = Color(0xFF496B80), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("登录 DailyGlow", color = Color.White) }
            }
            loading -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = accent) }
            rows.isEmpty() -> Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = Color(0xFF625F5B), textAlign = TextAlign.Center) }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rows) { row ->
                    val payload = row.optJSONObject("payload") ?: JSONObject()
                    val doc = payload.optJSONObject("document")
                    val body = doc?.optString("body").orEmpty().ifBlank { payload.optString("body") }.ifBlank { payload.toString(2) }
                    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(row.optString("title", kindTitle), color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (row.optString("summary").isNotBlank()) Text(row.optString("summary"), color = Color(0xFF625F5B), fontSize = 13.sp, lineHeight = 20.sp)
                        DailyGlowDocument(body, accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyGlowDocument(body: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        body.lineSequence().filter { it.isNotBlank() }.take(500).forEach { raw ->
            val line = raw.trim()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^[-•●▪]\\s*"), "• ")
                .replace(Regex("^\\d+[.)、]\\s*"), "• ")
                .replace(Regex("\\*\\*|__"), "")
                .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
                .replace("|", "  ·  ")
            val heading = raw.trim().startsWith("#") || raw.trim().matches(Regex("^[一二三四五六七八九十]+[、.].*"))
            Text(line, color = if (heading) accent else Color(0xFF514D49), fontSize = if (heading) 15.sp else 12.sp,
                fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal, lineHeight = if (heading) 21.sp else 19.sp)
        }
    }
}
