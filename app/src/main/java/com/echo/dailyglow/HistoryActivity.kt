package com.echo.dailyglow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val HistoryBackground = Color(0xFFF1E4D9)
private val HistoryBlush = Color(0xFFF7D7CD)
private val HistoryBerry = Color(0xFF984343)
private val HistoryInk = Color(0xFF372A2A)
private val HistoryMist = Color(0xFFD8EEF0)

private data class HistoryRecord(
    val date: String,
    val title: String,
    val planJson: String,
    val completedIds: Set<String>,
    val items: List<HistoryItem>
)

private data class HistoryItem(val id: String, val phase: String, val name: String)

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HistoryScreen(onBack = { finish() }) }
    }
}

@Composable
private fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val records = remember { loadHistory(context) }
    var selected by remember { mutableStateOf<HistoryRecord?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = HistoryBackground) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HistoryBlush)
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = HistoryBerry) }
                    Column {
                        Text("训练历史", color = HistoryInk, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("查看完整记录，或复用某天训练", color = HistoryBerry, fontSize = 12.sp)
                    }
                }
                if (records.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("完成或导入一次训练后，记录会保存在这里。", color = HistoryInk.copy(alpha = .7f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(records, key = { it.date }) { record ->
                            HistoryCard(record, onClick = { selected = record })
                        }
                    }
                }
            }
        }
    }
    selected?.let { record ->
        HistoryDetailDialog(
            record = record,
            onDismiss = { selected = null },
            onUseToday = {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_HISTORY_PLAN, record.planJson)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        )
    }
}

@Composable
private fun HistoryCard(record: HistoryRecord, onClick: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .8f))
) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = HistoryMist) {
                Text(record.date, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = HistoryBerry, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text("${record.completedIds.size}/${record.items.size} 已完成", color = HistoryBerry, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text(record.title, color = HistoryInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text("查看当天完整训练  →", color = HistoryBerry, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryDetailDialog(record: HistoryRecord, onDismiss: () -> Unit, onUseToday: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(record.title, color = HistoryInk, fontWeight = FontWeight.Bold)
                Text(record.date, color = HistoryBerry, fontSize = 13.sp)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.height(270.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(record.items, key = { it.id }) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = if (item.id in record.completedIds) HistoryBerry else HistoryInk.copy(alpha = .22f)
                        )
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(item.phase, color = HistoryBerry, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.name, color = HistoryInk, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Divider(color = HistoryBlush.copy(alpha = .65f))
                }
            }
        },
        confirmButton = {
            Button(onClick = onUseToday, colors = ButtonDefaults.buttonColors(containerColor = HistoryBerry)) {
                Icon(Icons.Default.PlayArrow, null)
                Text("导入为今日训练")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭", color = HistoryBerry) } }
    )
}

private fun loadHistory(context: Context): List<HistoryRecord> {
    val raw = context.getSharedPreferences("dailyglow", Context.MODE_PRIVATE).getString("history", "[]") ?: "[]"
    val array = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    return buildList {
        for (index in 0 until array.length()) {
            val record = array.optJSONObject(index) ?: continue
            val planJson = record.optString("plan")
            val plan = try { JSONObject(planJson) } catch (_: Exception) { continue }
            val itemArray = plan.optJSONArray("items") ?: JSONArray()
            val completedArray = record.optJSONArray("completed") ?: JSONArray()
            val completed = buildSet {
                for (i in 0 until completedArray.length()) add(completedArray.optString(i))
            }
            val historyItems = buildList {
                for (i in 0 until itemArray.length()) {
                    val item = itemArray.optJSONObject(i) ?: continue
                    add(HistoryItem(item.optString("id"), item.optString("phase"), item.optString("name")))
                }
            }
            add(HistoryRecord(record.optString("date"), record.optString("title"), planJson, completed, historyItems))
        }
    }.sortedByDescending { it.date }
}
