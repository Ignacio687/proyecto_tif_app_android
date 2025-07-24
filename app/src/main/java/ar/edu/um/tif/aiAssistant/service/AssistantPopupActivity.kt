package ar.edu.um.tif.aiAssistant.service

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ar.edu.um.tif.aiAssistant.MainActivity
import ar.edu.um.tif.aiAssistant.core.auth.AuthManager
import ar.edu.um.tif.aiAssistant.ui.theme.AI_AssistantTheme
import com.justai.aimybox.Aimybox
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// Authentication state enum moved outside the Composable
enum class AuthState {
    Checking,
    Authenticated,
    NotAuthenticated,
    Error
}

@AndroidEntryPoint
class AssistantPopupActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AssistantPopupActivity"
    }

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var aimybox: Aimybox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Popup activity created")

        // Use modern approach for showing over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            AI_AssistantTheme {
                AssistantPopupScreen(
                    authManager = authManager,
                    aimybox = aimybox,
                    onDismiss = {
                        // Restart wake word service when dismissing
                        restartWakeWordService()
                        finish()
                    },
                    onOpenFullApp = { openFullApp() },
                    onOpenLogin = { openLoginFlow() }
                )
            }
        }
    }

    private fun openFullApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_assistant", true)
        }
        startActivity(intent)
        finish()
    }

    private fun openLoginFlow() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Don't set navigate_to_assistant, let it go through normal auth flow
        }
        startActivity(intent)
        finish()
    }

    private fun restartWakeWordService() {
        Log.d(TAG, "Restarting wake word service from popup")

        // Notify the background service that popup is being dismissed
        val serviceIntent = Intent(this, KaldiWakeWordService::class.java).apply {
            action = "POPUP_DISMISSED"
        }
        startService(serviceIntent)
    }
}

@Composable
fun AssistantPopupScreen(
    authManager: AuthManager,
    aimybox: Aimybox,
    onDismiss: () -> Unit,
    onOpenFullApp: () -> Unit,
    onOpenLogin: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var authState by remember { mutableStateOf(AuthState.Checking) }
    var isListening by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var assistantResponse by remember { mutableStateOf("") }
    var userHasInteracted by remember { mutableStateOf(false) }
    var autoDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Function to start/restart the 15-second auto-dismiss timer
    fun startAutoDismissTimer() {
        autoDismissJob?.cancel() // Cancel any existing timer
        autoDismissJob = coroutineScope.launch {
            delay(15000) // 15 seconds
            if (!userHasInteracted) {
                Log.d("AssistantPopup", "Auto-dismissing popup after 15 seconds of no interaction")
                onDismiss()
            }
        }
    }

    // Function to cancel auto-dismiss when user interacts - popup stays open indefinitely
    fun cancelAutoDismissTimer() {
        autoDismissJob?.cancel()
        userHasInteracted = true
        Log.d("AssistantPopup", "User interaction detected - popup will stay open indefinitely")
    }

    // Check authentication status when popup opens
    LaunchedEffect(Unit) {
        try {
            val isAuthenticated = authManager.verifyAuthentication()
            authState = if (isAuthenticated) {
                AuthState.Authenticated
            } else {
                AuthState.NotAuthenticated
            }
        } catch (e: Exception) {
            Log.e("AssistantPopup", "Error checking auth status", e)
            authState = AuthState.Error
        }
    }

    // Start auto-dismiss timer when authenticated and ready
    LaunchedEffect(authState) {
        if (authState == AuthState.Authenticated && !userHasInteracted) {
            startAutoDismissTimer()
        }
    }

    // Start listening immediately if authenticated
    LaunchedEffect(authState) {
        if (authState == AuthState.Authenticated && !isListening) {
            try {
                // Check for microphone permission before starting recognition
                if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

                    isListening = true
                    Log.d("AssistantPopup", "Starting voice recognition")
                    @Suppress("MissingPermission") // Permission already checked above
                    aimybox.startRecognition()
                } else {
                    Log.w("AssistantPopup", "No microphone permission available")
                    authState = AuthState.Error
                }
            } catch (e: Exception) {
                Log.e("AssistantPopup", "Error starting recognition", e)
                isListening = false
            }
        }
    }

    // Monitor user interactions - cancel auto-dismiss when user speaks
    LaunchedEffect(recognizedText) {
        if (recognizedText.isNotEmpty()) {
            cancelAutoDismissTimer()
        }
    }

    // Monitor assistant responses - cancel auto-dismiss when assistant responds
    LaunchedEffect(assistantResponse) {
        if (assistantResponse.isNotEmpty()) {
            cancelAutoDismissTimer()
        }
    }

    // Monitor listening state - detect when user actually starts speaking
    LaunchedEffect(isListening) {
        if (isListening && authState == AuthState.Authenticated) {
            // We'll wait for actual speech recognition or response to cancel timer
        }
    }

    // Auto-dismiss after timeout if not authenticated (unchanged)
    LaunchedEffect(authState) {
        if (authState == AuthState.NotAuthenticated || authState == AuthState.Error) {
            kotlinx.coroutines.delay(8000) // 8 seconds to read error message
            onDismiss()
        }
    }

    // Clean up timer when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            autoDismissJob?.cancel()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Assistant",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "Close"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content based on authentication state
                    when (authState) {
                        AuthState.Checking -> {
                            // Checking authentication
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Verificando sesión...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        AuthState.Authenticated -> {
                            // User authenticated - show voice interface
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Microphone icon
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_btn_speak_now),
                                    contentDescription = "Listening",
                                    modifier = Modifier.size(64.dp),
                                    tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Status text
                                Text(
                                    text = if (isListening) "Escuchando..." else "Listo para escuchar",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Recognized text
                                if (recognizedText.isNotEmpty()) {
                                    Text(
                                        text = "Dijiste: \"$recognizedText\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Assistant response
                                if (assistantResponse.isNotEmpty()) {
                                    Text(
                                        text = assistantResponse,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // Helper text
                                Text(
                                    text = "Di algo o toca fuera para cerrar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        AuthState.NotAuthenticated -> {
                            // User not logged in - show error
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                                    contentDescription = "Login required",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Sesión requerida",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Necesitas iniciar sesión para usar el asistente",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = onOpenLogin,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Iniciar Sesión")
                                }
                            }
                        }

                        AuthState.Error -> {
                            // Authentication error - show error
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                                    contentDescription = "Error",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Error de conexión",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "No se pudo verificar la sesión. Verifica tu conexión a internet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = onOpenFullApp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Abrir App Completa")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
