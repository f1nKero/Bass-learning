package com.f1nkero.bassmemo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private val strings = listOf("G" to 7, "D" to 2, "A" to 9, "E" to 4) // high to low on screen
private data class Pos(val string: Int, val fret: Int) {
    val note: String get() = names[(strings[string].second + fret) % 12]
}
private fun pool(level: Int): List<Pos> {
    val maxFret = listOf(0, 3, 5, 12)[level]
    return strings.indices.flatMap { s -> (0..maxFret).map { Pos(s, it) } }
}
private fun nextQuestion(level: Int, misses: Map<String, Int>, previous: Pos? = null): Pos {
    val choices = pool(level).filter { it != previous }.ifEmpty { pool(level) }
    val weighted = choices.flatMap { p -> List(1 + (misses["${p.string}:${p.fret}"] ?: 0).coerceAtMost(4)) { p } }
    return weighted.random()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bassmemo", Context.MODE_PRIVATE)
        setContent {
            var tab by remember { mutableIntStateOf(0) }
            var level by remember { mutableIntStateOf(prefs.getInt("level", 0).coerceIn(0, 3)) }
            var correct by remember { mutableIntStateOf(prefs.getInt("correct", 0)) }
            var total by remember { mutableIntStateOf(prefs.getInt("total", 0)) }
            var streak by remember { mutableIntStateOf(0) }
            val misses = remember { mutableStateMapOf<String, Int>().apply {
                prefs.all.forEach { (k, v) -> if (k.startsWith("miss_") && v is Int) put(k.removePrefix("miss_"), v) }
            } }
            var selected by remember { mutableStateOf<Pos?>(null) }
            var revealAll by remember { mutableStateOf(false) }
            var question by remember { mutableStateOf(nextQuestion(level, misses)) }
            var feedback by remember { mutableStateOf("") }
            var answered by remember { mutableStateOf(false) }
            val accent = Color(0xFF4B77B8)
            MaterialTheme(colorScheme = lightColorScheme(primary = accent)) {
                Column(Modifier.fillMaxSize().background(Color(0xFFF6F8FC)).padding(16.dp)) {
                    Text("BassMemo  🎸", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("每天十分钟，记住每一格的音名", color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("指板", "识音", "找音", "记录").forEachIndexed { index, name ->
                            FilterChip(selected = tab == index, onClick = { tab = index; feedback = ""; answered = false }, label = { Text(name) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("练习范围", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("空弦", "0–3品", "0–5品", "0–12品").forEachIndexed { index, name ->
                            FilterChip(selected = level == index, onClick = {
                                level = index; prefs.edit().putInt("level", level).apply()
                                question = nextQuestion(level, misses); feedback = ""; answered = false; selected = null
                            }, label = { Text(name) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (tab) {
                        0 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("点击位置查看音名", fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { revealAll = !revealAll }) {
                                    Text(if (revealAll) "隐藏音名" else "显示全部")
                                }
                            }
                            Text("先记住空弦：E（4弦）· A（3弦）· D（2弦）· G（1弦）",
                                color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Fretboard(12, selected, { selected = it }, showNotes = revealAll)
                            Spacer(Modifier.height(16.dp))
                            Text(selected?.let { "${strings[it.string].first} 弦 · 第 ${it.fret} 品 = ${it.note}" } ?: "先点一个位置试试", style = MaterialTheme.typography.titleLarge)
                            Text("小技巧：第 12 品与空弦同名，相差一个八度。", color = Color.Gray)
                        }
                        1 -> {
                            Text("这个位置是什么音？", style = MaterialTheme.typography.titleLarge)
                            Text("${strings[question.string].first} 弦 · 第 ${question.fret} 品", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Fretboard(listOf(0, 3, 5, 12)[level], question, {}, false, enabled = false)
                            Spacer(Modifier.height(12.dp))
                            val answerChoices = remember(question, level) {
                                val learned = pool(level).map { it.note }.distinct()
                                    .filter { it != question.note }.shuffled()
                                val remaining = names.filter { it != question.note }.shuffled()
                                ((learned + remaining).distinct().take(3) + question.note).shuffled()
                            }
                            LazyColumn {
                                items(answerChoices.chunked(2)) { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        row.forEach { note ->
                                            OutlinedButton(onClick = {
                                                if (!answered) {
                                                    val ok = note == question.note
                                                    correct += if (ok) 1 else 0; total++; streak = if (ok) streak + 1 else 0
                                                    if (!ok) { val key = "${question.string}:${question.fret}"; misses[key] = (misses[key] ?: 0) + 1; prefs.edit().putInt("miss_$key", misses[key]!!).apply() }
                                                    prefs.edit().putInt("correct", correct).putInt("total", total).apply()
                                                    feedback = if (ok) "答对啦！" else "正确答案：${question.note}"
                                                    answered = true
                                                }
                                            }, modifier = Modifier.weight(1f)) { Text(note) }
                                        }
                                    }
                                }
                            }
                            Text(feedback, fontWeight = FontWeight.Bold, color = accent)
                            if (answered) Button(onClick = { question = nextQuestion(level, misses, question); feedback = ""; answered = false }) { Text("下一题") }
                        }
                        2 -> {
                            Text("在指板上找到 ${question.note}", style = MaterialTheme.typography.titleLarge)
                            Text("点击指板上任意一个正确位置", color = Color.Gray)
                            Spacer(Modifier.height(12.dp))
                            Fretboard(listOf(0, 3, 5, 12)[level], selected, { pos ->
                                if (!answered) {
                                    selected = pos
                                    val ok = pos.note == question.note
                                    correct += if (ok) 1 else 0; total++; streak = if (ok) streak + 1 else 0
                                    if (!ok) { val key = "${question.string}:${question.fret}"; misses[key] = (misses[key] ?: 0) + 1; prefs.edit().putInt("miss_$key", misses[key]!!).apply() }
                                    prefs.edit().putInt("correct", correct).putInt("total", total).apply()
                                    feedback = if (ok) "答对啦！" else "这是 ${pos.note}，再记住它的位置"
                                    answered = true
                                }
                            }, false)
                            Spacer(Modifier.height(12.dp))
                            Text(feedback, color = accent, fontWeight = FontWeight.Bold)
                            if (answered) Button(onClick = { question = nextQuestion(level, misses, question); selected = null; feedback = ""; answered = false }) { Text("下一题") }
                        }
                        else -> {
                            Text("我的学习记录", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(12.dp))
                            Text("已答题：$total")
                            Text("正确题数：$correct")
                            Text("正确率：${if (total == 0) 0 else correct * 100 / total}%")
                            Text("本次连续答对：$streak")
                            Text("累计错题位置：${misses.size}")
                            Spacer(Modifier.height(12.dp))
                            Text("错题会在识音测验中获得更高抽取权重。", color = Color.Gray)
                            OutlinedButton(onClick = {
                                prefs.edit().clear().apply(); correct = 0; total = 0; streak = 0; misses.clear()
                                question = nextQuestion(level, misses); feedback = ""; answered = false
                            }) { Text("清空学习记录") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fretboard(maxFret: Int, highlighted: Pos?, onTap: (Pos) -> Unit, showNotes: Boolean, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row {
            Box(Modifier.width(44.dp).height(25.dp), contentAlignment = Alignment.Center) { Text("弦") }
            (0..maxFret).forEach { fret ->
                Box(Modifier.width(54.dp).height(25.dp), contentAlignment = Alignment.Center) { Text("$fret", color = Color.Gray) }
            }
        }
        strings.forEachIndexed { s, string ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(44.dp).height(54.dp), contentAlignment = Alignment.Center) { Text(string.first, fontWeight = FontWeight.Bold) }
                (0..maxFret).forEach { fret ->
                    val pos = Pos(s, fret)
                    val isSelected = highlighted == pos
                    Box(
                        Modifier.width(54.dp).height(54.dp).padding(2.dp)
                            .background(if (isSelected) Color(0xFF8BB9F1) else Color(0xFFE5EAF0), RoundedCornerShape(8.dp))
                            .then(if (enabled) Modifier.clickable { onTap(pos) } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (showNotes) pos.note else if (isSelected) "●" else "•",
                            fontWeight = FontWeight.Bold, color = if (isSelected) Color(0xFF153B71) else Color(0xFF8290A0))
                    }
                }
            }
        }
    }
}
