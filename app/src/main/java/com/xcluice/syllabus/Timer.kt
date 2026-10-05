package com.xcluice.syllabus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder

object Notifier {
    const val CH_RUN = "timer_run"
    const val CH_DONE = "timer_done"

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "Timer running", NotificationManager.IMPORTANCE_LOW))
        val d = NotificationChannel(CH_DONE, "Timer finished", NotificationManager.IMPORTANCE_HIGH)
        d.enableVibration(true)
        nm.createNotificationChannel(d)
    }

    fun open(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun running(c: Context, end: Long): Notification = Notification.Builder(c, CH_RUN)
        .setSmallIcon(R.drawable.ic_notif)
        .setContentTitle("Studying 📚")
        .setContentText("Stay focused. Timer is running")
        .setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        .setOngoing(true).setContentIntent(open(c)).build()

    fun done(c: Context) {
        channels(c)
        val n = Notification.Builder(c, CH_DONE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Time's up! 🎉")
            .setContentText("Great session. Take a short break.")
            .setAutoCancel(true).setContentIntent(open(c)).build()
        try { c.getSystemService(NotificationManager::class.java).notify(2, n) } catch (e: SecurityException) { }
    }
}

// Keeps the process alive + shows the countdown notification while the timer runs
class TimerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val st = Store.get(this)
        Notifier.channels(this)
        startForeground(1, Notifier.running(this, st.tEnd))
        if (st.tState != 1) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }
}

// Fires at the exact end time, even if the app was swiped away or the phone is dozing
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val st = Store.get(context)
        if (st.tState == 1) {
            st.finishTimer()
            Notifier.done(context)
        }
        context.stopService(Intent(context, TimerService::class.java))
    }
}
