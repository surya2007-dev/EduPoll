package com.example.attendancecompanion

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.attendancecompanion.data.DataRepository
import com.example.attendancecompanion.ui.onboarding.OnboardingScreen
import com.example.attendancecompanion.ui.main.MainScreen

@Composable
fun MainNavigation(
    repository: DataRepository,
    onOpenWebView: (String) -> Unit,
    onOpenQRScanner: () -> Unit
) {
    val studentState by repository.getStudent().collectAsStateWithLifecycle(initialValue = null)
    
    // Manage state representing if Room has finished its first loading query
    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(studentState) {
        // Simple delay or checking state to toggle load state
        isLoaded = true
    }

    if (!isLoaded) {
        // Blank screen on initial DB read loading
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
    } else {
        if (studentState == null) {
            OnboardingScreen(
                repository = repository,
                onFinished = {
                    // Triggers state change which recomposes to main dashboard
                }
            )
        } else {
            MainScreen(
                repository = repository,
                onOpenWebView = onOpenWebView,
                onOpenQRScanner = onOpenQRScanner,
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            )
        }
    }
}
