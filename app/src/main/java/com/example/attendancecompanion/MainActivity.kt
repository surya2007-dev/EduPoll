package com.example.attendancecompanion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.attendancecompanion.data.DataRepository
import com.example.attendancecompanion.theme.AttendanceCompanionTheme
import com.example.attendancecompanion.ui.attendance.AttendanceWebViewActivity
import com.example.attendancecompanion.ui.attendance.QRScannerActivity
import com.example.attendancecompanion.notification.AlarmScheduler
import com.example.attendancecompanion.notification.DailyDigestWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var repository: DataRepository

    // Activity launcher for QR Scanner
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val url = result.data?.getStringExtra("SCANNED_URL")
            if (!url.isNullOrEmpty()) {
                launchWebView(url)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as AttendanceApplication).repository

        // Schedule daily digest and alarms on launch
        AlarmScheduler.scheduleAlarms(this)
        DailyDigestWorker.scheduleDailyDigest(this)

        // Handle auto-open intent from pre-attendance notifications
        val autoOpen = intent.getBooleanExtra("AUTO_OPEN_ATTENDANCE", false)
        val periodId = intent.getLongExtra("PERIOD_ID", -1L)
        if (autoOpen) {
            CoroutineScope(Dispatchers.IO).launch {
                val student = repository.getStudentDirect()
                val masterLink = student?.masterLink ?: ""
                if (masterLink.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        launchWebView(masterLink, periodId)
                    }
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            AttendanceCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        repository = repository,
                        onOpenWebView = { url -> launchWebView(url) },
                        onOpenQRScanner = { launchQRScanner() }
                    )
                }
            }
        }
    }

    private fun launchQRScanner() {
        qrScannerLauncher.launch(Intent(this, QRScannerActivity::class.java))
    }

    private fun launchWebView(url: String, periodId: Long = -1) {
        val intent = Intent(this, AttendanceWebViewActivity::class.java).apply {
            putExtra("LOAD_URL", url)
            putExtra("PERIOD_ID", periodId)
        }
        startActivity(intent)
    }
}
