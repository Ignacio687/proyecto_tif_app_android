package ar.edu.um.tif.aiAssistant.component.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ar.edu.um.tif.aiAssistant.ui.theme.AI_AssistantTheme

@Composable
fun EmailVerificationScreen(
    viewModel: EmailVerificationViewModel = hiltViewModel(),
    onVerificationSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onResendCode: () -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    var verificationCode by rememberSaveable { mutableStateOf("") }
    var emailInput by rememberSaveable { mutableStateOf(uiState.email ?: "") }

    // Changed to display a success message and navigate to login instead of directly continuing
    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) {
            // Wait briefly to show the success message before navigating
            kotlinx.coroutines.delay(1500)
            onNavigateToLogin() // Navigate to login screen instead of success
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Verify Your Email",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 32.dp)
            )

            // If we have the email, display it
            if (!uiState.needsEmailInput && !uiState.email.isNullOrBlank()) {
                Text(
                    text = "Email: ${uiState.email}",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // If we need the email, show input field
            if (uiState.needsEmailInput) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Please enter your email address:",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (emailInput.contains("@")) {
                                        viewModel.updateEmail(emailInput)
                                    }
                                }
                            )
                        )

                        Button(
                            onClick = {
                                if (emailInput.contains("@")) {
                                    viewModel.updateEmail(emailInput)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(top = 8.dp),
                            enabled = emailInput.contains("@")
                        ) {
                            Text("Confirm Email")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Only show verification instructions and form if we don't need email input
            if (!uiState.needsEmailInput) {
                // Instructions
                Text(
                    text = "We've sent a verification code to your email. Please enter the code below to verify your account.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Verification Form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Code Field
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { verificationCode = it },
                            label = { Text("Verification Code") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.verifyEmail(verificationCode)
                                }
                            ),
                            isError = uiState.errorMessage != null
                        )

                        // Error Message
                        if (uiState.errorMessage != null) {
                            Text(
                                text = uiState.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Success Message
                        if (uiState.successMessage != null) {
                            Text(
                                text = uiState.successMessage ?: "",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Verify Button
                        Button(
                            onClick = { viewModel.verifyEmail(verificationCode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(top = 16.dp),
                            enabled = !uiState.isLoading && verificationCode.isNotBlank()
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Verify Email")
                            }
                        }

                        // Resend Code
                        TextButton(
                            onClick = {
                                viewModel.resendVerificationCode()
                                onResendCode()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 16.dp)
                        ) {
                            Text("Resend Code")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Back Button
            TextButton(
                onClick = onBackClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Back")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EmailVerificationScreenPreview() {
    AI_AssistantTheme {
        EmailVerificationScreen(
            onVerificationSuccess = {},
            onResendCode = {},
            onBackClick = {},
            onNavigateToLogin = {}
        )
    }
}
