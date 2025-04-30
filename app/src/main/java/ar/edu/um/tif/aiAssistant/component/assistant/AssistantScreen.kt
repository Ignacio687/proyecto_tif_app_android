package ar.edu.um.tif.aiAssistant.component.assistant

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel


@Composable
fun AssistantScreen(navigateToLogin: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AssistantViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AssistantViewModel(context.applicationContext as Application) as T
            }
        }
    )

    val isListening = viewModel.isListening
    val errorMessage = viewModel.errorMessage

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF222222)),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isListening) "Listening..." else "Press to start listening",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(
                    onClick = {
                        if (isListening) {
                            viewModel.stopService(context)
                        } else {
                            viewModel.startService(context)
                        }
                    }
                ) {
                    Text(if (!isListening) "Start Listening" else "Stop Listening")
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}