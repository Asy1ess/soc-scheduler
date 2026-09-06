package com.soc.scheduler

import android.content.Context
import com.soc.scheduler.data.AppDatabase
import com.soc.scheduler.data.Repository

/** 아주 작은 앱이라 DI 프레임워크 없이 싱글턴 하나로 의존성을 잡는다. */
object Graph {
    lateinit var appContext: Context
        private set

    private lateinit var db: AppDatabase

    val repo: Repository by lazy { Repository(db) }

    fun init(context: Context) {
        appContext = context.applicationContext
        db = AppDatabase.build(appContext)
    }
}
