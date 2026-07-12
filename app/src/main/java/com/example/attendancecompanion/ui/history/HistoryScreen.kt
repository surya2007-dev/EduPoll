package com.example.attendancecompanion.ui.history

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.attendancecompanion.theme.AttendanceTheme
import com.example.attendancecompanion.ui.dashboard.parseColor
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors

    Column(modifier = modifier.fillMaxSize()) {
        // Toggle view mode & export
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // View Mode Selector (Monochrome Buttons)
            Row(
                modifier = Modifier
                    .border(1.dp, customColors.border, RoundedCornerShape(8.dp))
                    .background(customColors.surface2, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                val isByDay = state.viewMode == HistoryViewMode.BY_DAY
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isByDay) colors.primary else Color.Transparent)
                        .clickable { viewModel.setViewMode(HistoryViewMode.BY_DAY) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "BY DATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isByDay) colors.background else colors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isByDay) colors.primary else Color.Transparent)
                        .clickable { viewModel.setViewMode(HistoryViewMode.BY_SUBJECT) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "BY SUBJECT",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!isByDay) colors.background else colors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Export Button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val uri = viewModel.exportToCsv(context)
                        if (uri != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Attendance History"))
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                border = ButtonDefaults.outlinedButtonBorder(true).copy(width = 1.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("EXPORT CSV", style = MaterialTheme.typography.labelSmall)
            }
        }

        Divider(color = customColors.border, thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))

        // Content
        when (state.viewMode) {
            HistoryViewMode.BY_DAY -> {
                if (state.recordsByDate.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp)
                            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "NO HISTORY RECORDS FOUND",
                            style = MaterialTheme.typography.bodyMedium,
                            color = customColors.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        state.recordsByDate.forEach { (dateGroup, items) ->
                            item {
                                Text(
                                    text = dateGroup,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = customColors.textDim,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            
                            items(items) { dayItem ->
                                HistoryDayRow(dayItem)
                            }
                        }
                    }
                }
            }
            HistoryViewMode.BY_SUBJECT -> {
                if (state.subjectAnalytics.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp)
                            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "NO SUBJECTS TO ANALYZE",
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
                        items(state.subjectAnalytics) { analytic ->
                            HistorySubjectRow(analytic)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- UI Sub-Components ----------------

@Composable
fun HistoryDayRow(item: HistoryDayItem) {
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors
    val subjectColor = item.subject?.colorHex?.let { parseColor(it) } ?: colors.primary
    val isMarked = item.record.status == "MARKED"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(90.dp)) {
            Text(
                text = item.period?.startTime ?: "--:--",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )
            Text(
                text = item.period?.endTime ?: "--:--",
                style = MaterialTheme.typography.bodySmall,
                color = customColors.textDim
            )
        }

        Box(
            modifier = Modifier
                .size(10.dp)
                .background(subjectColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.subject?.name ?: "Unknown Subject",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = if (isMarked) TextDecoration.LineThrough else null,
                color = colors.onBackground
            )
            val room = item.period?.roomOverride?.ifEmpty { item.subject?.defaultRoom } ?: "No Room"
            Text(
                text = room,
                style = MaterialTheme.typography.bodySmall,
                color = customColors.textDim
            )
        }

        if (isMarked) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = customColors.green,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = "✕",
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HistorySubjectRow(analytic: HistorySubjectAnalytic) {
    val colors = MaterialTheme.colorScheme
    val customColors = AttendanceTheme.customColors
    val subjectColor = parseColor(analytic.subject.colorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, customColors.border, RoundedCornerShape(16.dp))
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored side-chip
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .background(subjectColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))

        // Info Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = analytic.subject.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${analytic.attendedCount} ATTENDED / ${analytic.totalCount} TOTAL",
                style = MaterialTheme.typography.bodySmall,
                color = customColors.textDim
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Custom drawn canvas progress ring
        AttendanceProgressRing(
            percentage = analytic.percentage,
            color = if (analytic.percentage >= 75f) customColors.green else colors.primary,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun AttendanceProgressRing(
    percentage: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val customColors = AttendanceTheme.customColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            // Draw background gray path
            drawArc(
                color = customColors.dotColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
            // Draw progress colored path
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = percentage * 3.6f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${percentage.toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
