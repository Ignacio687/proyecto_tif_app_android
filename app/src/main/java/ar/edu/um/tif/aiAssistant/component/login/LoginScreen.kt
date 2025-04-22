package ar.edu.um.tif.aiAssistant.component.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.um.tif.aiAssistant.R

@Composable
fun LoginScreen(navigateToHome: () -> Unit, loginViewModel: LoginViewModel = viewModel()) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(id = R.drawable.logocomputech),
                contentDescription = "Logo aiAssistant",
                modifier = Modifier.size(400.dp)
            )
            UserNameField(loginViewModel)
            PasswordField(loginViewModel)
            LoginButton(loginViewModel, navigateToHome)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserNameField(loginViewModel: LoginViewModel) {
    val userName by loginViewModel.userName.observeAsState("")
    val isUserNameValid by loginViewModel.isUserNameValid.observeAsState(true)
    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = userName,
            onValueChange = { loginViewModel.onUserNameChanged(it) },
            placeholder = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E1E1E),
                focusedTextColor = Color.White,
                focusedPlaceholderColor = Color.Gray
            )
        )
        if (!isUserNameValid && userName.isNotEmpty()) {
            Text(
                text = "El usuario no debe superar los 50 caracteres",
                color = Color.Red,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordField(loginViewModel: LoginViewModel) {
    val password by loginViewModel.password.observeAsState("")
    val isPasswordValid by loginViewModel.isPasswordValid.observeAsState(true)
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = password,
            onValueChange = { loginViewModel.onPasswordChanged(it) },
            placeholder = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) R.drawable.ic_visibility
                else R.drawable.ic_visibility_off
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(painterResource(id = image), contentDescription = null)
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E1E1E),
                focusedTextColor = Color.White,
                focusedPlaceholderColor = Color.Gray
            )
        )
        if (!isPasswordValid && password.isNotEmpty()) {
            Text(
                text = "La contraseña debe tener entre 4 y 100 caracteres",
                color = Color.Red,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun LoginButton(loginViewModel: LoginViewModel, navigateToHome: () -> Unit) {
    val isLoginEnabled by loginViewModel.isLoginEnabled.observeAsState(false)
    Button(
        onClick = { loginViewModel.authenticate(navigateToHome) },
        enabled = isLoginEnabled,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isLoginEnabled) Color(0xFF6200EE) else Color(0xFF3700B3),
            contentColor = Color.White
        )
    ) {
        Text(text = "Login", fontSize = 20.sp)
    }
    val isInvalidCredentials by loginViewModel.isInvalidCredentials.observeAsState(false)
    val isLoginError by loginViewModel.isLoginError.observeAsState(false)
    if (!isInvalidCredentials) {
        Text(
            text = "Credenciales inválidas",
            color = Color.Red,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 8.dp)
        )
    } else if (isLoginError) {
        Text(
            text = "Error al iniciar sesión, intente más tarde",
            color = Color.Red,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}