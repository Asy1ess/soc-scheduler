package com.soc.scheduler.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soc.scheduler.remote.FriendRepository
import com.soc.scheduler.remote.SyncManager
import com.soc.scheduler.remote.FriendRequestDto
import com.soc.scheduler.remote.FriendScheduleDto
import com.soc.scheduler.remote.MyProfileDto
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class FriendsUi(
    val available: Boolean = FriendRepository.isAvailable,
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val profile: MyProfileDto? = null,
    val friends: List<FriendScheduleDto> = emptyList(),
    val requests: List<FriendRequestDto> = emptyList(),
    val overrides: Map<String, Map<Long, String>> = emptyMap(),
    val message: String? = null,
    /** 안내인지 오류인지. 카드 색을 가른다. */
    val messageIsError: Boolean = false,
) {
    /** 내가 수락을 눌러 줘야 하는 요청 */
    val incoming: List<FriendRequestDto> get() = requests.filter { it.incoming }

    /** 상대의 수락을 기다리는 중인 요청 */
    val outgoing: List<FriendRequestDto> get() = requests.filterNot { it.incoming }
}

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

    fun signUpWithId(id: String, password: String) = run {
        val clean = id.trim().lowercase()
        when {
            !FriendRepository.isValidUserId(clean) ->
                throw IllegalArgumentException("invalid_user_id")
            password.length < 6 ->
                throw IllegalArgumentException("weak_password")
        }
        FriendRepository.signUpWithId(clean, password)
    }

    fun signInWithId(id: String, password: String) = run {
        val clean = id.trim().lowercase()
        if (clean.isBlank() || password.isBlank()) throw IllegalArgumentException("empty_credentials")
        FriendRepository.signInWithId(clean, password)
    }

    fun signOut() = run {
        FriendRepository.signOut()
        _state.value = _state.value.copy(
            profile = null,
            friends = emptyList(),
            requests = emptyList(),
        )
    }

    /** 내 근무표를 서버에 올리고 친구 목록을 새로 받아 온다. */
    fun refresh() = run {
        SyncManager.syncNow(publishPattern = true)
        refreshInline()
    }

    /**
     * 초대 코드로 친구 요청을 보낸다.
     * 상대가 수락해야 서로의 근무표가 보인다.
     */
    /**
     * 입력이 이메일이면 그대로, 초대 코드 모양이면 코드로 먼저 시도하고,
     * 코드가 없으면 아이디로 본다. 아이디는 서버가 받는 이메일 형태로 바꿔 보낸다.
     */
    fun addFriend(input: String) = run {
        val text = input.trim()
        if (text.isBlank()) return@run
        val result = when {
            text.contains('@') -> FriendRepository.requestFriendByEmail(text)
            looksLikeCode(text) -> try {
                FriendRepository.requestFriendByCode(text)
            } catch (e: Exception) {
                if (e.message?.contains("invalid_code") == true && FriendRepository.isValidUserId(text.lowercase())) {
                    FriendRepository.requestFriendByEmail(FriendRepository.emailForUserId(text))
                } else throw e
            }
            FriendRepository.isValidUserId(text.lowercase()) ->
                FriendRepository.requestFriendByEmail(FriendRepository.emailForUserId(text))
            else -> FriendRepository.requestFriendByCode(text)
        }
        _state.value = _state.value.copy(
            messageIsError = false,
            message = if (result == "accepted") {
                "상대도 요청을 보내 둔 상태라 바로 친구가 되었습니다."
            } else {
                "친구 요청을 보냈습니다. 상대가 수락하면 근무표가 보입니다."
            }
        )
        refreshInline()
    }

    fun acceptRequest(requesterId: String) = run {
        FriendRepository.acceptRequest(requesterId)
        _state.value = _state.value.copy(
            message = "친구 요청을 수락했습니다.",
            messageIsError = false,
        )
        refreshInline()
    }

    fun dismissRequest(request: FriendRequestDto) = run {
        val id = request.otherId
        if (id == null) FriendRepository.cancelEmailInvite(request.displayName)
        else FriendRepository.dismissRequest(id)
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

    /**
     * 한 단계씩 반영한다.
     * 뒤쪽 호출이 실패해도 이미 받아 온 정보는 화면에 남는다.
     */
    private suspend fun refreshInline() {
        val profile = FriendRepository.myProfile()
        _state.update { it.copy(profile = profile) }

        val friends = FriendRepository.friends()
        _state.update { it.copy(friends = friends) }

        val requests = FriendRepository.requests()
        _state.update { it.copy(requests = requests) }

        val overrides = FriendRepository.friendOverrides()
        _state.update { it.copy(overrides = overrides) }
    }

    /** 로딩 표시와 오류 메시지 처리를 한곳에 모은다. */
    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                block()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    message = friendlyMessage(e),
                    messageIsError = true,
                )
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    /** 초대 코드 모양인가: 헷갈리는 글자를 뺀 8자 (하이픈 허용) */
    private fun looksLikeCode(text: String): Boolean =
        Regex("^[A-Za-z2-9]{4}-?[A-Za-z2-9]{4}$").matches(text) &&
            text.uppercase().none { it in "01IO" }

    private fun friendlyMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("invalid_user_id") -> "아이디는 영문 소문자·숫자·밑줄 3~20자입니다."
            raw.contains("weak_password") || raw.contains("Password should be") -> "비밀번호는 6자 이상이어야 합니다."
            raw.contains("empty_credentials") -> "아이디와 비밀번호를 입력하세요."
            raw.contains("already registered") || raw.contains("already_exists") -> "이미 있는 아이디입니다."
            raw.contains("Invalid login credentials") -> "아이디 또는 비밀번호가 맞지 않습니다."
            raw.contains("Email not confirmed") -> "서버에서 이메일 확인이 켜져 있습니다. Supabase 설정에서 Confirm email 을 끄세요."
            raw.contains("invalid_code") -> "초대 코드가 없습니다. 다시 확인해 주세요."
            raw.contains("self_code") -> "본인 코드는 추가할 수 없습니다."
            raw.contains("self_email") -> "본인 이메일은 추가할 수 없습니다."
            raw.contains("invalid_email") -> "이메일 형식이 아닙니다."
            raw.contains("already_friend") -> "이미 친구입니다."
            raw.contains("no_request") -> "요청이 이미 처리되었습니다."
            raw.contains("not_authenticated") -> "로그인이 필요합니다."
            raw.contains("Unable to resolve host", true) ||
                raw.contains("timeout", true) -> "네트워크에 연결할 수 없습니다."
            else -> "처리하지 못했습니다: ${raw.take(120)}"
        }
    }
}
