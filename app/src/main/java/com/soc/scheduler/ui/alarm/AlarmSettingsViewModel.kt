package com.soc.scheduler.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.Graph
import com.soc.scheduler.data.ShiftAlarm
import com.soc.scheduler.data.ShiftType
import com.soc.scheduler.data.defaultAlarmFor
import com.soc.scheduler.notify.ShiftAlarms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AlarmSettingsUi(
    val workingTypes: List<ShiftType> = emptyList(),
    val alarms: Map<Long, ShiftAlarm> = emptyMap(),
    val nextText: String = "",
) {
    /** 저장된 설정이 없으면 근무 유형에 맞는 기본값을 돌려준다. */
    fun alarmOf(type: ShiftType): ShiftAlarm =
        alarms[type.id] ?: defaultAlarmFor(type)
}

class AlarmSettingsViewModel : ViewModel() {

    private val repo = Graph.repo
    private val dao = repo.shiftAlarmDao

    private val _next = MutableStateFlow("")

    val state: StateFlow<AlarmSettingsUi> = combine(
        repo.shiftDao.observeTypes(),
        dao.observeAll(),
        _next,
    ) { types, alarms, next ->
        AlarmSettingsUi(
            // 비번·휴무에는 기상 알람이 의미가 없으므로 실제 근무 유형만 보여 준다.
            workingTypes = types.filter { it.isWorking },
            alarms = alarms.associateBy { it.shiftTypeId },
            nextText = next,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlarmSettingsUi())

    init {
        dao.observeAll()
            .onEach { refreshNext() }
            .launchIn(viewModelScope)
    }

    /**
     * 켜면 근무 유형에 맞는 기본값으로 시작하고, 끄면 설정 자체를 지운다.
     * 그래서 다시 켰을 때 항상 기본값에서 출발한다.
     */
    fun setEnabled(type: ShiftType, enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            dao.upsert(defaultAlarmFor(type).copy(enabled = true))
        } else {
            dao.delete(type.id)
        }
        ShiftAlarms.reschedule(Graph.appContext)
        refreshNext()
    }

    fun setTime(type: ShiftType, hour: Int, minute: Int) =
        update(type) { it.copy(hour = hour, minute = minute) }

    fun setSound(type: ShiftType, uri: String) = update(type) { it.copy(soundUri = uri) }

    fun setVibrate(type: ShiftType, vibrate: Boolean) = update(type) { it.copy(vibrate = vibrate) }

    fun setSnooze(type: ShiftType, minutes: Int) = update(type) { it.copy(snoozeMinutes = minutes) }

    private fun update(type: ShiftType, transform: (ShiftAlarm) -> ShiftAlarm) = viewModelScope.launch {
        val current = state.value.alarmOf(type)
        dao.upsert(transform(current))
        ShiftAlarms.reschedule(Graph.appContext)
        refreshNext()
    }

    private suspend fun refreshNext() {
        val next = runCatching { ShiftAlarms.findNext() }.getOrNull()
        _next.value = if (next == null) {
            ""
        } else {
            val fmt = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREA)
            "${next.at.format(fmt)} · ${next.shiftName}"
        }
    }
}
