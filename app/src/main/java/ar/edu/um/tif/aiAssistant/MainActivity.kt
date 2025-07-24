package ar.edu.um.tif.aiAssistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.edu.um.tif.aiAssistant.ui.theme.AI_AssistantTheme
import ar.edu.um.tif.aiAssistant.core.navigation.NavigationWrapper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if we need to navigate directly to assistant
        val navigateToAssistant = intent.getBooleanExtra("navigate_to_assistant", false)

        setContent {
            AI_AssistantTheme {
                NavigationWrapper(navigateToAssistant = navigateToAssistant)
            }
        }
    }
}