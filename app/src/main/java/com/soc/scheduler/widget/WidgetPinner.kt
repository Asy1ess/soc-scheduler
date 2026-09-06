package com.soc.scheduler.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** 앱 안에서 홈 화면에 위젯을 바로 추가한다. (런처가 지원하는 경우) */
object WidgetPinner {

    data class Entry(val label: String, val description: String, val provider: Class<*>)

    val ALL = listOf(
        Entry("오늘 근무", "오늘 근무 유형과 시간대", TodayShiftWidget::class.java),
        Entry("이번 주 근무", "일~토 7일간 근무", WeekShiftWidget::class.java),
        Entry("월간 근무표", "이번 달 달력 전체", MonthShiftWidget::class.java),
        Entry("오늘 할 일", "점검 항목과 일정", TodayTaskWidget::class.java),
    )

    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true

    /** @return 요청이 런처로 전달되었으면 true */
    fun pin(context: Context, entry: Entry): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching {
            manager.requestPinAppWidget(ComponentName(context, entry.provider), null, null)
        }.getOrDefault(false)
    }
}
