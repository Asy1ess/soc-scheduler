package com.soc.scheduler

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import com.soc.scheduler.remote.Supa
import io.github.jan.supabase.auth.handleDeeplinks
import com.soc.scheduler.ui.AppRoot
import com.soc.scheduler.ui.theme.SocSchedulerTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        restoreSessionFromDeeplink(intent)

        setContent {
            SocSchedulerTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        restoreSessionFromDeeplink(intent)
    }

    /** 소셜 로그인 후 돌아온 딥링크에서 세션을 복원한다. */
    private fun restoreSessionFromDeeplink(intent: Intent?) {
        if (!Supa.isConfigured || intent == null) return
        runCatching { Supa.client.handleDeeplinks(intent) }
    }
}
