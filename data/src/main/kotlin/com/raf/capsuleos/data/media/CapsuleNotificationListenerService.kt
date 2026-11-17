package com.raf.capsuleos.data.media

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * [NotificationListenerService] bridging:
 * - Active [android.media.session.MediaSessionManager] playback sessions,
 * - Ongoing Live Activity notifications (timers, stopwatches, calls, navigation),
 * - Incoming non-low/non-silent notifications surfaced in the capsule view stack.
 */
class CapsuleNotificationListenerService : NotificationListenerService() {

    private val seenSignatures = ConcurrentHashMap<String, String>()
    private val appIconBytesCache = ConcurrentHashMap<String, ByteArray>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        _isConnected.value = true
        refreshLiveActivities()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        activeInstance = null
        _isConnected.value = false
        _activeLiveActivity.value = null
        pendingIntents.clear()
        cachedActions.clear()
        seenSignatures.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        super.onNotificationPosted(sbn, rankingMap)
        val statusBarNotif = sbn ?: return
        refreshLiveActivities()
        processIncomingNotification(statusBarNotif, rankingMap ?: runCatching { currentRanking }.getOrNull())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        val statusBarNotif = sbn ?: return
        pendingIntents.remove(statusBarNotif.key)
        cachedActions.remove(statusBarNotif.key)
        seenSignatures.remove(statusBarNotif.key)
        refreshLiveActivities()
        _notificationEvents.tryEmit(CapsuleEvent.NotificationRemoved(statusBarNotif.key))
    }

    private fun refreshLiveActivities() {
        val notifications = runCatching { activeNotifications }.getOrNull().orEmpty()
        _activeNotificationCount.value = notifications.count { sbn ->
            if (sbn.packageName == packageName || sbn.packageName == "android") return@count false
            val notif = sbn.notification ?: return@count false
            !sbn.isOngoing &&
                (notif.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                (notif.flags and Notification.FLAG_FOREGROUND_SERVICE) == 0 &&
                (notif.flags and Notification.FLAG_ONGOING_EVENT) == 0
        }
        val candidate = notifications.firstOrNull { sbn ->
            if (sbn.packageName == packageName) return@firstOrNull false
            val notif = sbn.notification ?: return@firstOrNull false
            val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            if (!isOngoing) return@firstOrNull false

            val hasMediaSession = notif.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
            if (hasMediaSession) return@firstOrNull false

            val category = notif.category
            val showChronometer = notif.extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
            category == Notification.CATEGORY_STOPWATCH ||
                category == Notification.CATEGORY_ALARM ||
                category == Notification.CATEGORY_CALL ||
                category == Notification.CATEGORY_PROGRESS ||
                category == Notification.CATEGORY_NAVIGATION ||
                showChronometer
        }

        val live = candidate?.toLiveActivity()
        if (candidate != null) {
            candidate.notification.contentIntent?.let { pi ->
                pendingIntents[candidate.key] = pi
            }
        }
        _activeLiveActivity.value = live
    }

    private fun processIncomingNotification(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val pkg = sbn.packageName
        if (pkg == packageName || pkg == "android") return

        val notif = sbn.notification ?: return
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (notif.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) return
        if (pkg == "com.android.systemui" && (sbn.tag == "adaptive_charging" || notif.channelId == "BAT")) return

        val ranking = Ranking()
        val hasRanking = rankingMap?.getRanking(sbn.key, ranking) == true
        if (hasRanking) {
            if (ranking.isAmbient) return
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return
            val channelImportance = ranking.channel?.importance ?: ranking.importance
            if (channelImportance < NotificationManager.IMPORTANCE_DEFAULT) return
        }

        val extras = notif.extras ?: return
        val appName = resolveAppLabel(pkg)
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val title = rawTitle.ifBlank { appName }
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val text = bigText.ifBlank { normalText }

        if (title.isBlank() && text.isBlank()) return

        val signature = "$title|$text"
        val prevSignature = seenSignatures.put(sbn.key, signature)
        if (prevSignature == signature) {
            return
        }

        notif.contentIntent?.let { pi ->
            pendingIntents[sbn.key] = pi
        }

        val rawActions = notif.actions?.toList().orEmpty()
        if (rawActions.isNotEmpty()) {
            cachedActions[sbn.key] = rawActions
        } else {
            cachedActions.remove(sbn.key)
        }
        val actionItems = rawActions.mapIndexedNotNull { idx, action ->
            val label = action.title?.toString()?.trim().orEmpty()
            if (label.isBlank() || action.actionIntent == null) return@mapIndexedNotNull null
            val firstRemoteInput = action.remoteInputs?.firstOrNull { !it.isDataOnly }
            CapsuleContent.NotificationActionItem(
                actionId = idx,
                label = label,
                supportsInlineReply = firstRemoteInput != null,
                replyHint = firstRemoteInput?.label?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            )
        }.take(3)

        val alert = CapsuleContent.NotificationAlert(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            iconBytes = loadNotificationIconBytes(sbn, notif),
            timestampMs = sbn.postTime,
            actions = actionItems,
        )
        _notificationEvents.tryEmit(CapsuleEvent.NotificationPosted(alert))
    }

    private fun resolveAppLabel(packageName: String): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

    private fun loadNotificationIconBytes(sbn: StatusBarNotification, notif: Notification): ByteArray? {
        appIconBytesCache[sbn.packageName]?.let { return it }

        val drawable = runCatching {
            packageManager.getApplicationIcon(sbn.packageName)
        }.getOrNull() ?: runCatching {
            notif.smallIcon?.loadDrawable(this)
        }.getOrNull() ?: return null

        return runCatching {
            val bitmap = drawable.toBitmap(width = 72, height = 72, config = Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.toByteArray()
            }
            bitmap.recycle()
            appIconBytesCache[sbn.packageName] = bytes
            bytes
        }.getOrNull()
    }

    private fun StatusBarNotification.toLiveActivity(): CapsuleContent.LiveActivity? {
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val whenMs = notification.`when`
        val showChrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)

        val badge = when {
            text.isNotBlank() && text.length <= 10 -> text
            subText.isNotBlank() && subText.length <= 10 -> subText
            showChrono && whenMs > 0L -> {
                val deltaSec = abs(System.currentTimeMillis() - whenMs) / 1000L
                String.format(Locale.US, "%02d:%02d", deltaSec / 60L, deltaSec % 60L)
            }
            text.isNotBlank() -> text.take(8)
            else -> "LIVE"
        }

        return CapsuleContent.LiveActivity(
            key = key,
            title = title,
            statusText = badge,
            packageName = packageName,
        )
    }

    companion object {
        @Volatile
        private var activeInstance: CapsuleNotificationListenerService? = null

        private val _isConnected = MutableStateFlow(false)
        private val _activeLiveActivity = MutableStateFlow<CapsuleContent.LiveActivity?>(null)
        private val _activeNotificationCount = MutableStateFlow(0)
        private val _notificationEvents = MutableSharedFlow<CapsuleEvent>(extraBufferCapacity = 32)
        private val pendingIntents = ConcurrentHashMap<String, PendingIntent>()
        private val cachedActions = ConcurrentHashMap<String, List<Notification.Action>>()

        /** Emits true while the system notification listener bridge is bound. */
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /** Emits the top active ongoing timer/live activity notification, or null. */
        val activeLiveActivity: StateFlow<CapsuleContent.LiveActivity?> = _activeLiveActivity.asStateFlow()

        /** Emits current clearable non-ongoing active notification count across status bar. */
        val activeNotificationCount: StateFlow<Int> = _activeNotificationCount.asStateFlow()

        /** Emits posted and removed non-silent notification events. */
        val notificationEvents: SharedFlow<CapsuleEvent> = _notificationEvents.asSharedFlow()

        /** Retrieves cached notification [PendingIntent] for launching from the capsule. */
        fun pendingIntentFor(key: String): PendingIntent? = pendingIntents[key]

        /** Executes an extracted Android [Notification.Action], including optional [RemoteInput] inline reply. */
        fun triggerAction(
            context: Context,
            key: String,
            actionId: Int,
            replyText: String?,
            optionsBundle: Bundle,
        ): Boolean {
            val action = cachedActions[key]?.getOrNull(actionId) ?: return false
            val intent = action.actionIntent ?: return false
            val remoteInputs = action.remoteInputs
            return runCatching {
                if (!replyText.isNullOrBlank() && remoteInputs != null && remoteInputs.isNotEmpty()) {
                    val fillInIntent = Intent()
                    val results = Bundle().apply {
                        remoteInputs.forEach { input ->
                            putCharSequence(input.resultKey, replyText)
                        }
                    }
                    RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, results)
                    intent.send(context, 0, fillInIntent, null, null, null, optionsBundle)
                } else {
                    intent.send(context, 0, null, null, null, null, optionsBundle)
                }
            }.isSuccess
        }

        /** Dismisses a notification from both system shade and local action caches. */
        fun dismissByKey(key: String) {
            pendingIntents.remove(key)
            cachedActions.remove(key)
            activeInstance?.runCatching { cancelNotification(key) }
        }
    }
}
