package com.soc.scheduler.remote

import com.soc.scheduler.Graph
import com.soc.scheduler.data.ShiftType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Kakao
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

// ---------------------------------------------------------------- 전송 모델

@Serializable
data class ShiftTypeDto(
    val label: String,
    val name: String,
    val start: String = "",
    val end: String = "",
    val color: String = "",
)

@Serializable
data class MyProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
)

@Serializable
data class SharePayload(
    @SerialName("user_id") val userId: String,
    @SerialName("pattern_name") val patternName: String,
    @SerialName("cycle_days") val cycleDays: Int,
    @SerialName("anchor_epoch_day") val anchorEpochDay: Long,
    @SerialName("my_offset") val myOffset: Int,
    @SerialName("start_epoch_day") val startEpochDay: Long? = null,
    val cycle: List<String>,
    val types: List<ShiftTypeDto>,
)

/** sync_overrides() 에 보내고 돌려받는 행 */
@Serializable
data class OverrideSyncRow(
    @SerialName("epoch_day") val epochDay: Long,
    val label: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String,
)

/** list_friend_schedules() 결과 */
@Serializable
data class FriendScheduleDto(
    @SerialName("friend_id") val friendId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
    @SerialName("pattern_name") val patternName: String = "",
    @SerialName("cycle_days") val cycleDays: Int = 0,
    @SerialName("anchor_epoch_day") val anchorEpochDay: Long = 0,
    @SerialName("my_offset") val myOffset: Int = 0,
    @SerialName("start_epoch_day") val startEpochDay: Long? = null,
    val cycle: List<String> = emptyList(),
    val types: List<ShiftTypeDto> = emptyList(),
) {
    /** 친구의 특정 날짜 근무 라벨. 패턴이 없으면 null */
    fun labelAt(date: LocalDate, overrides: Map<Long, String> = emptyMap()): String? {
        overrides[date.toEpochDay()]?.let { return it }

        // 교대 근무 시작 전 구간은 앱과 동일하게 기본 주간 일정으로 채운다.
        val start = startEpochDay
        if (start != null && date.toEpochDay() < start) return preStartLabel(date)

        if (cycleDays <= 0 || cycle.isEmpty()) return null
        val diff = date.toEpochDay() - anchorEpochDay
        val raw = (diff + myOffset) % cycleDays
        val index = ((raw + cycleDays) % cycleDays).toInt()
        return cycle.getOrNull(index)
    }

    private fun preStartLabel(date: LocalDate): String? {
        val weekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
            date.dayOfWeek == java.time.DayOfWeek.SUNDAY
        return if (weekend) {
            types.firstOrNull { it.name.contains("휴무") || it.label == "휴" }?.label
        } else {
            types.firstOrNull { it.name.contains("주간") || it.label == "주" }?.label
        }
    }

    fun typeOf(label: String?): ShiftTypeDto? =
        if (label == null) null else types.firstOrNull { it.label == label }
}

/** 아직 수락되지 않은 친구 요청 */
@Serializable
data class FriendRequestDto(
    @SerialName("other_id") val otherId: String,
    @SerialName("display_name") val displayName: String = "",
    /** incoming = 내가 받은 요청, outgoing = 내가 보낸 요청 */
    val direction: String = "incoming",
) {
    val incoming: Boolean get() = direction == "incoming"
}

@Serializable
data class FriendOverrideDto(
    @SerialName("user_id") val userId: String,
    @SerialName("epoch_day") val epochDay: Long,
    val label: String,
    val deleted: Boolean = false,
)

// ---------------------------------------------------------------- 저장소

/**
 * 친구 공유 기능의 네트워크 계층.
 *
 * 올라가는 것은 근무표(패턴 + 근무 유형 + 날짜별 변경)뿐이다.
 * 일정 · 인수인계 · 점검 기록은 이 파일 어디에서도 다루지 않는다.
 */
object FriendRepository {

    val isAvailable: Boolean get() = Supa.isConfigured

    val sessionStatus: Flow<SessionStatus>
        get() = if (isAvailable) Supa.client.auth.sessionStatus else flowOf(SessionStatus.NotAuthenticated(false))

    fun currentUserId(): String? =
        if (isAvailable) Supa.client.auth.currentUserOrNull()?.id else null

    suspend fun signInWithGoogle() {
        Supa.client.auth.signInWith(Google)
    }

    suspend fun signInWithKakao() {
        Supa.client.auth.signInWith(Kakao)
    }

    suspend fun signOut() {
        Supa.client.auth.signOut()
    }

