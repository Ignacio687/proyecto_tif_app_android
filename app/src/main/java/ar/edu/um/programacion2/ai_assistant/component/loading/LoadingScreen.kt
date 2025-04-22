package ar.edu.um.programacion2.computech.component.loading

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.um.programacion2.computech.R
import okhttp3.internal.wait

@Composable
fun LoadingScreen(navigateToLogin: () -> Unit, navigateToHome: () -> Unit,
                  loadingViewModel: LoadingViewModel = viewModel()) {

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        loadingViewModel.fetchAccount(navigateToHome, navigateToLogin)
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logocomputech),
                contentDescription = "Logo Computech",
                modifier = Modifier.size(400.dp)
            )
        }
    }
}