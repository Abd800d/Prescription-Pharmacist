package com.maakthi.prescriptionpharmacist.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.AppLanguage
import com.maakthi.prescriptionpharmacist.presentation.capture.CaptureScreen
import com.maakthi.prescriptionpharmacist.presentation.history.HistoryScreen
import com.maakthi.prescriptionpharmacist.presentation.history.PrescriptionDetailScreen
import com.maakthi.prescriptionpharmacist.presentation.review.ReviewScreen
import com.maakthi.prescriptionpharmacist.presentation.settings.SettingsScreen

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: @Composable () -> Unit
)

@Composable
fun AppNavHost(
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topLevelDestinations = listOf(
        TopLevelDestination(Routes.CAPTURE, R.string.nav_capture) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
        },
        TopLevelDestination(Routes.HISTORY, R.string.nav_history) {
            Icon(Icons.Filled.History, contentDescription = null)
        },
        TopLevelDestination(Routes.SETTINGS, R.string.nav_settings) {
            Icon(Icons.Filled.Settings, contentDescription = null)
        }
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevelDestinations.map { it.route }) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CAPTURE,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    onImageReady = { imagePath ->
                        navController.navigate(Routes.review(imagePath))
                    }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenPrescription = { id ->
                        navController.navigate(Routes.detail(id))
                    }
                )
            }
            composable(
                route = Routes.REVIEW,
                arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
            ) {
                ReviewScreen(
                    onFinished = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("prescriptionId") { type = NavType.StringType })
            ) {
                PrescriptionDetailScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onLanguageSelected = onLanguageSelected
                )
            }
        }
    }
}
