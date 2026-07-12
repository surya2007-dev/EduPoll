package com.example.attendancecompanion.ui.dashboard

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.attendancecompanion.theme.AttendanceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenWebView: (String) -> Unit,
    onOpenQRScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    // Bottom Sheet states
    var selectedPeriodItem by remember { mutableStateOf<DashboardPeriodItem?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // A. Status Strip
            item {
                StatusStrip(
                    timeText = state.timeText,
                    dateText = state.todayDateText,
                    showColon = state.showBlinkingColon
                )
            }

            // B. Current Period Hero Card
            item {
                CurrentPeriodHero(
                    currentState = state.currentPeriod,
                    onOpenAttendance = { showActionSheet = true }
                )
            }

            // C. Two Mini-Widgets (side by side)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniWidgetNextAttendance(
                        modifier = Modifier.weight(1f),
                        nextState = state.nextPeriod
                    )
                    MiniWidgetProgress(
                        modifier = Modifier.weight(1f),
                        marked = state.progressMarked,
                        total = state.progressTotal
                    )
                }
            }

            // D. Master Link Card
            item {
                val masterLink = state.student?.masterLink ?: ""
                MasterLinkCard(
                    link = masterLink,
                    onOpen = { if (masterLink.isNotEmpty()) onOpenWebView(masterLink) },
                    onCopy = {
                        if (masterLink.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(masterLink))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = {
                        if (masterLink.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, masterLink)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Master Link"))
                        }
                    }
                )
            }

            // E. Today's Schedule list header
            item {
                Text(
                    text = "TODAY'S SCHEDULE",
                    style = MaterialTheme.typography.labelSmall,
                    color = customColors.textDim,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // Schedule Items
            if (state.todayPeriods.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "NO CLASSES SCHEDULED TODAY",
                            style = MaterialTheme.typography.bodyMedium,
                            color = customColors.textMuted
                        )
                    }
                }
            } else {
                items(state.todayPeriods, key = { it.period.id }) { item ->
                    ScheduleRowItem(
                        item = item,
                        onClick = { selectedPeriodItem = item }
                    )
                }
            }
        }

        // Attendance Action Sheet
        if (showActionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showActionSheet = false },
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = customColors.border) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        "OPEN ATTENDANCE PORTAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = customColors.textDim,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Button(
                        onClick = {
                            showActionSheet = false
                            onOpenQRScanner()
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("SCAN QR CODE", style = MaterialTheme.typography.bodyMedium, color = colors.background)
                    }

                    OutlinedButton(
                        onClick = {
                            showActionSheet = false
                            val link = state.student?.masterLink ?: ""
                            if (link.isNotEmpty()) {
                                onOpenWebView(link)
                            } else {
                                Toast.makeText(context, "No Master Link set in Profile", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(true).copy(width = 1.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground)
                    ) {
                        Text("OPEN MASTER LINK", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Period Detail Bottom Sheet
        if (selectedPeriodItem != null) {
            val item = selectedPeriodItem!!
            ModalBottomSheet(
                onDismissRequest = { selectedPeriodItem = null },
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = customColors.border) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        "PERIOD DETAIL",
                        style = MaterialTheme.typography.labelSmall,
                        color = customColors.textDim,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = item.subject?.name ?: "No Subject",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onBackground
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Room
                    val room = item.period.roomOverride.ifEmpty { item.subject?.defaultRoom ?: "No Room" }
                    DetailRow(label = "ROOM", value = room)
                    DetailRow(label = "TIME", value = "${item.period.startTime} – ${item.period.endTime}")
                    
                    // Notes Link if any
                    val notesUrl = item.subject?.notesUrl ?: ""
                    if (notesUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = customColors.border, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "NOTES LINK",
                                style = MaterialTheme.typography.labelMedium,
                                color = customColors.textDim
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(notesUrl))
                                Toast.makeText(context, "Notes URL copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy notes link", tint = colors.primary)
                            }
                        }
                        Text(
                            text = notesUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                onOpenWebView(notesUrl)
                                selectedPeriodItem = null
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = customColors.border, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mark status toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "MARKED PRESENT",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onBackground
                        )
                        Switch(
                            checked = item.isMarked,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    viewModel.markPeriodStatus(item.period.id, isChecked)
                                    selectedPeriodItem = null
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.primary)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// ---------------- UI Components ----------------

@Composable
fun StatusStrip(timeText: String, dateText: String, showColon: Boolean) {
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        val colon = if (showColon) ":" else " "
        val splitTime = timeText.split(":")
        
        Row(verticalAlignment = Alignment.Bottom) {
            if (splitTime.size == 2) {
                Text(
                    text = splitTime[0],
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.onBackground
                )
                Text(
                    text = colon,
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.onBackground,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = splitTime[1],
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.onBackground
                )
            } else {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.onBackground
                )
            }
        }
        
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelSmall,
            color = customColors.textDim,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun CurrentPeriodHero(
    currentState: CurrentPeriodState,
    onOpenAttendance: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors
    
    // Slow pulse transition for active period dot (2s loop)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, customColors.borderLight, RoundedCornerShape(20.dp))
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CURRENT PERIOD",
                style = MaterialTheme.typography.labelSmall,
                color = customColors.textDim
            )
            
            // State indicator
            when (currentState.state) {
                PeriodState.OPEN -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(alphaPulse)
                                .background(customColors.green, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "ATTENDANCE OPEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = customColors.green
                        )
                    }
                }
                PeriodState.OPENS_SOON -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colors.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "OPENS IN ${currentState.countdownText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary
                        )
                    }
                }
                PeriodState.CLOSED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(customColors.textMuted, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "ATTENDANCE CLOSED",
                            style = MaterialTheme.typography.labelSmall,
                            color = customColors.textDim
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentState.period == null) {
            Text(
                "NO ACTIVE CLASS",
                style = MaterialTheme.typography.titleLarge,
                color = customColors.textMuted
            )
            Text(
                "No class scheduled at this time",
                style = MaterialTheme.typography.bodyMedium,
                color = customColors.textDim,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            val subjectColor = currentState.subject?.colorHex?.let { parseColor(it) } ?: colors.primary
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(subjectColor, RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = currentState.subject?.name ?: "No Subject",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val room = currentState.period.roomOverride.ifEmpty { currentState.subject?.defaultRoom ?: "No Room" }
            Text(
                text = "${currentState.period.startTime} – ${currentState.period.endTime}  ·  $room",
                style = MaterialTheme.typography.bodyMedium,
                color = customColors.textDim
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main CTA
            Button(
                onClick = onOpenAttendance,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = currentState.state != PeriodState.CLOSED,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    disabledContainerColor = customColors.surface2,
                    disabledContentColor = customColors.textMuted
                )
            ) {
                Text(
                    text = "OPEN ATTENDANCE",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentState.state != PeriodState.CLOSED) colors.background else customColors.textMuted
                )
            }
        }
    }
}

