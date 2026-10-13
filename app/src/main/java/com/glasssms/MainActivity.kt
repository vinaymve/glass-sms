package com.glasssms

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.glasssms.ui.AppRoot
import com.glasssms.ui.theme.GlassTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            GlassTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
        if (threadId > 0) {
            vm.openThread(threadId, intent.getStringExtra(EXTRA_ADDRESS).orEmpty())
            return
        }
        val data = intent.data
        if ((intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND) &&
            data?.scheme in setOf("sms", "smsto", "mms", "mmsto")
        ) {
            val address = data?.schemeSpecificPart?.substringBefore('?').orEmpty()
            val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            vm.openNewMessage(address, body)
        }
    }

    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"

        fun defaultSmsRoleIntent(activity: ComponentActivity): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
            }
    }
}
