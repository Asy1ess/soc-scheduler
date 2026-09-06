package com.soc.scheduler.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soc.scheduler.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 위젯에서 점검 항목을 눌렀을 때 앱을 열지 않고 바로 체크/해제한다. */
class CheckToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val runId = intent.getLongExtra(EXTRA_RUN_ID, -1L)
        if (runId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = Graph.repo.checkDao
                val current = dao.isDone(runId) ?: return@launch
                dao.setDone(runId, !current, if (!current) System.currentTimeMillis() else null)
                WidgetUpdater.updateAll(context)
            } catch (e: Exception) {
                // 위젯 탭 처리 실패가 앱을 멈추게 하지 않도록 삼킨다.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.soc.scheduler.action.TOGGLE_CHECK"
        private const val EXTRA_RUN_ID = "extra_run_id"

        fun intentFor(context: Context, runId: Long): PendingIntent {
            val intent = Intent(context, CheckToggleReceiver::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(EXTRA_RUN_ID, runId)
            return PendingIntent.getBroadcast(
                context,
                (REQUEST_BASE + runId).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val REQUEST_BASE = 700_000L
    }
}
