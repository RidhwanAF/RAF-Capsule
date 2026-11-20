package com.raf.capsuleos.presentation.simulation.components

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.raf.capsuleos.domain.model.CapsuleContent
import com.raf.capsuleos.domain.model.CapsuleEvent
import com.raf.capsuleos.domain.model.CapsuleUiState
import com.raf.capsuleos.domain.model.CapsuleVisualState
import com.raf.capsuleos.presentation.capsule.ui.CAPSULE_JIGGLE_DAMPING
import com.raf.capsuleos.presentation.capsule.ui.CAPSULE_JIGGLE_DURATION_MS
import com.raf.capsuleos.presentation.capsule.ui.CAPSULE_JIGGLE_STIFFNESS
import com.raf.capsuleos.presentation.capsule.ui.CAPSULE_JIGGLE_VELOCITY_DP_PER_SEC
import com.raf.capsuleos.presentation.capsule.ui.CAPSULE_SCRUB_MAX_COAST_VELOCITY_PX
import com.raf.capsuleos.presentation.capsule.ui.CAPSULE_SPRING_DAMPING
import com.raf.capsuleos.presentation.capsule.ui.CAPSULE_SPRING_STIFFNESS
import com.raf.capsuleos.presentation.capsule.ui.CapsuleScrubPresentation
import com.raf.capsuleos.presentation.capsule.ui.CapsuleSpringAxis
import com.raf.capsuleos.presentation.capsule.ui.computeCapsuleLayoutDpSpec
import com.raf.capsuleos.presentation.capsule.ui.lerpCapsuleFloat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min

/**
 * Pixel-exact VSYNC spring controller for [CapsulePhoneMockup], mirroring
 * [com.raf.capsuleos.presentation.capsule.service.CapsuleOverlayController] 1:1 so in-app live
 * previews exhibit identical spring physics, scrub-to-coast velocity handoff, directional swipe
 * jiggle impulses, and spring-settled satellite separation with zero parent recompositions.
 */
