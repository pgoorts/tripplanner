package com.pgoorts.tripplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreenPlaceholder()
        }
        composable(
            route = Screen.OpenedTrip.route,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            PlaceholderScreen(title = "Opened Trip Screen", detail = "Trip ID: $tripId")
        }
        composable(
            route = Screen.OpenedEvent.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            PlaceholderScreen(title = "Opened Event Screen", detail = "Event ID: $eventId")
        }
        composable(
            route = Screen.OpenedNote.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
            PlaceholderScreen(title = "Opened Note Screen", detail = "Note ID: $noteId")
        }
        composable(
            route = Screen.OpenedReminder.route,
            arguments = listOf(navArgument("reminderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getString("reminderId") ?: ""
            PlaceholderScreen(title = "Opened Reminder Screen", detail = "Reminder ID: $reminderId")
        }
        composable(Screen.Profile.route) {
            PlaceholderScreen(title = "Profile Screen", detail = "User Profile Settings")
        }
    }
}

@Composable
fun HomeScreenPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Welcome to TripPlanner!",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun PlaceholderScreen(title: String, detail: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$title\n$detail",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
