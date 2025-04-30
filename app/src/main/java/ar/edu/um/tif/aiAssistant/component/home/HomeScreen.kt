package ar.edu.um.tif.aiAssistant.component.home

import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.um.tif.aiAssistant.R

@Composable
fun HomeScreen(navigateToLogin: () -> Unit, homeViewModel: HomeViewModel = viewModel()) {

    val validLogin by homeViewModel.validLogin.observeAsState(true)
    val snackbarHostState = remember { SnackbarHostState() }

    if (!validLogin) {
        homeViewModel.logout(navigateToLogin)
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundImage()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80121212)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp, top = 35.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 2.dp, top = 1.dp, start = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MenuIcon()
                        UserIcon(homeViewModel, navigateToLogin)
                    }
                }
                SnackbarHost(snackbarHostState)
            }
        }
    }
}

@Composable
fun MenuIcon() {
    IconButton(onClick = { }) {
        Icon(
            painter = painterResource(id = R.drawable.ic_menu),
            contentDescription = "User Icon",
            tint = Color.White,
            modifier = Modifier.graphicsLayer(scaleX = 1f, scaleY = 1f)
        )
    }
}

@Composable
fun UserIcon(homeViewModel : HomeViewModel, navigateToLogin: () -> Unit) {
    IconButton(onClick = { homeViewModel.logout(navigateToLogin) }) {
        Icon(
            painter = painterResource(id = R.drawable.ic_account),
            contentDescription = "User Icon",
            tint = Color.White,
            modifier = Modifier.graphicsLayer(scaleX = 1.3f, scaleY = 1.3f)
        )
    }
}

@Composable
fun BackgroundImage() {
    Image(
        painter = painterResource(id = R.drawable.computech_background),
        contentDescription = "Background",
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                rotationZ = 90f,
                scaleX = 2.5f,
                scaleY = 2.5f,
                alpha = 1f
            )
            .blur(1.8.dp)
    )
}

@Composable
fun SnackbarHost(snackbarHostState: SnackbarHostState) {
    SnackbarHost(hostState = snackbarHostState)
}
