package com.pgoorts.tripplanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pgoorts.tripplanner.auth.GoogleAuthClient
import com.pgoorts.tripplanner.auth.UserSessionManager
import com.pgoorts.tripplanner.ui.home.HomeScreen
import com.pgoorts.tripplanner.ui.theme.TripPlannerTheme
import com.pgoorts.tripplanner.ui.trip.OpenedTripScreen
import com.pgoorts.tripplanner.ui.event.OpenedEventScreen
import com.pgoorts.tripplanner.ui.note.OpenedNoteScreen
import com.pgoorts.tripplanner.ui.settings.SettingsScreen
import com.pgoorts.tripplanner.ui.reminder.OpenedReminderScreen
import com.pgoorts.tripplanner.ui.signin.SignInScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

sealed class Screen(val route: String) {
    object SignIn : Screen("signIn")
    object Home : Screen("home")
    object OpenedTrip : Screen("openedTrip/{tripId}") {
        fun createRoute(tripId: String) = "openedTrip/$tripId"
    }
    object OpenedEvent : Screen("openedEvent/{eventId}") {
        fun createRoute(eventId: String) = "openedEvent/$eventId"
    }
    object OpenedNote : Screen("openedNote/{noteId}") {
        fun createRoute(noteId: String) = "openedNote/$noteId"
    }
    object OpenedReminder : Screen("openedReminder/{reminderId}") {
        fun createRoute(reminderId: String) = "openedReminder/$reminderId"
    }
    object Settings : Screen("settings")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var googleAuthClient: GoogleAuthClient

    @Inject
    lateinit var userSessionManager: UserSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val webClientId = getString(R.string.default_web_client_id)

        setContent {
            TripPlannerTheme {
                val navController = rememberNavController()
                val startDestination = if (userSessionManager.isSignedIn) {
                    Screen.Home.route
                } else {
                    Screen.SignIn.route
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.SignIn.route) {
                        SignInScreen(
                            googleAuthClient = googleAuthClient,
                            webClientId = webClientId,
                            onSignInSuccess = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.SignIn.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(
                            onTripClick = { tripId ->
                                navController.navigate(Screen.OpenedTrip.createRoute(tripId))
                            },
                            onSettingsClick = {
                                navController.navigate(Screen.Settings.route)
                            }
                        )
                    }
                    composable(
                        route = Screen.OpenedTrip.route,
                        arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
                        OpenedTripScreen(
                            tripId = tripId,
                            onBack = { navController.popBackStack() },
                            onEventClick = { eventId ->
                                navController.navigate(Screen.OpenedEvent.createRoute(eventId))
                            },
                            onNoteClick = { noteId ->
                                navController.navigate(Screen.OpenedNote.createRoute(noteId))
                            },
                            onReminderClick = { reminderId ->
                                navController.navigate(Screen.OpenedReminder.createRoute(reminderId))
                            }
                        )
                    }
                    composable(
                        route = Screen.OpenedEvent.route,
                        arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
                        OpenedEventScreen(
                            eventId = eventId,
                            onBack = { navController.popBackStack() },
                            onNoteClick = { noteId ->
                                navController.navigate(Screen.OpenedNote.createRoute(noteId))
                            },
                            onReminderClick = { reminderId ->
                                navController.navigate(Screen.OpenedReminder.createRoute(reminderId))
                            }
                        )
                    }
                    composable(
                        route = Screen.OpenedNote.route,
                        arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
                        OpenedNoteScreen(
                            noteId = noteId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Screen.OpenedReminder.route,
                        arguments = listOf(navArgument("reminderId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val reminderId = backStackEntry.arguments?.getString("reminderId") ?: return@composable
                        OpenedReminderScreen(
                            reminderId = reminderId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            googleAuthClient = googleAuthClient,
                            onBack = { navController.popBackStack() },
                            onSignOut = {
                                navController.navigate(Screen.SignIn.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
