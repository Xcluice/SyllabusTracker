package com.xcluice.syllabus

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

// ---------- Store (all state + saving) ----------
class Store private constructor(ctx: Context) {
    companion object {
        @Volatile private var inst: Store? = null
        fun get(c: Context): Store = inst ?: synchronized(this) { inst ?: Store(c.applicationContext).also { inst = it } }
    }
    private val app = ctx.applicationContext
    private val p = ctx.getSharedPreferences("t2", Context.MODE_PRIVATE)
    // Timer state lives here (survives swipe-away); 0 idle, 1 running, 2 paused
    var tState by mutableIntStateOf(p.getInt("t_state", 0))
    var tPreset by mutableIntStateOf(p.getInt("t_preset", 25))
    var tLeft by mutableIntStateOf(p.getInt("t_left", 25 * 60))
    var tEnd by mutableLongStateOf(p.getLong("t_end", 0L))
    private var tBase = p.getInt("t_base", 0)
    val flags = mutableStateMapOf<String, Int>() // bit1 done, bit2 revised, bit4 weak star
    var examDay by mutableLongStateOf(p.getLong("exam", 0L))
    var lastDay by mutableLongStateOf(p.getLong("last", 0L))
    var streak by mutableIntStateOf(0)
    var todaySec by mutableIntStateOf(0)

    init {
        val raw = p.getString("flags", null)
        if (raw == null) {
            SUBJECTS.filter { it.preDone }.forEach { s ->
                s.groups.forEachIndexed { gi, g -> g.items.indices.forEach { ii -> flags["${s.id}.$gi.$ii"] = 1 } }
            }
            saveFlags()
        } else {
            raw.split(";").filter { it.contains("=") }.forEach {
                val kv = it.split("=")
                flags[kv[0]] = kv[1].toIntOrNull() ?: 0
            }
        }
        if (raw != null && p.getInt("dv", 0) < 2) {
            // syllabus updated for Science / Social Science / Urdu: clear their old ticks
            flags.keys.filter { it.startsWith("s.") || it.startsWith("ss.") || it.startsWith("u.") }.toList().forEach { flags.remove(it) }
            SUBJECTS.filter { it.id == "s" }.forEach { sub ->
                sub.groups.forEachIndexed { gi, g -> g.items.indices.forEach { ii -> flags["${sub.id}.$gi.$ii"] = 1 } }
            }
            saveFlags()
        }
        p.edit().putInt("dv", 2).apply()
        val today = LocalDate.now().toEpochDay()
        streak = if (lastDay >= today - 1) p.getInt("streak", 0) else 0
        todaySec = if (p.getLong("secDay", 0L) == today) p.getInt("sec", 0) else 0
    }

