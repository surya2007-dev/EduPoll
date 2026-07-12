package com.example.attendancecompanion.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.attendancecompanion.data.DataRepository
import com.example.attendancecompanion.data.StudentEntity
import com.example.attendancecompanion.data.SettingsEntity
import com.example.attendancecompanion.data.SubjectEntity
import com.example.attendancecompanion.data.PeriodEntity
import com.example.attendancecompanion.theme.AttendanceTheme
import com.example.attendancecompanion.theme.drawDotGrid
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    repository: DataRepository,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()
    
    // Inputs
    var registerNumber by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var masterLink by remember { mutableStateOf("") }
    
    // Notification inputs
    var dailyDigestEnabled by remember { mutableStateOf(true) }
    var preAlertEnabled by remember { mutableStateOf(true) }
    var preAlertLeadTime by remember { mutableStateOf(5) }
    
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .drawDotGrid(customColors.dotColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, customColors.border, RoundedCornerShape(20.dp))
                .background(colors.surface, RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step Number Header (Dot-matrix stylized size)
            Text(
                text = "0$step / 04",
                style = MaterialTheme.typography.displayMedium,
                color = colors.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = when(step) {
                    1 -> "IDENTITY"
                    2 -> "MASTER LINK"
                    3 -> "TIMETABLE"
                    else -> "NOTIFICATIONS"
                },
                style = MaterialTheme.typography.labelSmall,
                color = customColors.textDim,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Content depending on step
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                when (step) {
                    1 -> StepIdentity(
                        registerNumber = registerNumber,
                        onRegisterChange = { registerNumber = it },
                        displayName = displayName,
                        onNameChange = { displayName = it }
                    )
                    2 -> StepMasterLink(
                        masterLink = masterLink,
                        onLinkChange = { masterLink = it }
                    )
                    3 -> StepTimetable()
                    4 -> StepNotifications(
                        dailyDigest = dailyDigestEnabled,
                        onDailyDigestChange = { dailyDigestEnabled = it },
                        preAlert = preAlertEnabled,
                        onPreAlertChange = { preAlertEnabled = it },
                        preAlertLead = preAlertLeadTime,
                        onPreAlertLeadChange = { preAlertLeadTime = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        border = ButtonDefaults.outlinedButtonBorder(true).copy(width = 1.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground)
                    ) {
                        Text("BACK", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (step < 4) {
                            step++
                        } else {
                            scope.launch {
                                // Save student
                                repository.saveStudent(
                                    StudentEntity(
                                        registerNumber = registerNumber.trim().uppercase(),
                                        displayName = displayName.trim(),
                                        masterLink = masterLink.trim()
                                    )
                                )
                                // Save settings
                                repository.saveSettings(
                                    SettingsEntity(
                                        notificationsEnabled = dailyDigestEnabled || preAlertEnabled,
                                        preAlertMinutes = preAlertLeadTime
                                    )
                                )
                                // Add default settings/subjects if needed
                                createDefaultSubjectsAndPeriods(repository)
                                onFinished()
                            }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.background
                    ),
                    enabled = when(step) {
                        1 -> registerNumber.trim().isNotEmpty()
                        else -> true
                    }
                ) {
                    Text(
                        text = if (step == 4) "FINISH" else "CONTINUE",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// ---------------- Helper Setup Functions ----------------
private suspend fun createDefaultSubjectsAndPeriods(repository: DataRepository) {
    // 1. Save Subjects
    val sla = repository.saveSubject(SubjectEntity(name = "SLA (Statistics & Linear Algebra)", colorHex = "#3CFF8A", defaultRoom = "E2106"))
    val ddc = repository.saveSubject(SubjectEntity(name = "DDC (Digital Design & Computer Org)", colorHex = "#3C8AFF", defaultRoom = "E2106"))
    val ds = repository.saveSubject(SubjectEntity(name = "DS (Data Structures & Algorithms)", colorHex = "#FF3C3C", defaultRoom = "E2106"))
    val se = repository.saveSubject(SubjectEntity(name = "SE (Software Engineering)", colorHex = "#A83CFF", defaultRoom = "E2106"))
    val dbms = repository.saveSubject(SubjectEntity(name = "DBMS (Database Management Systems)", colorHex = "#FFA83C", defaultRoom = "E2106"))
    val uhv = repository.saveSubject(SubjectEntity(name = "UHV (Universal Human Values-II)", colorHex = "#3CFFE3", defaultRoom = "E2106"))
    val dsLab = repository.saveSubject(SubjectEntity(name = "DS Lab (Data Structures Lab)", colorHex = "#FF3CD3", defaultRoom = "E2106"))
    val idpLab = repository.saveSubject(SubjectEntity(name = "IDP Lab (Innovation Design Lab-1)", colorHex = "#A8FF3C", defaultRoom = "E2106"))
    val apts = repository.saveSubject(SubjectEntity(name = "APTS (Aptitude Skills)", colorHex = "#E5E1D8", defaultRoom = "E2106"))
    val joy = repository.saveSubject(SubjectEntity(name = "JOY (Joy of Computing - Python)", colorHex = "#8A3CFF", defaultRoom = "E2106"))
    val habits = repository.saveSubject(SubjectEntity(name = "7 HABITS", colorHex = "#888888", defaultRoom = "E2106"))
    val mentoring = repository.saveSubject(SubjectEntity(name = "MENTORING", colorHex = "#BBBBBB", defaultRoom = "E2106"))

    // 2. Insert Periods

    // --- MONDAY (Day 1) ---
    // 09:00 - 10:40: DDC Lab
    repository.savePeriod(PeriodEntity(dayOfWeek = 1, startTime = "09:00", endTime = "09:50", subjectId = ddc, roomOverride = "DDC Lab"))
    repository.savePeriod(PeriodEntity(dayOfWeek = 1, startTime = "09:50", endTime = "10:40", subjectId = ddc, roomOverride = "DDC Lab"))
    // 10:55 - 11:45: DS
    repository.savePeriod(PeriodEntity(dayOfWeek = 1, startTime = "10:55", endTime = "11:45", subjectId = ds))
    // 11:45 - 12:35: DBMS
    repository.savePeriod(PeriodEntity(dayOfWeek = 1, startTime = "11:45", endTime = "12:35", subjectId = dbms))
    // 13:25 - 14:15: SLA
    repository.savePeriod(PeriodEntity(dayOfWeek = 1, startTime = "13:25", endTime = "14:15", subjectId = sla))
    // 14:15 - 15:05: DS
    repository.savePeriod(PeriodEntity(dayOfWeek = 1, startTime = "14:15", endTime = "15:05", subjectId = ds))
    // 15:20 - 16:10: 7 HABITS
    repository.savePeriod(PeriodEntity(dayOfWeek = 1, startTime = "15:20", endTime = "16:10", subjectId = habits))

    // --- TUESDAY (Day 2) ---
    // 09:00 - 09:50: SLA
    repository.savePeriod(PeriodEntity(dayOfWeek = 2, startTime = "09:00", endTime = "09:50", subjectId = sla))
    // 09:50 - 10:40: DBMS
    repository.savePeriod(PeriodEntity(dayOfWeek = 2, startTime = "09:50", endTime = "10:40", subjectId = dbms))
    // 10:55 - 11:45: UHV
    repository.savePeriod(PeriodEntity(dayOfWeek = 2, startTime = "10:55", endTime = "11:45", subjectId = uhv))
    // 11:45 - 12:35: JOY
    repository.savePeriod(PeriodEntity(dayOfWeek = 2, startTime = "11:45", endTime = "12:35", subjectId = joy))
    // 13:25 - 15:05: IDP lab
    repository.savePeriod(PeriodEntity(dayOfWeek = 2, startTime = "13:25", endTime = "14:15", subjectId = idpLab, roomOverride = "IDP Lab"))
    repository.savePeriod(PeriodEntity(dayOfWeek = 2, startTime = "14:15", endTime = "15:05", subjectId = idpLab, roomOverride = "IDP Lab"))
    // 15:20 - 16:10: SE
    repository.savePeriod(PeriodEntity(dayOfWeek = 2, startTime = "15:20", endTime = "16:10", subjectId = se))

    // --- WEDNESDAY (Day 3) ---
    // 09:00 - 09:50: SE
    repository.savePeriod(PeriodEntity(dayOfWeek = 3, startTime = "09:00", endTime = "09:50", subjectId = se))
    // 09:50 - 10:40: APTS
    repository.savePeriod(PeriodEntity(dayOfWeek = 3, startTime = "09:50", endTime = "10:40", subjectId = apts))
    // 10:55 - 11:45: SLA
    repository.savePeriod(PeriodEntity(dayOfWeek = 3, startTime = "10:55", endTime = "11:45", subjectId = sla))
    // 11:45 - 12:35: UHV
    repository.savePeriod(PeriodEntity(dayOfWeek = 3, startTime = "11:45", endTime = "12:35", subjectId = uhv))
    // 13:25 - 14:15: DDC
    repository.savePeriod(PeriodEntity(dayOfWeek = 3, startTime = "13:25", endTime = "14:15", subjectId = ddc))
    // 14:15 - 15:05: DBMS
    repository.savePeriod(PeriodEntity(dayOfWeek = 3, startTime = "14:15", endTime = "15:05", subjectId = dbms))
    // 15:20 - 16:10: SLA
    repository.savePeriod(PeriodEntity(dayOfWeek = 3, startTime = "15:20", endTime = "16:10", subjectId = sla))

    // --- THURSDAY (Day 4) ---
    // 09:00 - 09:50: DS
    repository.savePeriod(PeriodEntity(dayOfWeek = 4, startTime = "09:00", endTime = "09:50", subjectId = ds))
    // 09:50 - 10:40: APTS
    repository.savePeriod(PeriodEntity(dayOfWeek = 4, startTime = "09:50", endTime = "10:40", subjectId = apts))
    // 10:55 - 11:45: DDC
    repository.savePeriod(PeriodEntity(dayOfWeek = 4, startTime = "10:55", endTime = "11:45", subjectId = ddc))
    // 11:45 - 12:35: SLA
    repository.savePeriod(PeriodEntity(dayOfWeek = 4, startTime = "11:45", endTime = "12:35", subjectId = sla))
    // 13:25 - 16:10: DS Lab
    repository.savePeriod(PeriodEntity(dayOfWeek = 4, startTime = "13:25", endTime = "14:15", subjectId = dsLab, roomOverride = "DS Lab"))
    repository.savePeriod(PeriodEntity(dayOfWeek = 4, startTime = "14:15", endTime = "15:05", subjectId = dsLab, roomOverride = "DS Lab"))
    repository.savePeriod(PeriodEntity(dayOfWeek = 4, startTime = "15:20", endTime = "16:10", subjectId = dsLab, roomOverride = "DS Lab"))

    // --- FRIDAY (Day 5) ---
    // 09:00 - 09:50: DDC
    repository.savePeriod(PeriodEntity(dayOfWeek = 5, startTime = "09:00", endTime = "09:50", subjectId = ddc))
    // 09:50 - 10:40: UHV
    repository.savePeriod(PeriodEntity(dayOfWeek = 5, startTime = "09:50", endTime = "10:40", subjectId = uhv))
    // 10:55 - 12:35: DBMS Lab
    repository.savePeriod(PeriodEntity(dayOfWeek = 5, startTime = "10:55", endTime = "11:45", subjectId = dbms, roomOverride = "DBMS Lab"))
    repository.savePeriod(PeriodEntity(dayOfWeek = 5, startTime = "11:45", endTime = "12:35", subjectId = dbms, roomOverride = "DBMS Lab"))
    // 13:25 - 14:15: DS
    repository.savePeriod(PeriodEntity(dayOfWeek = 5, startTime = "13:25", endTime = "14:15", subjectId = ds))
    // 14:15 - 15:05: SE
    repository.savePeriod(PeriodEntity(dayOfWeek = 5, startTime = "14:15", endTime = "15:05", subjectId = se))
    // 15:20 - 16:10: Mentoring
    repository.savePeriod(PeriodEntity(dayOfWeek = 5, startTime = "15:20", endTime = "16:10", subjectId = mentoring))
}

// ---------------- UI Steps ----------------

@Composable
fun StepIdentity(
    registerNumber: String,
    onRegisterChange: (String) -> Unit,
    displayName: String,
    onNameChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "REGISTER NUMBER",
            style = MaterialTheme.typography.labelSmall,
            color = customColors.textDim,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        CustomInputField(
            value = registerNumber,
            onValueChange = onRegisterChange,
            placeholder = "e.g., SEC25IT367",
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            "DISPLAY NAME (OPTIONAL)",
            style = MaterialTheme.typography.labelSmall,
            color = customColors.textDim,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        CustomInputField(
            value = displayName,
            onValueChange = onNameChange,
            placeholder = "e.g., Alex"
        )
    }
}

@Composable
fun StepMasterLink(
    masterLink: String,
    onLinkChange: (String) -> Unit
) {
    val customColors = AttendanceTheme.customColors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "GOOGLE APPS SCRIPT WEB URL",
            style = MaterialTheme.typography.labelSmall,
            color = customColors.textDim,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        CustomInputField(
            value = masterLink,
            onValueChange = onLinkChange,
            placeholder = "https://script.google.com/macros/s/..."
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Tapping Open Attendance loads this link, which maps directly to your hourly-refreshing class portal.",
            style = MaterialTheme.typography.bodySmall,
            color = customColors.textDim,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun StepTimetable() {
    val customColors = AttendanceTheme.customColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "TIMETABLE CONFIGURATION",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "We have populated a few default subjects & periods (Software Engineering, DBMS, Data Structures) on Monday for your first launch. You can freely edit, add, or delete subjects and full schedules in the Schedule Tab.",
            style = MaterialTheme.typography.bodySmall,
            color = customColors.textDim,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun StepNotifications(
    dailyDigest: Boolean,
    onDailyDigestChange: (Boolean) -> Unit,
    preAlert: Boolean,
    onPreAlertChange: (Boolean) -> Unit,
    preAlertLead: Int,
    onPreAlertLeadChange: (Int) -> Unit
) {
    val customColors = AttendanceTheme.customColors
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DAILY DIGEST",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onBackground
                )
                Text(
                    "Morning class rundown (8:45 AM)",
                    style = MaterialTheme.typography.bodySmall,
                    color = customColors.textDim
                )
            }
            Switch(
                checked = dailyDigest,
                onCheckedChange = onDailyDigestChange,
                colors = SwitchDefaults.colors(checkedThumbColor = colors.primary)
            )
        }

        Divider(color = customColors.border, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ATTENDANCE ALERTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onBackground
                )
                Text(
                    "Remind $preAlertLead mins before period start",
                    style = MaterialTheme.typography.bodySmall,
                    color = customColors.textDim
                )
            }
            Switch(
                checked = preAlert,
                onCheckedChange = onPreAlertChange,
                colors = SwitchDefaults.colors(checkedThumbColor = colors.primary)
            )
        }
    }
}

@Composable
fun CustomInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val customColors = AttendanceTheme.customColors
    val colors = MaterialTheme.colorScheme

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = colors.onBackground,
            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
            fontSize = 15.sp
        ),
        cursorBrush = SolidColor(colors.primary),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, customColors.border, RoundedCornerShape(12.dp))
                    .background(customColors.surface2, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = customColors.textMuted
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier
    )
}
