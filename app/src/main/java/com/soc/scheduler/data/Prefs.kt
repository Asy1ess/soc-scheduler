package com.soc.scheduler.data

import android.content.Context

/** 초기 설정 완료 여부처럼 아주 단순한 플래그만 담는다. */
object Prefs {
    private const val FILE = "soc_scheduler_prefs"
    private const val KEY_SETUP_DONE = "setup_done"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isSetupDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun setSetupDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_SETUP_DONE, done).apply()
    }
}