@Composable
fun MiniWidgetNextAttendance(
    modifier: Modifier = Modifier,
    nextState: CurrentPeriodState
) {
    val customColors = AttendanceTheme.customColors
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .height(76.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "NEXT WINDOW",
            style = MaterialTheme.typography.labelSmall,
            color = customColors.textDim
        )
        if (nextState.period == null) {
            Text(
                "NONE",
                style = MaterialTheme.typography.titleMedium,
                color = customColors.textMuted
            )
        } else {
            Column {
                Text(
                    text = nextState.subject?.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (nextState.state == PeriodState.OPEN) "OPEN NOW" else "IN ${nextState.countdownText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (nextState.state == PeriodState.OPEN) customColors.green else colors.primary
                )
            }
        }
    }
}

@Composable
fun MiniWidgetProgress(
    modifier: Modifier = Modifier,
    marked: Int,
    total: Int
) {
    val customColors = AttendanceTheme.customColors
    val colors = MaterialTheme.colorScheme
    val fraction = if (total > 0) marked.toFloat() / total else 0f

    Column(
        modifier = modifier
            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .height(76.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "TODAY'S WORK",
                style = MaterialTheme.typography.labelSmall,
                color = customColors.textDim
            )
            Text(
                "$marked / $total",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onBackground
            )
        }
        
        Column {
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (fraction == 1f) customColors.green else colors.primary,
                trackColor = customColors.border
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun MasterLinkCard(
    link: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val customColors = AttendanceTheme.customColors
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "Master link",
                tint = customColors.textDim,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (link.isEmpty()) "NO MASTER LINK SAVED" else link,
                style = MaterialTheme.typography.bodySmall,
                color = if (link.isEmpty()) customColors.textMuted else colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpen,
                enabled = link.isNotEmpty(),
                modifier = Modifier.weight(1f).height(36.dp).border(1.dp, customColors.border, RoundedCornerShape(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onBackground)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("OPEN", style = MaterialTheme.typography.bodySmall)
                }
            }

            IconButton(
                onClick = onCopy,
                enabled = link.isNotEmpty(),
                modifier = Modifier.weight(1f).height(36.dp).border(1.dp, customColors.border, RoundedCornerShape(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onBackground)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY", style = MaterialTheme.typography.bodySmall)
                }
            }

            IconButton(
                onClick = onShare,
                enabled = link.isNotEmpty(),
                modifier = Modifier.weight(1f).height(36.dp).border(1.dp, customColors.border, RoundedCornerShape(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onBackground)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHARE", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ScheduleRowItem(
    item: DashboardPeriodItem,
    onClick: () -> Unit
) {
    val customColors = AttendanceTheme.customColors
    val colors = MaterialTheme.colorScheme
    val subjectColor = item.subject?.colorHex?.let { parseColor(it) } ?: colors.primary

    // Styling based on status
    val opacity = if (item.isMarked) 0.5f else 1f
    val subjectDecoration = if (item.isMarked) TextDecoration.LineThrough else null
    val rowModifier = if (item.isActive && !item.isMarked) {
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.primary, RoundedCornerShape(16.dp))
            .background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
    } else {
        Modifier
            .fillMaxWidth()
            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
            .background(colors.surface, RoundedCornerShape(16.dp))
    }

    Row(
        modifier = rowModifier
            .alpha(opacity)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time Column
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

        // Color Indicator
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(subjectColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Info Column
        val room = item.period.roomOverride.ifEmpty { item.subject?.defaultRoom ?: "No Room" }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.subject?.name ?: "No Subject",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = subjectDecoration,
                color = colors.onBackground
            )
            Text(
                text = room,
                style = MaterialTheme.typography.bodySmall,
                color = customColors.textDim
            )
        }

        // Status indicator
        if (item.isMarked) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = customColors.green,
                fontWeight = FontWeight.Bold
            )
        } else if (item.isActive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colors.primary, CircleShape)
            )
        } else {
            Text(
                text = "□",
                style = MaterialTheme.typography.titleMedium,
                color = customColors.textMuted
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    val customColors = AttendanceTheme.customColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = customColors.textDim
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// Utility to parse hex color safely
fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Red
    }
}