    private fun saveFlags() {
        p.edit().putString("flags", flags.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }
    fun has(key: String, bit: Int) = ((flags[key] ?: 0) and bit) != 0
    fun total(s: Subject) = s.groups.sumOf { it.items.size }
    fun count(s: Subject, bit: Int): Int =
        s.groups.withIndex().sumOf { (gi, g) -> g.items.indices.count { has("${s.id}.$gi.$it", bit) } }

    fun toggle(key: String, bit: Int) {
        val v = flags[key] ?: 0
        flags[key] = v xor bit
        if (bit == 1 && (v and 1) == 0) markStudied()
        saveFlags()
    }
    fun setGroup(s: Subject, gi: Int) {
        val keys = s.groups[gi].items.indices.map { "${s.id}.$gi.$it" }
        val all = keys.all { has(it, 1) }
        keys.forEach { flags[it] = if (all) (flags[it] ?: 0) and 1.inv() else (flags[it] ?: 0) or 1 }
        if (!all) markStudied()
        saveFlags()
    }
    fun resetAll() { flags.clear(); saveFlags() }
    fun setExam(day: Long) { examDay = day; p.edit().putLong("exam", day).apply() }

    fun markStudied() {
        val today = LocalDate.now().toEpochDay()
        if (lastDay == today) return
        streak = if (lastDay == today - 1) streak + 1 else 1
        lastDay = today
        p.edit().putInt("streak", streak).putLong("last", lastDay).apply()
    }
    fun crash(): String? = p.getString("crash", null)
    fun clearCrash() { p.edit().remove("crash").apply() }
    fun logErr(e: Throwable) { p.edit().putString("crash", android.util.Log.getStackTraceString(e).take(1500)).commit() }
    private fun credit(sec: Int) { if (sec > 0) { todaySec += sec; saveSec() } }
    private fun saveTimer() {
        p.edit().putInt("t_state", tState).putInt("t_preset", tPreset).putInt("t_left", tLeft)
            .putInt("t_base", tBase).putLong("t_end", tEnd).apply()
    }
    private fun alarmPI() = PendingIntent.getBroadcast(app, 7, Intent(app, TimerReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun alarmMgr() = app.getSystemService(AlarmManager::class.java)
    fun canExact() = Build.VERSION.SDK_INT < 31 || alarmMgr().canScheduleExactAlarms()
    fun exactAsked() = p.getBoolean("exactAsked", false)
    fun markExactAsked() { p.edit().putBoolean("exactAsked", true).apply() }
    fun remaining(now: Long = System.currentTimeMillis()): Int =
        if (tState == 1) max(0, ((tEnd - now + 999) / 1000).toInt()) else tLeft
    fun startTimer() {
        if (tState == 1) return
        if (tLeft <= 0) tLeft = tPreset * 60
        tBase = tLeft
        tEnd = System.currentTimeMillis() + tLeft * 1000L
        tState = 1
        markStudied(); saveTimer()
        try { alarmMgr().setAlarmClock(AlarmManager.AlarmClockInfo(tEnd, Notifier.open(app)), alarmPI()) }
        catch (e: SecurityException) {
            try { alarmMgr().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tEnd, alarmPI()) } catch (x: Throwable) { logErr(x) }
        } catch (e: Throwable) { logErr(e) }
        try { Notifier.showRunning(app, tEnd) } catch (e: Throwable) { logErr(e) }
    }
    fun pauseTimer() {
        if (tState != 1) return
        val r = remaining(); credit(tBase - r); tLeft = r; tState = 2
        alarmMgr().cancel(alarmPI()); Notifier.cancelRunning(app); saveTimer()
    }
    fun resetTimer() {
        if (tState == 1) { credit(tBase - remaining()); alarmMgr().cancel(alarmPI()); Notifier.cancelRunning(app) }
        tLeft = tPreset * 60; tState = 0; saveTimer()
    }
    fun setPreset(m: Int) { if (tState == 1) return; tPreset = m; tLeft = m * 60; tState = 0; saveTimer() }
    fun finishTimer() { if (tState != 1) return; credit(tBase); tLeft = 0; tState = 0; saveTimer() }
    fun saveSec() {
        p.edit().putInt("sec", todaySec).putLong("secDay", LocalDate.now().toEpochDay()).apply()
    }
}

// ---------- Theme ----------
class Pal(val bg: Color, val card: Color, val ink: Color, val sub: Color, val line: Color)
val LightPal = Pal(Color(0xFFF4F3FF), Color.White, Color(0xFF1E1B4B), Color(0xFF6B7094), Color(0xFFE4E2F7))
val DarkPal = Pal(Color(0xFF0F0D24), Color(0xFF1A1740), Color(0xFFF1F0FF), Color(0xFFA5A3D1), Color(0xFF2C2860))
val LocalPal = staticCompositionLocalOf { LightPal }
val Rainbow = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFFF59E0B)))

// No ripple / tap highlight anywhere
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
fun T(
    s: String, size: Int = 14, w: FontWeight = FontWeight.Bold, c: Color = LocalPal.current.ink,
    mod: Modifier = Modifier, style: TextStyle = TextStyle.Default,
    deco: TextDecoration? = null, align: TextAlign? = null
) = Text(s, modifier = mod, color = c, fontSize = size.sp, fontWeight = w, style = style, textDecoration = deco, textAlign = align)

