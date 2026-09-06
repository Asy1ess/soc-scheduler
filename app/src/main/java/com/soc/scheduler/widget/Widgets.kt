package com.soc.scheduler.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.soc.scheduler.Graph
import com.soc.scheduler.MainActivity
import com.soc.scheduler.R
import com.soc.scheduler.ui.common.endOfDayMillis
import com.soc.scheduler.ui.common.startOfDayMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA)
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)
private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")

/** 색상의 알파만 바꾼다. */
private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

/** 일요일을 주의 시작으로 본다 (앱 캘린더와 동일). */
private fun weekStart(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value % 7).toLong())

private fun dayTextColor(context: Context, date: LocalDate, inMonth: Boolean): Int {
    val base = when (date.dayOfWeek.value) {
        7 -> ContextCompat.getColor(context, R.color.widget_sunday)
        6 -> ContextCompat.getColor(context, R.color.widget_saturday)
        else -> ContextCompat.getColor(context, R.color.widget_text)
    }
    return if (inMonth) base else withAlpha(base, 0x55)
}

/** 위젯 공통: 백그라운드에서 데이터를 읽어 RemoteViews 를 만들어 붙인다. */
abstract class BaseShiftWidget : AppWidgetProvider() {

    protected abstract suspend fun render(context: Context): RemoteViews

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refresh(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleMidnight(context)
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = render(context)
                ids.forEach { id -> runCatching { manager.updateAppWidget(id, views) } }
                WidgetUpdater.scheduleMidnight(context)
            } catch (e: Exception) {
                // 위젯 갱신 실패가 앱을 멈추게 하지 않도록 삼킨다.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

// ------------------------------------------------------------------ 오늘 근무

class TodayShiftWidget : BaseShiftWidget() {

    override suspend fun render(context: Context): RemoteViews {
        val today = LocalDate.now()
        val data = WidgetData.load(today, today)
        val shift = data.shiftAt(today)
        val color = shift.type?.colorArgb?.toInt() ?: Color.GRAY

        return RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.date, today.format(DATE_FMT))
            setTextViewText(R.id.shift, shift.type?.name ?: "근무 없음")
            setTextColor(R.id.shift, color)
            setInt(R.id.accent, "setColorFilter", color)

            val type = shift.type
            val timeText = when {
                type == null -> "패턴을 설정해 주세요"
                type.startTime.isBlank() -> if (shift.isOverride) "휴식 · 변경됨" else "휴식"
                shift.isOverride -> "${type.startTime} ~ ${type.endTime} · 변경됨"
                else -> "${type.startTime} ~ ${type.endTime}"
            }
            setTextViewText(R.id.time, timeText)
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }
    }
}

// ------------------------------------------------------------------ 이번 주 근무

class WeekShiftWidget : BaseShiftWidget() {

    override suspend fun render(context: Context): RemoteViews {
        val today = LocalDate.now()
        val start = weekStart(today)
        val end = start.plusDays(6)
        val data = WidgetData.load(start, end)

        return RemoteViews(context.packageName, R.layout.widget_week).apply {
            setTextViewText(R.id.title, "이번 주 근무")
            setTextViewText(R.id.subtitle, data.patternName)
            setTextColor(R.id.title, ContextCompat.getColor(context, R.color.widget_text))
            removeAllViews(R.id.week_container)

            (0..6).forEach { offset ->
                val date = start.plusDays(offset.toLong())
                val shift = data.shiftAt(date)
                val color = shift.type?.colorArgb?.toInt() ?: Color.GRAY
                val isToday = date == today

                val cell = RemoteViews(context.packageName, R.layout.widget_week_cell).apply {
                    setTextViewText(R.id.dow, WEEKDAYS[date.dayOfWeek.value % 7])
                    setTextColor(R.id.dow, dayTextColor(context, date, true))
                    setTextViewText(R.id.day, date.dayOfMonth.toString())
                    setTextColor(R.id.day, dayTextColor(context, date, true))
                    setTextViewText(R.id.label, shift.type?.shortLabel ?: "-")
                    setTextColor(R.id.label, color)
                    setInt(R.id.cell_bg, "setColorFilter", color)
                    setInt(R.id.cell_bg, "setImageAlpha", if (isToday) 0x5C else 0x22)
                }
                addView(R.id.week_container, cell)
            }

            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }
    }
}

// ------------------------------------------------------------------ 월간 근무표

class MonthShiftWidget : BaseShiftWidget() {

