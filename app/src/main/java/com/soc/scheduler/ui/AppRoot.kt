package com.soc.scheduler.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soc.scheduler.ui.checklist.ChecklistScreen
import com.soc.scheduler.ui.checklist.TemplateScreen
import com.soc.scheduler.data.Prefs
import com.soc.scheduler.ui.friends.FriendScheduleScreen
import com.soc.scheduler.ui.friends.FriendsScreen
import com.soc.scheduler.ui.handover.HandoverScreen
import com.soc.scheduler.ui.onboarding.OnboardingScreen
import com.soc.scheduler.ui.settings.SettingsScreen
import com.soc.scheduler.ui.shift.PatternScreen
import com.soc.scheduler.ui.shift.ShiftScreen
import com.soc.scheduler.ui.task.TaskScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Shift("shift", "근무표", Icons.Default.DateRange),
    Task("task", "일정", Icons.Default.Notifications),
    Handover("handover", "인계", Icons.Default.Edit),
    Check("check", "점검", Icons.Default.CheckCircle),
    Settings("settings", "설정", Icons.Default.Settings),
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var setupDone by remember { mutableStateOf(Prefs.isSetupDone(context)) }

    if (!setupDone) {
        OnboardingScreen(
            onDone = {
                Prefs.setSetupDone(context, true)
                setupDone = true
            }
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = Tab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Shift.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Shift.route) { ShiftScreen() }
            composable(Tab.Task.route) { TaskScreen() }
            composable(Tab.Handover.route) { HandoverScreen() }
            composable(Tab.Check.route) {
                ChecklistScreen(onManageTemplates = { navController.navigate("templates") })
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    onEditPattern = { navController.navigate("pattern") },
                    onManageTemplates = { navController.navigate("templates") },
                    onRerunSetup = { navController.navigate("setup") },
                    onOpenFriends = { navController.navigate("friends") },
                )
            }
            composable("setup") {
                OnboardingScreen(
                    onDone = { navController.popBackStack() },
                    editMode = true,
                    onCancel = { navController.popBackStack() },
                )
            }
            composable("friends") {
                FriendsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFriend = { id -> navController.navigate("friend/$id") },
                )
            }
            composable("friend/{friendId}") { entry ->
                FriendScheduleScreen(
                    friendId = entry.arguments?.getString("friendId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("pattern") { PatternScreen(onBack = { navController.popBackStack() }) }
            composable("templates") { TemplateScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
