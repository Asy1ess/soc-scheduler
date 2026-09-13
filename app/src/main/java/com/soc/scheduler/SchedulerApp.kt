package com.soc.scheduler

import android.app.Application
import com.soc.scheduler.notify.DailyBrief
import com.soc.scheduler.notify.Notifications
import com.soc.scheduler.notify.ShiftAlarms
import com.soc.scheduler.remote.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SchedulerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        SyncManager.start(this)
        Notifications.createChannel(this)
        DailyBrief.createChannel(this)
        DailyBrief.reschedule(this)
        ShiftAlarms.createChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ShiftAlarms.reschedule(this@SchedulerApp) }
        }
    }
}
