package com.dailysmart.app.v2

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

data class ReminderItem(
    val id: Long,
    val title: String,
    val triggerAt: Long
)

/** Device-local one-shot reminders. Uses inexact alarms so no exact-alarm special access is needed. */
object ReminderManager {
    private const val PREFS = "dailysmart_local_reminders"
    private const val KEY_ITEMS = "items"
    private const val CHANNEL_ID = "dailysmart_reminders"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TITLE = "reminder_title"

    fun epochMillis(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long {
        require(hour in 0..23)
        require(minute in 0..59)
        return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    fun load(context: Context): List<ReminderItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optLong("id", 0L)
                val title = item.optString("title").trim()
                val triggerAt = item.optLong("triggerAt", 0L)
                if (id > 0L && title.isNotBlank() && triggerAt > 0L) {
                    add(ReminderItem(id, title, triggerAt))
                }
            }
        }.sortedBy { it.triggerAt }
    }

    fun schedule(context: Context, reminder: ReminderItem) {
        require(reminder.id > 0L && reminder.title.isNotBlank() && reminder.triggerAt > System.currentTimeMillis())
        save(context, load(context).filterNot { it.id == reminder.id } + reminder)
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAt,
            pendingIntent(context, reminder.id, reminder.title)
        )
    }

    fun cancel(context: Context, id: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(id),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
        save(context, load(context).filterNot { it.id == id })
    }

    internal fun remove(context: Context, id: Long) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val upcoming = load(context).filter { it.triggerAt > now }
        save(context, upcoming)
        upcoming.forEach { reminder ->
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAt,
                pendingIntent(context, reminder.id, reminder.title)
            )
        }
        ensureChannel(context)
    }

    internal fun showNotification(context: Context, reminder: ReminderItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("DailySmart")
            .setContentText(reminder.title)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(requestCode(reminder.id), notification)
    }

    private fun pendingIntent(context: Context, id: Long, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(id: Long): Int = (id xor (id ushr 32)).toInt()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DailySmart reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun save(context: Context, items: List<ReminderItem>) {
        val array = JSONArray()
        items.sortedBy { it.triggerAt }.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("triggerAt", item.triggerAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderManager.EXTRA_ID, 0L)
        val title = intent.getStringExtra(ReminderManager.EXTRA_TITLE).orEmpty()
        if (id <= 0L || title.isBlank()) return
        ReminderManager.remove(context, id)
        ReminderManager.showNotification(context, ReminderItem(id, title, System.currentTimeMillis()))
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderManager.rescheduleAll(context)
        }
    }
}
