package com.glasssms.data

import android.provider.Telephony.Sms
import com.glasssms.otp.Otp

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
) {
    val isOutgoing get() = type != Sms.MESSAGE_TYPE_INBOX
    val isFailed get() = type == Sms.MESSAGE_TYPE_FAILED
    val isPending get() = type == Sms.MESSAGE_TYPE_OUTBOX || type == Sms.MESSAGE_TYPE_QUEUED
}

data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val isKnown: Boolean,
    val latest: Message,
    val unreadCount: Int,
    /** Code detected in the latest message, if it is an incoming one. */
    val otp: Otp?,
)

data class OtpEntry(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val date: Long,
    val otp: Otp,
) {
    fun expiresAt() = date + otp.validForMillis
    fun isActive(now: Long) = now < expiresAt()
}