@Composable
fun Bar(frac: Float, color: Color, track: Color, h: Int = 10) {
    val a by animateFloatAsState(frac, label = "bar")
    Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(99.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(a.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(color))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store.get(applicationContext)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { store.logErr(e) } catch (x: Throwable) { }
            prev?.uncaughtException(t, e)
        }
        setContent { App(store) }
    }
}

// ---------- App + back navigation ----------
@Composable
fun App(st: Store) {
    val pal = if (isSystemInDarkTheme()) DarkPal else LightPal
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // Back press: subject screen -> home. On home it exits the app normally.
    BackHandler(enabled = open != null) { open = null }
    var crash by remember { mutableStateOf(st.crash()) }
    CompositionLocalProvider(LocalPal provides pal) {
        crash?.let { msg ->
            AlertDialog(
                onDismissRequest = { st.clearCrash(); crash = null }, containerColor = pal.card,
                title = { T("App error (send me a screenshot)", 16) },
                text = { T(msg, 10, FontWeight.Normal, pal.sub) },
                confirmButton = { T("OK", 15, mod = Modifier.tap { st.clearCrash(); crash = null }.padding(12.dp)) }
            )
        }
        Box(Modifier.fillMaxSize().background(pal.bg)) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                AnimatedContent(open, transitionSpec = {
                    if (targetState != null)
                        (slideInHorizontally(tween(200)) { w -> w / 4 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(200)) { w -> -w / 8 } + fadeOut(tween(120)))
                    else
                        (slideInHorizontally(tween(200)) { w -> -w / 8 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(200)) { w -> w / 4 } + fadeOut(tween(120)))
                }, label = "nav") { id ->
                    if (id == null) Home(st) { open = it }
                    else SubjectScreen(st, SUBJECTS.first { s -> s.id == id }) { open = null }
                }
            }
        }
    }
}

// ---------- Home ----------
@Composable
fun Home(st: Store, onOpen: (String) -> Unit) {
    val pal = LocalPal.current
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item("head") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Rainbow), contentAlignment = Alignment.Center) {
                    T("✓", 24, c = Color.White)
                }
                Column {
                    T("T2 Syllabus Tracker", 21, FontWeight.Black)
                    T("JKBOSE Class 9 · Term II", 13, c = pal.sub)
                }
            }
        }
        item("hero") { Hero(st) }
        item("info") { InfoRow(st) }
        item("timer") { TimerCard(st) }
        items(SUBJECTS.chunked(2), key = { it[0].id }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { SubjectCard(st, it, Modifier.weight(1f), onOpen) }
            }
        }
        item("foot") {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.card)
                    .border(1.5.dp, pal.line, RoundedCornerShape(14.dp)).tap { confirm = true }.padding(12.dp),
                contentAlignment = Alignment.Center
            ) { T("↺  Reset all ticks", 13) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = pal.card,
        title = { T("Clear all ticks?", 18) },
        text = { T("Completed, revised and weak-topic marks will be removed.", 14, FontWeight.Normal, pal.sub) },
        confirmButton = { T("Reset", 15, c = Color(0xFFEF4444), mod = Modifier.tap { st.resetAll(); confirm = false }.padding(12.dp)) },
        dismissButton = { T("Cancel", 15, mod = Modifier.tap { confirm = false }.padding(12.dp)) }
    )
}

@Composable
fun Hero(st: Store) {
    val total = SUBJECTS.sumOf { st.total(it) }
    val done = SUBJECTS.sumOf { st.count(it, 1) }
    val rev = SUBJECTS.sumOf { st.count(it, 2) }
    val frac = if (total == 0) 0f else done.toFloat() / total
    val pct = (frac * 100).toInt()
    val line = when { pct == 100 -> "All done. Go ace it! 🏆"; pct >= 70 -> "Almost there, keep going! 🔥"; pct >= 40 -> "Good pace. Keep ticking! 💪"; else -> "Let's start ticking chapters ✨" }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Rainbow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T("Overall progress", 13, c = Color.White.copy(alpha = .9f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T("$pct%", 38, FontWeight.Black, Color.White)
            T("$done/$total chapters · $rev revised", 13, c = Color.White, mod = Modifier.padding(bottom = 8.dp))
        }
        Bar(frac, Color.White, Color.White.copy(alpha = .3f), 12)
        T(line, 13, c = Color.White)
    }
}

