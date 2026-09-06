package com.soc.scheduler

import android.app.Application
import com.soc.scheduler.notify.Notifications

class SchedulerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifications.createChannel(this)
    }
}
