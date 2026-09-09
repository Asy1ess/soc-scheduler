package com.soc.scheduler.ui.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.remote.FriendScheduleDto
import com.soc.scheduler.ui.common.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenFriend: (String) -> Unit,
    vm: FriendsViewModel = viewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var nameEdit by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("친구 근무표") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    if (ui.signedIn) TextButton(onClick = { vm.refresh() }) { Text("새로고침") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            ui.message?.let { msg ->
                Card(
                    Modifier.fillMaxWidth().clickable { vm.clearMessage() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (ui.messageIsError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                ) {
                    Text(
                        msg,
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ui.messageIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }

            when {
                !ui.available -> NotConfigured()
                !ui.signedIn -> SignIn(vm)
                else -> {
                    MyCode(
                        name = ui.profile?.displayName.orEmpty(),
                        code = ui.profile?.inviteCode.orEmpty(),
                        onCopy = { copyToClipboard(context, it) },
                        onEditName = { nameEdit = ui.profile?.displayName.orEmpty() },
                        onSignOut = { vm.signOut() },
                    )

                    if (ui.incoming.isNotEmpty()) {
                        SectionCard(title = "받은 요청 (${ui.incoming.size})") {
                            Text(
                                "수락하면 그때부터 서로 근무표를 볼 수 있습니다.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ui.incoming.forEach { req ->
                                RequestRow(
                                    name = req.displayName,
                                    enabled = !ui.loading,
                                    onAccept = { vm.acceptRequest(req.otherId) },
                                    onDismiss = { vm.dismissRequest(req.otherId) },
                                )
                            }
                        }
                    }

                    SectionCard(title = "친구 추가") {
                        Text(
                            "친구에게 받은 초대 코드를 입력하세요. 요청을 보내면 " +
                                "상대가 수락해야 서로 근무표가 보입니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it.uppercase() },
                                label = { Text("초대 코드") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    vm.addFriend(code)
                                    code = ""
                                },
                                enabled = code.isNotBlank() && !ui.loading,
                            ) { Text("요청") }
                        }

                        ui.outgoing.forEach { req ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${req.displayName.ifBlank { "이름 없음" }} · 수락 대기 중",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = { vm.dismissRequest(req.otherId) },
                                    enabled = !ui.loading,
                                ) { Text("취소") }
                            }
                        }
                    }

                    SectionCard(title = "친구 (${ui.friends.size})") {
                        if (ui.friends.isEmpty()) {
                            Text(
                                "아직 친구가 없습니다.\n초대 코드를 주고받아 요청을 보내 보세요.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            )
                        }
                        ui.friends.forEach { friend ->
                            FriendRow(
                                friend = friend,
                                overrides = ui.overrides[friend.friendId].orEmpty(),
                                onClick = { onOpenFriend(friend.friendId) },
                                onRemove = { vm.removeFriend(friend.friendId) },
                            )
                        }
                    }

                    Text(
                        "공유되는 것은 근무표뿐입니다. 일정·인수인계·점검 기록은 이 기기에만 남습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }
        }
    }

    nameEdit?.let { initial ->
        var value by remember(initial) { mutableStateOf(initial) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { nameEdit = null },
            title = { Text("표시 이름") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("친구에게 보이는 이름") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.setDisplayName(value)
                    nameEdit = null
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { nameEdit = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun NotConfigured() {
    SectionCard(title = "친구 기능이 꺼져 있습니다") {
        Text(
            "이 빌드에는 서버 키가 들어 있지 않습니다. 앱은 완전한 로컬 전용으로 동작합니다.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "친구 근무표 공유를 쓰려면 local.properties 에 supabase.url 과 supabase.anonKey 를 넣고 다시 빌드하세요. 자세한 방법은 README 를 참고하세요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignIn(vm: FriendsViewModel) {
    SectionCard(title = "로그인") {
        Text(
            "로그인하면 내 근무표가 서버에 올라가고, 친구가 볼 수 있게 됩니다.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "올라가는 것은 근무 주기와 근무 유형뿐입니다. 일정·인수인계·점검 기록은 전송되지 않습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { vm.signInGoogle() }, modifier = Modifier.fillMaxWidth()) {
            Text("Google로 로그인")
        }
        OutlinedButton(onClick = { vm.signInKakao() }, modifier = Modifier.fillMaxWidth()) {
            Text("카카오로 로그인")
        }
    }
}

@Composable
private fun MyCode(
    name: String,
    code: String,
    onCopy: (String) -> Unit,
    onEditName: () -> Unit,
    onSignOut: () -> Unit,
) {
    SectionCard(
        title = "내 초대 코드",
        trailing = { TextButton(onClick = onSignOut) { Text("로그아웃") } },
    ) {
        Text(
            if (code.isBlank()) "불러오는 중..." else formatCode(code),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "이 코드를 친구에게 알려 주면 친구가 나를 추가할 수 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onCopy(formatCode(code)) },
                enabled = code.isNotBlank(),
            ) { Text("코드 복사") }
            OutlinedButton(onClick = onEditName) {
                Text(if (name.isBlank()) "이름 설정" else name)
            }
        }
    }
}

@Composable
private fun RequestRow(
    name: String,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            name.ifBlank { "이름 없음" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, enabled = enabled) { Text("거절") }
        Button(onClick = onAccept, enabled = enabled) { Text("수락") }
    }
}

@Composable
private fun FriendRow(
    friend: FriendScheduleDto,
    overrides: Map<Long, String>,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val today = LocalDate.now()
    val label = friend.labelAt(today, overrides)
    val type = friend.typeOf(label)
    val color = parseColor(type?.color)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column(Modifier.weight(1f)) {
            Text(
                friend.displayName.ifBlank { "이름 없음" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildString {
                    append("오늘 ")
                    append(type?.name ?: label ?: "근무 정보 없음")
                    if (type != null && type.start.isNotBlank()) {
                        append(" ${type.start}~${type.end}")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRemove) { Text("삭제") }
    }
}

private fun formatCode(code: String): String =
    if (code.length == 8) "${code.take(4)}-${code.drop(4)}" else code

internal fun parseColor(hex: String?): Color =
    runCatching { Color(hex!!.toLong(16).toInt()) }.getOrDefault(Color.Gray)

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("초대 코드", text))
    Toast.makeText(context, "초대 코드를 복사했습니다.", Toast.LENGTH_SHORT).show()
}
