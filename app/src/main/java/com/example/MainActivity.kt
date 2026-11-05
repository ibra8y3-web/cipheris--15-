package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.screens.CipherisAppContent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ChatViewModelFactory

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.screens.MasterPinLockScreen

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
            application as CipherisApplication,
            (application as CipherisApplication).repository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Smart Protection: Enable screenshot blocking ONLY on real physical devices.
        // This allows the AI Studio Preview (Emulator) to work while ensuring
        // maximum security once the app is installed on a real phone.
        if (!isEmulator()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        handleIntent(intent)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val whiteToneLevel by viewModel.whiteToneLevel.collectAsState()
            MyApplicationTheme(darkTheme = isDarkTheme, whiteToneLevel = whiteToneLevel) {
                val isLocked by viewModel.isLocked.collectAsState()
                
                if (isLocked) {
                    MasterPinLockScreen(
                        onCorrectPin = { viewModel.unlock(viewModel.masterPin ?: "") },
                        onPanicPin = { viewModel.handlePanic() },
                        correctPin = viewModel.masterPin,
                        panicPin = viewModel.panicPin
                    )
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        // Root app shell
                        CipherisAppContent(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatic Link and Node ID detection from clipboard upon resuming/opening app
        viewModel.checkClipboardForLinks(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uriStr = intent?.dataString ?: intent?.data?.toString()
        if (!uriStr.isNullOrBlank()) {
            val parsed = com.example.util.DeepLinkHelper.parse(uriStr)
            if (parsed != null) {
                viewModel.handleDeepLink(uriStr)
            } else {
                viewModel.handleDeepLink(uriStr)
            }
        }
    }

    /**
     * Detects if the app is running on an emulator.
     */
    private fun isEmulator(): Boolean {
        val model = android.os.Build.MODEL
        val product = android.os.Build.PRODUCT
        val hardware = android.os.Build.HARDWARE
        val fingerPrint = android.os.Build.FINGERPRINT
        
        return fingerPrint.startsWith("generic")
                || fingerPrint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || product.contains("sdk_google")
                || product.contains("google_sdk")
                || product.contains("sdk")
                || product.contains("sdk_x86")
                || product.contains("vbox86p")
                || product.contains("emulator")
                || product.contains("simulator")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
    }
}
