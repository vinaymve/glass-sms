package com.glasssms.otp

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.glasssms.app
import java.util.concurrent.TimeUnit

object CodeActions {
    fun copy(context: Context, code: String) {
        val clip = ClipData.newPlainText("Verification code", code).apply {
            // Hides the code from the clipboard preview on Android 13+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    /** Copies the code and, if the user opted in, schedules the message for deletion. */
    suspend fun copyAndMaybeCleanUp(context: Context, code: String, messageId: Long) {
        copy(context, code)
        val settings = context.app.settings.current()
        if (settings.deleteAfterCopy) scheduleCleanup(context, messageId, settings.deleteDelayMinutes)
    }

    fun scheduleCleanup(context: Context, messageId: Long, delayMinutes: Int) {
        val request = OneTimeWorkRequestBuilder<CodeCleanupWorker>()
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(CodeCleanupWorker.KEY_ID to messageId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("cleanup-$messageId", ExistingWorkPolicy.REPLACE, request)
    }
}

class CodeCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        if (id > 0) applicationContext.app.repo.deleteMessages(listOf(id))
        return Result.success()
    }

    companion object {
        const val KEY_ID = "message_id"
    }
}
