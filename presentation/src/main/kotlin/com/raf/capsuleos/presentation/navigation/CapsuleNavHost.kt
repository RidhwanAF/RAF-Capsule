package com.raf.capsuleos.presentation.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.raf.capsuleos.presentation.appsettings.AppSettingsScreen
import com.raf.capsuleos.presentation.customization.CustomizationScreen
import com.raf.capsuleos.presentation.setup.SetupScreen
import com.raf.capsuleos.presentation.simulation.SimulationScreen

/**
 * Top-level Jetpack Navigation 3 host managing [CapsuleRoute.Setup], separate customization screens
 * ([CapsuleRoute.CameraPosition], [CapsuleRoute.BehaviorLayout], [CapsuleRoute.IndicatorsMedia],
 * [CapsuleRoute.GesturesActions]), [CapsuleRoute.Simulation], and [CapsuleRoute.AppSettings]
 * with scaleIn/scaleOut (1.0 <-> 1.15) + fade transitions and [SharedTransitionLayout] morphing.
 *
 * @param modifier Applied to the root [SharedTransitionLayout] container.
 */
@Composable
fun CapsuleNavHost(
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(CapsuleRoute.Setup)
    val popBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    SharedTransitionLayout(modifier = modifier) {
        NavDisplay(
            backStack = backStack,
            onBack = popBack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = {
                (scaleIn(
                    initialScale = 1.15f,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(240))) togetherWith (scaleOut(
                    targetScale = 1.15f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(180)))
            },
            popTransitionSpec = {
                (scaleIn(
                    initialScale = 1.15f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(220))) togetherWith (scaleOut(
                    targetScale = 1.15f,
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(170)))
            },
            predictivePopTransitionSpec = {
                (scaleIn(
                    initialScale = 1.15f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(220))) togetherWith (scaleOut(
                    targetScale = 1.15f,
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(170)))
            },
            entryProvider = entryProvider {
                entry<CapsuleRoute.Setup> {
                    SetupScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onOpenCustomizationSection = { section ->
                            val targetRoute = when (section) {
                                CustomizationSection.CAMERA_POSITION -> CapsuleRoute.CameraPosition
                                CustomizationSection.BEHAVIOR_LAYOUT -> CapsuleRoute.BehaviorLayout
                                CustomizationSection.INDICATORS_MEDIA -> CapsuleRoute.IndicatorsMedia
                                CustomizationSection.GESTURES_ACTIONS -> CapsuleRoute.GesturesActions
                            }
                            if (backStack.lastOrNull() != targetRoute) {
                                backStack.add(targetRoute)
                            }
                        },
                        onOpenSimulation = {
                            if (backStack.lastOrNull() != CapsuleRoute.Simulation) {
                                backStack.add(CapsuleRoute.Simulation)
                            }
                        },
                        onOpenAppSettings = {
                            if (backStack.lastOrNull() != CapsuleRoute.AppSettings) {
                                backStack.add(CapsuleRoute.AppSettings)
                            }
                        },
                    )
                }
                entry<CapsuleRoute.CameraPosition> {
                    CustomizationScreen(
                        section = CustomizationSection.CAMERA_POSITION,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onBack = popBack,
                    )
                }
                entry<CapsuleRoute.BehaviorLayout> {
                    CustomizationScreen(
                        section = CustomizationSection.BEHAVIOR_LAYOUT,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onBack = popBack,
                    )
                }
                entry<CapsuleRoute.IndicatorsMedia> {
                    CustomizationScreen(
                        section = CustomizationSection.INDICATORS_MEDIA,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onBack = popBack,
                    )
                }
                entry<CapsuleRoute.GesturesActions> {
                    CustomizationScreen(
                        section = CustomizationSection.GESTURES_ACTIONS,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onBack = popBack,
                    )
                }
                entry<CapsuleRoute.Simulation> {
                    SimulationScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onBack = popBack,
                    )
                }
                entry<CapsuleRoute.AppSettings> {
                    AppSettingsScreen(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onBack = popBack,
                    )
                }
            },
        )
    }
}
