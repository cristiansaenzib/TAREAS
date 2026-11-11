package com.ya.tareas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Task(val id: Long, val title: String, val priority: Int, val createdAt: Long,
                val done: Boolean = false, val doneAt: Long = 0)

/** Prioridad: 3 = alta, 2 = media, 1 = baja. Guarda todo en SharedPreferences (app y widget comparten datos). */
object TaskRepo {
    private fun prefs(c: Context) = c.getSharedPreferences("ya", Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toEpochDay()

    fun all(c: Context): List<Task> {
        val a = JSONArray(prefs(c).getString("tasks", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Task(o.getLong("id"), o.getString("title"), o.getInt("p"), o.getLong("c"), o.optBoolean("d"), o.optLong("dt"))
        }
    }

    private fun save(c: Context, list: List<Task>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("p", it.priority)
            .put("c", it.createdAt).put("d", it.done).put("dt", it.doneAt)) }
        prefs(c).edit().putString("tasks", a.toString()).apply()
    }

    /** Más importante primero; a igual prioridad, la más antigua primero. */
    fun sortPending(list: List<Task>) =
        list.filter { !it.done }.sortedWith(compareByDescending<Task> { it.priority }.thenBy { it.createdAt })

    fun pending(c: Context) = sortPending(all(c))

    fun add(c: Context, title: String, priority: Int) {
        val now = System.currentTimeMillis()
        save(c, all(c) + Task(now, title.trim(), priority, now))
    }

    fun complete(c: Context, id: Long) {
        val now = System.currentTimeMillis()
        val list = all(c)
        save(c, list.map { if (it.id == id) it.copy(done = true, doneAt = now) else it })
        if (list.any { it.id == id && it.priority == 3 }) {
            val days = prefs(c).getString("days", "")!!
            prefs(c).edit().putString("days", "$days,${today()}").apply()
        }
    }

    fun undo(c: Context, id: Long) = save(c, all(c).map { if (it.id == id) it.copy(done = false) else it })
    fun delete(c: Context, id: Long) = save(c, all(c).filter { it.id != id })

    /** Días seguidos en los que completaste al menos una tarea de prioridad alta. */
    fun streak(c: Context): Int {
        val days = prefs(c).getString("days", "")!!.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        var d = today()
        if (d !in days) d -= 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}
