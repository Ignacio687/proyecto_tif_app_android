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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import ar.edu.um.tif.aiAssistant.MainActivity
import ar.edu.um.tif.aiAssistant.R
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
    onDismiss: () -> Unit,
    onOpenFullApp: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: AssistantPopupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var authState by remember { mutableStateOf(AuthState.Checking) }
    var autoDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()

    // Voice Assistant Components - Only initialized when permission is granted
    val hasRecordAudioPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val aimyboxWidgets = if (hasRecordAudioPermission) {
        viewModel.widgets.observeAsState(emptyList()).value
    } else {
        emptyList()
    }

    val aimyboxState = if (hasRecordAudioPermission) {
        viewModel.aimyboxState.observeAsState().value
    } else {
        null
    }

    val isListening = aimyboxState != null && aimyboxState.toString().contains("LISTENING")

    // Convert AimyBox widgets to UI messages (similar to AssistantScreen)
    LaunchedEffect(aimyboxWidgets) {
        aimyboxWidgets.forEach { widget ->
            when (widget::class.simpleName) {
                "ResponseWidget" -> {
                    val text = widget.javaClass.getMethod("getText").invoke(widget) as String
                    // Only add the response to chat if it should not be filtered
                    if (!viewModel.shouldFilterResponse(text)) {
                        viewModel.addVoiceResponseMessage(text)
                    }
                }
                "RequestWidget" -> {
                    val text = widget.javaClass.getMethod("getText").invoke(widget) as String
                    // Add the user request to chat messages directly
                    viewModel.addVoiceRequestMessage(text)
                }
                // Add other widget types as needed
            }
        }
    }

    // Function to start/restart the 15-second auto-dismiss timer
    fun startAutoDismissTimer() {
        autoDismissJob?.cancel()
        autoDismissJob = coroutineScope.launch {
            delay(15000)
            if (!uiState.userHasInteracted) {
                Log.d("AssistantPopup", "Auto-dismissing popup after 15 seconds of no interaction")
                onDismiss()
            }
        }
    }

    // Check authentication status when popup opens
    LaunchedEffect(Unit) {
        try {
            val isAuthenticated = viewModel.verifyAuthentication()
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
        if (authState == AuthState.Authenticated && !uiState.userHasInteracted) {
            startAutoDismissTimer()
        }
    }

    // Auto-dismiss after timeout if not authenticated
    LaunchedEffect(authState) {
        if (authState == AuthState.NotAuthenticated || authState == AuthState.Error) {
            delay(8000) // 8 seconds to read error message
            onDismiss()
        }
    }

    // Handle authentication errors from ViewModel
    LaunchedEffect(uiState.authError) {
        if (uiState.authError) {
            authState = AuthState.Error
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            scrollState.animateScrollToItem(uiState.messages.size - 1)
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
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(bottom = 80.dp), // Add bottom padding to prevent cutoff
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.35f)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                when (authState) {
                    AuthState.Checking -> {
                        CheckingAuthContent()
                    }
                    AuthState.Authenticated -> {
                        AuthenticatedPopupContent(
                            messages = uiState.messages,
                            isListening = isListening,
                            isLoading = uiState.isLoading,
                            scrollState = scrollState,
                            onSendMessage = viewModel::sendMessage,
                            onMicClick = {
                                if (hasRecordAudioPermission) {
                                    viewModel.onMicButtonClick()
                                } else {
                                    Log.w("AssistantPopup", "No microphone permission for voice recognition")
                                }
                            },
                            onDismiss = onDismiss
                        )
                    }
                    AuthState.NotAuthenticated -> {
                        NotAuthenticatedContent(onOpenLogin = onOpenLogin)
                    }
                    AuthState.Error -> {
                        ErrorContent(onOpenFullApp = onOpenFullApp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckingAuthContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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

@Composable
private fun AuthenticatedPopupContent(
    messages: List<PopupChatMessage>,
    isListening: Boolean,
    isLoading: Boolean,
    scrollState: LazyListState,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        PopupHeader(onDismiss = onDismiss)

        // Messages List (compact version)
        PopupMessageList(
            messages = messages,
            isLoading = isLoading,
            scrollState = scrollState,
            modifier = Modifier.weight(1f)
        )

        // Input Bar (same style as AssistantScreen)
        PopupInputBar(
            isListening = isListening,
            onMicClick = onMicClick,
            onSendMessage = onSendMessage,
            isLoading = isLoading
        )
    }
}

@Composable
private fun PopupHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
}

@Composable
private fun PopupMessageList(
    messages: List<PopupChatMessage>,
    isLoading: Boolean,
    scrollState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        state = scrollState,
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        // Chat messages
        items(messages) { message ->
            PopupChatMessageItem(message = message)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Loading indicator
        if (isLoading) {
            item {
                PopupLoadingIndicator()
            }
        }
    }
}

@Composable
private fun PopupChatMessageItem(message: PopupChatMessage) {
    val bubbleColor = if (message.isFromUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    val textColor = if (message.isFromUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    val alignment = if (message.isFromUser) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment
    ) {
        if (!message.isFromUser) {
            Box(
                modifier = Modifier
                    .size(32.dp) // Slightly smaller for popup
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 240.dp) // Slightly smaller for popup
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PopupLoadingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AI",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun PopupInputBar(
    isListening: Boolean,
    onMicClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    isLoading: Boolean
) {
    val focusManager = LocalFocusManager.current
    var userInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp), // Slightly smaller padding for popup
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe aquí...") },
                singleLine = true, // Single line for popup to save space
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (userInput.isNotBlank()) {
                            onSendMessage(userInput)
                            userInput = ""
                            focusManager.clearFocus()
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (userInput.isNotBlank()) {
                        onSendMessage(userInput)
                        userInput = ""
                        focusManager.clearFocus()
                    }
                },
                enabled = !isLoading && userInput.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (!isLoading && userInput.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = onMicClick,
                containerColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp) // Slightly smaller for popup
            ) {
                if (isListening) {
                    Icon(
                        painter = painterResource(id = R.drawable.assistant_mic_icon_24),
                        contentDescription = "Detener",
                        tint = Color.White
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.assistant_mic_off_icon_24),
                        contentDescription = "Iniciar",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun NotAuthenticatedContent(onOpenLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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

@Composable
private fun ErrorContent(onOpenFullApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
