package com.raf.capsuleos.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.raf.capsuleos.presentation.setup.SetupScreen
import com.raf.capsuleos.presentation.simulation.SimulationScreen

/**
 * Top-level Jetpack Navigation 3 host managing [CapsuleRoute.Setup] and
 * [CapsuleRoute.Simulation] with Entry-scoped ViewModels and predictive back support.
 *
 * @param modifier Applied to the root [NavDisplay] container.
 */
@Composable
fun CapsuleNavHost(
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(CapsuleRoute.Setup)

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<CapsuleRoute.Setup> {
                SetupScreen(
                    onOpenSimulation = {
                        if (backStack.lastOrNull() != CapsuleRoute.Simulation) {
                            backStack.add(CapsuleRoute.Simulation)
                        }
                    },
                )
            }
            entry<CapsuleRoute.Simulation> {
                SimulationScreen(
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        }
                    },
                )
            }
        },
        modifier = modifier,
    )
}
