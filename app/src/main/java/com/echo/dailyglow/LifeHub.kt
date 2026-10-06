package com.echo.dailyglow

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
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
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val date = selectedDate.toString()
    val isToday = selectedDate == LocalDate.now()
    var section by remember { mutableStateOf("home") }
    var companion by remember { mutableStateOf(prefs.getString("companion", "拾光小队") ?: "拾光小队") }
    var water by remember(date) { mutableIntStateOf(prefs.getInt("water_$date", 0)) }
    var diary by remember(date) { mutableStateOf(prefs.getString("diary_$date", "") ?: "") }
    var outfit by remember(date) { mutableStateOf(prefs.getString("outfit_$date", "") ?: "") }
    var closet by remember { mutableStateOf(prefs.getString("closet_items", "") ?: "") }
    var expenseSource by remember { mutableStateOf("支付宝") }
    var expenseCategory by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var transactions by remember { mutableStateOf(prefs.getString("transactions_$date", "") ?: "") }
    var saved by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf("") }
    var ocrStatus by remember { mutableStateOf("") }
    val receiptPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ocrText = ""
        ocrStatus = "正在本机识别截图…"
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    ocrText = result.text
                    val candidates = ocrAmountCandidates(result.text)
                    if (candidates.size == 1) {
                        amount = String.format(Locale.US, "%.2f", candidates.single())
                        ocrStatus = "识别完成，请核对金额、账户和分类后再保存。"
                    } else {
                        ocrStatus = if (result.text.isBlank()) "没有识别出文字，请换一张清晰截图。" else "已提取截图文字；金额不唯一，请核对后手动填写。"
                    }
                    recognizer.close()
                }
                .addOnFailureListener {
                    ocrStatus = "截图识别失败，请手动填写流水。"
                    recognizer.close()
                }
        } catch (_: Exception) {
            ocrStatus = "无法读取这张截图，请重新选择或手动填写。"
        }
    }

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
                    Text(todayLabel(selectedDate), color = LifeInk.copy(alpha = .7f), fontSize = 13.sp)
                }
                if (section != "home") Text("‹ 返回", Modifier.clickable { section = "home" }.padding(8.dp), color = LifeBlue)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("‹ 前一天", Modifier.clickable { selectedDate = selectedDate.minusDays(1) }.padding(vertical = 8.dp), color = LifeBlue)
                Text(if (isToday) "今天" else "查看记录", color = LifeInk.copy(alpha = .72f), fontSize = 13.sp)
                Text("后一天 ›", Modifier.clickable(enabled = !isToday) { selectedDate = selectedDate.plusDays(1) }.padding(vertical = 8.dp), color = if (isToday) LifeInk.copy(alpha = .35f) else LifeBlue)
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
            item { LifeCardBlock("今日小确幸", "喝水 $water 杯 · 三餐与习惯记录", "按日期查看与补记", "打开记录", LifeSage, { section = "water" }) }
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
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = LifeCard)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("三餐记录", color = LifeBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        listOf("早餐", "午餐", "晚餐").forEach { meal ->
                            var mealText by remember(date, meal) { mutableStateOf(prefs.getString("meal_${meal}_$date", "") ?: "") }
                            OutlinedTextField(value = mealText, onValueChange = { mealText = it }, modifier = Modifier.fillMaxWidth(), label = { Text(meal) }, singleLine = true)
                            Button(onClick = { prefs.edit().putString("meal_${meal}_$date", mealText).apply(); saved = true }, colors = ButtonDefaults.buttonColors(containerColor = LifePeach)) { Text("保存$meal", color = LifeBlue) }
                        }
                        Text("生活习惯", color = LifeBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        listOf("今天活动过", "按时休息").forEachIndexed { index, label ->
                            val key = if (index == 0) "habit_move_$date" else "habit_sleep_$date"
                            var checked by remember(date, key) { mutableStateOf(prefs.getBoolean(key, false)) }
                            Text((if (checked) "☑ " else "□ ") + label, modifier = Modifier.clickable { checked = !checked; prefs.edit().putBoolean(key, checked).apply() }.padding(vertical = 6.dp), color = LifeInk, fontSize = 15.sp)
                        }
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
            item { OutlinedTextField(value = closet, onValueChange = { closet = it; saved = false }, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("衣橱单品，每行一件") }) }
            item { Button(onClick = { prefs.edit().putString("closet_items", closet).apply(); saved = true }, colors = ButtonDefaults.buttonColors(containerColor = LifeSage)) { Text(if (saved) "已保存衣物清单" else "保存衣物清单", color = LifeBlue) } }
            item { Text("照片管理与 AI 穿搭建议将在后续阶段接入。", color = LifeInk.copy(alpha = .68f), fontSize = 12.sp) }
        } else {
            item {
                Button(onClick = { receiptPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }, colors = ButtonDefaults.buttonColors(containerColor = LifeSage)) {
                    Text("从截图识别流水", color = LifeBlue)
                }
            }
            if (ocrStatus.isNotBlank()) item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LifeSage.copy(alpha = .55f))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ocrStatus, color = LifeBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (ocrText.isNotBlank()) Text(ocrText.take(1000), color = LifeInk, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("截图只在本机识别，不会保存原图。", color = LifeInk.copy(alpha = .7f), fontSize = 11.sp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("农行", "支付宝", "微信").forEach { source ->
                        Button(onClick = { expenseSource = source }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp), colors = ButtonDefaults.buttonColors(containerColor = if (expenseSource == source) LifePeach else LifeSage)) { Text(source, color = LifeBlue, fontSize = 12.sp) }
                    }
                }
            }
            item { OutlinedTextField(value = amount, onValueChange = { amount = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("金额（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
            item { OutlinedTextField(value = expenseCategory, onValueChange = { expenseCategory = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("消费分类，例如：餐饮、交通") }) }
            item { OutlinedTextField(value = note, onValueChange = { note = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("备注（可选）") }) }
            item {
                Button(onClick = {
                    val value = amount.toDoubleOrNull()
                    if (value != null && value > 0) {
                        val newTransactions = listOfNotNull("$expenseSource · ${expenseCategory.ifBlank { "未分类" }} · $note  -¥${"%.2f".format(value)}", transactions.takeIf { it.isNotBlank() }).joinToString("\n")
                        transactions = newTransactions
                        prefs.edit().putString("transactions_$date", newTransactions).apply()
                        amount = ""; note = ""; expenseCategory = ""; saved = true
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = LifePeach)) { Text("保存流水", color = LifeBlue) }
            }
            if (transactions.isNotBlank()) item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = LifeCard)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("今日流水", color = LifeBlue, fontWeight = FontWeight.Bold)
                        Text(transactions, color = LifeInk, lineHeight = 24.sp)
                    }
                }
            }
            item { Text("识别结果需手动核对并确认保存；流水记录保存在本机。", color = LifeInk.copy(alpha = .68f), fontSize = 12.sp) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}


private fun todayLabel(date: LocalDate): String = "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

private fun ocrAmountCandidates(raw: String): List<Double> {
    val candidates = mutableListOf<Double>()
    raw.lineSequence().forEach { line ->
        val hasCurrencyMarker = line.contains("¥") || line.contains("￥") || line.contains("元")
        if (hasCurrencyMarker) {
            val tokens = line.replace("¥", " ").replace("￥", " ").replace("元", " ")
                .split(Regex("[^0-9.,+-]+"))
            tokens.mapNotNull { it.replace(",", ".").toDoubleOrNull() }
                .filter { it > 0.0 }
                .forEach { candidates.add(it) }
        }
    }
    return candidates.distinct()
}
