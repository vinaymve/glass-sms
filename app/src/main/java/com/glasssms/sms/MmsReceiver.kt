package com.glasssms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Declared so Android lets this app hold the default SMS role. Downloading and storing MMS
 * needs the carrier's MMSC settings and is not implemented yet.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