    override suspend fun render(context: Context): RemoteViews {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value % 7
        val gridStart = first.minusDays(leading.toLong())
        val weeks = ((leading + month.lengthOfMonth() + 6) / 7).coerceAtLeast(1)
        val gridEnd = gridStart.plusDays((weeks * 7 - 1).toLong())
        val data = WidgetData.load(gridStart, gridEnd)

        val summary = (0 until month.lengthOfMonth())
            .mapNotNull { data.shiftAt(first.plusDays(it.toLong())).type }
            .filter { it.isWorking }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { it.first.sortOrder }
            .joinToString(" ") { "${it.first.shortLabel}${it.second}" }

        return RemoteViews(context.packageName, R.layout.widget_month).apply {
            setTextViewText(R.id.title, "${month.year}년 ${month.monthValue}월")
            setTextViewText(R.id.summary, summary)

            removeAllViews(R.id.dow_header)
            WEEKDAYS.forEachIndexed { index, name ->
                val head = RemoteViews(context.packageName, R.layout.widget_month_dow).apply {
                    setTextViewText(R.id.dow, name)
                    setTextColor(
                        R.id.dow,
                        when (index) {
                            0 -> ContextCompat.getColor(context, R.color.widget_sunday)
                            6 -> ContextCompat.getColor(context, R.color.widget_saturday)
                            else -> ContextCompat.getColor(context, R.color.widget_dim)
                        },
                    )
                }
                addView(R.id.dow_header, head)
            }

            removeAllViews(R.id.weeks_container)
            (0 until weeks).forEach { week ->
                val row = RemoteViews(context.packageName, R.layout.widget_month_row)
                (0..6).forEach { dow ->
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    val inMonth = YearMonth.from(date) == month
                    val shift = data.shiftAt(date)
                    val color = shift.type?.colorArgb?.toInt() ?: Color.GRAY
                    val isToday = date == today

                    val cell = RemoteViews(context.packageName, R.layout.widget_month_cell).apply {
                        setTextViewText(R.id.day, date.dayOfMonth.toString())
                        setTextColor(R.id.day, dayTextColor(context, date, inMonth))
                        setTextViewText(R.id.label, shift.type?.shortLabel ?: "")
                        setTextColor(R.id.label, if (inMonth) color else withAlpha(color, 0x55))
                        setInt(
                            R.id.cell,
                            "setBackgroundColor",
                            when {
                                isToday -> withAlpha(color, 0x5C)
                                inMonth -> withAlpha(color, 0x1F)
                                else -> Color.TRANSPARENT
                            },
                        )
                    }
                    row.addView(R.id.row, cell)
                }
                addView(R.id.weeks_container, row)
            }

            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }
    }
}

// ------------------------------------------------------------------ 오늘 할 일

class TodayTaskWidget : BaseShiftWidget() {

    override suspend fun render(context: Context): RemoteViews {
        val today = LocalDate.now()
        val repo = Graph.repo
        val data = WidgetData.load(today, today)
        val shift = data.shiftAt(today)
        val color = shift.type?.colorArgb?.toInt() ?: Color.GRAY

        runCatching { repo.ensureCheckRuns(today) }
        val runs = runCatching { repo.checkDao.runsOnce(today.toEpochDay()) }.getOrDefault(emptyList())
        val tasks = repo.taskDao
            .tasksBetween(today.startOfDayMillis(), today.endOfDayMillis())
            .filter { !it.done }

        return RemoteViews(context.packageName, R.layout.widget_tasks).apply {
            setTextViewText(R.id.title, "오늘 · ${today.format(DATE_FMT)}")
            setTextViewText(R.id.shift, shift.type?.shortLabel ?: "-")
            setTextColor(R.id.shift, color)
            setTextViewText(
                R.id.progress,
                if (runs.isEmpty()) {
                    "점검 항목 없음"
                } else {
                    "점검 ${runs.count { it.done }}/${runs.size} 완료"
                },
            )

            removeAllViews(R.id.task_container)
            if (tasks.isEmpty()) {
                setViewVisibility(R.id.empty, android.view.View.VISIBLE)
                setTextViewText(R.id.empty, "남은 일정이 없습니다.")
            } else {
                setViewVisibility(R.id.empty, android.view.View.GONE)
                tasks.take(4).forEach { task ->
                    val time = java.time.Instant.ofEpochMilli(task.dueAtMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(TIME_FMT)
                    val row = RemoteViews(context.packageName, R.layout.widget_task_row).apply {
                        setTextViewText(R.id.time, time)
                        setTextViewText(R.id.title, task.title)
                    }
                    addView(R.id.task_container, row)
                }
                if (tasks.size > 4) {
                    val more = RemoteViews(context.packageName, R.layout.widget_task_row).apply {
                        setTextViewText(R.id.time, "")
                        setTextViewText(R.id.title, "외 ${tasks.size - 4}건")
                    }
                    addView(R.id.task_container, more)
                }
            }

            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }
    }
}
