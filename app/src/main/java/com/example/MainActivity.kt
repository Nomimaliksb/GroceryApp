package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.GroceryAppContent
import com.example.ui.GroceryViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GroceryViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context?) {
        if (newBase != null) {
            val sharedPrefs = newBase.getSharedPreferences("grocery_prefs", android.content.Context.MODE_PRIVATE)
            val savedLang = sharedPrefs.getString("selected_lang", "en") ?: "en"
            val locale = java.util.Locale(savedLang)
            java.util.Locale.setDefault(locale)
            
            val config = newBase.resources.configuration
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            val context = newBase.createConfigurationContext(config)
            super.attachBaseContext(context)
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply persistent language configuration
        val sharedPrefs = getSharedPreferences("grocery_prefs", android.content.Context.MODE_PRIVATE)
        val savedLang = sharedPrefs.getString("selected_lang", "en") ?: "en"
        val locale = java.util.Locale(savedLang)
        java.util.Locale.setDefault(locale)
        
        val resources = resources
        val configuration = resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)

        enableEdgeToEdge()
        setContent {
            // Support user-driven toggle, defaulting to system settings
            var isDarkTheme by remember { mutableStateOf(false) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    floatingActionButton = {
                        // Highly accessible floating toggle button on the top-right / corner
                        FloatingActionButton(
                            onClick = { isDarkTheme = !isDarkTheme },
                            modifier = Modifier
                                .padding(16.dp)
                                .border(3.dp, MaterialTheme.colorScheme.primary, FloatingActionButtonDefaults.shape)
                                .testTag("theme_toggle_button"),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = if (isDarkTheme) stringResource(R.string.theme_light) else stringResource(R.string.theme_dark),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    },
                    floatingActionButtonPosition = FabPosition.End
                ) { innerPadding ->
                    GroceryAppContent(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}
