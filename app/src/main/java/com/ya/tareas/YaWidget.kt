package com.ya.tareas

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider

val IdKey = ActionParameters.Key<Long>("id")

class YaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val all = TaskRepo.pending(context)
        val streak = TaskRepo.streak(context)
        provideContent { Content(context, all.take(4), all.size, streak) }
    }
}

class YaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = YaWidget()
}

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[IdKey] ?: return
        TaskRepo.complete(context, id)
        YaWidget().updateAll(context)
    }
}

private fun txt(c: Color, s: Int, bold: Boolean = false) =
    TextStyle(color = ColorProvider(c), fontSize = s.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

@Composable
private fun Content(context: Context, tasks: List<Task>, total: Int, streak: Int) {
    Column(GlanceModifier.fillMaxSize().background(Color(0xFF18202F)).padding(12.dp)
        .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text("Ya.", style = txt(Color.White, 18, true))
            Spacer(GlanceModifier.defaultWeight())
            Text("🔥 $streak · $total pendientes", style = txt(Color(0xFF9AA6BA), 12))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (tasks.isEmpty()) Text("Todo listo. Buen trabajo.", style = txt(Color.White, 14))
        tasks.forEach { t ->
            val c = when (t.priority) { 3 -> Color(0xFFFF6B6D); 2 -> Color(0xFFF2B134); else -> Color(0xFF4FC3B3) }
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("●  ", style = txt(c, 12))
                Text(t.title, modifier = GlanceModifier.defaultWeight(), maxLines = 1, style = txt(Color.White, 14))
                Text("  ○", style = txt(Color.White, 22),
                    modifier = GlanceModifier.clickable(actionRunCallback<CompleteAction>(actionParametersOf(IdKey to t.id))))
            }
        }
    }
}
