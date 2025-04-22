package ar.edu.um.programacion2.ai_assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.edu.um.programacion2.ai_assistant.ui.theme.AI_AssistantTheme
import ar.edu.um.programacion2.ai_assistant.core.navigation.NavigationWrapper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AI_AssistantTheme {
                NavigationWrapper()
            }
        }
    }
}