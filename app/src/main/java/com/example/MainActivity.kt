package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ui.ChatXAppRoot
import com.example.ui.theme.ChatXTheme
import com.example.viewmodel.WhatsAppAndSettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ChatXApplication

        setContent {
            val settingsVm: WhatsAppAndSettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        WhatsAppAndSettingsViewModel(
                            prefs = app.preferencesManager,
                            waNotificationDao = app.localDb.whatsAppNotificationDao(),
                            waBusinessRepo = app.whatsAppBusinessRepository,
                            authRepo = app.authAndAdminRepository,
                            cryptoManager = app.cryptoManager
                        )
                    }
                }
            )
            val darkTheme by settingsVm.darkThemeEnabled.collectAsStateWithLifecycle()

            ChatXTheme(darkTheme = darkTheme) {
                ChatXAppRoot(app = app, settingsVm = settingsVm)
            }
        }
    }
}
