package com.f1nkero.bassmemo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

private val notes = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
private val tuning = listOf("G" to 7, "D" to 2, "A" to 9, "E" to 4)
private val anchors = setOf(0,3,5,7,9,12)
private data class Spot(val string: Int, val fret: Int) {
    val note: String get() = notes[(tuning[string].second + fret) % 12]
    val key: String get() = "$string:$fret"
}
private fun allSpots(max: Int) = (0..3).flatMap { s -> (0..max).map { Spot(s,it) } }
private fun candidatePool(stage: Int): List<Spot> {
    val max = listOf(0,5,12,12)[stage]
    return allSpots(max).filter { p ->
        when(stage) {
            0 -> p.fret == 0
            1 -> p.string >= 2 && !p.note.contains("#")
            2 -> !p.note.contains("#")
            else -> true
        }
    }
}
private fun nextSpot(stage: Int, prefs: android.content.SharedPreferences, old: Spot? = null): Spot {
    val now = System.currentTimeMillis()
    val options = candidatePool(stage).filter { it != old }.ifEmpty { candidatePool(stage) }
    val due = options.filter { prefs.getLong("due_${it.key}", 0L) <= now }
    val base = due.ifEmpty { options }
    val weights = base.flatMap { spot ->
        val errors = prefs.getInt("err_${spot.key}", 0).coerceAtMost(4)
        List(1 + errors) { spot }
    }
    return weights.random()
}
private fun record(prefs: android.content.SharedPreferences, spot: Spot, remembered: Boolean) {
    val key = spot.key
    val level = prefs.getInt("box_$key", 0)
    val next = if (remembered) (level + 1).coerceAtMost(5) else 0
    val delays = longArrayOf(0,1,3,7,14,30)
    val due = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(delays[next])
    prefs.edit().putInt("box_$key", next).putLong("due_$key", due)
        .putInt("err_$key", if (remembered) prefs.getInt("err_$key",0) else prefs.getInt("err_$key",0)+1)
        .putInt("total",prefs.getInt("total",0)+1)
        .putInt("correct",prefs.getInt("correct",0)+(if(remembered)1 else 0)).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bassmemo_v2",Context.MODE_PRIVATE)
        setContent {
            var tab by remember { mutableIntStateOf(0) }
            var stage by remember { mutableIntStateOf(prefs.getInt("stage",0).coerceIn(0,3)) }
            var spot by remember { mutableStateOf(nextSpot(stage,prefs)) }
            var selected by remember { mutableStateOf<Spot?>(null) }
            var showMap by remember { mutableStateOf(false) }
            var revealed by remember { mutableStateOf(false) }
            var feedback by remember { mutableStateOf("") }
            var tick by remember { mutableIntStateOf(0) }
            var target by remember { mutableStateOf("C") }
            var found by remember { mutableStateOf(setOf<Spot>()) }
            var checked by remember { mutableStateOf(false) }
            val max = listOf(0,5,12,12)[stage]
            fun newQuestion() {
                spot = nextSpot(stage,prefs,spot)
                revealed = false; feedback = ""; selected = null; tick++
            }
            fun nextTarget() {
                target = notes.filter { it != target }.random()
                found = emptySet(); checked = false; feedback = ""
            }
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3366A5))) {
                Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FB)).padding(horizontal=12.dp, vertical=6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("BassMemo 🎸",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                        listOf("指板地图","主动回忆","找遍同音","学习记录").forEachIndexed { i,t ->
                            FilterChip(selected=tab==i,onClick={tab=i;feedback="";revealed=false},label={Text(t)})
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("学习阶段",style=MaterialTheme.typography.labelLarge)
                        listOf("①空弦","②E/A自然音","③四弦自然音","④全部半音").forEachIndexed { i,t ->
                            FilterChip(selected=stage==i,onClick={
                                stage=i;prefs.edit().putInt("stage",i).apply();newQuestion()
                                found=emptySet();checked=false
                            },label={Text(t)})
                        }
                        Text("横屏 · 0–12品",color=Color.Gray,style=MaterialTheme.typography.labelSmall)
                    }
                    when(tab) {
                        0 -> {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("先记锚点，再隐藏音名主动回忆。",fontWeight=FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick={showMap=!showMap}) { Text(if(showMap)"隐藏音名" else "显示音名") }
                            }
                            Fretboard(12, selected, { selected=it }, showMap, anchorsOnly=!showMap)
                            Text(selected?.let { "${tuning[it.string].first}弦 第${it.fret}品 = ${it.note}" }
                                ?: "锚点：空弦 E A D G；第5品与下一根较细弦的空弦同名；第12品重复空弦",
                                style=MaterialTheme.typography.bodyLarge)
                            Text("八度形状：从 E/A 弦向较细方向跨两根弦、向高品位移动两品。先用规律定位，再练瞬间认音。",
                                color=Color.Gray,style=MaterialTheme.typography.bodySmall)
                        }
                        1 -> {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("闭眼想一想：${tuning[spot.string].first}弦 第${spot.fret}品 是什么音？",
                                    style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                if(!revealed) Button(onClick={revealed=true}) { Text("我想好了 · 揭晓") }
                            }
                            Fretboard(max,spot,{},false,enabled=false)
                            if(revealed) {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Text("答案：${spot.note}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                    Button(onClick={record(prefs,spot,true);newQuestion()}) { Text("我独立想起来了") }
                                    OutlinedButton(onClick={record(prefs,spot,false);newQuestion()}) { Text("没想起来 / 靠推算") }
                                }
                                Text("只有无需看提示、直接想起音名才选“独立想起来了”。",color=Color.Gray)
                            } else {
                                Text("请先说出音名，再揭晓；不要通过选项猜答案。",color=Color.Gray)
                            }
                        }
                        2 -> {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("找出 0–12 品所有的 $target",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                Text("已选择 ${found.size} 个",color=Color.Gray)
                                Spacer(Modifier.weight(1f))
                                if(!checked) Button(onClick={
                                    checked=true
                                    val answers=allSpots(12).filter { it.note==target }.toSet()
                                    feedback="正确 ${found.intersect(answers).size}/${answers.size}；误选 ${found.subtract(answers).size} 个"
                                }) { Text("检查答案") }
                                else Button(onClick={nextTarget()}) { Text("下一个音") }
                                TextButton(onClick={found=emptySet();checked=false;feedback=""}) { Text("重试") }
                            }
                            Fretboard(12,null,{p->if(!checked)found=if(p in found)found-p else found+p},false,
                                selectedSet=found,correctSet=if(checked)allSpots(12).filter{it.note==target}.toSet() else emptySet())
                            Text(if(checked)feedback else "跨弦找全相同音名的位置；点亮所有位置后再检查。",color=Color.Gray)
                        }
                        else -> {
                            val total=prefs.getInt("total",0)
                            val correct=prefs.getInt("correct",0)
                            val due=candidatePool(stage).count { prefs.getLong("due_${it.key}",0L)<=System.currentTimeMillis() }
                            val mastered=candidatePool(stage).count { prefs.getInt("box_${it.key}",0)>=3 }
                            Text("累计主动回忆：$total 次 · 独立回忆：$correct 次 · 正确率：${if(total==0)0 else correct*100/total}%",
                                style=MaterialTheme.typography.titleMedium)
                            Text("当前阶段：${candidatePool(stage).size} 个位置 · 待复习：$due · 达到7天复习级别：$mastered")
                            Text("复习间隔：答对后约1、3、7、14、30天；答错重置。题目优先抽取到期位置与错题。",
                                color=Color.Gray)
                            Text("每天建议：2分钟锚点复习 + 5分钟主动回忆 + 3分钟跨弦找音。",fontWeight=FontWeight.Medium)
                            OutlinedButton(onClick={
                                prefs.edit().clear().apply();stage=0;newQuestion();tick++
                            }) { Text("重置全部学习记录") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fretboard(max: Int, highlighted: Spot?, onTap: (Spot)->Unit,
    showNotes: Boolean, enabled: Boolean=true, anchorsOnly: Boolean=false,
    selectedSet: Set<Spot> = emptySet(), correctSet: Set<Spot> = emptySet()) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=270.dp)) {
        val cellWidth = ((maxWidth - 44.dp) / (max+1)).coerceAtLeast(34.dp)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Box(Modifier.width(44.dp).height(24.dp),contentAlignment=Alignment.Center) { Text("弦") }
                (0..max).forEach { fret ->
                    Box(Modifier.width(cellWidth).height(24.dp),contentAlignment=Alignment.Center) {
                        Text("$fret",color=if(fret in anchors)Color(0xFF245F9C) else Color.Gray,
                            fontWeight=if(fret in anchors)FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            tuning.forEachIndexed { s,string ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.width(44.dp).height(48.dp),contentAlignment=Alignment.Center) {
                        Text(string.first,fontWeight=FontWeight.Bold)
                    }
                    (0..max).forEach { fret ->
                        val p=Spot(s,fret)
                        val active=p==highlighted || p in selectedSet
                        val correct=p in correctSet
                        Box(Modifier.width(cellWidth).height(48.dp).padding(2.dp)
                            .background(when {
                                correct -> Color(0xFFB5E1C1)
                                active -> Color(0xFF9EC6F2)
                                fret in anchors && anchorsOnly -> Color(0xFFE1E9F5)
                                else -> Color(0xFFEBEDF0)
                            },RoundedCornerShape(5.dp))
                            .then(if(enabled)Modifier.clickable{onTap(p)}else Modifier),
                            contentAlignment=Alignment.Center) {
                            Text(when {
                                showNotes -> p.note
                                active || correct -> "●"
                                fret in anchors -> "•"
                                else -> "·"
                            },fontWeight=FontWeight.Bold,color=Color(0xFF244C79))
                        }
                    }
                }
            }
        }
    }
}