    suspend fun myProfile(): MyProfileDto? {
        val uid = currentUserId() ?: return null
        return Supa.client.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<MyProfileDto>()
    }

    suspend fun setDisplayName(name: String) {
        val uid = currentUserId() ?: return
        Supa.client.from("profiles").update(
            buildJsonObject { put("display_name", JsonPrimitive(name)) }
        ) { filter { eq("id", uid) } }
    }

    /**
     * 교대 패턴과 근무 유형을 서버에 올린다. 로그인 상태가 아니면 아무것도 하지 않는다.
     *
     * 날짜별 변경은 여기서 다루지 않는다. 그건 [syncOverrides] 가 행 단위로
     * 합친다. 예전에는 여기서 통째로 지우고 다시 넣었는데, 그러면 웹에서 고친
     * 것이 사라졌다.
     */
    suspend fun publishPattern() {
        val uid = currentUserId() ?: return
        val repo = Graph.repo
        val pattern = repo.shiftDao.activePattern() ?: return
        val days = repo.shiftDao.patternDays(pattern.id)
        if (days.isEmpty()) return

        val types: List<ShiftType> = repo.shiftDao.types()
        val typeMap = types.associateBy { it.id }
        val cycle = days.sortedBy { it.dayIndex }
            .mapNotNull { typeMap[it.shiftTypeId]?.shortLabel }
        if (cycle.size != days.size) return

        Supa.client.from("shift_shares").upsert(
            SharePayload(
                userId = uid,
                patternName = pattern.name,
                cycleDays = pattern.cycleDays,
                anchorEpochDay = pattern.anchorEpochDay,
                myOffset = pattern.myOffset,
                startEpochDay = pattern.startEpochDay,
                cycle = cycle,
                types = types.map {
                    ShiftTypeDto(
                        label = it.shortLabel,
                        name = it.name,
                        start = it.startTime,
                        end = it.endTime,
                        color = java.lang.Long.toHexString(it.colorArgb).uppercase(),
                    )
                },
            )
        )
    }

    /**
     * 날짜별 변경을 서버와 합친다. 보낸 행은 서버에서 "나중에 저장한 쪽이 이김"
     * 으로 반영되고, 그 사용자의 서버 전체 목록이 돌아온다.
     */
    suspend fun syncOverrides(rows: List<OverrideSyncRow>): List<OverrideSyncRow> =
        Supa.client.postgrest.rpc(
            "sync_overrides",
            buildJsonObject { put("rows", Json.encodeToJsonElement(rows)) },
        ).decodeList()

    suspend fun friends(): List<FriendScheduleDto> =
        Supa.client.postgrest.rpc("list_friend_schedules").decodeList()

    suspend fun friendOverrides(): Map<String, Map<Long, String>> {
        val rows = Supa.client.from("shift_overrides")
            .select { filter { eq("deleted", false) } }
            .decodeList<FriendOverrideDto>()
        return rows.groupBy { it.userId }
            .mapValues { entry -> entry.value.associate { it.epochDay to it.label } }
    }

    /**
     * 초대 코드로 친구 요청을 보낸다. 바로 친구가 되지는 않는다.
     *
     * @return "requested" 면 상대의 수락을 기다리는 상태,
     *         "accepted" 면 상대가 이미 나에게 요청을 보내 둬서 바로 맺어진 경우.
     */
    suspend fun requestFriendByCode(code: String): String =
        Supa.client.postgrest.rpc(
            "request_friend_by_code",
            buildJsonObject { put("code", JsonPrimitive(code)) },
        ).decodeAs()

    /** 받은 요청을 수락한다. 이때 비로소 서로 근무표가 보인다. */
    suspend fun acceptRequest(requesterId: String) {
        Supa.client.postgrest.rpc(
            "accept_friend_request",
            buildJsonObject { put("requester", JsonPrimitive(requesterId)) },
        )
    }

    /** 받은 요청을 거절하거나, 내가 보낸 요청을 취소한다. */
    suspend fun dismissRequest(otherId: String) {
        Supa.client.postgrest.rpc(
            "dismiss_friend_request",
            buildJsonObject { put("other", JsonPrimitive(otherId)) },
        )
    }

    suspend fun requests(): List<FriendRequestDto> =
        Supa.client.postgrest.rpc("list_friend_requests").decodeList()

    suspend fun removeFriend(friendId: String) {
        Supa.client.postgrest.rpc(
            "remove_friend",
            buildJsonObject { put("target", JsonPrimitive(friendId)) },
        )
    }
}
