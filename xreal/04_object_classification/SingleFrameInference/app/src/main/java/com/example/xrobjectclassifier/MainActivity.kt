@file:OptIn(ExperimentalProjectedApi::class)

package com.example.xrobjectclassifier

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val classificationResult = remember {
                mutableStateOf<ClassificationResult?>(null)
            }

            val lastInferenceTimeMs = remember {
                mutableStateOf(0L)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                CameraPreview(
                    analysisExecutor = analysisExecutor,
                    shouldRunInference = {
                        val now = System.currentTimeMillis()

                        if (now - lastInferenceTimeMs.value > 2000L) {
                            lastInferenceTimeMs.value = now
                            true
                        } else {
                            false
                        }
                    },
                    classificationResult = classificationResult
                )

                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val boxSize = size.minDimension * 0.50f
                    val left = (size.width - boxSize) / 2f
                    val top = (size.height - boxSize) / 2f

                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(left, top),
                        size = Size(boxSize, boxSize),
                        style = Stroke(width = 4f)
                    )
                }

                val displayText = classificationResult.value?.let {
                    if (it.confidence > 0.40f) {
                        "${it.label}\n${String.format("%.1f", it.confidence * 100)}%"
                    } else {
                        "No confident match"
                    }
                } ?: "Scanning..."

                Text(
                    text = displayText,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }
}

@Composable
fun CameraPreview(
    analysisExecutor: Executor,
    shouldRunInference: () -> Boolean,
    classificationResult: MutableState<ClassificationResult?>
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val inferencePipeline = InferencePipeline(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = CameraXPreview.Builder()
                    .build()
                    .also { cameraPreview ->
                        cameraPreview.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                if (shouldRunInference()) {
                                    val result = inferencePipeline.run(imageProxy)

                                    Log.d(
                                        "XR_INFERENCE",
                                        "Result: ${result.label}, " +
                                                "classIndex=${result.classIndex}, " +
                                                "confidence=${result.confidence}"
                                    )

                                    ContextCompat.getMainExecutor(ctx).execute {
                                        classificationResult.value = result
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("XR_INFERENCE", "Classification failed", e)

                                ContextCompat.getMainExecutor(ctx).execute {
                                    classificationResult.value = ClassificationResult(
                                        label = "error",
                                        confidence = 0.0f,
                                        classIndex = -1
                                    )
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}