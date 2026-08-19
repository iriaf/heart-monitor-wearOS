package com.example.myapplication.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.myapplication.presentation.theme.MyApplicationTheme
import com.example.myapplication.service.HeartRateService
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.wear.compose.material3.ButtonDefaults



class MainActivity : ComponentActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        setContent {
            WearApp(
                onStartClick = { startHeartRateService() },
                onFinishClick = { stopHeartRateService() },
            )
        }
    }

    private fun startHeartRateService()
    {
        val intent = Intent(this, HeartRateService::class.java) // we tell the kernel to manage ts for us
        startForegroundService(intent)
        // this is very different from the direct memory manip im used to ngl
    }

    private fun stopHeartRateService()
    {
        val intent = Intent(this, HeartRateService::class.java)
        stopService(intent)
    }

}

@Composable
fun WearApp(
    onStartClick: () -> Unit,
    onFinishClick: () -> Unit
) {
    val context = LocalContext.current

    // Per Android policy, we are required to ask permission to access body sensor data.
    val permissionsToRequest = mutableListOf(
        Manifest.permission.BODY_SENSORS
    )

    // SDK_INT >= 33 requires us to get permission to send notifications.
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }

        if(allGranted)
        {
            onStartClick()
        }
        else
        {
            Toast.makeText(context, "Necessary permissions not allowed by user", Toast.LENGTH_SHORT).show()
        }
    }

    // Defining the app's interface
    MyApplicationTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            ScreenScaffold(
                scrollState = listState,
                ) { contentPadding ->
                    TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                        item {
                            ListHeader(
                                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) {
                                Text("Monitor Cardíaco")
                            }
                        }
                        item {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            // Change color and scale if button is held
                            val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1.0f, label = "scale")
                            val bgColor by animateColorAsState(targetValue = if (isPressed) Color(127, 255, 92) else Color(48, 224, 0), label = "color")

                            Button(
                                onClick = {
                                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                },
                                interactionSource = interactionSource,
                                colors = ButtonDefaults.buttonColors(containerColor = bgColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(scale)
                                    .transformedHeight(this, transformationSpec),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) {
                                Text("Iniciar")
                            }
                        }
                        item {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            // Change color and scale if button is held
                            val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1.0f, label = "scale")
                            val bgColor by animateColorAsState(targetValue = if (isPressed) Color(254, 139, 139) else Color(252, 68, 68), label = "color")

                            Button(
                                onClick = onFinishClick,
                                interactionSource = interactionSource,
                                colors = ButtonDefaults.buttonColors(containerColor = bgColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(scale)
                                    .transformedHeight(this, transformationSpec),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) {
                                Text("Finalizar") } } }
            }
        }
    }
}

// Make sure to import the following in order to verify previews:
// import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
// import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
/*
@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearApp(
        onStartClick = {},
        onFinishClick = {}
    )
}
*/