package com.raf.capsuleos.data.notification

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.raf.capsuleos.data.media.CapsuleNotificationListenerService
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.repository.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [CapsuleNotificationListenerService] notification alerts, live activities,
 * and background-safe PendingIntent / app launches.
 */
@Singleton
class NotificationRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationRepository {

    override fun observeNotificationEvents(): Flow<CapsuleEvent> {
        val liveFlow = CapsuleNotificationListenerService.activeLiveActivity.map { live ->
            CapsuleEvent.LiveActivityUpdated(live)
        }
        val countFlow = CapsuleNotificationListenerService.activeNotificationCount.map { count ->
            CapsuleEvent.ActiveNotificationCountUpdated(count)
        }
        return merge(liveFlow, countFlow, CapsuleNotificationListenerService.notificationEvents)
    }

    override fun currentLiveActivity(): CapsuleContent.LiveActivity? =
        CapsuleNotificationListenerService.activeLiveActivity.value

    override fun observeActiveNotificationCount(): Flow<Int> =
        CapsuleNotificationListenerService.activeNotificationCount

    override fun currentActiveNotificationCount(): Int =
        CapsuleNotificationListenerService.activeNotificationCount.value

    override fun openNotificationOrApp(key: String?, packageName: String) {
        val optionsBundle = buildBackgroundLaunchOptions()
        val pendingIntent = key?.let { CapsuleNotificationListenerService.pendingIntentFor(it) }
        if (pendingIntent != null) {
            val sent = runCatching {
                pendingIntent.send(context, 0, null, null, null, null, optionsBundle)
            }.isSuccess
            if (sent) return
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        } ?: return
        runCatching {
            context.startActivity(launchIntent, optionsBundle)
        }
    }

    override fun executeNotificationAction(key: String, actionId: Int, replyText: String?): Boolean =
        CapsuleNotificationListenerService.triggerAction(
            context = context,
            key = key,
            actionId = actionId,
            replyText = replyText,
            optionsBundle = buildBackgroundLaunchOptions(),
        )

    override fun dismissNotification(key: String) {
        CapsuleNotificationListenerService.dismissByKey(key)
    }

    private fun buildBackgroundLaunchOptions(): Bundle {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
            )
        }
        return options.toBundle()
    }
}
