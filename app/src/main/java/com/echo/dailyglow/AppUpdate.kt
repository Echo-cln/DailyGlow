package com.echo.dailyglow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class DailyGlowRelease(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String
)

suspend fun checkDailyGlowRelease(context: Context): DailyGlowRelease? = withContext(Dispatchers.IO) {
    val connection = (URL("https://api.github.com/repos/Echo-cln/DailyGlow/releases/latest").openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 10000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    }
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val asset = release.optJSONArray("assets")
        val apkUrl = (0 until (asset?.length() ?: 0))
            .mapNotNull { asset?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url")
            .orEmpty()
        if (apkUrl.isBlank()) return@withContext null
        val latest = release.optString("tag_name").removePrefix("v")
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().removePrefix("v")
        if (compareVersions(latest, current) <= 0) return@withContext null
        DailyGlowRelease(latest, release.optString("name", "DailyGlow $latest"), release.optString("body", ""), apkUrl)
    } finally {
        connection.disconnect()
    }
}

private fun compareVersions(left: String, right: String): Int {
    fun parts(value: String) = value.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(left)
    val b = parts(right)
    for (index in 0 until maxOf(a.size, b.size)) {
        val compare = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
        if (compare != 0) return compare
    }
    return 0
}

fun installDailyGlowRelease(context: Context, release: DailyGlowRelease, onStatus: (String) -> Unit) {
    if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
        val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(settings)
        onStatus("请允许 DailyGlow 安装更新，然后回到这里再次点击安装。")
        return
    }
    Thread {
        try {
            val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
            }
            val bytes = try {
                if (connection.responseCode !in 200..299) error("下载更新失败：HTTP ${connection.responseCode}")
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("dailyglow.apk", 0, bytes.size.toLong()).use { output ->
                    output.write(bytes)
                    session.fsync(output)
                }
                val intent = Intent(context, DailyGlowInstallReceiver::class.java).setAction("com.echo.dailyglow.INSTALL_STATUS")
                val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    if (android.os.Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0
                val pending = android.app.PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
            (context as? android.app.Activity)?.runOnUiThread { onStatus("更新包已交给 Android 安装器，请按系统提示完成更新。") }
        } catch (error: Exception) {
            (context as? android.app.Activity)?.runOnUiThread { onStatus(error.message ?: "安装更新失败，请稍后重试。") }
        }
    }.start()
}

class DailyGlowInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
                return
            }
            PackageInstaller.STATUS_SUCCESS -> "DailyGlow 已更新"
            else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "更新未完成"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun DailyGlowUpdateDialog(release: DailyGlowRelease?, onDismiss: () -> Unit, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    if (release == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(release.title) },
        text = { Text(release.notes.ifBlank { "发现 DailyGlow 新版本 ${release.version}。" }) },
        confirmButton = {
            Button(onClick = { installDailyGlowRelease(context, release, onStatus) }) { Text("下载并安装") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
    )
}
