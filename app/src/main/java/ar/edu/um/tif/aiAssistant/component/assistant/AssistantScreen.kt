package ar.edu.um.tif.aiAssistant.component.assistant

import android.Manifest
import android.app.Activity
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
import androidx.compose.material3.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ar.edu.um.tif.aiAssistant.core.voice.AimyboxApplication
import com.justai.aimybox.components.widget.Button
import ar.edu.um.tif.aiAssistant.R.drawable
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch

// --- UI Model ---
sealed interface AssistantUiWidget
data class AssistantUiResponse(val text: String) : AssistantUiWidget
data class AssistantUiRequest(val text: String) : AssistantUiWidget
data class AssistantUiButtons(val buttons: List<AssistantUiButton>) : AssistantUiWidget
data class AssistantUiRecognition(val text: String) : AssistantUiWidget
data class AssistantUiImage(val url: String) : AssistantUiWidget

data class AssistantUiButton(val text: String, val onClick: () -> Unit)

// --- Main Composable ---
@Composable
fun AssistantScreen(navigateToLogin: () -> Unit) {
    val context = LocalContext.current
    // Find the activity to check rationale. Fallback might be needed if not directly available.
    val activity = LocalActivity.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    // Flag to track if rationale should be shown or if settings need to be opened
    var showRationaleOrOpenSettings by remember { mutableStateOf(true) }

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

    // Request permission on initial composition if not already granted
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            // Check rationale status *before* the first launch as well
            showRationaleOrOpenSettings = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.RECORD_AUDIO) != false
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
    ) {
        if (hasPermission) {
            // --- Main Content: Only shown when permission is granted ---
            val aimybox = (context.applicationContext as AimyboxApplication).aimybox
            val viewModel: AssistantViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
                            @Suppress("UNCHECKED_CAST")
                            return AssistantViewModel(aimybox) as T
                        }
                        throw IllegalArgumentException("Unknown ViewModel class")
                    }
                }
            )

            val aimyboxWidgets by viewModel.widgets.observeAsState(emptyList())
            val aimyboxState by viewModel.aimyboxState.observeAsState()
            val isListening = aimyboxState?.name == "LISTENING"

            val uiWidgets = remember(aimyboxWidgets) {
                aimyboxWidgets.mapNotNull {
                    when (it::class.simpleName) {
                        "ResponseWidget" -> AssistantUiResponse(
                            it.javaClass.getMethod("getText").invoke(it) as String
                        )
                        "RequestWidget" -> AssistantUiRequest(
                            it.javaClass.getMethod("getText").invoke(it) as String
                        )
                        "ButtonsWidget" -> {
                            val buttons = it.javaClass.getMethod("getButtons").invoke(it) as List<*>
                            AssistantUiButtons(
                                buttons.map { btn ->
                                    val text = btn?.javaClass?.getMethod("getText")?.invoke(btn) as String
                                    AssistantUiButton(text) {
                                        viewModel.onButtonClick(btn as Button)
                                    }
                                }
                            )
                        }
                        "ImageWidget" -> AssistantUiImage(
                            it.javaClass.getMethod("getUrl").invoke(it) as String
                        )
                        "RecognitionWidget" -> AssistantUiRecognition(
                            it.javaClass.getMethod("getText").invoke(it) as String
                        )
                        else -> null
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp) // leave space for FAB
            ) {
                AssistantWidgetList(
                    widgets = uiWidgets
                )
            }
            AssistantMicToggleButton(
                isListening = isListening,
                onClick = {
                    // Permission is already granted here
                    viewModel.onAssistantButtonClick()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )

        } else {
            // --- Permission Required State ---
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
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = buttonAction) {
                    Text(buttonText)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The app cannot function without this permission.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// --- Widget List ---
@Composable
private fun AssistantWidgetList(widgets: List<AssistantUiWidget>) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll to the last item when widgets change
    LaunchedEffect(widgets.size) {
        if (widgets.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(widgets.lastIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        items(widgets) { widget ->
            when (widget) {
                is AssistantUiResponse -> AssistantResponseWidget(widget)
                is AssistantUiRequest -> AssistantRequestWidget(widget)
                is AssistantUiButtons -> AssistantButtonsWidget(widget)
                is AssistantUiImage -> AssistantImageWidget(widget)
                is AssistantUiRecognition -> AssistantRecognitionWidget(widget)
            }
        }
    }
}

// --- Individual Widget Composables ---
@Composable
private fun AssistantResponseWidget(widget: AssistantUiResponse) {
    Text(
        text = widget.text,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AssistantRequestWidget(widget: AssistantUiRequest) {
    Text(
        text = widget.text,
        color = Color(0xFFB0BEC5),
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AssistantButtonsWidget(widget: AssistantUiButtons) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        widget.buttons.forEach { button ->
            Button(
                onClick = button.onClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(button.text, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun AssistantImageWidget(widget: AssistantUiImage) {
    // Replace with image loader when you provide images
    Text(
        text = "Image: ${widget.url}",
        color = Color.LightGray,
        fontSize = 18.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun AssistantRecognitionWidget(widget: AssistantUiRecognition) {
    Text(
        text = widget.text,
        color = Color.Cyan,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

// --- Toggle Button (FAB style) ---
@Composable
private fun AssistantMicToggleButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary,
        modifier = modifier.size(72.dp)
    ) {
        if (isListening) {
            Icon(painterResource(id = drawable.assistant_mic_off_icon_24),
                contentDescription = "Stop", tint = Color.White)
        } else {
            Icon(painterResource(id = drawable.assistant_mic_icon_24),
                contentDescription = "Start", tint = Color.White)
        }
    }
}