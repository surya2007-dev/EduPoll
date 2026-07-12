package com.example.attendancecompanion.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancecompanion.data.DataRepository
import com.example.attendancecompanion.theme.AttendanceTheme
import com.example.attendancecompanion.ui.dashboard.DashboardScreen
import com.example.attendancecompanion.ui.dashboard.DashboardViewModel
import com.example.attendancecompanion.ui.history.HistoryScreen
import com.example.attendancecompanion.ui.history.HistoryViewModel
import com.example.attendancecompanion.ui.profile.ProfileScreen
import com.example.attendancecompanion.ui.profile.ProfileViewModel
import com.example.attendancecompanion.ui.schedule.ScheduleScreen
import com.example.attendancecompanion.ui.schedule.ScheduleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: DataRepository,
    onOpenWebView: (String) -> Unit,
    onOpenQRScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    // ViewModels initialized with local DB repository
    val dashboardViewModel: DashboardViewModel = viewModel { DashboardViewModel(repository) }
    val scheduleViewModel: ScheduleViewModel = viewModel { ScheduleViewModel(repository) }
    val historyViewModel: HistoryViewModel = viewModel { HistoryViewModel(repository) }
    val profileViewModel: ProfileViewModel = viewModel { ProfileViewModel(repository) }

    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent, // Let parent background handles it
        bottomBar = {
            // Curated Monochrome Navigation Bar
            NavigationBar(
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .border(1.dp, customColors.border, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                // Home Tab
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("HOME", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        unselectedIconColor = customColors.textDim,
                        unselectedTextColor = customColors.textDim,
                        indicatorColor = colors.surface
                    )
                )

                // Schedule Tab
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Schedule") },
                    label = { Text("SCHEDULE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        unselectedIconColor = customColors.textDim,
                        unselectedTextColor = customColors.textDim,
                        indicatorColor = colors.surface
                    )
                )

                // History Tab
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("HISTORY", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        unselectedIconColor = customColors.textDim,
                        unselectedTextColor = customColors.textDim,
                        indicatorColor = colors.surface
                    )
                )

                // Profile Tab
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("PROFILE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        unselectedIconColor = customColors.textDim,
                        unselectedTextColor = customColors.textDim,
                        indicatorColor = colors.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    viewModel = dashboardViewModel,
                    onOpenWebView = onOpenWebView,
                    onOpenQRScanner = onOpenQRScanner
                )
                1 -> ScheduleScreen(
                    viewModel = scheduleViewModel
                )
                2 -> HistoryScreen(
                    viewModel = historyViewModel
                )
                3 -> ProfileScreen(
                    viewModel = profileViewModel
                )
            }
        }
    }
}
