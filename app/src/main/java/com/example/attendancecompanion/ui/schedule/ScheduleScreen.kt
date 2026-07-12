package com.example.attendancecompanion.ui.schedule

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.attendancecompanion.theme.AttendanceTheme
import com.example.attendancecompanion.theme.AccentRed
import com.example.attendancecompanion.ui.dashboard.parseColor
import com.example.attendancecompanion.ui.onboarding.CustomInputField
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = TODAY, 1 = WEEK, 2 = FULL
    
    // Bottom Sheet states
    var showAddEditSheet by remember { mutableStateOf(false) }
    var editingPeriodItem by remember { mutableStateOf<TimetableItem?>(null) }
    
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Tab Header (Stylized Monochrome Tabs)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.background,
                contentColor = colors.onBackground,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = colors.primary
                    )
                },
                divider = { Divider(color = customColors.border, thickness = 1.dp) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("TODAY", style = MaterialTheme.typography.labelSmall) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("WEEK", style = MaterialTheme.typography.labelSmall) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("TIMETABLE", style = MaterialTheme.typography.labelSmall) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    // Today Tab
                    val today = getAppDayOfWeek()
                    val todayClasses = state.timetableByDay[today] ?: emptyList()
                    ScheduleList(
                        classes = todayClasses,
                        emptyText = "NO CLASSES SCHEDULED TODAY",
                        onItemClick = { item ->
                            editingPeriodItem = item
                            showAddEditSheet = true
                        }
                    )
                }
                1 -> {
                    // Week Tab
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Day Selector Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val days = listOf("M", "T", "W", "T", "F", "S", "S")
                            days.forEachIndexed { index, name ->
                                val dayNum = index + 1
                                val isSelected = state.weekSelectedDay == dayNum
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) colors.primary else customColors.surface2)
                                        .clickable { viewModel.selectDay(dayNum) }
                                        .border(
                                            1.dp,
                                            if (isSelected) colors.primary else customColors.border,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) colors.background else colors.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val weekClasses = state.timetableByDay[state.weekSelectedDay] ?: emptyList()
                        ScheduleList(
                            classes = weekClasses,
                            emptyText = "NO CLASSES SCHEDULED FOR THIS DAY",
                            onItemClick = { item ->
                                editingPeriodItem = item
                                showAddEditSheet = true
                            }
                        )
                    }
                }
                2 -> {
                    // Full Timetable Expandable Accordions
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val dayNames = listOf(
                            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
                        )
                        dayNames.forEachIndexed { index, name ->
                            val dayNum = index + 1
                            val dayClasses = state.timetableByDay[dayNum] ?: emptyList()
                            
                            item {
                                DayAccordion(
                                    name = name,
                                    classes = dayClasses,
                                    onItemClick = { item ->
                                        editingPeriodItem = item
                                        showAddEditSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for adding periods
        if (selectedTab == 2) {
            FloatingActionButton(
                onClick = {
                    editingPeriodItem = null
                    showAddEditSheet = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 72.dp),
                shape = CircleShape,
                containerColor = colors.primary,
                contentColor = colors.background
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add period")
            }
        }

        // Add/Edit Period Bottom Sheet
        if (showAddEditSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showAddEditSheet = false
                    editingPeriodItem = null
                },
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = customColors.border) }
            ) {
                AddEditPeriodSheetContent(
                    item = editingPeriodItem,
                    defaultDay = if (selectedTab == 1) state.weekSelectedDay else getAppDayOfWeek(),
                    onSave = { day, start, end, name, color, room, notes ->
                        viewModel.savePeriod(
                            periodId = editingPeriodItem?.period?.id ?: 0,
                            dayOfWeek = day,
                            startTime = start,
                            endTime = end,
                            subjectName = name,
                            subjectColorHex = color,
                            room = room,
                            notesUrl = notes
                        )
                        showAddEditSheet = false
                        editingPeriodItem = null
                        Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        editingPeriodItem?.let {
                            viewModel.deletePeriod(it.period)
                        }
                        showAddEditSheet = false
                        editingPeriodItem = null
                        Toast.makeText(context, "Deleted period", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

// ---------------- Accordion Component ----------------

@Composable
fun DayAccordion(
    name: String,
    classes: List<TimetableItem>,
    onItemClick: (TimetableItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
            .background(colors.surface, RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${classes.size} CLASSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = customColors.textDim
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Expand",
                    tint = customColors.textDim,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = customColors.border, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (classes.isEmpty()) {
                Text(
                    "NO CLASSES CONFIG",
                    style = MaterialTheme.typography.bodySmall,
                    color = customColors.textMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                classes.forEach { item ->
                    val color = item.subject?.colorHex?.let { parseColor(it) } ?: colors.primary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${item.period.startTime} – ${item.period.endTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onBackground,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            text = item.subject?.name ?: "No Subject",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Add/Edit Form Sheet ----------------

@Composable
fun AddEditPeriodSheetContent(
    item: TimetableItem?,
    defaultDay: Int,
    onSave: (day: Int, start: String, end: String, name: String, color: String, room: String, notes: String) -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors
    
    var day by remember { mutableStateOf(item?.period?.dayOfWeek ?: defaultDay) }
    var startTime by remember { mutableStateOf(item?.period?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(item?.period?.endTime ?: "09:50") }
    var subjectName by remember { mutableStateOf(item?.subject?.name ?: "") }
    
    // Subject Colors Preset Palette
    val colorOptions = listOf("#FF3C3C", "#3CFF8A", "#3C8AFF", "#AF3CFF", "#FF8A3C", "#FFD33C")
    var selectedColor by remember { mutableStateOf(item?.subject?.colorHex ?: colorOptions[0]) }
    
    var room by remember { mutableStateOf(item?.period?.roomOverride ?: item?.subject?.defaultRoom ?: "") }
    var notesUrl by remember { mutableStateOf(item?.subject?.notesUrl ?: "") }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (item == null) "ADD TIMETABLE SLOT" else "EDIT TIMETABLE SLOT",
                style = MaterialTheme.typography.labelSmall,
                color = customColors.textDim
            )
        }

        // Subject Name Input
        item {
            Column {
                Text("SUBJECT NAME", style = MaterialTheme.typography.labelSmall, color = customColors.textDim, modifier = Modifier.padding(bottom = 6.dp))
                CustomInputField(value = subjectName, onValueChange = { subjectName = it }, placeholder = "e.g., Software Engineering")
            }
        }

        // Color Picker Preset
        item {
            Column {
                Text("SUBJECT ACCENT COLOR", style = MaterialTheme.typography.labelSmall, color = customColors.textDim, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colorOptions.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseColor(hex))
                                .clickable { selectedColor = hex }
                                .border(
                                    2.dp,
                                    if (isSelected) colors.onBackground else Color.Transparent,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }

        // Day of Week Picker (Row)
        item {
            Column {
                Text("DAY OF WEEK", style = MaterialTheme.typography.labelSmall, color = customColors.textDim, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val days = listOf("M", "T", "W", "T", "F", "S", "S")
                    days.forEachIndexed { index, name ->
                        val dNum = index + 1
                        val isSelected = day == dNum
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) colors.primary else customColors.surface2)
                                .clickable { day = dNum }
                                .border(1.dp, customColors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) colors.background else colors.onBackground
                            )
                        }
                    }
                }
            }
        }

        // Start & End Time Inputs
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("START TIME", style = MaterialTheme.typography.labelSmall, color = customColors.textDim, modifier = Modifier.padding(bottom = 6.dp))
                    CustomInputField(value = startTime, onValueChange = { startTime = it }, placeholder = "09:00")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("END TIME", style = MaterialTheme.typography.labelSmall, color = customColors.textDim, modifier = Modifier.padding(bottom = 6.dp))
                    CustomInputField(value = endTime, onValueChange = { endTime = it }, placeholder = "09:50")
                }
            }
        }

        // Room
        item {
            Column {
                Text("ROOM / CLASSROOM", style = MaterialTheme.typography.labelSmall, color = customColors.textDim, modifier = Modifier.padding(bottom = 6.dp))
                CustomInputField(value = room, onValueChange = { room = it }, placeholder = "e.g., E2106")
            }
        }

        // Notes link
        item {
            Column {
                Text("NOTES LINK (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = customColors.textDim, modifier = Modifier.padding(bottom = 6.dp))
                CustomInputField(value = notesUrl, onValueChange = { notesUrl = it }, placeholder = "https://drive.google.com/...")
            }
        }

        // Action Buttons
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (item != null) {
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("DELETE", style = MaterialTheme.typography.bodyMedium, color = colors.background)
                    }
                }
                
                Button(
                    onClick = {
                        if (subjectName.trim().isEmpty()) return@Button
                        onSave(day, startTime, endTime, subjectName, selectedColor, room, notesUrl)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = subjectName.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("SAVE", style = MaterialTheme.typography.bodyMedium, color = colors.background)
                }
            }
        }
    }
}

// ---------------- Period List Display ----------------

@Composable
fun ScheduleList(
    classes: List<TimetableItem>,
    emptyText: String,
    onItemClick: (TimetableItem) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors

    if (classes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp)
                .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = customColors.textMuted
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(classes) { item ->
                val subColor = item.subject?.colorHex?.let { parseColor(it) } ?: colors.primary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .clickable { onItemClick(item) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(90.dp)) {
                        Text(
                            text = item.period.startTime,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                        Text(
                            text = item.period.endTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.textDim
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(subColor, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    val room = item.period.roomOverride.ifEmpty { item.subject?.defaultRoom ?: "No Room" }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.subject?.name ?: "No Subject",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                        Text(
                            text = room,
                            style = MaterialTheme.typography.bodySmall,
                            color = customColors.textDim
                        )
                    }
                }
            }
        }
    }
}

private fun getAppDayOfWeek(): Int {
    val calendar = java.util.Calendar.getInstance()
    return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> 1
        java.util.Calendar.TUESDAY -> 2
        java.util.Calendar.WEDNESDAY -> 3
        java.util.Calendar.THURSDAY -> 4
        java.util.Calendar.FRIDAY -> 5
        java.util.Calendar.SATURDAY -> 6
        java.util.Calendar.SUNDAY -> 7
        else -> 1
    }
}
