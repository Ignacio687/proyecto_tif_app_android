package ar.edu.um.programacion2.computech.component.home

import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.um.programacion2.computech.R
import ar.edu.um.programacion2.computech.component.home.clientDataModel.Device
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.internal.wait

@Composable
fun HomeScreen(navigateToDevice: (Int) -> Unit, navigateToLogin: () -> Unit,
               homeViewModel: HomeViewModel = viewModel()) {

    val devices by homeViewModel.devices.observeAsState(emptyList())
    val validLogin by homeViewModel.validLogin.observeAsState(true)
    val snackbarHostState = remember { SnackbarHostState() }

    if (!validLogin) {
        homeViewModel.logout(navigateToLogin)
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            BackgroundImage()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80121212)), // Fondo semitransparente para el contenido
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
            DeviceCard(devices, navigateToDevice)
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
fun DeviceCard(devices: List<Device>, navigateToDevice: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x8B121212)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "DISPOSITIVOS",
                color = Color(0xFFE7E5E2),
                fontSize = 26.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterHorizontally),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(18.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(devices) { device ->
                    DeviceItem(device, navigateToDevice)
                }
            }
        }
    }
}

@Composable
fun SnackbarHost(snackbarHostState: SnackbarHostState) {
    SnackbarHost(hostState = snackbarHostState)
}

@Composable
fun DeviceItem(device: Device, navigateToDevice: (Int) -> Unit) {
    Button(
        onClick = { navigateToDevice(device.id) },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0x8B121212)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, color = Color(0xFF555151), shape = RoundedCornerShape(35.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = device.name, fontSize = 20.sp, color = Color.White)
            Text(
                text = "\$${device.basePrice}",
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}