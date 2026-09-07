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

    // ------------------------------------------------------------ 아침 근무 브리핑

    private const val KEY_BRIEF_ON = "brief_enabled"
    private const val KEY_BRIEF_HOUR = "brief_hour"
    private const val KEY_BRIEF_MINUTE = "brief_minute"
    private const val KEY_BRIEF_REST = "brief_on_rest_days"

    fun isBriefEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BRIEF_ON, false)

    fun briefHour(context: Context): Int = prefs(context).getInt(KEY_BRIEF_HOUR, 7)

    fun briefMinute(context: Context): Int = prefs(context).getInt(KEY_BRIEF_MINUTE, 30)

    /** 휴무·비번인 날에도 알릴지 */
    fun briefOnRestDays(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BRIEF_REST, true)

    fun setBrief(context: Context, enabled: Boolean, hour: Int, minute: Int, onRestDays: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_BRIEF_ON, enabled)
            .putInt(KEY_BRIEF_HOUR, hour)
            .putInt(KEY_BRIEF_MINUTE, minute)
            .putBoolean(KEY_BRIEF_REST, onRestDays)
            .apply()
    }
}
