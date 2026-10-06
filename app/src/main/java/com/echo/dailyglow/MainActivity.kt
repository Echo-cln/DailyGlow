package com.echo.dailyglow

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.concurrent.thread

private val Cashmere = Color(0xFFFFFAF4)
private val GoldPink = Color(0xFFF5D7C7)
private val Berry = Color(0xFFE9B7A5)
private val Maroon = Color(0xFFC87868)
private val Teal = Color(0xFFE4EBDD)
private val MistBlue = Color(0xFFEAF0F1)
private val Ink = Color(0xFF4D5660)

data class WorkoutPlan(val date: String, val title: String, val note: String, val playlist: String, val items: List<WorkoutItem>)
data class WorkoutItem(val id: String, val phase: String, val name: String, val instruction: String, val kind: String, val value: Int, val sets: Int, val tutorialQuery: String)

class MainActivity : ComponentActivity() {
    private var sharedPlanText by mutableStateOf<String?>(null)
    private var sharedPlanIsHistory by mutableStateOf(false)

    companion object {
        const val EXTRA_HISTORY_PLAN = "com.echo.dailyglow.HISTORY_PLAN"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedPlan(intent)
        setContent {
            DailyGlowApp(
                sharedPlanText = sharedPlanText,
                sharedPlanIsHistory = sharedPlanIsHistory,
                onSharedPlanHandled = {
                    sharedPlanText = null
                    sharedPlanIsHistory = false
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveSharedPlan(intent)
    }

    private fun receiveSharedPlan(intent: Intent?) {
        intent?.getStringExtra(EXTRA_HISTORY_PLAN)?.let {
            sharedPlanText = it
            sharedPlanIsHistory = true
            return
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedPlanText = intent.getStringExtra(Intent.EXTRA_TEXT)
            sharedPlanIsHistory = false
        }
    }
}

@Composable
private fun DailyGlowApp(sharedPlanText: String?, sharedPlanIsHistory: Boolean, onSharedPlanHandled: () -> Unit) {
    val context = LocalContext.current
    var plan by remember { mutableStateOf(loadPlan(context)) }
    var loading by remember { mutableStateOf(false) }
    val completed = remember(plan.date) { mutableStateListOf<String>().also { it.addAll(loadCompleted(context, plan.date)) } }
    var timerItem by remember { mutableStateOf<WorkoutItem?>(null) }
    var editingItem by remember { mutableStateOf<WorkoutItem?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sharedPlanText) {
        val raw = sharedPlanText ?: return@LaunchedEffect
        try {
            val json = extractPlanJson(raw)
            val parsed = parsePlan(json)
            val incoming = if (sharedPlanIsHistory) parsed.copy(date = LocalDate.now().toString()) else parsed
            if (sharedPlanIsHistory) clearCompleted(context, incoming.date)
            savePlan(context, incoming)
            plan = incoming
            Toast.makeText(context, if (sharedPlanIsHistory) "已导入到今天：${incoming.title}" else "已导入：${incoming.title}", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            importError = "未识别到训练计划。请提供一份完整的训练计划 JSON。"
        } finally {
            onSharedPlanHandled()
        }
    }

    LaunchedEffect(plan) { upsertHistory(context, plan, completed) }

    var currentTab by remember { mutableStateOf("life") }
    val cloud = remember(context) { DailyGlowCloud(context) }
    val cloudScope = rememberCoroutineScope()
    var cloudSignedIn by remember { mutableStateOf(cloud.hasStoredSession()) }
    var cloudSessionVersion by remember { mutableIntStateOf(if (cloudSignedIn) 1 else 0) }
    var showCloudLogin by remember { mutableStateOf(false) }
    var cloudStatus by remember { mutableStateOf("") }
    var appUpdateStatus by remember { mutableStateOf("") }
    var availableRelease by remember { mutableStateOf<DailyGlowRelease?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Cashmere) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (currentTab) {
                        "today" -> TodayLandingScreen(
                            trainingTitle = plan.title,
                            completed = completed.size,
                            onTraining = { currentTab = "training" },
                            onLife = { currentTab = "life" },
                            context = context
                        )
                        "life" -> DailyLifeHub(
                            context = context,
                            trainingTitle = plan.title,
                            trainingCompleted = completed.size,
                            onTraining = { currentTab = "training" },
                            onExit = { currentTab = "today" },
                            onOpenGrowth = { currentTab = "growth" },
                            onOpenInsights = { currentTab = "insights" },
                            cloudSignedIn = cloudSignedIn,
                            cloudStatus = cloudStatus,
                            onCloudLogin = { showCloudLogin = true },
                            updateStatus = appUpdateStatus,
                            onCheckUpdate = {
                                cloudScope.launch {
                                    appUpdateStatus = "正在检查版本…"
                                    try {
                                        availableRelease = checkDailyGlowRelease(context)
                                        appUpdateStatus = if (availableRelease == null) "当前已是最新版本，或暂无正式安装包。" else "发现新版本 ${availableRelease?.version}。"
                                    } catch (error: Exception) {
                                        appUpdateStatus = error.message ?: "检查更新失败，请稍后重试。"
                                    }
                                }
                            },
                            onCloudUpload = { date, payload ->
                                if (!cloudSignedIn) showCloudLogin = true
                                else cloudScope.launch {
                                    cloudStatus = "正在同步到云端…"
                                    try {
                                        cloud.uploadLifeSnapshot(date, payload)
                                        cloudStatus = "已同步到云端 · $date"
                                    } catch (e: Exception) {
                                        cloudStatus = e.message ?: "同步失败，请检查网络"
                                    }
                                }
                            }
                        )
                        "growth" -> DailyGlowCloudContent("growth", cloudSessionVersion, onLogin = { showCloudLogin = true })
                        "insights" -> DailyGlowCloudContent("insights", cloudSessionVersion, onLogin = { showCloudLogin = true })
                        else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Header(plan, completed.size, loading, onHistory = {
                            context.startActivity(Intent(context, HistoryActivity::class.java))
                        }, onRefresh = {
                            if (!loading) {
                                loading = true
                                fetchRemotePlan(context) { incoming ->
                                    if (incoming != null) {
                                        savePlan(context, incoming)
                                        plan = incoming
                                    }
                                    else Toast.makeText(context, "未配置或无法连接云端，已保留本地训练", Toast.LENGTH_SHORT).show()
                                    loading = false
                                }
                            }
                        })
                    }
                    item {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            MusicCard(plan.playlist) { song -> openNetEaseSearch(context, song) }
                        }
                    }
                    item {
                        Text(
                            "今日流程",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                    }
                    items(plan.items, key = { it.id }) { item ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            WorkoutCard(
                                item = item,
                                done = item.id in completed,
                                onDone = { checked ->
                                    if (checked) completed.add(item.id) else completed.remove(item.id)
                                    saveCompleted(context, plan.date, completed)
                                    upsertHistory(context, plan, completed)
                                },
                                onTimer = { timerItem = item },
                                onEdit = { editingItem = item },
                                onTutorial = { openTutorial(context, item.tutorialQuery) }
                            )
                        }
                    }
                    item { Box(Modifier.padding(horizontal = 16.dp)) { TipCard() } }
                    }
                    }
                }
                if (currentTab != "life") DailyGlowNavigationBar(currentTab) { currentTab = it }
            }
        }
    }
    timerItem?.let { TimerDialog(it, onDismiss = { timerItem = null }) }
    editingItem?.let { target ->
        EditDialog(target, onDismiss = { editingItem = null }, onSave = { changed ->
            plan = plan.copy(items = plan.items.map { if (it.id == changed.id) changed else it })
            savePlan(context, plan)
            editingItem = null
        })
    }
    importError?.let { message ->
        AlertDialog(onDismissRequest = { importError = null }, title = { Text("导入失败") }, text = { Text(message) }, confirmButton = { Button(onClick = { importError = null }) { Text("知道了") } })
    }
    DailyGlowUpdateDialog(
        release = availableRelease,
        onDismiss = { availableRelease = null },
        onStatus = { appUpdateStatus = it }
    )
    if (showCloudLogin) DailyGlowLoginDialog(
        onDismiss = { showCloudLogin = false },
        onSuccess = {
            cloudSignedIn = true
            cloudSessionVersion += 1
            showCloudLogin = false
            cloudStatus = "已连接 DailyGlow 云端"
        }
    )
}

