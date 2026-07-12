package com.example.attendancecompanion.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.attendancecompanion.theme.AccentRed
import com.example.attendancecompanion.theme.AttendanceTheme
import com.example.attendancecompanion.ui.onboarding.CustomInputField

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Form States
    var registerNumber by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var masterLink by remember { mutableStateOf("") }
    
    var showResetDialog by remember { mutableStateOf(false) }

    // Sync input fields with DB load
    LaunchedEffect(state.student) {
        state.student?.let {
            registerNumber = it.registerNumber
            displayName = it.displayName
            masterLink = it.masterLink
        }
    }

    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "PROFILE & SETTINGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = customColors.textDim
                )
            }

            // 1. Identity & Link Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("IDENTITY", style = MaterialTheme.typography.labelSmall, color = customColors.textDim)
                    
                    Column {
                        Text("REGISTER NUMBER", style = MaterialTheme.typography.labelSmall, color = customColors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                        CustomInputField(value = registerNumber, onValueChange = { registerNumber = it }, placeholder = "e.g., SEC25IT367")
                    }

                    Column {
                        Text("DISPLAY NAME", style = MaterialTheme.typography.labelSmall, color = customColors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                        CustomInputField(value = displayName, onValueChange = { displayName = it }, placeholder = "e.g., Alex")
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("PORTAL CONFIG", style = MaterialTheme.typography.labelSmall, color = customColors.textDim)

                    Column {
                        Text("MASTER LINK", style = MaterialTheme.typography.labelSmall, color = customColors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                        CustomInputField(value = masterLink, onValueChange = { masterLink = it }, placeholder = "https://script.google.com/...")
                    }

                    Button(
                        onClick = {
                            viewModel.updateStudent(registerNumber, displayName, masterLink)
                            Toast.makeText(context, "Identity details updated", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("SAVE CHANGES", style = MaterialTheme.typography.bodyMedium, color = colors.background)
                    }
                }
            }

            // 2. Notifications Config Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("NOTIFICATIONS", style = MaterialTheme.typography.labelSmall, color = customColors.textDim)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TIMETABLE REMINDERS", style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
                            Text("Trigger pre-attendance alert dialogs", style = MaterialTheme.typography.bodySmall, color = customColors.textDim)
                        }
                        Switch(
                            checked = state.settings.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotifications(enabled, state.settings.preAlertMinutes)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.primary)
                        )
                    }
                    
                    if (state.settings.notificationsEnabled) {
                        Divider(color = customColors.border, thickness = 1.dp)
                        
                        Text(
                            "REMINDER LEAD TIME: ${state.settings.preAlertMinutes} MINUTES",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onBackground
                        )
                        
                        Slider(
                            value = state.settings.preAlertMinutes.toFloat(),
                            onValueChange = { floatVal ->
                                viewModel.updateNotifications(state.settings.notificationsEnabled, floatVal.toInt())
                            },
                            valueRange = 1f..30f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = customColors.border
                            )
                        )
                    }
                }
            }

            // 3. Theme Toggle Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("APPEARANCE", style = MaterialTheme.typography.labelSmall, color = customColors.textDim)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DARK MODE", style = MaterialTheme.typography.bodyMedium, color = colors.onBackground)
                        val isDark = state.settings.theme == "DARK"
                        Switch(
                            checked = isDark,
                            onCheckedChange = { check ->
                                viewModel.updateTheme(if (check) "DARK" else "LIGHT")
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.primary)
                        )
                    }
                }
            }

            // 4. Reset & Destructive Actions
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("DATABASE ACTIONS", style = MaterialTheme.typography.labelSmall, color = customColors.textDim)
                    
                    Text(
                        "Resetting wipes all student register numbers, saved master links, customized timetable periods, and history logs permanently from this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.textDim,
                        lineHeight = 16.sp
                    )
                    
                    Button(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("RESET ALL LOCAL DATA", style = MaterialTheme.typography.bodyMedium, color = colors.background)
                    }
                }
            }

            // About
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ATTENDANCE COMPANION v1.0 · NO ADS · OFFLINE-FIRST",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = customColors.textMuted
                    )
                }
            }
        }

        // Reset Alert Dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                containerColor = colors.surface,
                titleContentColor = colors.onSurface,
                textContentColor = colors.onSurface,
                title = { Text("CONFIRM TOTAL RESET", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = { Text("Are you absolutely sure? This action is destructive and cannot be undone. All classes and marked histories will be lost.", style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog = false
                            viewModel.resetAllData()
                            Toast.makeText(context, "All data wiped", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("YES, WIPE DATA", style = MaterialTheme.typography.bodyMedium, color = colors.background)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showResetDialog = false },
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder(true).copy(width = 1.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground)
                    ) {
                        Text("CANCEL", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
        }
    }
}
