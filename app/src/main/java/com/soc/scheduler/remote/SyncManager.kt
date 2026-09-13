package com.soc.scheduler.remote

import android.content.Context
import android.util.Log
import com.soc.scheduler.Graph
import com.soc.scheduler.data.ShiftOverride
import com.soc.scheduler.notify.ShiftAlarms
import com.soc.scheduler.widget.WidgetUpdater
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.OffsetDateTime

/**
 * 날짜별 근무 변경을 폰과 서버(그리고 웹)가 같은 상태로 맞춘다.
 *
 * 규칙은 단순하다. 행마다 저장 시각이 있고, **나중에 저장한 쪽이 이긴다.**
 * 지운 것은 "지워짐" 표시가 붙은 행으로 남아 서버에도 전해진다.
 *
 * 흐름
 *  - 로컬에서 근무를 바꾸면 [requestSync] 가 불리고, 잠깐 모았다가 한 번에 보낸다.
 *  - 서버가 돌려준 전체 목록을 로컬과 행 단위로 비교해 새 것만 반영한다.
 *  - 로그인 중에는 Realtime 으로 서버 변경을 구독한다. 웹에서 고치면 몇 초 안에
 *    같은 [sync] 가 돌아 달력·위젯·알람이 따라온다.
 *
 * 로그인 상태가 아니면 아무것도 하지 않는다. 앱은 원래대로 로컬 전용이다.
 */
object SyncManager {

    private const val TAG = "SyncManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private var pending: Job? = null
    private var pendingPublish = false
    private var channel: RealtimeChannel? = null

    /** 앱이 뜰 때 한 번. 세션이 생기고 없어지는 것을 따라 구독을 켜고 끈다. */
    fun start(context: Context) {
        if (!FriendRepository.isAvailable) return
        Supa.client.auth.sessionStatus
            .onEach { status ->
                if (status is SessionStatus.Authenticated) {
                    subscribe(status.session.user?.id)
                    requestSync(publishPattern = true)
                } else {
                    unsubscribe()
                }
            }
            .launchIn(scope)
    }

    /**
     * 잠깐 뒤에 동기화한다. 야간 지정처럼 두 날짜가 연달아 바뀌는 경우를
     * 한 번의 왕복으로 처리하기 위해 800ms 모은다.
     *
     * @param publishPattern 교대 패턴 자체도 다시 올릴지 (초기 설정을 다시 했을 때)
     */
    fun requestSync(publishPattern: Boolean = false) {
        if (!FriendRepository.isAvailable || FriendRepository.currentUserId() == null) return
        if (publishPattern) pendingPublish = true
        pending?.cancel()
        pending = scope.launch {
            delay(800)
            val publish = pendingPublish
            pendingPublish = false
            runCatching { sync(publish) }
                .onFailure { Log.w(TAG, "sync failed", it) }
        }
    }

    /** 바로 동기화한다. 친구 화면의 새로고침처럼 사용자가 직접 요구했을 때. */
    suspend fun syncNow(publishPattern: Boolean = true) {
        if (FriendRepository.currentUserId() == null) return
        sync(publishPattern)
    }

    private suspend fun sync(publishPattern: Boolean) = lock.withLock {
        if (publishPattern) FriendRepository.publishPattern()

        val repo = Graph.repo
        val dao = repo.shiftDao
        val types = dao.types()
        val labelOf = types.associate { it.id to it.shortLabel }
        val idOf = types.associate { it.shortLabel to it.id }

        // 보내기: 로컬 전체 (지워진 행 포함)
        val outgoing = dao.allOverrideRows().mapNotNull { row ->
            val label = labelOf[row.shiftTypeId] ?: return@mapNotNull null
            OverrideSyncRow(
                epochDay = row.epochDay,
                label = label,
                deleted = row.deleted,
                updatedAt = Instant.ofEpochMilli(row.updatedAtMillis).toString(),
            )
        }

        // 서버가 합친 결과를 돌려준다
        val incoming = FriendRepository.syncOverrides(outgoing)

        // 받기: 서버 행이 더 새로우면 로컬을 덮는다
        val local = dao.allOverrideRows().associateBy { it.epochDay }
        val updates = incoming.mapNotNull { row ->
            val serverMillis = parseMillis(row.updatedAt) ?: return@mapNotNull null
            val mine = local[row.epochDay]
            if (mine != null && mine.updatedAtMillis >= serverMillis) return@mapNotNull null
            val typeId = idOf[row.label] ?: mine?.shiftTypeId ?: return@mapNotNull null
            ShiftOverride(
                epochDay = row.epochDay,
                shiftTypeId = typeId,
                memo = mine?.memo.orEmpty(),
                updatedAtMillis = serverMillis,
                deleted = row.deleted,
            )
        }
        if (updates.isNotEmpty()) {
            dao.upsertOverrides(updates)
            Log.i(TAG, "서버에서 ${updates.size}건 반영")
            val context = Graph.appContext
            WidgetUpdater.updateAll(context)
            runCatching { ShiftAlarms.reschedule(context) }
        }
    }

    private fun parseMillis(iso: String): Long? =
        runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(iso).toEpochMilli() }
            .getOrNull()

    // ---------------------------------------------------------------- Realtime

    private fun subscribe(userId: String?) {
        if (userId == null || channel != null) return
        scope.launch {
            runCatching {
                val ch = Supa.client.channel("overrides-$userId")
                ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "shift_overrides"
                    filter("user_id", FilterOperator.EQ, userId)
                }
                    .onEach { requestSync() }
                    .launchIn(scope)
                ch.subscribe()
                channel = ch
                Log.i(TAG, "realtime 구독 시작")
            }.onFailure { Log.w(TAG, "realtime 구독 실패", it) }
        }
    }

    private fun unsubscribe() {
        val ch = channel ?: return
        channel = null
        scope.launch {
            runCatching { Supa.client.realtime.removeChannel(ch) }
        }
    }
}