@Stable
internal class CapsuleMockupSpringHost(
    initialState: CapsuleUiState,
    initialDensity: Float,
    private val scope: CoroutineScope,
    var onResolveExpandTarget: () -> CapsuleContent,
    var onResolveCollapseTarget: () -> Pair<CapsuleVisualState, CapsuleContent>,
    var onEvent: (CapsuleEvent) -> Unit,
) {
    private var density: Float = initialDensity.coerceAtLeast(1f)
    private var latestState: CapsuleUiState = initialState
    private var lastVisualState: CapsuleVisualState = initialState.visualState
    private var lastQsRingOnly: Boolean = initialState.isQsRingOnlyActive
    private var lastSwipeJiggleSeq: Int = initialState.swipeJiggleSeq
    private var lastSyncedScreenWidthDp: Float = initialState.cutout.screenWidthDp

    private val widthSpring = CapsuleSpringAxis()
    private val heightSpring = CapsuleSpringAxis()
    private val offsetXSpring = CapsuleSpringAxis()
    private val topYSpring = CapsuleSpringAxis()
    private val cornerDpSpring = CapsuleSpringAxis()

    private var minClampWPx = 100f
    private var minClampHPx = 100f
    private var lastFrameNanos = 0L
    private var isSpringLoopRunning = false
    private var jiggleUntilMs = 0L
    private var state3EnteredAtMs = if (initialState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
        SystemClock.uptimeMillis()
    } else {
        0L
    }
    private var collapseStageJob: Job? = null
    private var activeScrubSession: MockupPixelScrubSession? = null
    private val wakeChannel = Channel<Unit>(Channel.CONFLATED)

    var isHolding: Boolean = false
        private set

    var forceCameraCircleStage: Boolean by mutableStateOf(false)
        private set

    var suppressSatelliteStage: Boolean by mutableStateOf(false)
        private set

    var scrubPresentation: CapsuleScrubPresentation? by mutableStateOf(null)
        private set

    var liveWidthPx: Float by mutableFloatStateOf(100f)
        private set

    var liveHeightPx: Float by mutableFloatStateOf(100f)
        private set

    var liveOffsetXPx: Float by mutableFloatStateOf(0f)
        private set

    var liveTopYPx: Float by mutableFloatStateOf(0f)
        private set

    var liveCornerDp: Float by mutableFloatStateOf(20f)
        private set

    init {
        snapSpringsTo(computePixelSpec(initialState, density))
    }

    /** Demand-driven VSYNC spring loop sleeping with zero CPU overhead once settled. */
    suspend fun runVsyncLoop() {
        while (coroutineContext.isActive) {
            wakeChannel.receive()
            lastFrameNanos = 0L
            while (coroutineContext.isActive && isSpringLoopRunning) {
                withFrameNanos { frameTimeNanos ->
                    stepSprings(frameTimeNanos)
                }
            }
        }
    }

    /** Synchronizes coordinator state changes into the VSYNC spring targets without 1-frame glitches. */
    fun syncState(state: CapsuleUiState, newDensity: Float) {
        val safeDensity = newDensity.coerceAtLeast(1f)
        val densityChanged = abs(safeDensity - density) > 0.001f
        if (state === latestState && !densityChanged) return

        val prevState = latestState
        val wasHidden = lastVisualState == CapsuleVisualState.HIDDEN_SYSTEM
        val wasState3 = lastVisualState == CapsuleVisualState.STATE3_FULL_EXPANDED
        val wasQsRingOnly = lastQsRingOnly
        val onlyWidthSync = !isSpringLoopRunning &&
            activeScrubSession == null &&
            state.visualState == prevState.visualState &&
            state.content == prevState.content &&
            state.leftSatellite == prevState.leftSatellite &&
            state.preferences == prevState.preferences &&
            state.cutout.screenWidthDp != lastSyncedScreenWidthDp

        density = safeDensity
        latestState = state
        lastVisualState = state.visualState
        lastQsRingOnly = state.isQsRingOnlyActive
        lastSyncedScreenWidthDp = state.cutout.screenWidthDp

        if (state.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED && !wasState3) {
            state3EnteredAtMs = SystemClock.uptimeMillis()
        }

        if (densityChanged || onlyWidthSync) {
            snapSpringsTo(computePixelSpec(state, density))
            return
        }

        val activeScrub = activeScrubSession
        if (activeScrub != null) {
            val isExpectedTransition = !activeScrub.isFingerDown &&
                state.visualState == activeScrub.expectedCoastVisualState
            if (state.visualState != CapsuleVisualState.HIDDEN_SYSTEM &&
                (activeScrub.isFingerDown || isExpectedTransition)
            ) {
                return
            }
            clearScrubState()
        }

        val isVisible = state.visualState != CapsuleVisualState.HIDDEN_SYSTEM
        if (state.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
            collapseStageJob?.cancel()
            collapseStageJob = null
            forceCameraCircleStage = false
            suppressSatelliteStage = false
            updateSpringTargets()
            return
        }

        if (!isVisible) {
            collapseStageJob?.cancel()
            collapseStageJob = null
            forceCameraCircleStage = false
            suppressSatelliteStage = false
            return
        }

        if (wasHidden) {
            collapseStageJob?.cancel()
            collapseStageJob = null
            forceCameraCircleStage = false
            suppressSatelliteStage = false
            snapSpringsTo(computePixelSpec(state, density))
            return
        }

        if (state.isQsRingOnlyActive) {
            collapseStageJob?.cancel()
            collapseStageJob = null
            forceCameraCircleStage = false
            suppressSatelliteStage = false
            updateSpringTargets()
            return
        }

        if (wasState3) {
            collapseStageJob?.cancel()
            if (state.leftSatellite != null) {
                forceCameraCircleStage = false
                suppressSatelliteStage = true
                updateSpringTargets()
                val mainOnlySpec = computePixelSpec(state.copy(leftSatellite = null), density)
                collapseStageJob = scope.launch {
                    val startMs = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - startMs < 520L) {
                        delay(16L)
                        val closeToMain = abs(widthSpring.value - mainOnlySpec.widthPx) <= 1.5f &&
                            abs(heightSpring.value - mainOnlySpec.heightPx) <= 1.5f &&
                            abs(offsetXSpring.value - mainOnlySpec.offsetXPx) <= 1.5f &&
                            abs(widthSpring.velocity) <= 12f &&
                            abs(heightSpring.velocity) <= 12f
                        if (closeToMain) break
                    }
                    delay(150L)
                    val finalState = latestState
                    if (finalState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
                        finalState.visualState != CapsuleVisualState.HIDDEN_SYSTEM &&
                        !finalState.isQsRingOnlyActive
                    ) {
                        suppressSatelliteStage = false
                        updateSpringTargets()
                    }
                }
            } else {
                forceCameraCircleStage = false
                suppressSatelliteStage = false
                updateSpringTargets()
            }
            return
        }

        if (wasQsRingOnly &&
            !state.isQsRingOnlyActive &&
            state.visualState == CapsuleVisualState.STATE2_HORIZONTAL_PILL &&
            state.leftSatellite != null
        ) {
            collapseStageJob?.cancel()
            forceCameraCircleStage = false
            suppressSatelliteStage = true
            updateSpringTargets()
            val mainOnlySpec = computePixelSpec(state.copy(leftSatellite = null), density)
            collapseStageJob = scope.launch {
                val startMs = SystemClock.uptimeMillis()
                while (SystemClock.uptimeMillis() - startMs < 250L) {
                    delay(16L)
                    val closeToMain = abs(widthSpring.value - mainOnlySpec.widthPx) <= 3.5f &&
                        abs(offsetXSpring.value - mainOnlySpec.offsetXPx) <= 3.5f
                    if (closeToMain) break
                }
                delay(90L)
                val latest = latestState
                if (!latest.isQsRingOnlyActive &&
                    latest.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
                    latest.visualState != CapsuleVisualState.HIDDEN_SYSTEM
                ) {
                    suppressSatelliteStage = false
                    updateSpringTargets()
                }
            }
            return
        }

        if (state.swipeJiggleSeq != lastSwipeJiggleSeq) {
            lastSwipeJiggleSeq = state.swipeJiggleSeq
            val dir = if (state.swipeDirection < 0) -1f else 1f
            jiggleUntilMs = SystemClock.uptimeMillis() + CAPSULE_JIGGLE_DURATION_MS
            offsetXSpring.velocity = dir * CAPSULE_JIGGLE_VELOCITY_DP_PER_SEC * density
            wakeSpringLoop()
        }

        if (!forceCameraCircleStage) {
            updateSpringTargets()
        }
    }

    fun onHoldingChanged(holding: Boolean) {
        if (isHolding != holding) {
            isHolding = holding
            updateSpringTargets()
        }
    }

    fun onMergeSatelliteIntoMainBeforeCamera() {
        collapseStageJob?.cancel()
        collapseStageJob = null
        forceCameraCircleStage = false
        suppressSatelliteStage = true
        updateSpringTargets()
    }

    fun onMergeToCameraBeforeExpand() {
        collapseStageJob?.cancel()
        collapseStageJob = null
        suppressSatelliteStage = false
        forceCameraCircleStage = true
        updateSpringTargets()
    }

    fun onExpandSatellite() {
        forceCameraCircleStage = false
        suppressSatelliteStage = false
        onEvent(CapsuleEvent.ExpandLeftSatellite)
    }

    fun onBackdropTap() {
        if (latestState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED &&
            SystemClock.uptimeMillis() - state3EnteredAtMs > 360L
        ) {
            onEvent(CapsuleEvent.CollapseCapsule)
        }
    }

    fun startVerticalScrub(): Pair<Float, Float> {
        collapseStageJob?.cancel()
        collapseStageJob = null
        forceCameraCircleStage = false
        suppressSatelliteStage = false
        isSpringLoopRunning = false
        isHolding = false

        val current = latestState
        val isFromExpanded = current.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED
        val compactState: CapsuleUiState
        val expandedState: CapsuleUiState

        if (isFromExpanded) {
            expandedState = current
            val (collapseVis, collapseContent) = onResolveCollapseTarget()
            compactState = current.copy(visualState = collapseVis, content = collapseContent)
        } else {
            compactState = if (current.content is CapsuleContent.BatteryPeek || current.content is CapsuleContent.Charging) {
                val (collapseVis, collapseContent) = onResolveCollapseTarget()
                current.copy(visualState = collapseVis, content = collapseContent)
            } else {
                current
            }
            expandedState = current.copy(
                visualState = CapsuleVisualState.STATE3_FULL_EXPANDED,
                content = onResolveExpandTarget(),
            )
        }

        val compactSpec = computePixelSpec(compactState, density)
        val compactBaseSpec = computePixelSpec(compactState.copy(leftSatellite = null), density)
        val expandedSpec = computePixelSpec(expandedState, density)
        val travelPx = (expandedSpec.heightPx - compactBaseSpec.heightPx).coerceAtLeast(160f)
        val initialFraction = ((heightSpring.value - compactBaseSpec.heightPx) / travelPx)
            .coerceIn(-0.04f, 1.14f)

        val session = MockupPixelScrubSession(
            compactUiState = compactState,
            compactSpec = compactSpec,
            compactBaseSpec = compactBaseSpec,
            expandedUiState = expandedState,
            expandedSpec = expandedSpec,
            expandedWidthDp = expandedSpec.widthPx / density,
            expandedHeightDp = expandedSpec.heightPx / density,
            travelPx = travelPx,
            isFingerDown = true,
            isCollapsing = isFromExpanded,
            expectedCoastVisualState = expandedState.visualState,
        )
        activeScrubSession = session
        minClampWPx = compactSpec.minWidthPx
        minClampHPx = compactSpec.minHeightPx

        applyScrubFrame(session, initialFraction)
        return travelPx to initialFraction
    }

    fun updateVerticalScrub(fraction: Float) {
        val session = activeScrubSession ?: return
        applyScrubFrame(session, fraction)
    }

    fun endVerticalScrub(
        commitExpanded: Boolean,
        velocityYPxPerSec: Float,
        isFastDirectSwipe: Boolean,
    ) {
        val session = activeScrubSession ?: return
        session.isFingerDown = false
        session.expectedCoastVisualState = if (commitExpanded) {
            CapsuleVisualState.STATE3_FULL_EXPANDED
        } else {
            session.compactUiState.visualState
        }

        if (isFastDirectSwipe) {
            // Release scrub crossfade immediately on fast flick so SharedTransitionLayout album morph runs
            activeScrubSession = null
            scrubPresentation = null
        }

        if (commitExpanded) {
            if (latestState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED) {
                onEvent(CapsuleEvent.ExpandToFull)
            } else {
                onEvent(CapsuleEvent.UserInteractedInCard)
            }
        } else {
            if (latestState.visualState == CapsuleVisualState.STATE3_FULL_EXPANDED) {
                onEvent(CapsuleEvent.CollapseCapsule)
            } else {
                onEvent(CapsuleEvent.HoldPeekReleased)
            }
        }

        val hideSatUntilSettled = !commitExpanded && session.isCollapsing && session.compactUiState.leftSatellite != null
        val targetSpec = when {
            commitExpanded -> session.expandedSpec
            hideSatUntilSettled -> session.compactBaseSpec
            else -> session.compactSpec
        }
        if (hideSatUntilSettled) {
            suppressSatelliteStage = true
        }
        minClampWPx = targetSpec.minWidthPx
        minClampHPx = targetSpec.minHeightPx

        widthSpring.target = targetSpec.widthPx
        heightSpring.target = targetSpec.heightPx
        offsetXSpring.target = targetSpec.offsetXPx
        topYSpring.target = targetSpec.topYPx
        cornerDpSpring.target = targetSpec.cornerDp

        heightSpring.velocity = velocityYPxPerSec.coerceIn(
            -CAPSULE_SCRUB_MAX_COAST_VELOCITY_PX,
            CAPSULE_SCRUB_MAX_COAST_VELOCITY_PX,
        )
        wakeSpringLoop()
    }

    fun dispose() {
        collapseStageJob?.cancel()
        collapseStageJob = null
        isSpringLoopRunning = false
    }

    private fun applyScrubFrame(session: MockupPixelScrubSession, fraction: Float) {
        val t = fraction.coerceIn(0f, 1f)
        val overFraction = (fraction - 1f).coerceAtLeast(0f)
        val satThreshold = 0.24f
        val satLeadT = if (!session.isCollapsing && session.compactUiState.leftSatellite != null) {
            ((satThreshold - t) / satThreshold).coerceIn(0f, 1f)
        } else {
            0f
        }

        val extraLeadW = session.compactSpec.widthPx - session.compactBaseSpec.widthPx
        val extraLeadX = session.compactSpec.offsetXPx - session.compactBaseSpec.offsetXPx
        val mainW = lerpCapsuleFloat(session.compactBaseSpec.widthPx, session.expandedSpec.widthPx, t) + overFraction * 24f
        val baseW = mainW + satLeadT * extraLeadW
        val baseH = lerpCapsuleFloat(session.compactBaseSpec.heightPx, session.expandedSpec.heightPx, t) + overFraction * 140f
        val targetX = lerpCapsuleFloat(session.compactBaseSpec.offsetXPx, session.expandedSpec.offsetXPx, t) + satLeadT * extraLeadX
        val targetY = lerpCapsuleFloat(session.compactBaseSpec.topYPx, session.expandedSpec.topYPx, t)
        val circleCornerNowDp = (min(mainW, baseH) / density) * 0.5f
        val compactCornerTarget = if (session.compactUiState.visualState == CapsuleVisualState.STATE1_CUTOUT_ANCHOR) {
            circleCornerNowDp
        } else {
            session.compactBaseSpec.cornerDp
        }
        val cornerDp = lerpCapsuleFloat(compactCornerTarget, session.expandedSpec.cornerDp, t)

        widthSpring.snapTo(baseW.coerceAtLeast(minClampWPx))
        heightSpring.snapTo(baseH.coerceAtLeast(minClampHPx))
        offsetXSpring.snapTo(targetX)
        topYSpring.snapTo(targetY)
        cornerDpSpring.snapTo(cornerDp)
        publishSprings()

        scrubPresentation = CapsuleScrubPresentation(
            fraction = fraction,
            compactVisualState = session.compactUiState.visualState,
            compactContent = session.compactUiState.content,
            expandedContent = session.expandedUiState.content,
            expandedWidthDp = session.expandedWidthDp,
            expandedHeightDp = session.expandedHeightDp,
            currentWidthDp = (widthSpring.value.coerceAtLeast(minClampWPx) / density).coerceAtLeast(1f),
            currentHeightDp = (heightSpring.value.coerceAtLeast(minClampHPx) / density).coerceAtLeast(1f),
            isCollapsing = session.isCollapsing,
            satelliteFraction = satLeadT,
            fromSatellite = session.expandedUiState.isExpandedFromSatellite,
        )
    }

    private fun clearScrubState() {
        val wasCollapsingWithSat = activeScrubSession?.let {
            it.isCollapsing && it.expectedCoastVisualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
                it.compactUiState.leftSatellite != null
        } == true
        activeScrubSession = null
        scrubPresentation = null
        if (wasCollapsingWithSat && suppressSatelliteStage) {
            collapseStageJob?.cancel()
            collapseStageJob = scope.launch {
                delay(150L)
                val finalState = latestState
                if (finalState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED &&
                    finalState.visualState != CapsuleVisualState.HIDDEN_SYSTEM &&
                    !finalState.isQsRingOnlyActive
                ) {
                    suppressSatelliteStage = false
                    updateSpringTargets()
                }
            }
        }
    }

    private fun updateSpringTargets() {
        val rawState = latestState
        val state = when {
            forceCameraCircleStage && rawState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED ->
                rawState.copy(
                    visualState = CapsuleVisualState.STATE1_CUTOUT_ANCHOR,
                    content = CapsuleContent.Idle,
                    leftSatellite = null,
                )
            suppressSatelliteStage && rawState.visualState != CapsuleVisualState.STATE3_FULL_EXPANDED ->
                rawState.copy(leftSatellite = null)
            else -> rawState
        }
        val spec = computePixelSpec(state, density)
        minClampWPx = spec.minWidthPx
        minClampHPx = spec.minHeightPx

        widthSpring.target = spec.widthPx
        heightSpring.target = spec.heightPx
        offsetXSpring.target = spec.offsetXPx
        topYSpring.target = spec.topYPx
        cornerDpSpring.target = spec.cornerDp

        wakeSpringLoop()
    }

    private fun snapSpringsTo(spec: MockupPixelSpec) {
        minClampWPx = spec.minWidthPx
        minClampHPx = spec.minHeightPx
        widthSpring.snapTo(spec.widthPx)
        heightSpring.snapTo(spec.heightPx)
        offsetXSpring.snapTo(spec.offsetXPx)
        topYSpring.snapTo(spec.topYPx)
        cornerDpSpring.snapTo(spec.cornerDp)
        publishSprings()
    }

    private fun wakeSpringLoop() {
        if (!isSpringLoopRunning) {
            isSpringLoopRunning = true
            wakeChannel.trySend(Unit)
        }
    }

    private fun stepSprings(frameTimeNanos: Long) {
        if (!isSpringLoopRunning) return
        val dtSec = if (lastFrameNanos == 0L) {
            0.016f
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.002f, 0.032f)
        }
        lastFrameNanos = frameTimeNanos

        val halfDt = dtSec * 0.5f
        val isJiggling = SystemClock.uptimeMillis() < jiggleUntilMs
        val xStiffness = if (isJiggling) CAPSULE_JIGGLE_STIFFNESS else CAPSULE_SPRING_STIFFNESS
        val xDamping = if (isJiggling) CAPSULE_JIGGLE_DAMPING else CAPSULE_SPRING_DAMPING
        var active = false
        repeat(2) {
            val wMoving = widthSpring.step(halfDt, stiffness = CAPSULE_SPRING_STIFFNESS, dampingRatio = CAPSULE_SPRING_DAMPING)
            val hMoving = heightSpring.step(halfDt, stiffness = CAPSULE_SPRING_STIFFNESS, dampingRatio = CAPSULE_SPRING_DAMPING)
            val xMoving = offsetXSpring.step(halfDt, stiffness = xStiffness, dampingRatio = xDamping)
            val yMoving = topYSpring.step(halfDt, stiffness = CAPSULE_SPRING_STIFFNESS, dampingRatio = CAPSULE_SPRING_DAMPING)
            val cMoving = cornerDpSpring.step(halfDt, stiffness = CAPSULE_SPRING_STIFFNESS, dampingRatio = CAPSULE_SPRING_DAMPING)
            active = wMoving || hMoving || xMoving || yMoving || cMoving
        }

        publishSprings()

        val session = activeScrubSession
        if (session != null && !session.isFingerDown) {
            val coastFraction = ((heightSpring.value - session.compactBaseSpec.heightPx) / session.travelPx)
                .coerceIn(-0.04f, 1.14f)
            val t = coastFraction.coerceIn(0f, 1f)
            val satThreshold = 0.24f
            val satLeadT = if (!session.isCollapsing && session.compactUiState.leftSatellite != null) {
                ((satThreshold - t) / satThreshold).coerceIn(0f, 1f)
            } else {
                0f
            }
            val liveWDp = (widthSpring.value.coerceAtLeast(minClampWPx) / density).coerceAtLeast(1f)
            val liveHDp = (heightSpring.value.coerceAtLeast(minClampHPx) / density).coerceAtLeast(1f)
            scrubPresentation = scrubPresentation?.copy(
                fraction = coastFraction,
                currentWidthDp = liveWDp,
                currentHeightDp = liveHDp,
                satelliteFraction = satLeadT,
            )
        }

        if (!active) {
            isSpringLoopRunning = false
            if (activeScrubSession?.isFingerDown == false) {
                clearScrubState()
            }
        }
    }

    private fun publishSprings() {
        liveWidthPx = widthSpring.value.coerceAtLeast(minClampWPx).coerceAtLeast(1f)
        liveHeightPx = heightSpring.value.coerceAtLeast(minClampHPx).coerceAtLeast(1f)
        liveOffsetXPx = offsetXSpring.value
        liveTopYPx = topYSpring.value
        liveCornerDp = cornerDpSpring.value.coerceAtLeast(8f)
    }

    private fun computePixelSpec(state: CapsuleUiState, densityPx: Float): MockupPixelSpec {
        val dpSpec = computeCapsuleLayoutDpSpec(state = state, isHoldingCapsule = isHolding)
        return MockupPixelSpec(
            widthPx = dpSpec.widthDp * densityPx,
            heightPx = dpSpec.heightDp * densityPx,
            minWidthPx = dpSpec.minWidthDp * densityPx,
            minHeightPx = dpSpec.minHeightDp * densityPx,
            offsetXPx = dpSpec.offsetXDp * densityPx,
            topYPx = dpSpec.topYDp * densityPx,
            cornerDp = dpSpec.cornerDp,
        )
    }
}

private data class MockupPixelSpec(
    val widthPx: Float,
    val heightPx: Float,
    val minWidthPx: Float,
    val minHeightPx: Float,
    val offsetXPx: Float,
    val topYPx: Float,
    val cornerDp: Float,
)

private data class MockupPixelScrubSession(
    val compactUiState: CapsuleUiState,
    val compactSpec: MockupPixelSpec,
    val compactBaseSpec: MockupPixelSpec,
    val expandedUiState: CapsuleUiState,
    val expandedSpec: MockupPixelSpec,
    val expandedWidthDp: Float,
    val expandedHeightDp: Float,
    val travelPx: Float,
    var isFingerDown: Boolean,
    val isCollapsing: Boolean,
    var expectedCoastVisualState: CapsuleVisualState,
)
