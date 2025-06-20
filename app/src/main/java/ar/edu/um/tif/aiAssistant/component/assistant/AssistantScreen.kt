package ar.edu.um.tif.aiAssistant.component.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import ar.edu.um.tif.aiAssistant.R
import ar.edu.um.tif.aiAssistant.core.AimyboxApplication
import ar.edu.um.tif.aiAssistant.ui.theme.AI_AssistantTheme
import com.justai.aimybox.components.widget.Button as AimyboxButton
import kotlinx.coroutines.launch

// UI model for AimyBox widgets
sealed interface AssistantUiWidget
data class AssistantUiResponse(val text: String) : AssistantUiWidget
data class AssistantUiRequest(val text: String) : AssistantUiWidget
data class AssistantUiButtons(val buttons: List<AssistantUiButton>) : AssistantUiWidget
data class AssistantUiRecognition(val text: String) : AssistantUiWidget
data class AssistantUiImage(val url: String) : AssistantUiWidget

data class AssistantUiButton(val text: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = hiltViewModel(),
    navigateToLogin: () -> Unit,
    navigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Microphone permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    // Flag to track if rationale should be shown or if settings need to be opened
    var showRationaleOrOpenSettings by remember { mutableStateOf(true) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            // Check if we should show rationale next time or direct to settings
            showRationaleOrOpenSettings = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.RECORD_AUDIO) != false
        }
    }

    // Initialize AimyBox when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            val aimyboxApp = context.applicationContext as? AimyboxApplication
            aimyboxApp?.aimybox?.let { aimybox ->
                viewModel.initializeAimybox(aimybox)
            }
        }
    }

    // Request permission on initial composition if not already granted
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            // Check rationale status *before* the first launch as well
            showRationaleOrOpenSettings = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.RECORD_AUDIO) != false
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Handle authentication errors
    LaunchedEffect(uiState.authError) {
        if (uiState.authError) {
            navigateToLogin()
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                scrollState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    // Voice Assistant Components - Only initialized when permission is granted
    val aimyboxWidgets = if (hasPermission) {
        viewModel.widgets?.observeAsState(emptyList())?.value ?: emptyList()
    } else {
        emptyList()
    }

    val aimyboxState = if (hasPermission) {
        viewModel.aimyboxState?.observeAsState()?.value
    } else {
        null
    }

    val isListening = aimyboxState != null && aimyboxState.toString().contains("LISTENING")

    // Convert AimyBox widgets to UI widgets
    val uiWidgets = remember(aimyboxWidgets) {
        aimyboxWidgets.mapNotNull {
            when (it::class.simpleName) {
                "ResponseWidget" -> {
                    val text = it.javaClass.getMethod("getText").invoke(it) as String
                    // Add the response to chat messages directly
                    viewModel.addVoiceResponseMessage(text)
                    null // Don't create a widget
                }
                "RequestWidget" -> {
                    val text = it.javaClass.getMethod("getText").invoke(it) as String
                    // Add the user request to chat messages directly
                    viewModel.addVoiceRequestMessage(text)
                    null // Don't create a widget
                }
                "ButtonsWidget" -> {
                    val buttons = it.javaClass.getMethod("getButtons").invoke(it) as List<*>
                    AssistantUiButtons(
                        buttons.map { btn ->
                            val text = btn?.javaClass?.getMethod("getText")?.invoke(btn) as String
                            AssistantUiButton(text) {
                                viewModel.onButtonClick(btn as AimyboxButton)
                            }
                        }
                    )
                }
                "ImageWidget" -> AssistantUiImage(
                    it.javaClass.getMethod("getUrl").invoke(it) as String
                )
                "RecognitionWidget" -> {
                    // The recognition widget shows what the user is saying while speaking
                    // We don't need to add this to chat history
                    AssistantUiRecognition(
                        it.javaClass.getMethod("getText").invoke(it) as String
                    )
                }
                else -> null
            }
        }
    }

    Scaffold(
        topBar = { AssistantTopBar(navigateBack = navigateBack) },
        bottomBar = {
            if (hasPermission) {
                AssistantInputBar(
                    isListening = isListening,
                    onMicClick = { viewModel.onAssistantButtonClick() },
                    onSendMessage = viewModel::sendMessage,
                    isLoading = uiState.isLoading
                )
            }
        }
    ) { paddingValues ->
        if (hasPermission) {
            AssistantContent(
                uiState = uiState,
                uiWidgets = uiWidgets,
                viewModel = viewModel,
                scrollState = scrollState,
                paddingValues = paddingValues
            )
        } else {
            PermissionRequestContent(
                showRationaleOrOpenSettings = showRationaleOrOpenSettings,
                permissionLauncher = permissionLauncher
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantTopBar(navigateBack: () -> Unit) {
    TopAppBar(
        title = { Text("Assistant") },
        navigationIcon = {
            IconButton(onClick = navigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    )
}

@Composable
private fun AssistantContent(
    uiState: AssistantUiState,
    uiWidgets: List<AssistantUiWidget>,
    viewModel: AssistantViewModel,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Messages List
        MessageList(
            messages = uiState.messages,
            uiWidgets = uiWidgets,
            isLoading = uiState.isLoading,
            errorMessage = uiState.errorMessage,
            scrollState = scrollState
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    uiWidgets: List<AssistantUiWidget>,
    isLoading: Boolean,
    errorMessage: String?,
    scrollState: androidx.compose.foundation.lazy.LazyListState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            state = scrollState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp) // Extra padding for FAB
        ) {
            // Add debug logging to check message ordering
            messages.forEachIndexed { index, message ->
                android.util.Log.d("MessageList", "Message $index: isFromUser=${message.isFromUser}, content=${message.content}, timestamp=${message.timestamp}")
            }

            // Chat messages - Display in the same order as they come from the server
            // Server sends index 0 = most recent, so we'll display them in that order
            items(messages) { message ->
                ChatMessageItem(message = message)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // AimyBox Widgets (Voice Assistant)
            if (uiWidgets.isNotEmpty()) {
                items(uiWidgets) { widget ->
                    when (widget) {
                        is AssistantUiResponse -> AssistantResponseWidget(widget)
                        is AssistantUiRequest -> AssistantRequestWidget(widget)
                        is AssistantUiButtons -> AssistantButtonsWidget(widget)
                        is AssistantUiImage -> AssistantImageWidget(widget)
                        is AssistantUiRecognition -> AssistantRecognitionWidget(widget)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (isLoading) {
                item {
                    LoadingIndicator()
                }
            }

            // Error Message
            if (errorMessage != null) {
                item {
                    ErrorMessage(errorMessage)
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AI",
                color = MaterialTheme.colorScheme.onPrimary
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
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun ErrorMessage(errorMessage: String) {
    Text(
        text = errorMessage,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Composable
private fun AssistantInputBar(
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
            .padding(16.dp),
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
                placeholder = { Text("Type a message...") },
                singleLine = false,
                maxLines = 4,
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
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
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
                modifier = Modifier.size(56.dp)
            ) {
                if (isListening) {
                    Icon(
                        painter = painterResource(id = R.drawable.assistant_mic_off_icon_24),
                        contentDescription = "Stop",
                        tint = Color.White
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.assistant_mic_icon_24),
                        contentDescription = "Start",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestContent(
    showRationaleOrOpenSettings: Boolean,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Determine the correct text and button based on rationale/permanent denial
        val (explanationText, buttonText, buttonAction) = if (showRationaleOrOpenSettings) {
            // Need to request again or show rationale
            Triple(
                "Microphone permission is required to use the voice assistant.",
                "Grant Permission",
                { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        } else {
            // Permanently denied, direct to settings
            Triple(
                "Microphone permission has been permanently denied. " +
                        "Please enable it in app settings to use the voice assistant.",
                "Open Settings",
                {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", context.packageName, null)
                    intent.data = uri
                    context.startActivity(intent)
                }
            )
        }

        Text(
            text = explanationText,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = buttonAction) {
            Text(buttonText)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The voice assistant cannot function without this permission.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

// AimyBox Widget Composables
@Composable
private fun AssistantResponseWidget(widget: AssistantUiResponse) {
    Text(
        text = widget.text,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = MaterialTheme.typography.bodyLarge.fontSize.times(1.1f)
        ),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AssistantRequestWidget(widget: AssistantUiRequest) {
    Text(
        text = widget.text,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AssistantButtonsWidget(widget: AssistantUiButtons) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        widget.buttons.forEach { button ->
            Button(
                onClick = button.onClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(button.text)
            }
        }
    }
}

@Composable
private fun AssistantImageWidget(widget: AssistantUiImage) {
    // Replace with image loader when you provide images
    Text(
        text = "Image: ${widget.url}",
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AssistantRecognitionWidget(widget: AssistantUiRecognition) {
    Text(
        text = widget.text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val bubbleColor = if (message.isFromUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    val textColor = if (message.isFromUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    val alignment = if (message.isFromUser) Arrangement.End else Arrangement.Start
    val textAlignment = if (message.isFromUser) Alignment.End else Alignment.Start

    // Format timestamp
    val formattedTime = remember(message.timestamp) {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
    ) {
        // Show timestamp only for user messages
        if (message.isFromUser) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = alignment
        ) {
            if (!message.isFromUser) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AI",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AssistantScreenPreview() {
    AI_AssistantTheme {
        AssistantScreen(
            navigateToLogin = {},
            navigateBack = {}
        )
    }
}
