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
                    ) {
                        // Placeholder — implemented in Block 4
                        PlaceholderScreen("Opened Trip")
                    }
                    composable(
                        route = Screen.OpenedEvent.route,
                        arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                    ) {
                        PlaceholderScreen("Opened Event")
                    }
                    composable(
                        route = Screen.OpenedNote.route,
                        arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                    ) {
                        PlaceholderScreen("Opened Note")
                    }
                    composable(
                        route = Screen.OpenedReminder.route,
                        arguments = listOf(navArgument("reminderId") { type = NavType.StringType })
                    ) {
                        PlaceholderScreen("Opened Reminder")
                    }
                    composable(Screen.Profile.route) {
                        PlaceholderScreen("Profile")
                    }
                }
            }
        }
    }
}
