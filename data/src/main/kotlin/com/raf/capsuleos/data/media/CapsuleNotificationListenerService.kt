package com.raf.capsuleos.data.media

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * [NotificationListenerService] bridging:
 * - Active [android.media.session.MediaSessionManager] playback sessions,
 * - Ongoing Live Activity notifications (timers, stopwatches, calls, navigation),
 * - Incoming non-low/non-silent notifications surfaced in the capsule view stack.
 */
class CapsuleNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val seenSignatures = ConcurrentHashMap<String, String>()
    private val appIconBytesCache = ConcurrentHashMap<String, ByteArray>()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        _isConnected.value = true
        serviceScope.launch {
            refreshLiveActivities()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        activeInstance = null
        _isConnected.value = false
        _activeLiveActivity.value = null
        _activeCall.value = null
        pendingIntents.clear()
        cachedActions.clear()
        callAnswerIntents.clear()
        callDeclineIntents.clear()
        seenSignatures.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        super.onNotificationPosted(sbn, rankingMap)
        val statusBarNotif = sbn ?: return
        serviceScope.launch {
            refreshLiveActivities()
            processIncomingNotification(
                statusBarNotif,
                rankingMap ?: runCatching { currentRanking }.getOrNull(),
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        val statusBarNotif = sbn ?: return
        pendingIntents.remove(statusBarNotif.key)
        cachedActions.remove(statusBarNotif.key)
        callAnswerIntents.remove(statusBarNotif.key)
        callDeclineIntents.remove(statusBarNotif.key)
        seenSignatures.remove(statusBarNotif.key)
        _notificationEvents.tryEmit(CapsuleEvent.NotificationRemoved(statusBarNotif.key))
        serviceScope.launch {
            refreshLiveActivities()
        }
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

        val activeKeys = notifications.mapTo(mutableSetOf()) { it.key }
        callAnswerIntents.keys.retainAll(activeKeys)
        callDeclineIntents.keys.retainAll(activeKeys)

        val callCandidate = notifications.firstOrNull { sbn ->
            if (sbn.packageName == packageName) return@firstOrNull false
            sbn.isActiveCallNotification()
        }
        val mappedCall = callCandidate?.toCallAlert()
        if (callCandidate != null && mappedCall != null) {
            callCandidate.notification.contentIntent?.let { pi ->
                pendingIntents[callCandidate.key] = pi
            }
        }
        _activeCall.value = mappedCall

        val candidate = notifications.firstOrNull { sbn ->
            if (sbn.packageName == packageName) return@firstOrNull false
            if (sbn.key == callCandidate?.key) return@firstOrNull false
            val notif = sbn.notification ?: return@firstOrNull false
            val isOngoing = sbn.isOngoing || (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            if (!isOngoing) return@firstOrNull false

            val hasMediaSession = notif.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
            if (hasMediaSession) return@firstOrNull false

            val category = notif.category
            val showChronometer = notif.extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
            category == Notification.CATEGORY_STOPWATCH ||
                category == Notification.CATEGORY_ALARM ||
                category == Notification.CATEGORY_PROGRESS ||
                category == Notification.CATEGORY_NAVIGATION ||
                showChronometer
        }

        val live = candidate?.toLiveActivity()
        if (candidate != null) {
            candidate.notification.contentIntent?.let { pi ->
                pendingIntents[candidate.key] = pi
            }
            val liveRawActions = candidate.notification.actions?.toList().orEmpty()
            if (liveRawActions.isNotEmpty()) {
                cachedActions[candidate.key] = liveRawActions
            } else {
                cachedActions.remove(candidate.key)
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
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            ?.joinToString(separator = "\n")
            .orEmpty()
        val messagingLines = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
            ?.sortedBy { it.getLong("time", 0L) }
            ?.mapNotNull { bundle ->
                val personSender = bundle.getParcelable("sender_person", Person::class.java)
                    ?.name?.toString()?.trim().orEmpty()
                val sender = personSender.ifBlank {
                    bundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
                }
                val msg = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                when {
                    msg.isBlank() -> null
                    sender.isNotBlank() && sender != title -> "$sender: $msg"
                    else -> msg
                }
            }
            ?.joinToString(separator = "\n")
            .orEmpty()
        val text = listOf(messagingLines, bigText, textLines, normalText)
            .filter { it.isNotBlank() }
            .maxByOrNull { it.length }
            .orEmpty()

        val (attachmentBytes, attachmentMime) = extractNotificationAttachment(extras)
        if (title.isBlank() && text.isBlank() && attachmentBytes == null) return

        val signature = "$title|$text|${attachmentBytes?.contentHashCode() ?: 0}|$attachmentMime"
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
            attachmentImageBytes = attachmentBytes,
            attachmentMimeType = attachmentMime,
        )
        _notificationEvents.tryEmit(CapsuleEvent.NotificationPosted(alert))
    }

    private fun extractNotificationAttachment(extras: Bundle): Pair<ByteArray?, String?> {
        // 1. MessagingStyle attachments (photos, GIFs, WebP stickers from WhatsApp/Telegram/Messages)
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
        if (messages != null) {
            for (idx in messages.lastIndex downTo 0) {
                val msg = messages[idx]
                val mime = msg.getString("type")?.lowercase(Locale.US)?.trim().orEmpty()
                val uri = msg.getParcelable("uri", Uri::class.java)
                if (uri != null && mime.startsWith("image/")) {
                    val loaded = readImageAttachmentFromUri(uri, mime)
                    if (loaded != null) {
                        return loaded to mime
                    }
                }
            }
        }

        // 2. BigPictureStyle Icon (Android 12+)
        val pictureIcon = extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
        if (pictureIcon != null) {
            val bytes = runCatching {
                val drawable = pictureIcon.loadDrawable(this) ?: return@runCatching null
                val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
                val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
                val scale = (MAX_ATTACHMENT_DIM_PX.toFloat() / maxOf(rawW, rawH)).coerceAtMost(1f)
                val targetW = (rawW * scale).toInt().coerceAtLeast(1)
                val targetH = (rawH * scale).toInt().coerceAtLeast(1)
                val bitmap = drawable.toBitmap(width = targetW, height = targetH, config = Bitmap.Config.ARGB_8888)
                compressAndRecycleBitmap(bitmap)
            }.getOrNull()
            if (bytes != null) return bytes to "image/png"
        }

        // 3. BigPictureStyle Bitmap
        val pictureBitmap = extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        if (pictureBitmap != null) {
            val bytes = encodeScaledBitmap(pictureBitmap)
            if (bytes != null) return bytes to "image/png"
        }

        return null to null
    }

    private fun readImageAttachmentFromUri(uri: Uri, mimeType: String): ByteArray? = runCatching {
        val rawBytes = contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                total += read
                if (total > MAX_ANIMATED_ATTACHMENT_BYTES) return@use null
                out.write(buf, 0, read)
            }
            out.toByteArray()
        } ?: return@runCatching null
        if (rawBytes.isEmpty()) return@runCatching null

        // Preserve raw bytes for GIF & WebP so ImageDecoder can animate GIFs and animated stickers
        if (mimeType == "image/gif" || mimeType == "image/webp") {
            return@runCatching rawBytes
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxDim <= 0) return@runCatching rawBytes
        var sample = 1
        while (maxDim / sample > MAX_ATTACHMENT_DIM_PX * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            rawBytes,
            0,
            rawBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return@runCatching null
        val encoded = encodeScaledBitmap(decoded)
        decoded.recycle()
        encoded
    }.getOrNull()

    private fun encodeScaledBitmap(source: Bitmap): ByteArray? = runCatching {
        val maxDim = maxOf(source.width, source.height).coerceAtLeast(1)
        val scale = (MAX_ATTACHMENT_DIM_PX.toFloat() / maxDim).coerceAtMost(1f)
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 0.99f) {
            source.scale(targetW, targetH)
        } else {
            source
        }
        val outBytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.toByteArray()
        }
        if (scaled !== source) {
            scaled.recycle()
        }
        outBytes
    }.getOrNull()

    private fun compressAndRecycleBitmap(bitmap: Bitmap): ByteArray {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.toByteArray()
        }
        bitmap.recycle()
        return bytes
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

    private fun StatusBarNotification.isActiveCallNotification(): Boolean {
        val notif = notification ?: return false
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return false
        val extras = notif.extras
        val template = extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return notif.category == Notification.CATEGORY_CALL || template.contains("CallStyle")
    }

    private fun StatusBarNotification.toCallAlert(): CapsuleContent.CallAlert? {
        val notif = notification ?: return null
        val extras = notif.extras ?: return null
        val appName = resolveAppLabel(packageName)
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val callerName = rawTitle.ifBlank { appName }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val showChrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)

        val answerExtra = extras.getParcelable(Notification.EXTRA_ANSWER_INTENT, PendingIntent::class.java)
        val declineExtra = extras.getParcelable(Notification.EXTRA_DECLINE_INTENT, PendingIntent::class.java)
            ?: extras.getParcelable(Notification.EXTRA_HANG_UP_INTENT, PendingIntent::class.java)

        val rawActions = notif.actions?.toList().orEmpty()
        val answerFromAction = rawActions.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase(Locale.US).orEmpty()
            label.contains("answer") || label.contains("accept")
        }?.actionIntent
        val declineFromAction = rawActions.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase(Locale.US).orEmpty()
            label.contains("decline") || label.contains("reject") || label.contains("hang up") || label.contains("end")
        }?.actionIntent

        val answerPi = answerExtra ?: answerFromAction
        val declinePi = declineExtra ?: declineFromAction

        if (answerPi != null) callAnswerIntents[key] = answerPi else callAnswerIntents.remove(key)
        if (declinePi != null) callDeclineIntents[key] = declinePi else callDeclineIntents.remove(key)

        val isIncoming = answerPi != null && !showChrono
        val isOngoing = answerPi == null && (showChrono || declinePi != null)
        val statusText = when {
            text.isNotBlank() -> text
            isIncoming -> "Incoming call"
            else -> "Ongoing call"
        }

        return CapsuleContent.CallAlert(
            key = key,
            callerName = callerName,
            callStatus = statusText,
            packageName = packageName,
            appName = appName,
            appIconBytes = loadNotificationIconBytes(this, notif),
            isIncoming = isIncoming,
            hasAnswerAction = answerPi != null,
            hasDeclineAction = declinePi != null,
            isOngoing = isOngoing,
            startedAtMs = if (notif.`when` > 0L) notif.`when` else postTime,
        )
    }

    private fun StatusBarNotification.toLiveActivity(): CapsuleContent.LiveActivity? {
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val whenMs = notification.`when`
        val showChrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val isCountdown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val effectiveWhenMs = when {
            whenMs > 0L -> whenMs
            showChrono && postTime > 0L -> postTime
            else -> 0L
        }
        val hasLiveChrono = showChrono && effectiveWhenMs > 0L

        val initialChrono = if (hasLiveChrono) {
            val now = System.currentTimeMillis()
            val deltaSec = if (isCountdown) {
                ((effectiveWhenMs - now).coerceAtLeast(0L) + 999L) / 1000L
            } else {
                (now - effectiveWhenMs).coerceAtLeast(0L) / 1000L
            }
            val hours = deltaSec / 3600L
            val minutes = (deltaSec % 3600L) / 60L
            val seconds = deltaSec % 60L
            if (hours > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        } else {
            null
        }

        val badge = when {
            initialChrono != null -> initialChrono
            text.isNotBlank() && text.length <= 10 -> text
            subText.isNotBlank() && subText.length <= 10 -> subText
            text.isNotBlank() -> text.take(8)
            else -> "LIVE"
        }
        val subtitle = listOf(bigText, text, subText)
            .firstOrNull { it.isNotBlank() && it != badge }
            .orEmpty()

        val rawActions = notification.actions?.toList().orEmpty()
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

        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val rawProgress = extras.getInt(Notification.EXTRA_PROGRESS, -1)
        val isIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val validProgress = !isIndeterminate && progressMax > 0 && rawProgress in 0..progressMax

        return CapsuleContent.LiveActivity(
            key = key,
            title = title,
            statusText = badge,
            subtitle = subtitle,
            packageName = packageName,
            iconBytes = loadNotificationIconBytes(this, notification),
            showChronometer = hasLiveChrono,
            chronometerBaseEpochMs = if (hasLiveChrono) effectiveWhenMs else 0L,
            isCountdown = isCountdown,
            actions = actionItems,
            progress = if (validProgress) rawProgress else -1,
            progressMax = if (validProgress) progressMax else 0,
        )
    }

    companion object {
        @Volatile
        private var activeInstance: CapsuleNotificationListenerService? = null

        private val _isConnected = MutableStateFlow(false)
        private val _activeLiveActivity = MutableStateFlow<CapsuleContent.LiveActivity?>(null)
        private val _activeCall = MutableStateFlow<CapsuleContent.CallAlert?>(null)
        private val _activeNotificationCount = MutableStateFlow(0)
        private val _notificationEvents = MutableSharedFlow<CapsuleEvent>(extraBufferCapacity = 32)
        private val pendingIntents = ConcurrentHashMap<String, PendingIntent>()
        private val cachedActions = ConcurrentHashMap<String, List<Notification.Action>>()
        private val callAnswerIntents = ConcurrentHashMap<String, PendingIntent>()
        private val callDeclineIntents = ConcurrentHashMap<String, PendingIntent>()

        /** Emits true while the system notification listener bridge is bound. */
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /** Emits the top active ongoing timer/live activity notification, or null. */
        val activeLiveActivity: StateFlow<CapsuleContent.LiveActivity?> = _activeLiveActivity.asStateFlow()

        /** Emits the top active incoming or ongoing call notification, or null. */
        val activeCall: StateFlow<CapsuleContent.CallAlert?> = _activeCall.asStateFlow()

        /** Executes the cached Answer PendingIntent for call notification [key]. */
        fun triggerCallAnswer(context: Context, key: String, optionsBundle: Bundle): Boolean {
            val pi = callAnswerIntents[key] ?: return false
            return runCatching {
                pi.send(context, 0, null, null, null, null, optionsBundle)
            }.isSuccess
        }

        /** Executes the cached Decline / Hang-up PendingIntent for call notification [key]. */
        fun triggerCallDecline(context: Context, key: String, optionsBundle: Bundle): Boolean {
            val pi = callDeclineIntents[key] ?: return false
            return runCatching {
                pi.send(context, 0, null, null, null, null, optionsBundle)
            }.isSuccess
        }

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
            callAnswerIntents.remove(key)
            callDeclineIntents.remove(key)
            activeInstance?.runCatching { cancelNotification(key) }
        }

        private const val MAX_ATTACHMENT_DIM_PX = 512
        private const val MAX_ANIMATED_ATTACHMENT_BYTES = 2 * 1024 * 1024
    }
}
