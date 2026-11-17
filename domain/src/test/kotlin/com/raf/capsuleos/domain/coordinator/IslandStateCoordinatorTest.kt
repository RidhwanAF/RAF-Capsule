package com.raf.capsuleos.domain.coordinator

import app.cash.turbine.test
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.BrightnessInfo
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEffect
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.StreamVolumeInfo
import com.raf.capsuleos.domain.model.UnlockMethod
import com.raf.capsuleos.domain.model.WeatherConditionKind
import com.raf.capsuleos.domain.model.WeatherSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IslandStateCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createCoordinator(): IslandStateCoordinator =
        IslandStateCoordinator(
            scope = testScope.backgroundScope,
            clockMillis = { testScope.testScheduler.currentTime },
        )

    @Test
    fun `given idle 1_1 circle, when media plays, then stays in state2 without hiding status bar until state3`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    belowStatusBar = false,
                    autoHideStatusBarIcons = true,
                ),
            )

            val media = sampleMedia()

            coordinator.uiState.test {
                val initial = awaitItem()
                assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, initial.visualState)
                assertEquals(initial.anchorWidthDp, initial.anchorHeightDp, 0.01f)
                assertFalse(initial.shouldHideStatusBarIcons)

                coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
                runCurrent()

                val sidePill = expectMostRecentItem()
                assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, sidePill.visualState)
                assertEquals(media, sidePill.content)
                // State 2 must NOT hide status bar icons (item #5)
                assertFalse(sidePill.shouldHideStatusBarIcons)

                coordinator.onEvent(CapsuleEvent.TapCapsule)
                runCurrent()

                val cardExpanded = expectMostRecentItem()
                assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, cardExpanded.visualState)
                assertTrue(cardExpanded.shouldHideStatusBarIcons)

                coordinator.onEvent(CapsuleEvent.MediaUpdated(null))
                runCurrent()

                val collapsed = expectMostRecentItem()
                assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, collapsed.visualState)
                assertFalse(collapsed.shouldHideStatusBarIcons)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given state2 stack with 2s dwell lock, when rapid events arrive, then holds current icon for at least 2s before switching`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 4000L))

            val media = sampleMedia()
            val alarm = CapsuleContent.Alarm(
                triggerTimeMillis = 123_456L,
                formattedTime = "07:00",
                isSet = true,
            )

            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            advanceTimeBy(500L)
            runCurrent()

            coordinator.onEvent(CapsuleEvent.AlarmUpdated(alarm))
            runCurrent()
            // Still holds media because < 2000ms has elapsed since media started showing
            assertEquals(media, coordinator.uiState.value.content)

            advanceTimeBy(1499L)
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            advanceTimeBy(2L)
            runCurrent()
            // Once >= 2000ms elapses, stack switches smoothly to queued alarm
            assertEquals(alarm, coordinator.uiState.value.content)
        }

    @Test
    fun `given notification alert with actions, when tapped in state2 expands card and tap inside card keeps actions interactive`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 4000L))
            val actions = listOf(
                CapsuleContent.NotificationActionItem(actionId = 0, label = "Reply", supportsInlineReply = true, replyHint = "Reply..."),
                CapsuleContent.NotificationActionItem(actionId = 1, label = "Mark read", supportsInlineReply = false),
            )
            val notif = CapsuleContent.NotificationAlert(
                key = "0|com.google.android.gm|7",
                packageName = "com.google.android.gm",
                appName = "Gmail",
                title = "Design Review",
                text = "Looks good!",
                iconBytes = null,
                timestampMs = 200L,
                actions = actions,
            )

            coordinator.effects.test {
                coordinator.onEvent(CapsuleEvent.NotificationPosted(notif))
                runCurrent()
                assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

                coordinator.onEvent(CapsuleEvent.TapCapsule)
                runCurrent()
                assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
                val expandedAlert = coordinator.uiState.value.content as CapsuleContent.NotificationAlert
                assertEquals(2, expandedAlert.actions.size)

                // Point 9: Wait 2.5s then tap inside State 3 keeps card interactive (no premature auto-launch)
                advanceTimeBy(2500L)
                runCurrent()
                coordinator.onEvent(CapsuleEvent.TapCapsule)
                runCurrent()
                expectNoEvents()
                assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

                advanceTimeBy(4001L)
                runCurrent()
                assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given live activity without big card, when tapped in state2, then opens app directly`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val timer = CapsuleContent.LiveActivity(
                key = "0|com.google.android.deskclock|10",
                title = "Timer",
                statusText = "03:15",
                packageName = "com.google.android.deskclock",
            )

            coordinator.effects.test {
                coordinator.onEvent(CapsuleEvent.LiveActivityUpdated(timer))
                runCurrent()
                assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

                coordinator.onEvent(CapsuleEvent.TapCapsule)
                runCurrent()
                assertEquals(
                    CapsuleEffect.LaunchApp(
                        packageName = "com.google.android.deskclock",
                        notificationKey = "0|com.google.android.deskclock|10",
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given state3 expanded card, when user interacts, then resets auto collapse timer`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 3000L))

            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            advanceTimeBy(2500L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
            runCurrent()

            advanceTimeBy(2500L)
            runCurrent()
            // Still open 5.0s after initial expand because interaction reset the 3s timer
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            advanceTimeBy(600L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given hold peek on battery vs charging, then shows BatteryPeek on battery and Charging icon on charge`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 74, isCharging = false))
            runCurrent()

            coordinator.onEvent(CapsuleEvent.HoldPeekStarted)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.BatteryPeek(74), coordinator.uiState.value.content)

            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)

            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 45, isCharging = true))
            runCurrent()
            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
            runCurrent()

            coordinator.onEvent(CapsuleEvent.HoldPeekStarted)
            runCurrent()
            assertEquals(CapsuleContent.Charging(45), coordinator.uiState.value.content)
        }

    @Test
    fun `given AOD or screen off event, then hides capsule completely`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(sampleMedia()))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

            coordinator.onEvent(CapsuleEvent.ScreenInactiveChanged(isScreenInactive = true))
            runCurrent()
            assertEquals(CapsuleVisualState.HIDDEN_SYSTEM, coordinator.uiState.value.visualState)

            coordinator.onEvent(CapsuleEvent.ScreenInactiveChanged(isScreenInactive = false))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given expanded state3 card, when redundant non-changing orientation or display events arrive, then stays expanded`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 4000L))

            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            // Simulate Pixel ARR onDisplayChanged firing unchanged portrait/screen-on events
            coordinator.onEvent(CapsuleEvent.OrientationChanged(isLandscape = false))
            coordinator.onEvent(CapsuleEvent.ScreenInactiveChanged(isScreenInactive = false))
            runCurrent()

            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            advanceTimeBy(4001L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given now brief expanded, when volume updates or torch enables, then updates live and morphs to torch card`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertTrue(coordinator.uiState.value.content is CapsuleContent.NowBrief)

            val updatedVolume = CapsuleContent.Volume(
                activeStream = AudioStreamType.MEDIA,
                streams = listOf(StreamVolumeInfo(AudioStreamType.MEDIA, 12, 15)),
            )
            coordinator.onEvent(CapsuleEvent.VolumeChanged(updatedVolume))
            runCurrent()
            val briefAfterVolume = coordinator.uiState.value.content as CapsuleContent.NowBrief
            assertEquals(12, briefAfterVolume.volume.streams.first().current)

            val torchOn = CapsuleContent.Torch(isEnabled = true, strengthLevel = 3, maxStrengthLevel = 5)
            assertEquals(60, torchOn.brightnessPercent)
            coordinator.onEvent(CapsuleEvent.TorchUpdated(torchOn))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertEquals(torchOn, coordinator.uiState.value.content)
        }

    @Test
    fun `given media and charging active, when cycling views, then returns to media priority after 10 seconds`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val media = sampleMedia()
            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 50, isCharging = true))
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            advanceTimeBy(2100L)
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            coordinator.onEvent(CapsuleEvent.CycleContent(direction = 1))
            runCurrent()
            assertTrue(coordinator.uiState.value.content is CapsuleContent.Charging)

            advanceTimeBy(9999L)
            runCurrent()
            assertTrue(coordinator.uiState.value.content is CapsuleContent.Charging)

            advanceTimeBy(2L)
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)
        }

    @Test
    fun `given device unlock event, when tapped expands card and auto collapses after delay`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 4000L))
            val unlock = CapsuleContent.Unlock(
                unlockMethod = UnlockMethod.FINGERPRINT,
                nextAlarmLabel = "07:00",
            )

            coordinator.onEvent(CapsuleEvent.DeviceUnlocked(unlock))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(unlock, coordinator.uiState.value.content)

            coordinator.onEvent(CapsuleEvent.TapCapsule)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertEquals(unlock, coordinator.uiState.value.content)

            advanceTimeBy(4001L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given idle or media state, resolveExpandTargetContent and resolveCollapseTarget match vertical scrub targets`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            assertTrue(coordinator.resolveExpandTargetContent() is CapsuleContent.NowBrief)

            val media = sampleMedia()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            assertEquals(media, coordinator.resolveExpandTargetContent())

            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(
                CapsuleVisualState.STATE2_HORIZONTAL_PILL to media,
                coordinator.resolveCollapseTarget(),
            )
        }

    @Test
    fun `given notification alert untouched with long autoCollapseDelayMs, then auto dismisses within 5 seconds max`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 10_000L))
            val notif = CapsuleContent.NotificationAlert(
                key = "0|com.whatsapp|99",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Sam",
                text = "Ping",
                iconBytes = null,
                timestampMs = 100L,
            )

            coordinator.onEvent(CapsuleEvent.NotificationPosted(notif))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

            // Re-posting identical notification signature must not extend timeout
            advanceTimeBy(3_000L)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.NotificationPosted(notif))
            runCurrent()

            advanceTimeBy(1_499L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

            advanceTimeBy(2L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given hold peek on charging when background media exists, when expanded, then opens NowBrief instead of jumping to media`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val media = sampleMedia()
            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 62, isCharging = true))
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            advanceTimeBy(2100L)
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            // Cycle to Charging pill, then expand: must expand NowBrief (owning context), NOT jump to media
            coordinator.onEvent(CapsuleEvent.CycleContent(direction = 1))
            runCurrent()
            assertTrue(coordinator.uiState.value.content is CapsuleContent.Charging)

            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertTrue(coordinator.uiState.value.content is CapsuleContent.NowBrief)
        }

    @Test
    fun `given torch card expanded, when turned off, then delays collapse per autoCollapseDelayMs and resets on interaction`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 4000L))

            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.TorchUpdated(CapsuleContent.Torch(isEnabled = true, strengthLevel = 3, maxStrengthLevel = 5)))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            // Point 3: turning off must NOT shrink immediately
            coordinator.onEvent(CapsuleEvent.TorchUpdated(CapsuleContent.Torch(isEnabled = false, strengthLevel = 3, maxStrengthLevel = 5)))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            val offTorch = coordinator.uiState.value.content as CapsuleContent.Torch
            assertFalse(offTorch.isEnabled)

            advanceTimeBy(3000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            // Interaction resets collapse timer again
            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
            runCurrent()
            advanceTimeBy(2500L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            advanceTimeBy(1600L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given multiple active items, when idle after swipe, returns to canonical priority notifications then media then live activity then other`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 4000L))
            val media = sampleMedia()
            val timer = CapsuleContent.LiveActivity(
                key = "0|com.google.android.deskclock|11",
                title = "Timer",
                statusText = "04:00",
                packageName = "com.google.android.deskclock",
            )
            val notif = CapsuleContent.NotificationAlert(
                key = "0|com.whatsapp|55",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Alex",
                text = "Here",
                iconBytes = null,
                timestampMs = 100L,
            )

            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 65, isCharging = true))
            coordinator.onEvent(CapsuleEvent.LiveActivityUpdated(timer))
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            coordinator.onEvent(CapsuleEvent.NotificationPosted(notif))
            runCurrent()
            // Notification is top priority even while Media and LiveActivity are active
            assertEquals(notif, coordinator.uiState.value.content)

            // After transient notification auto-dismisses at 4.5s, resting top priority is Media
            advanceTimeBy(4_501L)
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            // Swipe away from active Media to LiveActivity or Charging
            coordinator.onEvent(CapsuleEvent.CycleContent(direction = 1))
            runCurrent()
            assertTrue(coordinator.uiState.value.content != media)

            // After 10 seconds with no user interaction, returns strictly to resting top priority: Media
            advanceTimeBy(10_001L)
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            // When Media stops, falls back strictly to LiveActivity over Charging
            coordinator.onEvent(CapsuleEvent.MediaUpdated(null))
            runCurrent()
            advanceTimeBy(2_001L)
            runCurrent()
            assertEquals(timer, coordinator.uiState.value.content)
        }

    @Test
    fun `given now brief expanded, when brightness weather ringer and notification count update, then updates live`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()

            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertTrue(coordinator.uiState.value.content is CapsuleContent.NowBrief)

            val updatedBrightness = BrightnessInfo(levelPercent = 72, isAuto = false, canWrite = true)
            val updatedWeather = WeatherSummary(
                temperatureCelsius = 16,
                conditionLabel = "Clear",
                conditionKind = WeatherConditionKind.CLEAR,
            )
            val updatedVolume = CapsuleContent.Volume(
                activeStream = AudioStreamType.MEDIA,
                streams = listOf(StreamVolumeInfo(AudioStreamType.MEDIA, current = 9, max = 15)),
                ringerMode = RingerModeState.VIBRATE,
            )

            coordinator.onEvent(CapsuleEvent.BrightnessChanged(updatedBrightness))
            coordinator.onEvent(CapsuleEvent.WeatherUpdated(updatedWeather))
            coordinator.onEvent(CapsuleEvent.ActiveNotificationCountUpdated(4))
            coordinator.onEvent(CapsuleEvent.VolumeChanged(updatedVolume))
            runCurrent()

            val brief = coordinator.uiState.value.content as CapsuleContent.NowBrief
            assertEquals(updatedBrightness, brief.brightness)
            assertEquals(updatedWeather, brief.weather)
            assertEquals(4, brief.activeNotificationCount)
            assertEquals(RingerModeState.VIBRATE, brief.volume.ringerMode)
            assertEquals(9, brief.volume.streams.first().current)
        }

    @Test
    fun `given brightness weather and notif count cached before expand, when expanding to now brief, then populates snapshot`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val cachedBrightness = BrightnessInfo(levelPercent = 38, isAuto = true, canWrite = true)
            val cachedWeather = WeatherSummary(
                temperatureCelsius = 11,
                conditionLabel = "Rain",
                conditionKind = WeatherConditionKind.RAIN,
            )

            coordinator.onEvent(CapsuleEvent.BrightnessChanged(cachedBrightness))
            coordinator.onEvent(CapsuleEvent.WeatherUpdated(cachedWeather))
            coordinator.onEvent(CapsuleEvent.ActiveNotificationCountUpdated(2))
            runCurrent()

            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)

            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()

            val brief = coordinator.uiState.value.content as CapsuleContent.NowBrief
            assertEquals(cachedBrightness, brief.brightness)
            assertEquals(cachedWeather, brief.weather)
            assertEquals(2, brief.activeNotificationCount)
        }

    @Test
    fun `given single item in state2 pill, when swiped horizontally, then increments jiggle without switching or locking override`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 70, isCharging = false))
            coordinator.onEvent(CapsuleEvent.HoldPeekStarted)
            runCurrent()

            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            val batteryContent = coordinator.uiState.value.content
            assertTrue(batteryContent is CapsuleContent.BatteryPeek)
            val prevSwipeSeq = coordinator.uiState.value.manualSwipeSeq
            val prevJiggleSeq = coordinator.uiState.value.swipeJiggleSeq

            coordinator.onEvent(CapsuleEvent.CycleContent(direction = 1))
            runCurrent()

            assertEquals(prevJiggleSeq + 1, coordinator.uiState.value.swipeJiggleSeq)
            assertEquals(prevSwipeSeq, coordinator.uiState.value.manualSwipeSeq)
            assertEquals(1, coordinator.uiState.value.swipeDirection)
            assertEquals(batteryContent, coordinator.uiState.value.content)
        }

    @Test
    fun `given media playing and swiped to battery, when expanded and collapsed, then stays on battery until 10s inactivity returns to media`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val media = sampleMedia()
            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 78, isCharging = false))
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            advanceTimeBy(2_100L)
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            // Swipe from Media -> default Battery content
            coordinator.onEvent(CapsuleEvent.CycleContent(direction = 1))
            runCurrent()
            assertEquals(CapsuleContent.BatteryPeek(78), coordinator.uiState.value.content)

            // Expand Battery -> NowBrief, then collapse -> must stay on Battery (latest state), NOT jump straight to Media
            coordinator.onEvent(CapsuleEvent.TapCapsule)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertTrue(coordinator.uiState.value.content is CapsuleContent.NowBrief)

            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.BatteryPeek(78), coordinator.uiState.value.content)
            assertEquals(CapsuleContent.BatteryPeek(78), coordinator.resolveCollapseTarget().second)

            // Still on Battery just before 10s inactivity elapses
            advanceTimeBy(9_900L)
            runCurrent()
            assertEquals(CapsuleContent.BatteryPeek(78), coordinator.uiState.value.content)

            // After 10s user inactivity, returns automatically to top priority Media
            advanceTimeBy(200L)
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)
        }

    @Test
    fun `given hold on camera ring when media resumes mid-hold, then still expands NowBrief instead of media`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 65, isCharging = false))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)

            coordinator.onEvent(CapsuleEvent.HoldPeekStarted)
            runCurrent()
            assertTrue(coordinator.uiState.value.content is CapsuleContent.BatteryPeek)

            // Simulate background Media session waking up during hold before full expand fires
            coordinator.onEvent(CapsuleEvent.MediaUpdated(sampleMedia()))
            runCurrent()
            assertTrue(coordinator.uiState.value.content is CapsuleContent.BatteryPeek)

            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertTrue(coordinator.uiState.value.content is CapsuleContent.NowBrief)
        }

    @Test
    fun `given state3 expanded card, when collapsing to state1 ring, then triggers 1s collapse wave and 1s ring glow`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 15_000L))
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            advanceTimeBy(14_500L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
            runCurrent()

            val collapsedNow = coordinator.uiState.value
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, collapsedNow.visualState)
            assertTrue(collapsedNow.isCollapseWaveActive)
            assertTrue(collapsedNow.isRingGlowActive)

            advanceTimeBy(800L)
            runCurrent()
            val settled = coordinator.uiState.value
            assertFalse(settled.isCollapseWaveActive)
            assertFalse(settled.isRingGlowActive)
        }

    @Test
    fun `given playing media, when notifications arrive, then notification takes immediate top priority and updates unless interacted`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 4000L))
            val media = sampleMedia()
            val firstNotif = CapsuleContent.NotificationAlert(
                key = "0|com.whatsapp|1",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Alex",
                text = "First message",
                iconBytes = null,
                timestampMs = 100L,
            )
            val secondNotif = firstNotif.copy(
                key = "0|com.whatsapp|2",
                title = "Sam",
                text = "Newest message",
                timestampMs = 200L,
            )
            val thirdNotif = firstNotif.copy(
                key = "0|com.whatsapp|3",
                title = "Taylor",
                text = "Another ping",
                timestampMs = 300L,
            )

            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            // Even within 500ms (< 2s dwell), newest notification immediately takes top priority over media
            advanceTimeBy(300L)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.NotificationPosted(firstNotif))
            runCurrent()
            assertEquals(firstNotif, coordinator.uiState.value.content)

            // Second notification directly replaces untouched first notification in State 2
            coordinator.onEvent(CapsuleEvent.NotificationPosted(secondNotif))
            runCurrent()
            assertEquals(secondNotif, coordinator.uiState.value.content)

            // Expand into State 3 and interact with secondNotif: thirdNotif must keep secondNotif visible
            coordinator.onEvent(CapsuleEvent.TapCapsule)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.NotificationPosted(thirdNotif))
            runCurrent()
            assertEquals(secondNotif, coordinator.uiState.value.content)
        }

    @Test
    fun `given idle or playing media, when volume changes, then collapses two times faster unless user interacts`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 6000L))

            val vol = CapsuleContent.Volume(
                activeStream = AudioStreamType.MEDIA,
                streams = listOf(StreamVolumeInfo(AudioStreamType.MEDIA, 9, 15)),
                ringerMode = RingerModeState.NORMAL,
            )

            // State 2 low-priority Volume collapses at 6000 / 2 = 3000ms
            coordinator.onEvent(CapsuleEvent.VolumeChanged(vol))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(vol, coordinator.uiState.value.content)

            advanceTimeBy(2900L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

            advanceTimeBy(150L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)

            // State 3 untouched Volume also collapses 2x faster (3000ms), then full 6000ms after interaction
            coordinator.onEvent(CapsuleEvent.VolumeChanged(vol))
            runCurrent()
            coordinator.onEvent(CapsuleEvent.TapCapsule)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            advanceTimeBy(3100L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)

            // Now expand Volume and interact -> keeps full 6000ms timeout
            coordinator.onEvent(CapsuleEvent.VolumeChanged(vol))
            runCurrent()
            coordinator.onEvent(CapsuleEvent.TapCapsule)
            runCurrent()
            advanceTimeBy(1000L)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.UserInteractedInCard)
            runCurrent()

            advanceTimeBy(3500L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            advanceTimeBy(2600L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    private fun sampleMedia(): CapsuleContent.Media =
        CapsuleContent.Media(
            packageName = "com.spotify.music",
            title = "Midnight City",
            artist = "M83",
            isPlaying = true,
            positionMs = 0L,
            durationMs = 240_000L,
            artworkBytes = null,
            primaryColorArgb = 0xFF6C5CE7.toInt(),
            secondaryColorArgb = 0xFF00CEC9.toInt(),
        )
}
