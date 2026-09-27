package com.opencontacts.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val bootReceiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pendingResult = goAsync()
        bootReceiverScope.launch {
            runCatching {
                val settings = EntryPointAccessors.fromApplication(context.applicationContext, IncomingCallEntryPoint::class.java)
                    .appLockRepository()
                    .settings.first()
                ensureChannels(context, settings)
                AppVisibilityTracker.setForeground(false)
            }
            pendingResult.finish()
        }
    }
}
