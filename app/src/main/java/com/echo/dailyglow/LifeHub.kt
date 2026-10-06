package com.echo.dailyglow

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val LifePaper = Color(0xFFFFFAF4)
private val LifeCard = Color(0xFFFFFDF9)
private val LifeBlue = Color(0xFF476A86)
private val LifePeach = Color(0xFFF5D7C7)
private val LifeSage = Color(0xFFE4EBDD)
private val LifeInk = Color(0xFF4D5660)

@Composable
fun DailyGlowNavigationBar(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(LifePaper).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("today" to "今日", "training" to "训练", "life" to "生活").forEach { (key, label) ->
            val active = selected == key
            Text(
                text = (if (active) "● " else "") + label,
                modifier = Modifier.clickable { onSelect(key) }.padding(horizontal = 14.dp, vertical = 8.dp),
                color = if (active) LifeBlue else LifeInk.copy(alpha = .72f),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun TodayLandingScreen(trainingTitle: String, completed: Int, onTraining: () -> Unit, onLife: () -> Unit, context: Context) {
    val prefs = remember(context) { context.getSharedPreferences("dailyglow_life", Context.MODE_PRIVATE) }
    val date = LocalDate.now().toString()
    val water = remember(date) { prefs.getInt("water_$date", 0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LifePaper),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("今日", color = LifeBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(LocalDate.now().toString(), color = LifeInk.copy(alpha = .72f), fontSize = 14.sp)
        }
        item { LifeCardBlock("今日训练", trainingTitle.ifBlank { "今日训练计划" }, "已完成 $completed 项", "开始训练", LifePeach, onTraining) }
        item { LifeCardBlock("生活记录", "喝水 $water / 8 杯 · 日记 · 衣橱 · 流水", "拾光小队与朋友们陪伴共用记录", "打开生活区", LifeSage, onLife) }
        item { Text("记录按日期保存在这台设备上。", color = LifeInk.copy(alpha = .68f), fontSize = 13.sp) }
    }
}

@Composable
private fun LifeCardBlock(title: String, body: String, foot: String, action: String, tint: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = .72f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = LifeBlue, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(body, color = LifeInk, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(foot, color = LifeInk.copy(alpha = .72f), fontSize = 12.sp)
                Text(action + "  ›", color = LifeBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun DailyLifeHub(context: Context) {
    val prefs = remember(context) { context.getSharedPreferences("dailyglow_life", Context.MODE_PRIVATE) }
    val date = LocalDate.now().toString()
    var section by remember { mutableStateOf("home") }
    var companion by remember { mutableStateOf(prefs.getString("companion", "拾光小队") ?: "拾光小队") }
    var water by remember(date) { mutableIntStateOf(prefs.getInt("water_$date", 0)) }
    var diary by remember(date) { mutableStateOf(prefs.getString("diary_$date", "") ?: "") }
    var outfit by remember(date) { mutableStateOf(prefs.getString("outfit_$date", "") ?: "") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var transactions by remember { mutableStateOf(prefs.getString("transactions_$date", "") ?: "") }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LifePaper),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(if (section == "home") "今日生活" else when (section) {
                        "water" -> "今日小确幸 · 喝水"
                        "diary" -> "生活邮局"
                        "wardrobe" -> "衣橱裁缝铺"
                        else -> "每日流水"
                    }, color = LifeBlue, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text(LocalDate.now().toString(), color = LifeInk.copy(alpha = .7f), fontSize = 13.sp)
                }
                if (section != "home") Text("‹ 返回", Modifier.clickable { section = "home" }.padding(8.dp), color = LifeBlue)
            }
        }
        if (section == "home") {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = LifeCard)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("陪伴角色", color = LifeInk, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("拾光小队", "朋友们陪伴").forEach { option ->
                                Button(onClick = {
                                    companion = option
                                    prefs.edit().putString("companion", option).apply()
                                }, colors = ButtonDefaults.buttonColors(containerColor = if (companion == option) LifePeach else LifePaper)) {
                                    Text(option, color = LifeBlue)
                                }
                            }
                        }
                        Text(if (companion == "拾光小队") "小狗陪你开启今天" else "小新和朋友们陪你开启今天", color = LifeBlue)
                    }
                }
            }
            item { LifeCardBlock("生活邮局", "给今天留一句话", "日记按日期保存", "写日记", LifeCard, { section = "diary" }) }
            item { LifeCardBlock("今日小确幸", "喝水 $water / 8 杯 · 三餐与习惯记录", "小小照顾也值得记下", "记一杯水", LifeSage, { section = "water" }) }
            item { LifeCardBlock("衣橱裁缝铺", outfit.ifBlank { "记录今天的穿搭" }, "穿搭日记", "打开衣橱", LifePeach, { section = "wardrobe" }) }
            item { LifeCardBlock("每日流水", "记下今天的一笔收支", "记录只保存在本机", "记一笔", LifeCard, { section = "ledger" }) }
        } else if (section == "water") {
            item { Text("慢慢喝，照顾好自己。", color = LifeInk, fontSize = 15.sp) }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = LifeCard)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💧  $water / 8 杯", color = LifeBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0 until 8).forEach { index -> Text(if (index < water) "🥛" else "◯", fontSize = 22.sp, color = LifeBlue) }
                        }
                        Button(onClick = {
                            if (water < 8) water += 1
                            prefs.edit().putInt("water_$date", water).apply()
                        }, colors = ButtonDefaults.buttonColors(containerColor = LifePeach)) { Text("＋ 记录一杯", color = LifeBlue) }
                        Text("今日已记录约 ${water * 200} ml", color = LifeInk.copy(alpha = .76f))
                    }
                }
            }
        } else if (section == "diary") {
            item { OutlinedTextField(value = diary, onValueChange = { diary = it; saved = false }, modifier = Modifier.fillMaxWidth().height(180.dp), label = { Text("今天有什么想记下？") }) }
            item {
                Button(onClick = { prefs.edit().putString("diary_$date", diary).apply(); saved = true }, colors = ButtonDefaults.buttonColors(containerColor = LifePeach)) {
                    Text(if (saved) "已保存今天的小记" else "保存小记", color = LifeBlue)
                }
            }
        } else if (section == "wardrobe") {
            item { OutlinedTextField(value = outfit, onValueChange = { outfit = it; saved = false }, modifier = Modifier.fillMaxWidth().height(140.dp), label = { Text("今天穿了什么？") }) }
            item {
                Button(onClick = { prefs.edit().putString("outfit_$date", outfit).apply(); saved = true }, colors = ButtonDefaults.buttonColors(containerColor = LifePeach)) {
                    Text(if (saved) "已保存今日穿搭" else "保存今日穿搭", color = LifeBlue)
                }
            }
        } else {
            item { OutlinedTextField(value = amount, onValueChange = { amount = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("金额（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
            item { OutlinedTextField(value = note, onValueChange = { note = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("消费说明，例如：早餐") }) }
            item {
                Button(onClick = {
                    val value = amount.toDoubleOrNull()
                    if (value != null && value > 0) {
                        transactions = listOfNotNull("$note  -¥${"%.2f".format(value)}", transactions.takeIf { it.isNotBlank() }).joinToString("\n")
                        prefs.edit().putString("transactions_$date", transactions).apply()
                        amount = ""; note = ""; saved = true
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = LifePeach)) { Text("保存流水", color = LifeBlue) }
            }
            if (transactions.isNotBlank()) item { Text(transactions, color = LifeInk, lineHeight = 26.sp) }
            item { Text("截图识别与云端同步会在后续阶段接入；当前流水保存在本机。", color = LifeInk.copy(alpha = .68f), fontSize = 12.sp) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
