package com.opencontacts.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Lightweight exported entry activity that lets the app qualify for dialer role
 * resolution and forwards all flows to MainActivity.
 */
class DialerEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> intent?.data?.schemeSpecificPart
            else -> null
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_dialpad", true)
                .putExtra("prefill_number", number.orEmpty()),
        )
        finish()
    }
}
