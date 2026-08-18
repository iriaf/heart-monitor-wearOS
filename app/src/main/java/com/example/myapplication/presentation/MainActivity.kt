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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.myapplication.presentation.theme.MyApplicationTheme
import com.example.myapplication.service.HeartRateService
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.wear.compose.material3.ButtonDefaults



class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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
        val intent =
            Intent(this, HeartRateService::class.java) // we tell the kernel to manage ts for us
        startForegroundService(intent)
        // this is very different from the direct memory / kernel manip im used to ngl
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

    val permissionsToRequest = mutableListOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    // SDK_INT >= 33 requires permission from the user to send notifications.
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
            //isTrainingMode = true
        }
        else
        {
            Toast.makeText(context, "Necessary permissions not allowed by user", Toast.LENGTH_SHORT).show()
        }
    }

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
                            // Botão A modificado

                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            // Encolhe para 90% do tamanho se estiver pressionado
                            val scale by animateFloatAsState(targetValue = if (isPressed) 0.9f else 1f, label = "scale")
                            // Fica cinza escuro ao toque, retorna à cor primária ao soltar
                            val bgColor by animateColorAsState(targetValue = if (isPressed) Color.DarkGray else MaterialTheme.colorScheme.primary, label = "color")

                            Button(
                                onClick = {
                                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                },
                                interactionSource = interactionSource,
                                colors = ButtonDefaults.buttonColors(containerColor = bgColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(scale) // Aplica a animação de tamanho
                                    .transformedHeight(this, transformationSpec),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) {
                                Text("Conectar (Start)")
                            }
                        }
                        item {
                            // Botão B modificado
                            // Motores de Interação para o Botão "STOP"
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val scale by animateFloatAsState(targetValue = if (isPressed) 0.9f else 1f, label = "scale")
                            val bgColor by animateColorAsState(targetValue = if (isPressed) Color.DarkGray else MaterialTheme.colorScheme.onSurfaceVariant, label = "color")

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
                                Text("Desconectar (Stop)") } } }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearApp(
        onStartClick = {},
        onFinishClick = {}
    )
}