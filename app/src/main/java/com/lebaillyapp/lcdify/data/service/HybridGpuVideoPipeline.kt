package com.lebaillyapp.lcdify.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.HardwareBuffer
import android.media.*
import android.util.Log
import android.view.Surface
import androidx.annotation.RawRes
import com.lebaillyapp.lcdify.domain.ShaderConfig
import java.io.File

/**
 * # Hybrid GPU-CPU Video Pipeline (Android 13+)
 *
 * ## The Pivot Solution
 *
 * This pipeline implements a **Hybrid GPU-CPU Bridge** to bypass the architectural limitation
 * between HardwareRenderer (Skia/View system) and MediaCodec Input Surface (OpenGL ES context).
 *
 * ### Pipeline Flow:
 * ```
 * MediaExtractor → HardwareDecoder (GPU)
 *       ↓
 * HardwareBuffer (VRAM)
 *       ↓
 * RuntimeShader (AGSL - GPU Processing)
 *       ↓
 * Bitmap Render (GPU → CPU Bridge)
 *       ↓
 * Canvas.lockCanvas() + draw() + unlockCanvasAndPost()
 *       ↓
 * MediaCodec Encoder Surface → Final MP4
 * ```
 *
 * ### Trade-off:
 * -  No longer "zero-copy" (one GPU→CPU→GPU round-trip per frame)
 * -  AGSL shader fully functional (heavy GPU math still accelerated)
 * -  Stable, reliable encoding (no EGL sync issues)
 * - ⚡ ~2-3x slower than theoretical pure-GPU, but practical for short videos
 *
 * @property context Android context required for resource access
 * @property videoRes Raw resource ID of the input video
 * @property outputFile Destination file for the encoded video
 * @property shaderSource AGSL shader source code applied per frame
 * @property config Shader configuration (palette, scale factor, grid, dithering)
 * @property onProgress Callback invoked with (currentFrame, totalFrames)
 * @property isCancelled Lambda returning true if pipeline should abort
 */
