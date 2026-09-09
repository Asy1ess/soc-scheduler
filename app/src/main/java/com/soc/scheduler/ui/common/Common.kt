package com.soc.scheduler.ui.common

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

fun Long.toColor(): Color = Color(this.toInt())

fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.endOfDayMillis(): Long =
    plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

fun Long.toLocalDateTime(): LocalDateTime =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun LocalDateTime.toMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private val dateFmt = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA)
private val dateTimeFmt = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREA)
private val fullDateFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREA)

fun LocalDate.short(): String = format(dateFmt)
fun LocalDate.full(): String = format(fullDateFmt)
fun LocalDateTime.display(): String = format(dateTimeFmt)

/** 날짜 선택 다이얼로그 (플랫폼 기본 위젯) */
fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(LocalDate.of(year, month + 1, dayOfMonth)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

/** 시각 선택 다이얼로그 (24시간제) */
fun pickTime(context: Context, initialHour: Int, initialMinute: Int, onPicked: (Int, Int) -> Unit) {
    android.app.TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour, minute) },
        initialHour,
        initialMinute,
        true,
    ).show()
}

fun nowCalendar(): Calendar = Calendar.getInstance()

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * 받침 유무에 따라 조사를 고른다.
 *
 * 한글 음절은 0xAC00 부터 28개 종성 단위로 배열되므로, 그 나머지가 0이 아니면
 * 받침이 있다. "주간" -> "주간이었습니다", "연차" -> "연차였습니다".
 */
fun hasFinalConsonant(text: String): Boolean {
    val last = text.trim().lastOrNull() ?: return false
    if (last !in '가'..'힣') return false
    return (last.code - 0xAC00) % 28 != 0
}

/** 받침에 맞는 조사를 붙인다. [withBatchim] 은 받침이 있을 때 쓸 형태. */
fun josa(word: String, withBatchim: String, withoutBatchim: String): String =
    word + if (hasFinalConsonant(word)) withBatchim else withoutBatchim

