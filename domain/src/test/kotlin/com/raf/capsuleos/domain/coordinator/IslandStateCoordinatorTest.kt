package com.raf.capsuleos.domain.coordinator

import app.cash.turbine.test
import com.raf.capsuleos.domain.model.AppThemeMode
import com.raf.capsuleos.domain.model.AudioStreamType
import com.raf.capsuleos.domain.model.BluetoothDeviceCategory
import com.raf.capsuleos.domain.model.BrightnessInfo
import com.raf.capsuleos.domain.model.CameraAnchorPosition
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEffect
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsuleGestureAction
import com.raf.capsuleos.domain.model.CapsulePreferences
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.domain.model.CardGestureConfig
import com.raf.capsuleos.domain.model.CutoutGeometry
import com.raf.capsuleos.domain.model.GestureActionBinding
import com.raf.capsuleos.domain.model.GestureCardTarget
import com.raf.capsuleos.domain.model.RingerModeState
import com.raf.capsuleos.domain.model.SatellitePosition
import com.raf.capsuleos.domain.model.SatelliteSource
import com.raf.capsuleos.domain.model.StreamVolumeInfo
import com.raf.capsuleos.domain.model.SystemStatusKind
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `given hideOnQsOpen enabled and expanded media with satellite, when QS opens, then collapses to battery ring only, disables gestures, and restores on close`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    hideOnQsOpen = true,
                    enableLeftSatelliteBubble = true,
                    showTorchInSatellite = true,
                ),
            )

            val media = sampleMedia()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            coordinator.onEvent(
                CapsuleEvent.TorchUpdated(
                    CapsuleContent.Torch(isEnabled = true, strengthLevel = 2, maxStrengthLevel = 5),
                ),
            )
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()

            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertFalse(coordinator.uiState.value.isQsRingOnlyActive)

            // Opening Quick Settings smoothly reduces capsule to Battery Indicator Camera Ring only
            coordinator.onEvent(CapsuleEvent.QuickSettingsChanged(isQsOpen = true))
            runCurrent()

            val qsOpenState = coordinator.uiState.value
            assertTrue(qsOpenState.isQsRingOnlyActive)
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, qsOpenState.visualState)
            assertEquals(CapsuleContent.Idle, qsOpenState.content)
            assertNull(qsOpenState.leftSatellite)

            // Gestures remain disabled while QS is open
            coordinator.onEvent(CapsuleEvent.TapCapsule)
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            coordinator.onEvent(CapsuleEvent.DoubleTapCapsule)
            coordinator.onEvent(CapsuleEvent.LongHoldCapsule)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.Idle, coordinator.uiState.value.content)

            // Closing Quick Settings restores playing media & left satellite smoothly
            coordinator.onEvent(CapsuleEvent.QuickSettingsChanged(isQsOpen = false))
            runCurrent()
            assertFalse(coordinator.uiState.value.isQsRingOnlyActive)
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(media, coordinator.uiState.value.content)
            assertNotNull(coordinator.uiState.value.leftSatellite)
        }

    @Test
    fun `given playing media, when newer transient event arrives, then surfaces latest change immediately and returns to media priority after timeout`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    horizontalAutoCollapseDelayMs = 4000L,
                    autoCollapseDelayMs = 4000L,
                ),
            )

            val media = sampleMedia()
            val volume = CapsuleContent.Volume(
                activeStream = AudioStreamType.MEDIA,
                streams = listOf(StreamVolumeInfo(AudioStreamType.MEDIA, 10, 15)),
            )

            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            // Media position tick at 500ms keeps playback active without suppressing newer events
            advanceTimeBy(500L)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media.copy(positionMs = 500L)))
            coordinator.onEvent(CapsuleEvent.VolumeChanged(volume))
            runCurrent()
            assertEquals(volume, coordinator.uiState.value.content)

            // Even while Media ticks continue every 500ms, Volume stays on top during its burst
            advanceTimeBy(500L)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media.copy(positionMs = 1000L)))
            runCurrent()
            assertEquals(volume, coordinator.uiState.value.content)

            // Once Volume auto-collapse timeout elapses (4000 / 2 = 2000ms), returns to canonical Media priority
            advanceTimeBy(2000L)
            runCurrent()
            assertEquals(media.title, (coordinator.uiState.value.content as CapsuleContent.Media).title)
        }

    @Test
    fun `given notification alert with actions, when tapped in state2 expands card and tap inside card keeps actions interactive`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    horizontalAutoCollapseDelayMs = 4000L,
                    autoCollapseDelayMs = 4000L,
                ),
            )
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
    fun `given live activity with satellite disabled, when tapped in state2, then expands live activity content card and launches app on tap`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(enableLeftSatelliteBubble = false))
            val timer = CapsuleContent.LiveActivity(
                key = "0|com.google.android.deskclock|10",
                title = "Timer",
                statusText = "03:15",
                subtitle = "Kitchen timer",
                packageName = "com.google.android.deskclock",
            )

            coordinator.effects.test {
                coordinator.onEvent(CapsuleEvent.LiveActivityUpdated(timer))
                runCurrent()
                assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

                coordinator.onEvent(CapsuleEvent.TapCapsule)
                runCurrent()
                assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
                assertEquals(timer, coordinator.uiState.value.content)

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
            coordinator.updatePreferences(
                CapsulePreferences(
                    horizontalAutoCollapseDelayMs = 4000L,
                    autoCollapseDelayMs = 4000L,
                ),
            )

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
            coordinator.updatePreferences(
                CapsulePreferences(
                    horizontalAutoCollapseDelayMs = 4000L,
                    autoCollapseDelayMs = 4000L,
                ),
            )
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
    fun `given device unlock event on horizontal expand, then disappears after 1 second only`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(horizontalAutoCollapseDelayMs = 6000L),
            )
            val unlock = CapsuleContent.Unlock(unlockMethod = UnlockMethod.GENERIC)

            coordinator.onEvent(CapsuleEvent.DeviceUnlocked(unlock))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(unlock, coordinator.uiState.value.content)

            advanceTimeBy(999L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

            advanceTimeBy(2L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.Idle, coordinator.uiState.value.content)
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
    fun `given standalone notification alert on horizontal expand, then stays at least 6 seconds and respects custom horizontalAutoCollapseDelayMs`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    horizontalAutoCollapseDelayMs = 3_000L,
                    autoCollapseDelayMs = 10_000L,
                ),
            )
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

            // Re-posting identical notification signature must not extend timeout; standalone minimum is 6000ms
            advanceTimeBy(4_500L)
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
            coordinator.updatePreferences(
                CapsulePreferences(
                    horizontalAutoCollapseDelayMs = 4000L,
                    autoCollapseDelayMs = 4000L,
                ),
            )

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
    fun `given multiple active items with satellite disabled, when idle after swipe, returns to canonical priority notifications then media then live activity then other`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    autoCollapseDelayMs = 4000L,
                    enableLeftSatelliteBubble = false,
                ),
            )
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

            // After transient notification auto-dismisses at configured horizontal timeout (4000ms), resting top priority is Media
            advanceTimeBy(6_001L)
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

            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.NotificationSummary(count = 2), coordinator.uiState.value.content)

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
            coordinator.updatePreferences(
                CapsulePreferences(
                    horizontalAutoCollapseDelayMs = 4000L,
                    autoCollapseDelayMs = 4000L,
                ),
            )
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

    @Test
    fun `given camera position alignment and horizontal offset, resolves left center right bounds cleanly`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updateCutoutGeometry(
                CutoutGeometry(
                    holeWidthDp = 36f,
                    holeHeightDp = 36f,
                    screenWidthDp = 400f,
                    holeCenterOffsetXDp = 0f,
                ),
            )
            coordinator.updatePreferences(
                CapsulePreferences(
                    cameraPosition = CameraAnchorPosition.CENTER,
                    paddingWidthDp = 12f,
                    horizontalOffsetDp = 12.5f,
                ),
            )
            runCurrent()
            assertEquals(12.5f, coordinator.uiState.value.resolveAnchorCenterXOffsetDp(), 0.01f)

            coordinator.updatePreferences(
                CapsulePreferences(
                    cameraPosition = CameraAnchorPosition.LEFT,
                    paddingWidthDp = 12f,
                    horizontalOffsetDp = 0f,
                ),
            )
            runCurrent()
            // Half width 200 - radius 24 - corner margin 16 = 160dp left offset
            assertEquals(-160f, coordinator.uiState.value.resolveAnchorCenterXOffsetDp(), 0.01f)

            coordinator.updatePreferences(
                CapsulePreferences(
                    cameraPosition = CameraAnchorPosition.RIGHT,
                    paddingWidthDp = 12f,
                    horizontalOffsetDp = 0f,
                ),
            )
            runCurrent()
            assertEquals(160f, coordinator.uiState.value.resolveAnchorCenterXOffsetDp(), 0.01f)
        }

    @Test
    fun `given torch hotspot and live activity, then left satellite prioritizes torch then hotspot then live activity and expands smoothly`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val live = CapsuleContent.LiveActivity(
                key = "0|com.google.android.deskclock|12",
                title = "Timer",
                statusText = "02:30",
                packageName = "com.google.android.deskclock",
            )
            val hotspot = CapsuleContent.Hotspot(isEnabled = true, connectedClients = 1)
            val torch = CapsuleContent.Torch(isEnabled = true, strengthLevel = 2, maxStrengthLevel = 5)

            coordinator.onEvent(CapsuleEvent.LiveActivityUpdated(live))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
            assertEquals(SatelliteSource.LIVE_ACTIVITY, coordinator.uiState.value.leftSatellite?.source)

            coordinator.onEvent(CapsuleEvent.HotspotUpdated(hotspot))
            runCurrent()
            assertEquals(SatelliteSource.HOTSPOT, coordinator.uiState.value.leftSatellite?.source)

            coordinator.onEvent(CapsuleEvent.TorchUpdated(torch))
            runCurrent()
            assertEquals(SatelliteSource.TORCH, coordinator.uiState.value.leftSatellite?.source)

            coordinator.onEvent(CapsuleEvent.ExpandLeftSatellite)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertEquals(torch, coordinator.uiState.value.content)

            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
            coordinator.onEvent(CapsuleEvent.TorchUpdated(torch.copy(isEnabled = false)))
            runCurrent()
            assertEquals(SatelliteSource.HOTSPOT, coordinator.uiState.value.leftSatellite?.source)

            coordinator.onEvent(CapsuleEvent.HotspotUpdated(hotspot.copy(isEnabled = false)))
            runCurrent()
            assertEquals(SatelliteSource.LIVE_ACTIVITY, coordinator.uiState.value.leftSatellite?.source)

            coordinator.onEvent(CapsuleEvent.LiveActivityUpdated(null))
            runCurrent()
            assertNull(coordinator.uiState.value.leftSatellite)
        }

    @Test
    fun `given incoming call notification with auto expand enabled, then opens call card automatically and keeps open until ended`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 2000L, enableCallAutoExpand = true))
            val call = CapsuleContent.CallAlert(
                key = "0|com.google.android.dialer|77",
                callerName = "Jordan",
                callStatus = "Incoming call",
                packageName = "com.google.android.dialer",
                appName = "Phone",
                isIncoming = true,
                hasAnswerAction = true,
                hasDeclineAction = true,
            )

            coordinator.onEvent(CapsuleEvent.CallUpdated(call))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertEquals(call, coordinator.uiState.value.content)

            // Active Call card must NOT auto-collapse on inactivity timeout while ringing/ongoing
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            coordinator.onEvent(CapsuleEvent.CallUpdated(null))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given bluetooth device and multiple media sessions, then shows bt alert and switches active media session cleanly`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val pausedSession = sampleMedia().copy(
                packageName = "com.google.android.youtube",
                title = "Second Track",
                isPlaying = false,
                sessionTokenKey = "yt:2",
            )
            val playingSession = sampleMedia().copy(
                title = "Playing Track",
                isPlaying = true,
                sessionTokenKey = "sp:1",
            )

            coordinator.effects.test {
                coordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(listOf(pausedSession, playingSession)))
                runCurrent()
                assertEquals(2, coordinator.uiState.value.mediaSessions.size)
                assertEquals(1, coordinator.uiState.value.selectedMediaIndex)

                coordinator.onEvent(CapsuleEvent.SelectMediaSession(0))
                runCurrent()
                assertEquals(CapsuleEffect.SwitchMediaSession("yt:2"), awaitItem())
                assertEquals(0, coordinator.uiState.value.selectedMediaIndex)

                val buds = CapsuleContent.BluetoothDevice(
                    name = "Pixel Buds Pro",
                    address = "AA:BB:CC:DD:EE:FF",
                    batteryPercent = 88,
                    category = BluetoothDeviceCategory.HEADPHONES,
                    isConnected = true,
                )
                coordinator.onEvent(CapsuleEvent.BluetoothDeviceUpdated(buds))
                runCurrent()
                assertEquals(buds, coordinator.uiState.value.content)
                assertNotNull(coordinator.uiState.value.content as? CapsuleContent.BluetoothDevice)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given custom global and per-card double-tap and hold bindings, then resolves per-card override before global`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    globalDoubleTapAction = CapsuleGestureAction.LOCK_SCREEN,
                    globalLongHoldAction = CapsuleGestureAction.EXPAND_CARD,
                    cardGestures = mapOf(
                        GestureCardTarget.MEDIA to CardGestureConfig(
                            doubleTap = GestureActionBinding(action = CapsuleGestureAction.TOGGLE_MEDIA_PLAY_PAUSE),
                            longHold = GestureActionBinding(
                                action = CapsuleGestureAction.LAUNCH_CUSTOM_APP,
                                customAppPackage = "com.spotify.music",
                            ),
                        ),
                    ),
                ),
            )
            runCurrent()

            coordinator.effects.test {
                // On Idle ring -> uses Global Double-Tap (LockScreen)
                coordinator.onEvent(CapsuleEvent.DoubleTapCapsule)
                runCurrent()
                assertEquals(CapsuleEffect.LockScreen, awaitItem())

                // When Media is active -> uses Per-Card Media Double-Tap & Long-Hold overrides
                coordinator.onEvent(CapsuleEvent.MediaUpdated(sampleMedia()))
                runCurrent()
                coordinator.onEvent(CapsuleEvent.DoubleTapCapsule)
                runCurrent()
                assertEquals(CapsuleEffect.ToggleMediaPlayPause, awaitItem())

                coordinator.onEvent(CapsuleEvent.LongHoldCapsule)
                runCurrent()
                assertEquals(CapsuleEffect.LaunchApp("com.spotify.music"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given multiple active left satellite indicators, when CycleLeftSatellite dispatched, then switches indicator smoothly`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(
                CapsuleEvent.TorchUpdated(
                    CapsuleContent.Torch(isEnabled = true, strengthLevel = 2, maxStrengthLevel = 5),
                ),
            )
            coordinator.onEvent(
                CapsuleEvent.HotspotUpdated(
                    CapsuleContent.Hotspot(isEnabled = true, connectedClients = 2),
                ),
            )
            coordinator.onEvent(
                CapsuleEvent.LiveActivityUpdated(
                    CapsuleContent.LiveActivity(
                        key = "timer_1",
                        title = "Timer",
                        statusText = "02:30",
                        packageName = "com.android.deskclock",
                    ),
                ),
            )
            runCurrent()

            assertEquals(SatelliteSource.TORCH, coordinator.uiState.value.leftSatellite?.source)
            assertEquals(0, coordinator.uiState.value.leftSatellite?.activeIndex)
            assertEquals(3, coordinator.uiState.value.leftSatellite?.totalCount)

            coordinator.onEvent(CapsuleEvent.CycleLeftSatellite(direction = 1))
            runCurrent()
            assertEquals(SatelliteSource.HOTSPOT, coordinator.uiState.value.leftSatellite?.source)
            assertEquals(1, coordinator.uiState.value.leftSatellite?.activeIndex)
            assertEquals(3, coordinator.uiState.value.leftSatellite?.totalCount)
            assertEquals(1, coordinator.uiState.value.satelliteSwipeSeq)
            assertEquals(1, coordinator.uiState.value.satelliteSwipeDirection)

            coordinator.onEvent(CapsuleEvent.CycleLeftSatellite(direction = 1))
            runCurrent()
            assertEquals(SatelliteSource.LIVE_ACTIVITY, coordinator.uiState.value.leftSatellite?.source)
            assertEquals(2, coordinator.uiState.value.leftSatellite?.activeIndex)
            assertEquals(3, coordinator.uiState.value.leftSatellite?.totalCount)
            assertEquals(2, coordinator.uiState.value.satelliteSwipeSeq)

            coordinator.onEvent(CapsuleEvent.CycleLeftSatellite(direction = -1))
            runCurrent()
            assertEquals(SatelliteSource.HOTSPOT, coordinator.uiState.value.leftSatellite?.source)
            assertEquals(-1, coordinator.uiState.value.satelliteSwipeDirection)
        }

    @Test
    fun `given only one active left satellite indicator, when CycleLeftSatellite dispatched, then triggers jiggle without changing source`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(
                CapsuleEvent.TorchUpdated(
                    CapsuleContent.Torch(isEnabled = true, strengthLevel = 1, maxStrengthLevel = 5),
                ),
            )
            runCurrent()

            assertEquals(SatelliteSource.TORCH, coordinator.uiState.value.leftSatellite?.source)
            val beforeJiggle = coordinator.uiState.value.satelliteJiggleSeq
            val beforeSwipe = coordinator.uiState.value.satelliteSwipeSeq

            coordinator.onEvent(CapsuleEvent.CycleLeftSatellite(direction = 1))
            runCurrent()

            assertEquals(SatelliteSource.TORCH, coordinator.uiState.value.leftSatellite?.source)
            assertEquals(beforeJiggle + 1, coordinator.uiState.value.satelliteJiggleSeq)
            assertEquals(beforeSwipe, coordinator.uiState.value.satelliteSwipeSeq)
        }

    @Test
    fun `given default preferences, then theme defaults to SYSTEM, dynamic color enabled, hideOnQsOpen enabled, and keepExpandedMediaGlowAlways enabled`() {
        val prefs = CapsulePreferences()
        assertEquals(AppThemeMode.SYSTEM, prefs.appThemeMode)
        assertTrue(prefs.useDynamicColor)
        assertTrue(prefs.hideOnQsOpen)
        assertTrue(prefs.keepExpandedMediaGlowAlways)
    }

    @Test
    fun `given expanded notification with ReplyFocusChanged true, then pauses auto-collapse and blocks incoming notification replacement until reply ends`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(autoCollapseDelayMs = 2_000L))
            val firstAlert = CapsuleContent.NotificationAlert(
                key = "0|com.whatsapp|1",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Alex",
                text = "Hey, free tonight?",
                iconBytes = null,
                timestampMs = 1_000L,
            )
            val incomingAlert = CapsuleContent.NotificationAlert(
                key = "0|com.telegram|2",
                packageName = "com.telegram",
                appName = "Telegram",
                title = "Sam",
                text = "Ping",
                iconBytes = null,
                timestampMs = 2_000L,
            )

            coordinator.onEvent(CapsuleEvent.NotificationPosted(firstAlert))
            runCurrent()
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            // User starts typing a reply -> auto-collapse cancelled, incoming alerts must not replace card
            coordinator.onEvent(CapsuleEvent.ReplyFocusChanged(isReplying = true))
            runCurrent()

            advanceTimeBy(15_000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertEquals(firstAlert, coordinator.uiState.value.content)

            coordinator.onEvent(CapsuleEvent.NotificationPosted(incomingAlert))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertEquals(firstAlert, coordinator.uiState.value.content)

            // User finishes typing -> normal auto-collapse timeout resumes
            coordinator.onEvent(CapsuleEvent.ReplyFocusChanged(isReplying = false))
            runCurrent()
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            advanceTimeBy(1_500L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(incomingAlert, coordinator.uiState.value.content)

            advanceTimeBy(4_000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given left satellite expanded and collapsed, then marks satellite origin and pulses satellite glow on open and close`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(
                CapsuleEvent.HotspotUpdated(
                    CapsuleContent.Hotspot(isEnabled = true, connectedClients = 1),
                ),
            )
            runCurrent()
            assertEquals(SatelliteSource.HOTSPOT, coordinator.uiState.value.leftSatellite?.source)

            coordinator.onEvent(CapsuleEvent.ExpandLeftSatellite)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertTrue(coordinator.uiState.value.isExpandedFromSatellite)
            assertTrue(coordinator.uiState.value.isSatelliteGlowActive)

            advanceTimeBy(900L)
            runCurrent()
            assertFalse(coordinator.uiState.value.isSatelliteGlowActive)

            coordinator.onEvent(CapsuleEvent.CollapseCapsule)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
            assertFalse(coordinator.uiState.value.isExpandedFromSatellite)
            assertTrue(coordinator.uiState.value.isSatelliteGlowActive)

            advanceTimeBy(900L)
            runCurrent()
            assertFalse(coordinator.uiState.value.isSatelliteGlowActive)
        }

    @Test
    fun `given charging active, then keeps horizontal expand persistently until unplugged`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 68, isCharging = true))
            runCurrent()

            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.Charging(68), coordinator.uiState.value.content)

            // Even after 20 seconds, charging stays in horizontal expand while plugged in
            advanceTimeBy(20_000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.Charging(68), coordinator.uiState.value.content)

            // When unplugged, collapses back to State 1 ring
            coordinator.onEvent(CapsuleEvent.BatteryUpdated(percent = 69, isCharging = false))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given expanded multi-media on paused page, when playing session position ticks, then keeps paused page content and colors without blinking`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val playingSpotify = sampleMedia().copy(
                sessionTokenKey = "spotify_1",
                isPlaying = true,
                primaryColorArgb = 0xFF1DB954.toInt(),
                secondaryColorArgb = 0xFF191414.toInt(),
            )
            val pausedYoutube = sampleMedia().copy(
                packageName = "com.google.android.youtube",
                sessionTokenKey = "youtube_2",
                title = "Tech Review",
                artist = "Creator",
                isPlaying = false,
                primaryColorArgb = 0xFFFF0000.toInt(),
                secondaryColorArgb = 0xFF282828.toInt(),
            )

            coordinator.onEvent(CapsuleEvent.MediaSessionsUpdated(listOf(playingSpotify, pausedYoutube)))
            coordinator.onEvent(CapsuleEvent.MediaUpdated(playingSpotify))
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()

            // User swipes to the paused YouTube page (index 1)
            coordinator.onEvent(CapsuleEvent.SelectMediaSession(1))
            runCurrent()
            assertEquals(1, coordinator.uiState.value.selectedMediaIndex)
            assertEquals(
                0xFFFF0000.toInt(),
                (coordinator.uiState.value.content as CapsuleContent.Media).primaryColorArgb,
            )

            // Playing Spotify session emits a background 500ms position tick
            coordinator.onEvent(
                CapsuleEvent.MediaUpdated(playingSpotify.copy(positionMs = 15_500L)),
            )
            runCurrent()

            // Active expanded content must remain pinned to the paused YouTube page's colors (no blinking!)
            assertEquals(1, coordinator.uiState.value.selectedMediaIndex)
            val activeContent = coordinator.uiState.value.content as CapsuleContent.Media
            assertEquals("youtube_2", activeContent.sessionTokenKey)
            assertEquals(0xFFFF0000.toInt(), activeContent.primaryColorArgb)
        }

    @Test
    fun `given playing media that ducks or updates track, when notification arrives, then notification stays for full horizontalAutoCollapseDelayMs`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    horizontalAutoCollapseDelayMs = 7_500L,
                    autoCollapseDelayMs = 4_000L,
                ),
            )
            val media = sampleMedia()
            val notif = CapsuleContent.NotificationAlert(
                key = "0|com.whatsapp|301",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Alex",
                text = "Testing notification while media plays",
                iconBytes = null,
                timestampMs = 500L,
            )

            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            assertEquals(media, coordinator.uiState.value.content)

            advanceTimeBy(300L)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.NotificationPosted(notif))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(notif, coordinator.uiState.value.content)

            // Simulate notification chime briefly ducking/pausing media and resuming 400ms later
            advanceTimeBy(200L)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media.copy(isPlaying = false, positionMs = 500L)))
            advanceTimeBy(200L)
            runCurrent()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media.copy(isPlaying = true, title = "Next Track", positionMs = 0L)))
            runCurrent()

            // Notification must still be displayed for the full 7,500ms horizontal setting
            assertEquals(notif, coordinator.uiState.value.content)

            advanceTimeBy(7_000L)
            runCurrent()
            assertEquals(notif, coordinator.uiState.value.content)

            // After 7,500ms total elapses since notification arrived, returns cleanly to playing media
            advanceTimeBy(150L)
            runCurrent()
            assertEquals("Next Track", (coordinator.uiState.value.content as CapsuleContent.Media).title)
        }

    @Test
    fun `given active media in horizontal pill or expanded card, when ExpandNowBrief dispatched, then opens NowBrief card`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val media = sampleMedia()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(media, coordinator.uiState.value.content)

            coordinator.onEvent(CapsuleEvent.ExpandNowBrief)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertTrue(coordinator.uiState.value.content is CapsuleContent.NowBrief)
        }

    @Test
    fun `given live activity in left satellite, when ExpandLeftSatellite dispatched, then opens LiveActivity content instead of NowBrief`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val live = CapsuleContent.LiveActivity(
                key = "0|com.google.android.deskclock|44",
                title = "Workout Timer",
                statusText = "12:05",
                subtitle = "HIIT Interval",
                packageName = "com.google.android.deskclock",
            )
            coordinator.onEvent(CapsuleEvent.LiveActivityUpdated(live))
            runCurrent()
            assertEquals(SatelliteSource.LIVE_ACTIVITY, coordinator.uiState.value.leftSatellite?.source)

            coordinator.onEvent(CapsuleEvent.ExpandLeftSatellite)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertEquals(live, coordinator.uiState.value.content)
        }

    @Test
    fun `given expanded media player, when mediaFollowGlobalAutoCollapse disabled, then uses independent 10s-60s timeout instead of global timeout`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(
                CapsulePreferences(
                    autoCollapseDelayMs = 3_000L,
                    mediaFollowGlobalAutoCollapse = false,
                    mediaAutoCollapseDelayMs = 22_000L,
                ),
            )
            val media = sampleMedia()
            coordinator.onEvent(CapsuleEvent.MediaUpdated(media))
            runCurrent()
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            // Past global 3s timeout -> must stay expanded because custom media timeout is 22s
            advanceTimeBy(15_000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)

            // Past 22s custom media timeout -> collapses cleanly back to horizontal pill
            advanceTimeBy(7_500L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

            // Re-enable Follow Global -> now uses global 3s timeout
            coordinator.updatePreferences(
                CapsulePreferences(
                    autoCollapseDelayMs = 3_000L,
                    mediaFollowGlobalAutoCollapse = true,
                    mediaAutoCollapseDelayMs = 22_000L,
                ),
            )
            coordinator.onEvent(CapsuleEvent.ExpandToFull)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            advanceTimeBy(3_500L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given live activity with chronometer, then formats real-time count-up and countdown durations accurately`() {
        val screenRecorder = CapsuleContent.LiveActivity(
            key = "0|com.android.systemui|rec",
            title = "Recording screen",
            statusText = "00:00",
            showChronometer = true,
            chronometerBaseEpochMs = 10_000L,
            isCountdown = false,
        )
        assertEquals("00:00", screenRecorder.resolveDisplayStatus(nowEpochMs = 10_000L))
        assertEquals("00:07", screenRecorder.resolveDisplayStatus(nowEpochMs = 17_400L))
        assertEquals("01:05", screenRecorder.resolveDisplayStatus(nowEpochMs = 75_000L))
        assertEquals("1:00:02", screenRecorder.resolveDisplayStatus(nowEpochMs = 3_612_000L))

        val timer = CapsuleContent.LiveActivity(
            key = "0|com.google.android.deskclock|timer",
            title = "Timer",
            statusText = "01:00",
            showChronometer = true,
            chronometerBaseEpochMs = 70_000L,
            isCountdown = true,
        )
        assertEquals("01:00", timer.resolveDisplayStatus(nowEpochMs = 10_000L))
        assertEquals("00:01", timer.resolveDisplayStatus(nowEpochMs = 69_200L))
        assertEquals("00:00", timer.resolveDisplayStatus(nowEpochMs = 75_000L))

        val staticLive = CapsuleContent.LiveActivity(
            key = "0|com.example|static",
            title = "Delivery",
            statusText = "Arriving",
            showChronometer = false,
        )
        assertEquals("Arriving", staticLive.resolveDisplayStatus(nowEpochMs = 99_000L))
    }

    @Test
    fun `given multiple active system statuses (airplane, dnd, vpn), then left satellite cycles through all and expands SystemStatus card`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            val airplane = CapsuleContent.SystemStatus(kind = SystemStatusKind.AIRPLANE_MODE, isActive = true)
            val dnd = CapsuleContent.SystemStatus(kind = SystemStatusKind.DO_NOT_DISTURB, isActive = true)
            val vpn = CapsuleContent.SystemStatus(kind = SystemStatusKind.VPN, isActive = true)

            coordinator.onEvent(CapsuleEvent.SystemStatusUpdated(airplane))
            coordinator.onEvent(CapsuleEvent.SystemStatusUpdated(dnd))
            coordinator.onEvent(CapsuleEvent.SystemStatusUpdated(vpn))
            runCurrent()

            val firstSat = coordinator.uiState.value.leftSatellite
            assertEquals(SatelliteSource.AIRPLANE_MODE, firstSat?.source)
            assertEquals(3, firstSat?.totalCount)
            assertEquals(0, firstSat?.activeIndex)

            coordinator.onEvent(CapsuleEvent.CycleLeftSatellite(direction = 1))
            runCurrent()
            assertEquals(SatelliteSource.DO_NOT_DISTURB, coordinator.uiState.value.leftSatellite?.source)

            coordinator.onEvent(CapsuleEvent.ExpandLeftSatellite)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE3_FULL_EXPANDED, coordinator.uiState.value.visualState)
            assertEquals(dnd, coordinator.uiState.value.content)

            // Turning DND off while its expanded card is open collapses cleanly and removes it from satellite
            coordinator.onEvent(CapsuleEvent.SystemStatusUpdated(dnd.copy(isActive = false)))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
            assertEquals(2, coordinator.uiState.value.leftSatellite?.totalCount)
        }

    @Test
    fun `given idle or paused media with active notifications, then shows NotificationSummary periodically, auto-collapses, and tap opens shade`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.updatePreferences(CapsulePreferences(horizontalAutoCollapseDelayMs = 4_000L))
            coordinator.onEvent(CapsuleEvent.MediaUpdated(sampleMedia().copy(isPlaying = false)))
            coordinator.onEvent(CapsuleEvent.ActiveNotificationCountUpdated(3))
            runCurrent()

            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.NotificationSummary(count = 3), coordinator.uiState.value.content)

            coordinator.effects.test {
                coordinator.onEvent(CapsuleEvent.TapCapsule)
                runCurrent()
                assertEquals(CapsuleEffect.OpenNotificationShade, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            // Auto-collapses after configured horizontal auto-collapse timeout (4000ms)
            advanceTimeBy(4_100L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)

            // Reappears after 30s idle rolling interval while notifications remain active
            advanceTimeBy(30_000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.NotificationSummary(count = 3), coordinator.uiState.value.content)

            // Auto-collapses again after 4000ms
            advanceTimeBy(4_100L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)

            // Clearing notifications cancels periodic reappearance
            coordinator.onEvent(CapsuleEvent.ActiveNotificationCountUpdated(0))
            runCurrent()
            advanceTimeBy(35_000L)
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
        }

    @Test
    fun `given showOnLockScreen preference, when keyguard locks, then behaves like QS ring only when disabled and skips unlock bounce`() =
        testScope.runTest {
            val coordinator = createCoordinator()
            assertTrue(coordinator.uiState.value.preferences.showOnLockScreen)
            assertEquals(SatellitePosition.LEFT, coordinator.uiState.value.preferences.satellitePosition)

            coordinator.onEvent(CapsuleEvent.MediaUpdated(sampleMedia()))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)

            // Default showOnLockScreen = true keeps active pill visible on lock screen
            coordinator.onEvent(CapsuleEvent.KeyguardLockedChanged(isLocked = true))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertFalse(coordinator.uiState.value.isQsRingOnlyActive)

            // Disabling showOnLockScreen collapses lock screen to battery ring only (matching QS open)
            coordinator.updatePreferences(CapsulePreferences(showOnLockScreen = false))
            runCurrent()
            assertEquals(CapsuleVisualState.STATE1_CUTOUT_ANCHOR, coordinator.uiState.value.visualState)
            assertEquals(CapsuleContent.Idle, coordinator.uiState.value.content)
            assertNull(coordinator.uiState.value.leftSatellite)
            assertTrue(coordinator.uiState.value.isQsRingOnlyActive)

            // Unlocking restores the active pill directly and suppresses the redundant Unlocked bounce
            coordinator.onEvent(CapsuleEvent.KeyguardLockedChanged(isLocked = false))
            coordinator.onEvent(
                CapsuleEvent.DeviceUnlocked(
                    CapsuleContent.Unlock(unlockMethod = UnlockMethod.GENERIC),
                ),
            )
            runCurrent()
            assertFalse(coordinator.uiState.value.isQsRingOnlyActive)
            assertEquals(CapsuleVisualState.STATE2_HORIZONTAL_PILL, coordinator.uiState.value.visualState)
            assertTrue(coordinator.uiState.value.content is CapsuleContent.Media)
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