@Composable
fun InfoRow(st: Store) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val total = SUBJECTS.sumOf { st.total(it) }
    val left = total - SUBJECTS.sumOf { st.count(it, 1) }
    val today = LocalDate.now()
    val days = if (st.examDay == 0L) -1 else (st.examDay - today.toEpochDay()).toInt()
    val box = Modifier.clip(RoundedCornerShape(18.dp)).background(pal.card).border(1.5.dp, pal.line, RoundedCornerShape(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(box.weight(1f).tap {
            DatePickerDialog(ctx, { _, y, m, d -> st.setExam(LocalDate.of(y, m + 1, d).toEpochDay()) },
                today.year, today.monthValue - 1, today.dayOfMonth).show()
        }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                st.examDay == 0L -> { T("📅 Set exam date", 15, c = Color(0xFF6366F1)); T("Tap to pick a date", 12, c = pal.sub) }
                days < 0 -> { T("📅 Exam passed", 15); T("Tap to change date", 12, c = pal.sub) }
                days == 0 -> { T("📅 Exam today!", 15, c = Color(0xFFEF4444)); T("$left chapters left", 12, c = pal.sub) }
                else -> {
                    val per = ceil(left.toDouble() / max(days, 1)).toInt()
                    T("📅 $days days left", 15, c = Color(0xFF6366F1))
                    T(if (left == 0) "Syllabus done!" else "$per chapters/day", 12, c = pal.sub)
                }
            }
        }
        Column(box.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("🔥 ${st.streak} day streak", 15, c = Color(0xFFF59E0B))
            T("Today: ${st.todaySec / 60} min", 12, c = pal.sub)
        }
    }
}

@Composable
fun TimerCard(st: Store) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.tState) { while (st.tState == 1) { now = System.currentTimeMillis(); delay(250) } }
    val left = st.remaining(now)
    val running = st.tState == 1
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val teal = Color(0xFF06B6D4)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card).border(1.5.dp, teal.copy(alpha = .4f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            T("⏱ Study timer", 15)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 25, 45).forEach { m ->
                    val on = st.tPreset == m
                    Box(Modifier.clip(CircleShape).background(if (on) teal else teal.copy(alpha = .15f))
                        .tap { st.setPreset(m) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        T("$m", 12, c = if (on) Color.White else teal)
                    }
                }
            }
        }
        T("%02d:%02d".format(left / 60, left % 60), 44, FontWeight.Black, teal, Modifier.fillMaxWidth(), align = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (running) Color(0xFFF59E0B) else Color(0xFF10B981))
                .tap {
                    if (running) st.pauseTimer() else {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        if (!st.canExact() && !st.exactAsked()) {
                            st.markExactAsked()
                            try { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) } catch (e: Exception) { }
                        }
                        st.startTimer()
                    }
                }.padding(12.dp), contentAlignment = Alignment.Center) {
                T(if (running) "Pause" else if (st.tState == 2) "Resume" else "Start", 14, c = Color.White)
            }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(pal.line)
                .tap { st.resetTimer() }.padding(12.dp), contentAlignment = Alignment.Center) {
                T("Reset", 14)
            }
        }
        T("Keeps running in the background, even if you close the app", 11, FontWeight.Normal, pal.sub, Modifier.fillMaxWidth(), align = TextAlign.Center)
    }
}

@Composable
fun SubjectCard(st: Store, s: Subject, mod: Modifier, onOpen: (String) -> Unit) {
    val pal = LocalPal.current
    val t = st.total(s)
    val d = st.count(s, 1)
    val stars = st.count(s, 4)
    val shape = RoundedCornerShape(20.dp)
    Column(
        mod.clip(shape).background(pal.card).border(1.5.dp, s.color.copy(alpha = .4f), shape).tap { onOpen(s.id) }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(s.color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Text(s.emoji, fontSize = 22.sp)
        }
        T(s.name, 15)
        T("$d/$t done" + if (stars > 0) " · ⭐$stars" else "", 12, c = pal.sub)
        Bar(d.toFloat() / t, s.color, pal.line)
    }
}

