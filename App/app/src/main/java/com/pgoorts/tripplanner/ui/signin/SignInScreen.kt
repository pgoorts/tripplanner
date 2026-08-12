@file:OptIn(ExperimentalMaterial3Api::class)

package com.pgoorts.tripplanner.ui.signin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pgoorts.tripplanner.auth.GoogleAuthClient
import com.pgoorts.tripplanner.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    googleAuthClient: GoogleAuthClient,
    webClientId: String,
    onSignInSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = Teal300,
                modifier = Modifier.size(96.dp)
            )

            Text(
                text = "TripPlanner",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            )

            Text(
                text = "Sign in with your Google account to sync your trips across devices and collaborate with others.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Grey300,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Teal300)
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val result = googleAuthClient.signIn(
                                activityContext = context,
                                webClientId = webClientId
                            )
                            isLoading = false
                            if (result.isSuccess) {
                                onSignInSuccess()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Sign-in failed: ${result.exceptionOrNull()?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal400)
                ) {
                    Text(
                        "Sign in with Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = White
                    )
                }
            }
        }
    }
}
