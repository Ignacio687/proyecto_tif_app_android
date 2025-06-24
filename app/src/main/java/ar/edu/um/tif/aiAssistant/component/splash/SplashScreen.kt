package ar.edu.um.tif.aiAssistant.component.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ar.edu.um.tif.aiAssistant.R
import ar.edu.um.tif.aiAssistant.ui.theme.AI_AssistantTheme

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // App logo using the same launcher icon that users see when the app is opening
        Image(
            painter = painterResource(id = R.drawable.app_icon_v3),
            contentDescription = "App Logo",
            modifier = Modifier.size(220.dp)
        )

        // Loading indicator at the bottom
        Box(
            modifier = Modifier.align(Alignment.BottomCenter),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(50.dp)
                    .align(Alignment.Center)
                    .padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.primary
            )

            // Add spacer for bottom padding
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    AI_AssistantTheme {
        SplashScreen()
    }
}
