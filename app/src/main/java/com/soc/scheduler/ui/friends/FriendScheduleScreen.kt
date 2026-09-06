package com.soc.scheduler.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soc.scheduler.ui.common.full
import java.time.LocalDate
import java.time.YearMonth

private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScheduleScreen(
    friendId: String,
    onBack: () -> Unit,
    vm: FriendsViewModel = viewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val friend = ui.friends.firstOrNull { it.friendId == friendId }
    val overrides = ui.overrides[friendId].orEmpty()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(friend?.displayName?.ifBlank { "친구" } ?: "친구") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        }
    ) { padding ->
        if (friend == null) {
            Text(
                "친구 정보를 불러올 수 없습니다.",
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                textAlign = TextAlign.Center,
            )
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleLarge)
                    if (friend.patternName.isNotBlank()) {
                        Text(
                            friend.patternName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
                }
                TextButton(onClick = {
                    month = YearMonth.now()
                    selected = LocalDate.now()
                }) { Text("오늘") }
            }

            if (friend.cycleDays <= 0) {
                Text(
                    "이 친구는 아직 근무표를 공유하지 않았습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(Modifier.fillMaxWidth()) {
                WEEKDAYS.forEachIndexed { index, label ->
                    Text(
                        label,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (index) {
                            0 -> Color(0xFFD32F2F)
                            6 -> Color(0xFF1976D2)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            val first = month.atDay(1)
            val leading = first.dayOfWeek.value % 7
            val gridStart = first.minusDays(leading.toLong())
            val today = LocalDate.now()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 6).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..6).forEach { dow ->
                            val date = gridStart.plusDays((week * 7 + dow).toLong())
                            val inMonth = YearMonth.from(date) == month
                            val label = friend.labelAt(date, overrides)
                            val type = friend.typeOf(label)
                            val color = parseColor(type?.color)

                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color.copy(alpha = if (inMonth) 0.16f else 0.05f))
                                    .then(
                                        if (date == today) {
                                            Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.tertiary,
                                                RoundedCornerShape(8.dp),
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            date.dayOfWeek.value == 7 -> Color(0xFFD32F2F)
                                            date.dayOfWeek.value == 6 -> Color(0xFF1976D2)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        label ?: "-",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (inMonth) color else color.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val todayLabel = friend.labelAt(today, overrides)
            val todayType = friend.typeOf(todayLabel)
            Text(
                buildString {
                    append(today.full())
                    append(" · ")
                    append(todayType?.name ?: todayLabel ?: "근무 없음")
                    if (todayType != null && todayType.start.isNotBlank()) {
                        append(" ${todayType.start} ~ ${todayType.end}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
