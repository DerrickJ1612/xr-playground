package com.example.xrobjectclassifier

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp


data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val classIndex: Int
)

class InferencePipeline(
    private val context: Context
) {
    private val imagenetLabels: List<String> by lazy {
        context.assets.open("imagenet_classes.txt")
            .bufferedReader()
            .readLines()
    }

    fun run(imageProxy: ImageProxy): ClassificationResult {
        val rgba = imageProxyToRgbaBytes(imageProxy)

        val inputTensor = preprocessCenterCropRgbaToNchw224(
            rgba = rgba,
            srcWidth = imageProxy.width,
            srcHeight = imageProxy.height,
            cropFraction = 0.50f
        )

        val outputTensor = runInference(inputTensor)

        return postprocess(outputTensor)
    }

    private fun imageProxyToRgbaBytes(imageProxy: ImageProxy): ByteArray {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer

        buffer.rewind()

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return bytes
    }

    private fun preprocessRgbaToNchw224(
        rgba: ByteArray,
        srcWidth: Int,
        srcHeight: Int
    ): FloatArray {
        val inputSize = 224
        val channelSize = inputSize * inputSize
        val output = FloatArray(1 * 3 * channelSize)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val srcX = x * srcWidth / inputSize
                val srcY = y * srcHeight / inputSize

                val rgbaIndex = (srcY * srcWidth + srcX) * 4

                val r = rgba[rgbaIndex].toInt() and 0xFF
                val g = rgba[rgbaIndex + 1].toInt() and 0xFF
                val b = rgba[rgbaIndex + 2].toInt() and 0xFF

                val pixelIndex = y * inputSize + x

                output[pixelIndex] =
                    ((r / 255.0f) - mean[0]) / std[0]

                output[channelSize + pixelIndex] =
                    ((g / 255.0f) - mean[1]) / std[1]

                output[2 * channelSize + pixelIndex] =
                    ((b / 255.0f) - mean[2]) / std[2]
            }
        }

        return output
    }

    private fun runInference(inputTensor: FloatArray): FloatArray {
        val workDir = File(context.filesDir, "qnn_run")
        workDir.mkdirs()

        val inputRaw = File(workDir, "input.raw")
        val inputList = File(workDir, "input_list.txt")
        val outputDir = File(workDir, "output")

        outputDir.deleteRecursively()
        outputDir.mkdirs()

        writeFloatArrayAsRaw(inputTensor, inputRaw)
        inputList.writeText(inputRaw.absolutePath + "\n")

        val command = listOf(
            "/data/local/tmp/qnn/qnn-net-run",
            "--retrieve_context",
            "/data/local/tmp/qnn/mobilenet_v2.bin",
            "--backend",
            "/data/local/tmp/qnn/libQnnHtp.so",
            "--input_list",
            inputList.absolutePath,
            "--output_dir",
            outputDir.absolutePath
        )

        Log.d("XR_QNN", "Running: ${command.joinToString(" ")}")

        val processBuilder = ProcessBuilder(command)
        processBuilder.environment()["LD_LIBRARY_PATH"] = "/data/local/tmp/qnn"
        processBuilder.environment()["ADSP_LIBRARY_PATH"] = "/data/local/tmp/qnn"
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val stdout = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        Log.d("XR_QNN", "qnn-net-run exitCode=$exitCode")
        Log.d("XR_QNN", stdout)

        if (exitCode != 0) {
            throw RuntimeException("qnn-net-run failed with exitCode=$exitCode\n$stdout")
        }

        val outputRaw = findFirstRawOutput(outputDir)
            ?: throw RuntimeException("No .raw output found in ${outputDir.absolutePath}")

        Log.d("XR_QNN", "Reading output: ${outputRaw.absolutePath}")
        Log.d("XR_QNN", "Output raw size=${outputRaw.length()} bytes")

        return readRawFloatArray(outputRaw)
    }

    private fun postprocess(outputTensor: FloatArray): ClassificationResult {
        val probabilities = softmax(outputTensor)

        var bestIndex = 0
        var bestProbability = probabilities[0]

        for (i in 1 until probabilities.size) {
            if (probabilities[i] > bestProbability) {
                bestProbability = probabilities[i]
                bestIndex = i
            }
        }

        val label = imagenetLabels.getOrNull(bestIndex) ?: "class_$bestIndex"

        Log.d(
            "XR_INFERENCE",
            "Top result: index=$bestIndex label=$label confidence=$bestProbability"
        )

        return ClassificationResult(
            label = label,
            confidence = bestProbability,
            classIndex = bestIndex
        )
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0.0f
        val expValues = FloatArray(logits.size)

        var sum = 0.0f

        for (i in logits.indices) {
            val value = exp((logits[i] - maxLogit).toDouble()).toFloat()
            expValues[i] = value
            sum += value
        }

        return FloatArray(logits.size) { i ->
            expValues[i] / sum
        }
    }

    private fun writeFloatArrayAsRaw(values: FloatArray, file: File) {
        val buffer = ByteBuffer
            .allocate(values.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (value in values) {
            buffer.putFloat(value)
        }

        file.writeBytes(buffer.array())
    }

    private fun readRawFloatArray(file: File): FloatArray {
        val bytes = file.readBytes()

        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        val output = FloatArray(bytes.size / 4)

        for (i in output.indices) {
            output[i] = buffer.getFloat()
        }

        return output
    }

    private fun findFirstRawOutput(outputDir: File): File? {
        return outputDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "raw" }
            .firstOrNull()
    }
    private fun preprocessCenterCropRgbaToNchw224(
        rgba: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        cropFraction: Float
    ): FloatArray {
        val inputSize = 224
        val channelSize = inputSize * inputSize
        val output = FloatArray(1 * 3 * channelSize)

        val cropSize = (kotlin.math.min(srcWidth, srcHeight).toFloat() * cropFraction).toInt()
        val cropLeft = (srcWidth - cropSize) / 2
        val cropTop = (srcHeight - cropSize) / 2

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val srcX = cropLeft + x * cropSize / inputSize
                val srcY = cropTop + y * cropSize / inputSize

                val rgbaIndex = (srcY * srcWidth + srcX) * 4

                val r = rgba[rgbaIndex].toInt() and 0xFF
                val g = rgba[rgbaIndex + 1].toInt() and 0xFF
                val b = rgba[rgbaIndex + 2].toInt() and 0xFF

                val pixelIndex = y * inputSize + x

                output[pixelIndex] =
                    ((r / 255.0f) - mean[0]) / std[0]

                output[channelSize + pixelIndex] =
                    ((g / 255.0f) - mean[1]) / std[1]

                output[2 * channelSize + pixelIndex] =
                    ((b / 255.0f) - mean[2]) / std[2]
            }
        }

        return output
    }
}