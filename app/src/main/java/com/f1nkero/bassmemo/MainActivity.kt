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
import androidx.compose.foundation.verticalScroll
import java.util.concurrent.TimeUnit

private val notes = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
private val tuning = listOf("G" to 7, "D" to 2, "A" to 9, "E" to 4)
private val anchors = setOf(0,3,5,7,9,12,15,17,19,21,24)
private val stageMax = listOf(0,5,12,12,24)
private val naturalNotes = listOf("C","D","E","F","G","A","B")
private val fourths = listOf("C","F","A#","D#","G#","C#","F#","B","E","A","D","G")
private data class Spot(val string: Int, val fret: Int) {
    val note: String get() = notes[(tuning[string].second + fret) % 12]
    val key: String get() = "$string:$fret"
}
private fun allSpots(max: Int) = (0..3).flatMap { s -> (0..max).map { Spot(s,it) } }
private fun candidatePool(stage: Int): List<Spot> {
    val max = stageMax[stage]
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
            var stage by remember { mutableIntStateOf(prefs.getInt("stage",0).coerceIn(0,4)) }
            var spot by remember { mutableStateOf(nextSpot(stage,prefs)) }
            var selected by remember { mutableStateOf<Spot?>(null) }
            var showMap by remember { mutableStateOf(false) }
            var revealed by remember { mutableStateOf(false) }
            var feedback by remember { mutableStateOf("") }
            var tick by remember { mutableIntStateOf(0) }
            var target by remember { mutableStateOf("C") }
            var found by remember { mutableStateOf(setOf<Spot>()) }
            var checked by remember { mutableStateOf(false) }
            var testAnswered by remember { mutableStateOf(false) }
            var practiceString by remember { mutableIntStateOf(3) }
            var practiceIndex by remember { mutableIntStateOf(0) }
            var practiceReveal by remember { mutableStateOf(false) }
            var speedStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var quickAnswer by remember { mutableStateOf(false) }
            val max = stageMax[stage]
            val findMax = if (stage == 4) 24 else 12
            fun newQuestion() {
                spot = nextSpot(stage,prefs,spot)
                revealed = false; feedback = ""; selected = null; testAnswered = false; quickAnswer=false; speedStart=System.currentTimeMillis(); tick++
            }
            fun nextTarget() {
                target = (if(stage <= 2) naturalNotes else notes).filter { it != target }.random()
                found = emptySet(); checked = false; feedback = ""
            }
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3366A5))) {
                Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FB)).verticalScroll(rememberScrollState()).padding(horizontal=12.dp, vertical=6.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("BassMemo 🎸",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                        listOf("地图","回忆","实测","找全","跟弹","记录").forEachIndexed { i,t ->
                            FilterChip(selected=tab==i,onClick={tab=i;feedback="";revealed=false},label={Text(t)})
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("阶段",style=MaterialTheme.typography.labelLarge)
                        listOf("①空弦","②E/A自然音","③四弦自然音","④0–12全部","⑤0–24全板").forEachIndexed { i,t ->
                            FilterChip(selected=stage==i,onClick={
                                stage=i;prefs.edit().putInt("stage",i).apply();newQuestion()
                                found=emptySet();checked=false
                                target=(if(i <= 2)naturalNotes else notes).random()
                            },label={Text(t)})
                        }
                        Text("横屏 · 左右滑动",color=Color.Gray,style=MaterialTheme.typography.labelSmall)
                    }
                    when(tab) {
                        0 -> {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("先记锚点，再隐藏音名主动回忆。",fontWeight=FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick={showMap=!showMap}) { Text(if(showMap)"隐藏音名" else "显示音名") }
                            }
                            Fretboard(maxOf(12,max), selected, { selected=it }, showMap, anchorsOnly=!showMap)
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
                                if(!revealed) Button(onClick={quickAnswer=System.currentTimeMillis()-speedStart<=3000;revealed=true}) { Text("我想好了 · 揭晓") }
                            }
                            Fretboard(max,spot,{},false,enabled=false)
                            if(revealed) {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Text("答案：${spot.note}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                    Button(onClick={record(prefs,spot,true);newQuestion()}) { Text("直接想起") }
                                    OutlinedButton(onClick={record(prefs,spot,false);newQuestion()}) { Text("靠推算 / 没想起") }
                                }
                                Text(if(quickAnswer) "3秒内揭晓：如果是直接想起，可以选「直接想起」。" else "超过3秒：如果依靠逐品推算，请选「靠推算」。",color=Color.Gray)
                            } else {
                                Text("请先说出音名，再揭晓；不要通过选项猜答案。",color=Color.Gray)
                            }
                        }
                        2 -> {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("找出 0–${findMax} 品所有的 $target",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                Text("已选择 ${found.size} 个",color=Color.Gray)
                                Spacer(Modifier.weight(1f))
                                if(!checked) Button(onClick={
                                    checked=true
                                    val answers=allSpots(findMax).filter { it.note==target }.toSet()
                                    feedback="正确 ${found.intersect(answers).size}/${answers.size}；误选 ${found.subtract(answers).size} 个"
                                }) { Text("检查答案") }
                                else Button(onClick={nextTarget()}) { Text("下一个音") }
                                TextButton(onClick={found=emptySet();checked=false;feedback=""}) { Text("重试") }
                            }
                            Fretboard(findMax,null,{p->if(!checked)found=if(p in found)found-p else found+p},false,
                                selectedSet=found,correctSet=if(checked)allSpots(findMax).filter{it.note==target}.toSet() else emptySet())
                            Text(if(checked)feedback else "跨弦找全相同音名的位置；点亮所有位置后再检查。",color=Color.Gray)
                        }
                        3 -> {
                            Text("给出音名，尽可能在整块指板找到全部位置；不依靠数品位。",style=MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("目标音：$target",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                Text("已选 ${found.size}")
                                Button(onClick={
                                    checked=true
                                    val answers=allSpots(findMax).filter { it.note==target }.toSet()
                                    feedback="找到 ${found.intersect(answers).size}/${answers.size}，误选 ${found.subtract(answers).size}"
                                }) { Text("检查") }
                                OutlinedButton(onClick={nextTarget()}) { Text("换一个音") }
                            }
                            Fretboard(findMax,null,{p->if(!checked)found=if(p in found)found-p else found+p},false,
                                selectedSet=found,correctSet=if(checked)allSpots(findMax).filter{it.note==target}.toSet() else emptySet())
                            Text(if(checked)feedback else "先找全，再检查；绿色位置为正确答案。")
                        }
                        4 -> {
                            Text("跟弹训练：沿一根弦逐品弹奏，先念音名，再点开答案。",style=MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf(3,2,1,0).forEach { i ->
                                    FilterChip(selected=practiceString==i,onClick={practiceString=i;practiceIndex=0;practiceReveal=false},label={Text("${tuning[i].first}弦")})
                                }
                            }
                            val p=Spot(practiceString,practiceIndex)
                            Text("${tuning[practiceString].first}弦 · 第$practiceIndex 品",style=MaterialTheme.typography.headlineSmall)
                            Fretboard(maxOf(12,max),p,{},false,enabled=false)
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                OutlinedButton(onClick={practiceIndex=(practiceIndex-1).coerceAtLeast(0);practiceReveal=false}) { Text("上一品") }
                                Button(onClick={practiceReveal=!practiceReveal}) { Text(if(practiceReveal)"隐藏答案" else "显示答案") }
                                OutlinedButton(onClick={practiceIndex=(practiceIndex+1)%(maxOf(12,max)+1);practiceReveal=false}) { Text("下一品") }
                                if(practiceReveal) Text(p.note,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                            }
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
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=205.dp)) {
        val cellWidth = ((maxWidth - 38.dp) / (max+1)).coerceAtLeast(30.dp).coerceAtMost(59.dp)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Box(Modifier.width(38.dp).height(21.dp),contentAlignment=Alignment.Center) { Text("弦") }
                (0..max).forEach { fret ->
                    Box(Modifier.width(cellWidth).height(21.dp),contentAlignment=Alignment.Center) {
                        Text("$fret",color=if(fret in anchors)Color(0xFF245F9C) else Color.Gray,
                            fontWeight=if(fret in anchors)FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            tuning.forEachIndexed { s,string ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.width(38.dp).height(38.dp),contentAlignment=Alignment.Center) {
                        Text(string.first,fontWeight=FontWeight.Bold)
                    }
                    (0..max).forEach { fret ->
                        val p=Spot(s,fret)
                        val active=p==highlighted || p in selectedSet
                        val correct=p in correctSet
                        Box(Modifier.width(cellWidth).height(38.dp).padding(1.dp)
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
