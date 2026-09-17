package com.fatih.futuresbot

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fatih.futuresbot.presentation.navigation.MainShell
import com.fatih.futuresbot.presentation.onboarding.ApiKeyScreen
import com.fatih.futuresbot.presentation.theme.FuturesBotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as FuturesBotApp).container

        setContent {
            FuturesBotTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val hasKeys by container.credentialStore.hasCredentials.collectAsStateWithLifecycle()
                    var demoWithoutKeys by rememberSaveable { mutableStateOf(false) }

                    if (hasKeys || demoWithoutKeys) {
                        MainShell(container)
                    } else {
                        ApiKeyScreen(container, onSkip = { demoWithoutKeys = true })
                    }
                }
            }
        }
    }
}