@SuppressLint("WrongConstant", "NewApi")
@OptIn(ExperimentalUnsignedTypes::class)
class HybridGpuVideoPipeline(
    private val context: Context,
    @RawRes private val videoRes: Int,
    private val outputFile: File,
    private val shaderSource: String,
    private val config: ShaderConfig,
    private val onProgress: (currentFrame: Int, totalFrames: Int) -> Unit,
    private val isCancelled: () -> Boolean
) {
    companion object {
        private const val TAG = "HybridGpuPipeline"
        private const val ENABLE_DEBUG_LOGS = true
    }

    // Media components
    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    // Surfaces and rendering
    private var encoderSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var runtimeShader: RuntimeShader? = null

    // Bitmap bridge for GPU→CPU→GPU transfer
    private var bitmapBridge: Bitmap? = null
    private val bitmapCanvas = Canvas()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Track indices and flags
    private var videoTrackIndex = -1
    private var muxerTrackIndex = -1
    private var muxerStarted = false

    // Video dimensions and frame count
    private var width = 0
    private var height = 0
    private var totalFrames = 0
    private var frameRate = 30

    /**
     * ## Main Process Entry Point
     */
    fun process() {
        try {
            logDebug("[HYBRID] Starting pipeline process")
            setupExtractor()
            setupEncoder()
            setupShaderRenderer()
            setupDecoder()
            runPipeline()
            logDebug("[HYBRID] Pipeline completed successfully")
        } catch (e: Exception) {
            logDebug("[HYBRID] Pipeline error: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            release()
        }
    }

    /**
     * ## Setup Extractor
     */
    private fun setupExtractor() {
        extractor = MediaExtractor().apply {
            val afd = context.resources.openRawResourceFd(videoRes)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            for (i in 0 until trackCount) {
                val format = getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    selectTrack(i)
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    totalFrames = ((durationUs / 1_000_000.0) * frameRate).toInt()

                    logDebug("Video detected: ${width}x${height} @ ${frameRate}fps, ~$totalFrames frames")
                    break
                }
            }
        }
        require(videoTrackIndex >= 0) { "No video track found in source" }
    }

    /**
     * ## Setup Encoder
     */
    private fun setupEncoder() {
        val encodeFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = createInputSurface()
            start()
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        logDebug("🎬 Encoder configured: ${width}x${height} @ ${frameRate}fps, 6Mbps")
    }

    /**
     * ## Setup Shader Renderer (GPU-accelerated AGSL)
     */
    private fun setupShaderRenderer() {
        // Create bitmap bridge for GPU→CPU transfer
        bitmapBridge = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmapCanvas.setBitmap(bitmapBridge)

        // Configure AGSL RuntimeShader
        runtimeShader = RuntimeShader(shaderSource).apply {
            setFloatUniform("res", width.toFloat(), height.toFloat())
            setFloatUniform("scaleFactor", config.scaleFactor)
            setFloatUniform("ditheringStrength", config.ditheringStrength)
            setFloatUniform("gridIntensity", config.gridIntensity)
            setFloatUniform("gridSize", config.gridSize)

            config.palette.forEachIndexed { i, c ->
                setFloatUniform("palette$i", c.red, c.green, c.blue, c.alpha)
            }
        }

        logDebug("Shader configured with ${config.palette.size} palette colors")
        logDebug("Bitmap bridge created: ${width}x${height} ARGB_8888")
    }

    /**
     * ## Setup Decoder
     */
    private fun setupDecoder() {
        val format = extractor!!.getTrackFormat(videoTrackIndex)

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            3,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        )

        decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply {
            configure(format, imageReader!!.surface, null, 0)
            start()
        }

        logDebug("Decoder started with HardwareBuffer output")
    }

    /**
     * ## Run Pipeline (Main Loop - Hybrid Version)
     */
    private fun runPipeline() {
        val bufferInfo = MediaCodec.BufferInfo()
        var extractorDone = false
        var decoderDone = false
        var encoderDone = false
        var decodedFrameCount = 0
        var encodedFrameCount = 0

        logDebug("Starting hybrid pipeline loop")

        while (!encoderDone && !isCancelled()) {

            // --- Feed decoder from extractor ---
            if (!extractorDone) {
                val inputIndex = decoder!!.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val buf = decoder!!.getInputBuffer(inputIndex)!!
                    val size = extractor!!.readSampleData(buf, 0)

                    if (size < 0) {
                        decoder!!.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        extractorDone = true
                        logDebug("Extractor EOS sent to decoder")
                    } else {
                        decoder!!.queueInputBuffer(inputIndex, 0, size, extractor!!.sampleTime, 0)
                        extractor!!.advance()
                    }
                }
            }

            // --- Process decoder output ---
            if (!decoderDone) {
                val outIndex = decoder!!.dequeueOutputBuffer(bufferInfo, 10_000)

                if (outIndex >= 0) {
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val presentationTimeUs = bufferInfo.presentationTimeUs

                    decoder!!.releaseOutputBuffer(outIndex, true)

                    // Acquire decoded frame from ImageReader
                    val img = imageReader!!.acquireNextImage()
                    img?.let {
                        //  HYBRID BRIDGE: GPU shader → Bitmap → Encoder Surface
                        renderFrameHybrid(it, presentationTimeUs)
                        it.close()
                        decodedFrameCount++

                        // Drain encoder after each frame
                        encodedFrameCount = drainEncoderAfterFrame(bufferInfo, encodedFrameCount)

                        if (decodedFrameCount % 10 == 0) {
                            onProgress(decodedFrameCount, totalFrames)
                            logDebug("Progress: $decodedFrameCount/$totalFrames decoded, $encodedFrameCount encoded")
                        }
                    } ?: logDebug("No image available from ImageReader")

                    if (eos) {
                        decoderDone = true
                        encoder!!.signalEndOfInputStream()
                        logDebug("Decoder EOS, signaled to encoder")
                    }
                }
            }

            // --- Final encoder drain after decoder EOS ---
            if (decoderDone && !encoderDone) {
                val outIndex = encoder!!.dequeueOutputBuffer(bufferInfo, 10_000)

                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxerTrackIndex = muxer!!.addTrack(encoder!!.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                            logDebug("🎥 Muxer started, track index: $muxerTrackIndex")
                        }
                    }

                    outIndex >= 0 -> {
                        val buf = encoder!!.getOutputBuffer(outIndex)!!

                        if (bufferInfo.size > 0 && muxerStarted) {
                            buf.position(bufferInfo.offset)
                            buf.limit(bufferInfo.offset + bufferInfo.size)

                            muxer!!.writeSampleData(muxerTrackIndex, buf, bufferInfo)
                            encodedFrameCount++
                        }

                        encoder!!.releaseOutputBuffer(outIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                            logDebug("Encoder EOS received, $encodedFrameCount frames encoded")
                        }
                    }
                }
            }
        }

        logDebug("Final stats: $decodedFrameCount decoded, $encodedFrameCount encoded")
    }

    /**
     * ## Render Frame (Hybrid GPU→CPU→GPU Bridge)
     *
     * This is the CRITICAL pivot implementation:
     * 1. Wrap HardwareBuffer from decoder into Bitmap (GPU memory)
     * 2. Apply AGSL shader to intermediate Bitmap (GPU-accelerated)
     * 3. Lock encoder Surface Canvas (CPU contract)
     * 4. Draw processed Bitmap to encoder Surface
     * 5. Unlock and post to encoder
     */
    private fun renderFrameHybrid(image: Image, presentationTimeUs: Long) {
        val hBuffer = image.hardwareBuffer ?: run {
            logDebug("No HardwareBuffer in image")
            return
        }

        // Step 1: Wrap HardwareBuffer as Bitmap (still in GPU memory)
        val sourceBitmap = Bitmap.wrapHardwareBuffer(
            hBuffer,
            ColorSpace.get(ColorSpace.Named.SRGB)
        ) ?: run {
            logDebug("Failed to wrap HardwareBuffer")
            return
        }

        try {
            // Step 2: Apply AGSL shader to source (GPU processing)
            val frameShader = BitmapShader(
                sourceBitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            )
            runtimeShader?.setInputBuffer("inputFrame", frameShader)

            // Render shader output to intermediate Bitmap (GPU→CPU bridge)
            bitmapPaint.shader = runtimeShader
            bitmapCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bitmapPaint)

            // Step 3-5: Transfer processed Bitmap to encoder Surface (CPU→GPU)
            val canvas = encoderSurface?.lockCanvas(null)
            if (canvas != null) {
                canvas.drawBitmap(bitmapBridge!!, 0f, 0f, null)
                encoderSurface?.unlockCanvasAndPost(canvas)
            } else {
                logDebug("Failed to lock encoder surface canvas")
            }

        } finally {
            sourceBitmap.recycle()
        }
    }

    /**
     * ## Drain Encoder After Frame
     */
    private fun drainEncoderAfterFrame(bufferInfo: MediaCodec.BufferInfo, currentCount: Int): Int {
        var encodedCount = currentCount
        var attempts = 0

        while (attempts < 10) {
            val outIndex = encoder!!.dequeueOutputBuffer(bufferInfo, 5_000)

            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        muxerTrackIndex = muxer!!.addTrack(encoder!!.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                        logDebug("🎥 Muxer started, track index: $muxerTrackIndex")
                    }
                }

                outIndex >= 0 -> {
                    val buf = encoder!!.getOutputBuffer(outIndex)!!

                    if (bufferInfo.size > 0 && muxerStarted) {
                        buf.position(bufferInfo.offset)
                        buf.limit(bufferInfo.offset + bufferInfo.size)

                        val frameTimeUs = encodedCount * 1_000_000L / frameRate
                        bufferInfo.presentationTimeUs = frameTimeUs

                        muxer!!.writeSampleData(muxerTrackIndex, buf, bufferInfo)

                        if (encodedCount % 30 == 0) {
                            logDebug("Encoded frame $encodedCount @ ${frameTimeUs}µs")
                        }

                        encodedCount++
                    }

                    encoder!!.releaseOutputBuffer(outIndex, false)
                    break // Got a frame, done
                }

                else -> {
                    attempts++
                    try {
                        Thread.sleep(5)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }

        return encodedCount
    }

    /**
     * ## Release Resources
     */
    private fun release() {
        logDebug("Releasing resources")

        try {
            decoder?.stop()
            encoder?.stop()
            if (muxerStarted) muxer?.stop()
        } catch (e: Exception) {
            logDebug("Error during stop: ${e.message}")
        }

        decoder?.release()
        encoder?.release()
        extractor?.release()
        muxer?.release()
        imageReader?.close()
        encoderSurface?.release()

        bitmapBridge?.recycle()
        bitmapBridge = null

        logDebug("Resources released")
    }

    private fun logDebug(message: String) {
        if (ENABLE_DEBUG_LOGS) {
            Log.d(TAG, message)
        }
    }
}