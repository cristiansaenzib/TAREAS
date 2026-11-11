package com.ya.tareas

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    private val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        Reminder.schedule(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(tick) }
            }
        }
    }

    // Al volver (por ejemplo desde el widget) se recargan las tareas.
    override fun onResume() { super.onResume(); tick++ }
}

private fun priInfo(p: Int) = when (p) {
    3 -> "Alta" to Color(0xFFE5383B); 2 -> "Media" to Color(0xFFD98A00); else -> "Baja" to Color(0xFF2A9D8F)
}

private fun ago(ms: Long): String { val m = ms / 60000
    return when { m < 1 -> "recién creada"; m < 60 -> "hace $m min"; m < 1440 -> "hace ${m / 60} h"; else -> "hace ${m / 1440} d" } }

@Composable
fun App(tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tasks by remember(tick) { mutableStateOf(TaskRepo.all(ctx)) }
    var focus by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var pri by remember { mutableIntStateOf(2) }
    fun refresh() { tasks = TaskRepo.all(ctx); scope.launch { YaWidget().updateAll(ctx) } }

    val pending = TaskRepo.sortPending(tasks)
    val done = tasks.filter { it.done }.sortedByDescending { it.doneAt }.take(5)
    val streak = remember(tasks) { TaskRepo.streak(ctx) }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ya.", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            AssistChip(onClick = {}, label = { Text("🔥 $streak ${if (streak == 1) "día" else "días"}") })
        }
        Text("Racha: días seguidos completando una tarea de prioridad alta", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(focus, { focus = it })
            Spacer(Modifier.width(8.dp))
            Text("Modo enfoque: una tarea a la vez")
        }

        if (focus) {
            val t = pending.firstOrNull()
            Card(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Column(Modifier.padding(24.dp)) {
                    if (t == null) Text("Todo listo. Buen trabajo.", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    else {
                        val (name, color) = priInfo(t.priority)
                        Text("$name · ${ago(now - t.createdAt)}", color = color)
                        Spacer(Modifier.height(8.dp))
                        Text(t.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { TaskRepo.complete(ctx, t.id); refresh() }, Modifier.fillMaxWidth()) { Text("Listo") }
                    }
                }
            }
        } else {
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("¿Qué tienes pendiente?") }, singleLine = true)
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(3, 2, 1).forEach { p ->
                    FilterChip(pri == p, { pri = p }, { Text(priInfo(p).first) }, Modifier.padding(end = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (text.isNotBlank()) { TaskRepo.add(ctx, text, pri); text = ""; refresh() } }) { Text("Agregar") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Pendientes (${pending.size})", fontWeight = FontWeight.SemiBold) }
                if (pending.isEmpty()) item { Text("Nada pendiente. Agrega algo nuevo.") }
                items(pending, key = { it.id }) { t ->
                    val (name, color) = priInfo(t.priority)
                    val age = now - t.createdAt
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(false, { TaskRepo.complete(ctx, t.id); refresh() })
                            Box(Modifier.size(10.dp).background(color, CircleShape))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(t.title, fontWeight = FontWeight.SemiBold)
                                Text("$name · ${ago(age)}" + if (age > 2 * 86_400_000L) " · no lo sigas dejando" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (age > 2 * 86_400_000L) color else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { TaskRepo.delete(ctx, t.id); refresh() }) { Text("×") }
                        }
                    }
                }
                if (done.isNotEmpty()) item { Text("Hechas recientemente", fontWeight = FontWeight.SemiBold) }
                items(done, key = { it.id }) { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(true, { TaskRepo.undo(ctx, t.id); refresh() })
                        Text(t.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
