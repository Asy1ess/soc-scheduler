package com.soc.scheduler.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.remote.FriendRepository
import com.soc.scheduler.remote.FriendScheduleDto
import com.soc.scheduler.remote.MyProfileDto
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class FriendsUi(
    val available: Boolean = FriendRepository.isAvailable,
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val profile: MyProfileDto? = null,
    val friends: List<FriendScheduleDto> = emptyList(),
    val overrides: Map<String, Map<Long, String>> = emptyMap(),
    val message: String? = null,
)

class FriendsViewModel : ViewModel() {

    private val _state = MutableStateFlow(FriendsUi())
    val state: StateFlow<FriendsUi> = _state

    init {
        if (FriendRepository.isAvailable) {
            FriendRepository.sessionStatus
                .onEach { status ->
                    val signedIn = status is SessionStatus.Authenticated
                    _state.value = _state.value.copy(signedIn = signedIn)
                    if (signedIn) refresh()
                }
                .launchIn(viewModelScope)
        }
    }

    fun signInGoogle() = run { FriendRepository.signInWithGoogle() }

    fun signInKakao() = run { FriendRepository.signInWithKakao() }

    fun signOut() = run {
        FriendRepository.signOut()
        _state.value = _state.value.copy(profile = null, friends = emptyList())
    }

    /** 내 근무표를 서버에 올리고 친구 목록을 새로 받아 온다. */
    fun refresh() = run {
        FriendRepository.publishMySchedule()
        val profile = FriendRepository.myProfile()
        val friends = FriendRepository.friends()
        val overrides = FriendRepository.friendOverrides()
        _state.value = _state.value.copy(
            profile = profile,
            friends = friends,
            overrides = overrides,
        )
    }

    fun addFriend(code: String) = run {
        if (code.isBlank()) return@run
        FriendRepository.addFriendByCode(code)
        _state.value = _state.value.copy(message = "친구를 추가했습니다.")
        refreshInline()
    }

    fun removeFriend(friendId: String) = run {
        FriendRepository.removeFriend(friendId)
        refreshInline()
    }

    fun setDisplayName(name: String) = run {
        FriendRepository.setDisplayName(name.trim())
        refreshInline()
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun refreshInline() {
        _state.value = _state.value.copy(
            profile = FriendRepository.myProfile(),
            friends = FriendRepository.friends(),
            overrides = FriendRepository.friendOverrides(),
        )
    }

    /** 로딩 표시와 오류 메시지 처리를 한곳에 모은다. */
    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                block()
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = friendlyMessage(e))
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private fun friendlyMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("invalid_code") -> "그런 초대 코드는 없습니다. 다시 확인해 주세요."
            raw.contains("self_code") -> "본인 코드는 추가할 수 없습니다."
            raw.contains("not_authenticated") -> "로그인이 필요합니다."
            raw.contains("Unable to resolve host", true) ||
                raw.contains("timeout", true) -> "네트워크에 연결할 수 없습니다."
            else -> "처리하지 못했습니다: ${raw.take(120)}"
        }
    }
}
