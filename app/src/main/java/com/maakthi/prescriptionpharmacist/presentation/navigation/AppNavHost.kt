package com.maakthi.prescriptionpharmacist.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.AppLanguage
import com.maakthi.prescriptionpharmacist.presentation.assistant.AssistantScreen
import com.maakthi.prescriptionpharmacist.presentation.auth.GoogleSignInScreen
import com.maakthi.prescriptionpharmacist.presentation.auth.LoginScreen
import com.maakthi.prescriptionpharmacist.presentation.auth.RegisterScreen
import com.maakthi.prescriptionpharmacist.presentation.capture.CaptureScreen
import com.maakthi.prescriptionpharmacist.presentation.capture.CropEditScreen
import com.maakthi.prescriptionpharmacist.presentation.capture.GalleryScreen
import com.maakthi.prescriptionpharmacist.presentation.drugs.AddEditDrugScreen
import com.maakthi.prescriptionpharmacist.presentation.drugs.DrugDetailsScreen
import com.maakthi.prescriptionpharmacist.presentation.drugs.DrugSearchScreen
import com.maakthi.prescriptionpharmacist.presentation.history.HistoryScreen
import com.maakthi.prescriptionpharmacist.presentation.history.PrescriptionDetailScreen
import com.maakthi.prescriptionpharmacist.presentation.home.HomeScreen
import com.maakthi.prescriptionpharmacist.presentation.legal.PrivacyScreen
import com.maakthi.prescriptionpharmacist.presentation.legal.TermsScreen
import com.maakthi.prescriptionpharmacist.presentation.onboarding.OnboardingScreen
import com.maakthi.prescriptionpharmacist.presentation.products.ProductsScreen
import com.maakthi.prescriptionpharmacist.presentation.review.AnalysisScreen
import com.maakthi.prescriptionpharmacist.presentation.review.ConfirmEditScreen
import com.maakthi.prescriptionpharmacist.presentation.review.ExtractedMedicinesScreen
import com.maakthi.prescriptionpharmacist.presentation.review.ReviewFlowViewModel
import com.maakthi.prescriptionpharmacist.presentation.settings.BackupSyncScreen
import com.maakthi.prescriptionpharmacist.presentation.settings.LanguageSettingsScreen
import com.maakthi.prescriptionpharmacist.presentation.settings.SettingsScreen
import com.maakthi.prescriptionpharmacist.presentation.settings.SupportScreen
import com.maakthi.prescriptionpharmacist.presentation.splash.SplashScreen
import com.maakthi.prescriptionpharmacist.presentation.transfer.TransferScreen
import androidx.hilt.navigation.compose.hiltViewModel

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
        TopLevelDestination(Routes.HOME, R.string.nav_home) {
            Icon(Icons.Filled.Home, contentDescription = null)
        },
        TopLevelDestination(Routes.DRUG_SEARCH, R.string.nav_drug_search) {
            Icon(Icons.Filled.ManageSearch, contentDescription = null)
        },
        TopLevelDestination(Routes.SAVED, R.string.nav_saved) {
            Icon(Icons.Filled.Save, contentDescription = null)
        },
        TopLevelDestination(Routes.PRODUCTS, R.string.nav_products) {
            Icon(Icons.Filled.Inventory2, contentDescription = null)
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
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ---------- Splash & Onboarding ----------
            composable(Routes.SPLASH) {
                SplashScreen(
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            // ---------- Auth ----------
            composable(Routes.LOGIN) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    onGoRegister = { navController.navigate(Routes.REGISTER) },
                    onGoGoogle = { navController.navigate(Routes.GOOGLE_SIGN_IN) }
                )
            }
            composable(Routes.REGISTER) {
                RegisterScreen(
                    onBack = { navController.popBackStack() },
                    onRegistered = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    onGoLogin = { navController.popBackStack() }
                )
            }
            composable(Routes.GOOGLE_SIGN_IN) {
                GoogleSignInScreen(
                    onBack = { navController.popBackStack() },
                    onSignedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }

            // ---------- Top level ----------
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigate = { route -> navController.navigate(route) },
                    onToggleLanguage = { current ->
                        onLanguageSelected(
                            if (current == AppLanguage.ARABIC) AppLanguage.ENGLISH
                            else AppLanguage.ARABIC
                        )
                    }
                )
            }
            composable(Routes.DRUG_SEARCH) {
                DrugSearchScreen(
                    onOpenDrug = { drug -> navController.navigate(Routes.drugDetails(drug)) },
                    onAddDrug = { navController.navigate(Routes.DRUG_ADD) }
                )
            }
            composable(Routes.SAVED) {
                HistoryScreen(
                    onOpenPrescription = { id -> navController.navigate(Routes.detail(id)) }
                )
            }
            composable(Routes.PRODUCTS) {
                ProductsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            // ---------- Capture flow ----------
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    onImageReady = { imagePath ->
                        navController.navigate(Routes.crop(imagePath))
                    }
                )
            }
            composable(Routes.GALLERY) {
                GalleryScreen(
                    onBack = { navController.popBackStack() },
                    onImageReady = { imagePath ->
                        navController.navigate(Routes.crop(imagePath))
                    }
                )
            }
            composable(
                route = Routes.CROP,
                arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
            ) {
                CropEditScreen(
                    onBack = { navController.popBackStack() },
                    onEdited = { editedPath ->
                        navController.navigate(Routes.reviewGraph(editedPath))
                    }
                )
            }

            // ---------- Review flow (shared ViewModel) ----------
            navigation(startDestination = Routes.REVIEW_ANALYSIS, route = Routes.REVIEW_GRAPH) {
                composable(Routes.REVIEW_ANALYSIS) { entry ->
                    val viewModel = sharedReviewViewModel(entry, navController)
                    AnalysisScreen(
                        onAnalysisDone = {
                            navController.navigate(Routes.REVIEW_EXTRACTED) {
                                popUpTo(Routes.REVIEW_ANALYSIS) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        viewModel = viewModel
                    )
                }
                composable(Routes.REVIEW_EXTRACTED) { entry ->
                    val viewModel = sharedReviewViewModel(entry, navController)
                    ExtractedMedicinesScreen(
                        onContinue = { navController.navigate(Routes.REVIEW_CONFIRM) },
                        onReanalyze = {
                            viewModel.analyze()
                            navController.navigate(Routes.REVIEW_ANALYSIS)
                        },
                        viewModel = viewModel
                    )
                }
                composable(Routes.REVIEW_CONFIRM) { entry ->
                    val viewModel = sharedReviewViewModel(entry, navController)
                    ConfirmEditScreen(
                        onSaved = {
                            navController.navigate(Routes.SAVED) {
                                popUpTo(Routes.HOME)
                            }
                        },
                        onDiscarded = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        viewModel = viewModel
                    )
                }
            }

            // ---------- Drugs ----------
            composable(Routes.DRUG_ADD) {
                AddEditDrugScreen(
                    isEdit = false,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.DRUG_EDIT,
                arguments = listOf(navArgument("drugId") { type = NavType.StringType })
            ) {
                AddEditDrugScreen(
                    isEdit = true,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.DRUG_DETAILS,
                arguments = listOf(navArgument("drugJson") { type = NavType.StringType })
            ) { entry ->
                DrugDetailsScreen(
                    drugJson = entry.arguments?.getString("drugJson"),
                    onBack = { navController.popBackStack() },
                    onEditDrug = { drugId ->
                        navController.navigate(Routes.drugEdit(drugId))
                    }
                )
            }

            // ---------- Prescriptions ----------
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("prescriptionId") { type = NavType.StringType })
            ) {
                PrescriptionDetailScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ---------- Misc ----------
            composable(Routes.TRANSFER) {
                TransferScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ASSISTANT) {
                AssistantScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LANGUAGE_SETTINGS) {
                LanguageSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLanguageSelected = onLanguageSelected
                )
            }
            composable(Routes.BACKUP) {
                BackupSyncScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SUPPORT) {
                SupportScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TERMS) {
                TermsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun sharedReviewViewModel(
    entry: androidx.navigation.NavBackStackEntry,
    navController: androidx.navigation.NavHostController
): ReviewFlowViewModel {
    val parentEntry = remember(entry) {
        navController.getBackStackEntry(Routes.REVIEW_GRAPH)
    }
    return hiltViewModel(parentEntry)
}
