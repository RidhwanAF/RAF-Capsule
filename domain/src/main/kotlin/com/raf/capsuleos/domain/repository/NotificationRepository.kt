package com.raf.capsuleos.domain.repository

import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for observing non-silent system notifications and ongoing live activity notifications,
 * and opening their associated application or pending intent.
 */
interface NotificationRepository {
    /** Emits [CapsuleEvent.NotificationPosted], [CapsuleEvent.NotificationRemoved], and [CapsuleEvent.LiveActivityUpdated]. */
    fun observeNotificationEvents(): Flow<CapsuleEvent>

    /** Returns the current active live activity notification if any. */
    fun currentLiveActivity(): CapsuleContent.LiveActivity?

    /** Returns the current active incoming or ongoing call notification if any. */
    fun currentActiveCall(): CapsuleContent.CallAlert? = null

    /** Executes the active call notification Answer PendingIntent when supported. */
    fun answerCall(key: String): Boolean = false

    /** Executes the active call notification Decline / Hang-up PendingIntent when supported. */
    fun declineCall(key: String): Boolean = false

    /** Launches the cached notification PendingIntent or application launch intent. */
    fun openNotificationOrApp(key: String?, packageName: String)

    /** Executes an interactive notification action button, including optional inline [replyText]. */
    fun executeNotificationAction(key: String, actionId: Int, replyText: String? = null): Boolean

    /** Cancels/dismisses the active system notification matching [key]. */
    fun dismissNotification(key: String)

    /** Emits live counts of active clearable status-bar notifications. */
    fun observeActiveNotificationCount(): Flow<Int>

    /** Reads the current active notification count synchronously. */
    fun currentActiveNotificationCount(): Int
}
