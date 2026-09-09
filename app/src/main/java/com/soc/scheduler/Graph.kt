package com.soc.scheduler

import android.content.Context
import com.soc.scheduler.data.AppDatabase
import com.soc.scheduler.data.Prefs
import com.soc.scheduler.data.Repository
import com.soc.scheduler.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 아주 작은 앱이라 DI 프레임워크 없이 싱글턴 하나로 의존성을 잡는다. */
object Graph {
    lateinit var appContext: Context
        private set

    private lateinit var db: AppDatabase

    val repo: Repository by lazy { Repository(db) }

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    /** 화면 테마. 바꾸면 열려 있는 화면이 바로 다시 그려진다. */
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        Prefs.setThemeMode(appContext, mode)
        _themeMode.value = mode
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        db = AppDatabase.build(appContext)
        _themeMode.value = Prefs.themeMode(appContext)
    }
}