@Composable
private fun Header(plan: WorkoutPlan, finished: Int, loading: Boolean, onHistory: () -> Unit, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GoldPink)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DAILYGLOW  ·  DAILY MOVEMENT",
                    fontSize = 11.sp,
                    letterSpacing = 1.3.sp,
                    color = Maroon,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    plan.title,
                    fontSize = 27.sp,
                    lineHeight = 32.sp,
                    color = Ink,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .42f)) {
                    IconButton(onClick = onHistory, modifier = Modifier.width(46.dp)) {
                        Icon(Icons.Default.History, "训练历史", tint = Maroon)
                    }
                }
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .42f)) {
                    IconButton(onClick = onRefresh, modifier = Modifier.width(46.dp)) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(22.dp),
                                color = Maroon,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "刷新云端计划", tint = Maroon)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = MistBlue
            ) {
                Text(
                    "${plan.date}  ·  已完成 $finished/${plan.items.size}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Maroon,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = .28f)
        ) {
            Text(
                plan.note,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = Ink,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }
    }
}

private val WorkoutSongs = listOf(
    "Titanium · David Guetta ft. Sia",
    "Don't You Worry Child · Swedish House Mafia",
    "Wake Me Up · Avicii"
)

@Composable
private fun MusicCard(playlist: String, onPlaySong: (String) -> Unit) = Card(
    colors = CardDefaults.cardColors(containerColor = Teal),
    shape = RoundedCornerShape(22.dp)
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color.White.copy(alpha = .42f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Maroon)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("全局训练音乐", fontWeight = FontWeight.Bold, color = Ink, fontSize = 17.sp)
                Text(playlist, color = Ink.copy(alpha = .78f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        WorkoutSongs.forEachIndexed { index, song ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaySong(song) },
                shape = RoundedCornerShape(50.dp),
                color = MistBlue.copy(alpha = .88f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("0${index + 1}", color = Maroon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(song, modifier = Modifier.weight(1f), color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun WorkoutCard(item: WorkoutItem, done: Boolean, onDone: (Boolean) -> Unit, onTimer: () -> Unit, onEdit: () -> Unit, onTutorial: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .75f)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = done, onCheckedChange = onDone)
                Column(Modifier.weight(1f)) {
                    Text(item.phase, color = Maroon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(item.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(item.instruction, color = Ink.copy(alpha = .8f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = GoldPink.copy(alpha = .65f)) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑训练", tint = Maroon) }
                }
            }
            Spacer(Modifier.height(10.dp)); Divider(color = Berry.copy(alpha = .45f)); Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (item.kind == "TIME") "${item.value} 秒 × ${item.sets} 组" else "${item.value} 次 × ${item.sets} 组", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(12.dp), color = MistBlue) {
                    TextButton(onClick = onTutorial) {
                        Icon(Icons.Default.Search, null, tint = Maroon)
                        Text("动作示范", color = Maroon)
                    }
                }
                if (item.kind == "TIME") {
                    Spacer(Modifier.width(7.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MistBlue) {
                        TextButton(onClick = onTimer) {
                            Icon(Icons.Default.PlayArrow, null, tint = Maroon)
                            Text("开始计时", color = Maroon)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerDialog(item: WorkoutItem, onDismiss: () -> Unit) {
    val initialSeconds = item.value
    var seconds by remember { mutableIntStateOf(initialSeconds) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && seconds > 0) {
            delay(1000)
            seconds--
        }
        if (running && seconds == 0) {
            running = false
            finished = true
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 900)
            delay(950)
            tone.release()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    WheelNumber(
                        value = seconds / 60,
                        range = 0..10,
                        label = "分",
                        enabled = !running,
                        onValueChange = { minutes ->
                            seconds = minutes * 60 + seconds % 60
                            finished = false
                        }
                    )
                    Text(":", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Maroon)
                    WheelNumber(
                        value = seconds % 60,
                        range = 0..59,
                        label = "秒",
                        enabled = !running,
                        onValueChange = { secondPart ->
                            seconds = (seconds / 60) * 60 + secondPart
                            finished = false
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (finished || seconds == 0) {
                        seconds = initialSeconds
                        finished = false
                    }
                    running = !running
                },
                colors = ButtonDefaults.buttonColors(containerColor = Maroon)
            ) { Text(if (running) "暂停" else if (finished) "重新开始" else "开始") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    running = false
                    seconds = initialSeconds
                    finished = false
                }) { Text("复位", color = Maroon) }
                TextButton(onClick = onDismiss) { Text("关闭", color = Maroon) }
            }
        }
    )
}

@Composable
private fun WheelNumber(
    value: Int,
    range: IntRange,
    label: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    var dragAccumulator by remember { mutableStateOf(0f) }
    val latestValue by rememberUpdatedState(value)
    val numberModifier = if (enabled) {
        Modifier.pointerInput(range, enabled) {
            detectVerticalDragGestures { _, dragAmount ->
                dragAccumulator -= dragAmount
                val steps = (dragAccumulator / 12f).toInt()
                if (steps != 0) {
                    val nextValue = (latestValue + steps).coerceIn(range)
                    if (nextValue != latestValue) {
                        onValueChange(nextValue)
                        dragAccumulator -= steps * 12f
                    } else {
                        dragAccumulator = 0f
                    }
                }
            }
        }
    } else Modifier

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = numberModifier.padding(horizontal = 8.dp)) {
        Text(((value - 1).coerceIn(range)).toString().padStart(2, '0'), color = Maroon.copy(alpha = .24f), fontSize = 18.sp)
        Surface(shape = RoundedCornerShape(14.dp), color = GoldPink.copy(alpha = .56f)) {
            Text(
                value.toString().padStart(2, '0'),
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 3.dp),
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Maroon
            )
        }
        Text(((value + 1).coerceIn(range)).toString().padStart(2, '0'), color = Maroon.copy(alpha = .24f), fontSize = 18.sp)
        Text(label, color = Ink.copy(alpha = .65f), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDialog(item: WorkoutItem, onDismiss: () -> Unit, onSave: (WorkoutItem) -> Unit) {
    var value by remember { mutableStateOf(item.value.toString()) }; var sets by remember { mutableStateOf(item.sets.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("调整 ${item.name}") }, text = { Column {
        OutlinedTextField(value = value, onValueChange = { value = it.filter(Char::isDigit) }, label = { Text(if (item.kind == "TIME") "每组秒数" else "每组次数") })
        Spacer(Modifier.height(8.dp)); OutlinedTextField(value = sets, onValueChange = { sets = it.filter(Char::isDigit) }, label = { Text("组数") })
    } }, confirmButton = { Button(onClick = { onSave(item.copy(value = value.toIntOrNull()?.coerceAtLeast(1) ?: item.value, sets = sets.toIntOrNull()?.coerceAtLeast(1) ?: item.sets)) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable private fun TipCard() = Card(colors = CardDefaults.cardColors(containerColor = Berry.copy(alpha = .35f)), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Text("今天的小提醒", fontWeight = FontWeight.Bold, color = Ink); Text("动作变形时先停下来休息。核心训练不是憋气，坚持自然呼吸。", color = Ink, fontSize = 13.sp) } }

private fun loadPlan(context: Context): WorkoutPlan = try { parsePlan(context.getSharedPreferences("dailyglow", Context.MODE_PRIVATE).getString("plan", null) ?: context.assets.open("today_plan.json").bufferedReader().use { it.readText() }) } catch (_: Exception) { WorkoutPlan(LocalDate.now().toString(), "今日训练", "请更新训练计划", "运动歌单", emptyList()) }
private fun savePlan(context: Context, plan: WorkoutPlan) { context.getSharedPreferences("dailyglow", Context.MODE_PRIVATE).edit().putString("plan", planToJson(plan)).apply() }
private fun loadCompleted(context: Context, date: String): Set<String> = context.getSharedPreferences("dailyglow", Context.MODE_PRIVATE).getStringSet("done_$date", emptySet()) ?: emptySet()
private fun saveCompleted(context: Context, date: String, items: List<String>) { context.getSharedPreferences("dailyglow", Context.MODE_PRIVATE).edit().putStringSet("done_$date", items.toSet()).apply() }
private fun clearCompleted(context: Context, date: String) { context.getSharedPreferences("dailyglow", Context.MODE_PRIVATE).edit().remove("done_$date").apply() }

private fun upsertHistory(context: Context, plan: WorkoutPlan, completed: Collection<String>) {
    val prefs = context.getSharedPreferences("dailyglow", Context.MODE_PRIVATE)
    val existing = try {
        JSONArray(prefs.getString("history", "[]") ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }
    val updated = JSONArray()
    for (index in 0 until existing.length()) {
        val item = existing.optJSONObject(index) ?: continue
        if (item.optString("date") != plan.date) updated.put(item)
    }
    updated.put(
        JSONObject()
            .put("date", plan.date)
            .put("title", plan.title)
            .put("plan", planToJson(plan))
            .put("completed", JSONArray(completed.toList()))
    )
    prefs.edit().putString("history", updated.toString()).apply()
}
private fun parsePlan(raw: String): WorkoutPlan { val o = JSONObject(raw); val a = o.getJSONArray("items"); return WorkoutPlan(o.optString("date", LocalDate.now().toString()), o.optString("title"), o.optString("note"), o.optString("playlist", "运动歌单"), List(a.length()) { i -> a.getJSONObject(i).let { x -> WorkoutItem(x.getString("id"), x.getString("phase"), x.getString("name"), x.getString("instruction"), x.getString("kind"), x.getInt("value"), x.optInt("sets", 1), x.optString("tutorialQuery", x.getString("name"))) } }) }
private fun extractPlanJson(raw: String): String {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    require(start >= 0 && end > start) { "No JSON object" }
    return raw.substring(start, end + 1)
}
private fun planToJson(p: WorkoutPlan): String = JSONObject().put("date", p.date).put("title", p.title).put("note", p.note).put("playlist", p.playlist).put("items", JSONArray().apply { p.items.forEach { put(JSONObject().put("id", it.id).put("phase", it.phase).put("name", it.name).put("instruction", it.instruction).put("kind", it.kind).put("value", it.value).put("sets", it.sets).put("tutorialQuery", it.tutorialQuery)) } }).toString()
private fun fetchRemotePlan(context: Context, callback: (WorkoutPlan?) -> Unit) {
    val endpoint = BuildConfig.PLAN_ENDPOINT
    if (endpoint.isBlank()) { callback(null); return }
    thread {
        val plan = try {
            val separator = if (endpoint.contains("?")) "&" else "?"
            val connection = URL("$endpoint${separator}ts=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (connection.responseCode in 200..299) parsePlan(connection.inputStream.bufferedReader().use { it.readText() }) else null
        } catch (_: Exception) { null }
        (context as? MainActivity)?.runOnUiThread { callback(plan) }
    }
}
private fun openNetEaseSearch(context: Context, song: String) {
    val encoded = Uri.encode(song)
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("orpheus://search?keyword=$encoded"))
        .setPackage("com.netease.cloudmusic")
    try {
        context.startActivity(appIntent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.163.com/#/search/m/?s=$encoded&type=1")))
        } catch (_: Exception) {
            Toast.makeText(context, "请安装网易云音乐或可用浏览器", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openTutorial(context: Context, query: String) {
    val searchUri = Uri.Builder()
        .scheme("xhsdiscover")
        .authority("search")
        .appendPath("result")
        .appendQueryParameter("keyword", query)
        .appendQueryParameter("target_search", "notes")
        .build()
    val appIntent = Intent(Intent.ACTION_VIEW, searchUri)
        .setPackage("com.xingin.xhs")
    try {
        context.startActivity(appIntent)
    } catch (_: Exception) {
        try {
            val encoded = Uri.encode(query)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.xiaohongshu.com/search_result?keyword=$encoded")))
        } catch (_: Exception) {
            Toast.makeText(context, "请安装小红书或可用浏览器", Toast.LENGTH_SHORT).show()
        }
    }
}