// ---------- Subject screen (LazyColumn, flat list, stable keys = smooth scroll) ----------
sealed class Row(val key: String)
class RowG(val gi: Int, val g: Group) : Row("g$gi")
class RowI(val gi: Int, val ii: Int, val item: Item, val last: Boolean) : Row("i$gi.$ii")

@Composable
fun SubjectScreen(st: Store, s: Subject, onBack: () -> Unit) {
    val pal = LocalPal.current
    val rows = remember(s) {
        buildList<Row> {
            s.groups.forEachIndexed { gi, g ->
                add(RowG(gi, g))
                g.items.forEachIndexed { ii, it -> add(RowI(gi, ii, it, ii == g.items.lastIndex)) }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item("top") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("‹  All subjects", 15, c = pal.sub, mod = Modifier.tap(onBack).padding(vertical = 4.dp))
                val t = st.total(s); val d = st.count(s, 1)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(s.color, s.color.copy(alpha = .7f)))).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(s.emoji, fontSize = 30.sp)
                    T(s.name, 22, FontWeight.Black, Color.White)
                    T("$d/$t done · ${st.count(s, 2)} revised · ⭐ ${st.count(s, 4)} weak", 13, c = Color.White)
                    Bar(d.toFloat() / t, Color.White, Color.White.copy(alpha = .3f), 12)
                }
            }
        }
        items(rows, key = { it.key }) { r ->
            if (r is RowG) GroupHeader(st, s, r) else if (r is RowI) ItemRow(st, s, r)
        }
    }
}

@Composable
fun GroupHeader(st: Store, s: Subject, r: RowG) {
    val pal = LocalPal.current
    val gd = r.g.items.indices.count { st.has("${s.id}.${r.gi}.$it", 1) }
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(pal.card)
            .tap { st.setGroup(s, r.gi) }.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        T(r.g.title, 14, mod = Modifier.weight(1f))
        Box(Modifier.clip(CircleShape).background(s.color.copy(alpha = .15f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            T("$gd/${r.g.items.size} · tick all", 12, c = s.color)
        }
    }
}

@Composable
fun ItemRow(st: Store, s: Subject, r: RowI) {
    val pal = LocalPal.current
    val key = "${s.id}.${r.gi}.${r.ii}"
    val f = st.flags[key] ?: 0
    val done = (f and 1) != 0
    val rev = (f and 2) != 0
    val star = (f and 4) != 0
    val shape = if (r.last) RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(0.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(pal.card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        Row(
            Modifier.fillMaxWidth().tap { st.toggle(key, 1) }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(9.dp))
                    .then(if (done) Modifier.background(s.color) else Modifier.border(2.5.dp, pal.line, RoundedCornerShape(9.dp))),
                contentAlignment = Alignment.Center
            ) { if (done) T("✓", 15, c = Color.White) }
            T(
                r.item.name, if (r.item.rtl) 17 else 15, FontWeight.SemiBold,
                if (done) pal.sub else pal.ink, Modifier.weight(1f),
                style = if (r.item.rtl) TextStyle(textDirection = TextDirection.Rtl) else TextStyle.Default,
                deco = if (done) TextDecoration.LineThrough else null
            )
            if (r.item.marks.isNotEmpty()) T(r.item.marks, 12, c = pal.sub)
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (rev) Color(0xFF10B981) else pal.line).tap { st.toggle(key, 2) },
                contentAlignment = Alignment.Center) { T("↻", 17, c = if (rev) Color.White else pal.sub) }
            Box(Modifier.size(34.dp).tap { st.toggle(key, 4) }, contentAlignment = Alignment.Center) {
                T(if (star) "★" else "☆", 24, c = if (star) Color(0xFFF59E0B) else pal.sub)
            }
        }
    }
}
