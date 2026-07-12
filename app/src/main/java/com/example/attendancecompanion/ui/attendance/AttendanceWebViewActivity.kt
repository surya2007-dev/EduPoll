package com.example.attendancecompanion.ui.attendance

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.first
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.attendancecompanion.AttendanceApplication
import com.example.attendancecompanion.data.DataRepository
import com.example.attendancecompanion.data.PeriodEntity
import com.example.attendancecompanion.data.SubjectEntity
import com.example.attendancecompanion.data.AttendanceRecordEntity
import com.example.attendancecompanion.theme.AccentRed
import com.example.attendancecompanion.theme.AttendanceCompanionTheme
import com.example.attendancecompanion.theme.AttendanceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AttendanceWebViewActivity : ComponentActivity() {

    private lateinit var repository: DataRepository
    private var webView: WebView? = null
    
    private var registerNumber: String = ""
    private var targetSubjectName: String = "Class"
    private var targetPeriodId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as AttendanceApplication).repository

        val url = intent.getStringExtra("LOAD_URL") ?: ""
        targetPeriodId = intent.getLongExtra("PERIOD_ID", -1)

        CoroutineScope(Dispatchers.IO).launch {
            val student = repository.getStudentDirect()
            registerNumber = student?.registerNumber ?: ""
            
            if (targetPeriodId != -1L) {
                val period = repository.getPeriodById(targetPeriodId)
                val subject = period?.let { repository.getSubjectById(it.subjectId) }
                targetSubjectName = subject?.name ?: "Class"
            } else {
                // Try to find current active period
                val calendar = java.util.Calendar.getInstance()
                val currentMin = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
                val day = getAppDayOfWeek(calendar)
                val periods = repository.getAllPeriods().first().filter { it.dayOfWeek == day }
                val active = periods.find { p ->
                    val startMin = parseTimeToMinutes(p.startTime)
                    val endMin = parseTimeToMinutes(p.endTime)
                    currentMin in startMin until endMin
                }
                val subject = active?.let { repository.getSubjectById(it.subjectId) }
                if (subject != null) {
                    targetPeriodId = active.id
                    targetSubjectName = subject.name
                }
            }

            withContext(Dispatchers.Main) {
                initUi(url)
            }
        }
    }

    private fun initUi(url: String) {
        setContent {
            AttendanceCompanionTheme {
                var progress by remember { mutableStateOf(0f) }
                var showConfirmationDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val customColors = AttendanceTheme.customColors

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = targetSubjectName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showConfirmationDialog = true }) {
                                Icon(Icons.Default.Close, contentDescription = "Close webview", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }

                        // Loading Progress Indicator
                        if (progress < 1f) {
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        }

                        // WebView Frame
                        WebViewComponent(
                            url = url,
                            onProgressChange = { progress = it },
                            registerNumber = registerNumber,
                            modifier = Modifier.weight(1f),
                            onWebViewCreated = { webView = it }
                        )
                    }

                    // Floating Copy Chip (bottom right)
                    if (registerNumber.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(1.dp, customColors.borderLight, RoundedCornerShape(50))
                                    .clickable {
                                        copyToClipboard(registerNumber)
                                        Toast.makeText(context, "Copied Register ID", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = registerNumber,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.background,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Attendance confirmation overlay dialog
                    if (showConfirmationDialog) {
                        AlertDialog(
                            onDismissRequest = { showConfirmationDialog = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            textContentColor = MaterialTheme.colorScheme.onSurface,
                            title = { Text("MARK ATTENDANCE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                            text = { Text("Mark $targetSubjectName as attended in your local history logs?", style = MaterialTheme.typography.bodyMedium) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showConfirmationDialog = false
                                        saveAttendanceRecordAndFinish()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = customColors.green),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("YES, ATTENDED", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.background)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = {
                                        showConfirmationDialog = false
                                        finish()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = ButtonDefaults.outlinedButtonBorder(true).copy(width = 1.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                                ) {
                                    Text("NOT YET / SKIP", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Register Number", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun saveAttendanceRecordAndFinish() {
        if (targetPeriodId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                val calendar = java.util.Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayStr = sdf.format(calendar.time)
                
                val existing = repository.getAttendanceRecordForPeriodAndDate(todayStr, targetPeriodId)
                if (existing == null) {
                    repository.saveAttendanceRecord(
                        AttendanceRecordEntity(
                            date = todayStr,
                            periodId = targetPeriodId,
                            status = "MARKED",
                            markedAt = System.currentTimeMillis()
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        } else {
            Toast.makeText(this, "Attendance marked manually", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onBackPressed() {
        // Intercept back key to show the attendance verification dialog
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Mark Attendance")
                .setMessage("Mark $targetSubjectName as attended in your local history logs?")
                .setPositiveButton("Yes") { _, _ -> saveAttendanceRecordAndFinish() }
                .setNegativeButton("No") { _, _ -> finish() }
                .setNeutralButton("Cancel", null)
                .show()
        }
    }

    private fun getAppDayOfWeek(calendar: java.util.Calendar): Int {
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

    private fun parseTimeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewComponent(
    url: String,
    onProgressChange: (Float) -> Unit,
    registerNumber: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onProgressChange(0.1f)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onProgressChange(1f)
                        
                        // Inject autofill JavaScript if a register number is saved
                        if (registerNumber.isNotEmpty()) {
                            val js = """
                                (function() {
                                    var inputs = document.getElementsByTagName('input');
                                    for (var i = 0; i < inputs.length; i++) {
                                        var input = inputs[i];
                                        if (input.type === 'text' || input.type === 'number') {
                                            var placeholder = (input.placeholder || '').toLowerCase();
                                            var name = (input.name || '').toLowerCase();
                                            var id = (input.id || '').toLowerCase();
                                            
                                            // Autofill if matches registration/student ID naming conventions
                                            if (placeholder.indexOf('reg') !== -1 || 
                                                placeholder.indexOf('roll') !== -1 || 
                                                placeholder.indexOf('id') !== -1 || 
                                                name.indexOf('reg') !== -1 ||
                                                id.indexOf('reg') !== -1 ||
                                                inputs.length === 1 ||
                                                i === 0) {
                                                
                                                input.value = '$registerNumber';
                                                input.dispatchEvent(new Event('input', { bubbles: true }));
                                                input.dispatchEvent(new Event('change', { bubbles: true }));
                                            }
                                        }
                                    }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChange(newProgress / 100f)
                    }
                }
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        modifier = modifier
    )
}
