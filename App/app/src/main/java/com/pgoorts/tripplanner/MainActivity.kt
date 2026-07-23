package com.pgoorts.tripplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pgoorts.tripplanner.ui.home.HomeScreen
import com.pgoorts.tripplanner.ui.theme.TripPlannerTheme
import com.pgoorts.tripplanner.ui.trip.OpenedTripScreen
import com.pgoorts.tripplanner.ui.event.OpenedEventScreen
import com.pgoorts.tripplanner.ui.note.OpenedNoteScreen
import com.pgoorts.tripplanner.ui.reminder.OpenedReminderScreen
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String) {
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
    object Profile : Screen("profile")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TripPlannerTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            onTripClick = { tripId ->
                                navController.navigate(Screen.OpenedTrip.createRoute(tripId))
                            },
                            onProfileClick = {
                                navController.navigate(Screen.Profile.route)
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
                    composable(Screen.Profile.route) {
                        PlaceholderScreen("Profile")
                    }
                }
            }
        }
    }
}
