package com.xcluice.syllabus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object Notifier {
    const val CH_RUN = "timer_run"
    const val CH_DONE = "timer_done"

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun channels(c: Context) {
        nm(c).createNotificationChannel(NotificationChannel(CH_RUN, "Timer running", NotificationManager.IMPORTANCE_LOW))
        val d = NotificationChannel(CH_DONE, "Timer finished", NotificationManager.IMPORTANCE_HIGH)
        d.enableVibration(true)
        nm(c).createNotificationChannel(d)
    }

    fun open(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun showRunning(c: Context, end: Long) {
        channels(c)
        val n = Notification.Builder(c, CH_RUN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Studying 📚")
            .setContentText("Stay focused. Timer is running")
            .setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setContentIntent(open(c)).build()
        try { nm(c).notify(1, n) } catch (e: SecurityException) { }
    }

    fun cancelRunning(c: Context) { try { nm(c).cancel(1) } catch (e: Exception) { } }

    fun done(c: Context) {
        channels(c)
        val n = Notification.Builder(c, CH_DONE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Time's up! 🎉")
            .setContentText("Great session. Take a short break.")
            .setAutoCancel(true).setContentIntent(open(c)).build()
        try { nm(c).notify(2, n) } catch (e: SecurityException) { }
    }
}

// Fires at the exact end time, even if the app was swiped away or the phone is dozing
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val st = Store.get(context)
        if (st.tState == 1) {
            st.finishTimer()
            Notifier.cancelRunning(context)
            Notifier.done(context)
        }
    }
}
