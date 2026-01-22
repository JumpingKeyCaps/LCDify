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
 * # GPU Video Pipeline EVO (Fixed Version for Android 13+)
 *
 * A strict version of the original pipeline with some improvements to isolate the source of error :
 * - Timestamps cohérents et croissants (Stable timestamp control)
 * - Synchronisation GPU améliorée (GPU sync optimisation)
 * - acquireNextImage() pour ne perdre aucune frame (no lost frame allowed)
 * - Drain de l'encodeur après CHAQUE frame GPU (encoder drain after each GPU frame + delay)
 * - Logs de debug pour diagnostiquer les problèmes (full log process + exceptions handling)
 *
 * @property context Android context required for resource access.
 * @property videoRes Raw resource ID of the input video.
 * @property outputFile Destination file for the encoded video.
 * @property shaderSource AGSL shader source code applied per frame.
 * @property config Shader configuration (palette, scale factor, grid parameters, dithering).
 * @property onProgress Callback invoked periodically with (currentFrame, totalFrames).
 * @property isCancelled Lambda returning true if the pipeline should abort early.
 */
@SuppressLint("WrongConstant", "NewApi")
@OptIn(ExperimentalUnsignedTypes::class)
class GpuVideoPipelineEVO(
    private val context: Context,
    @RawRes private val videoRes: Int,
    private val outputFile: File,
    private val shaderSource: String,
    private val config: ShaderConfig,
    private val onProgress: (currentFrame: Int, totalFrames: Int) -> Unit,
    private val isCancelled: () -> Boolean
) {
    companion object {
        private const val TAG = "GpuVideoPipelineEVO"
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
    private var hardwareRenderer: HardwareRenderer? = null
    private var renderNode: RenderNode? = null
    private var runtimeShader: RuntimeShader? = null

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
     * ## Process
     */
    fun process() {
        try {
            logDebug("🚀 Starting pipeline process")
            setupExtractor()
            setupEncoder()
            setupShaderRenderer()
            setupDecoder()
            runPipeline()
            logDebug("✅ Pipeline completed successfully")
        } catch (e: Exception) {
            logDebug("❌ Pipeline error: ${e.message}")
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
                    logDebug("📹 Video: ${width}x${height} @ ${frameRate}fps, ~$totalFrames frames")
                    break
                }
            }
        }
        require(videoTrackIndex >= 0) { "No video track found" }
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
     * ## Setup Shader Renderer
     */
    private fun setupShaderRenderer() {
        renderNode = RenderNode("LcdifyRenderNodeEVO").apply {
            setPosition(0, 0, width, height)
        }

        hardwareRenderer = HardwareRenderer().apply {
            setContentRoot(renderNode)
            setSurface(encoderSurface) // <------ This is precisely where Android troll us ! ^^ ... and why the no-copy pipeline is fuck up!
        }

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
        logDebug("🎨 Shader configured with ${config.palette.size} colors")
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
        logDebug("📥 Decoder started")
    }

    /**
     * ## Run Pipeline (Main Loop - FIXED VERSION V2)
     */
    private fun runPipeline() {
        val bufferInfo = MediaCodec.BufferInfo()
        var extractorDone = false
        var decoderDone = false
        var encoderDone = false
        var decodedFrameCount = 0
        var encodedFrameCount = 0

        logDebug("⚙️ Starting pipeline loop")

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
                        logDebug("📤 Extractor EOS sent to decoder")
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

                    val img = imageReader!!.acquireNextImage()
                    img?.let {
                        renderFrameShaderZeroCopy(it, presentationTimeUs)
                        it.close()
                        decodedFrameCount++

                        // CRITICAL: Drain encoder after each GPU frame
                        encodedFrameCount = drainEncoderAfterFrame(bufferInfo, encodedFrameCount)

                        if (decodedFrameCount % 10 == 0) {
                            onProgress(decodedFrameCount, totalFrames)
                            logDebug("📊 Progress: $decodedFrameCount/$totalFrames decoded, $encodedFrameCount encoded")
                        }
                    } ?: logDebug("⚠️ No image available from ImageReader")

                    if (eos) {
                        decoderDone = true
                        encoder!!.signalEndOfInputStream()
                        logDebug("📤 Decoder EOS, signaled to encoder")
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
                            logDebug("🎥 Muxer started (late), track index: $muxerTrackIndex")
                        }
                    }

                    outIndex >= 0 -> {
                        val buf = encoder!!.getOutputBuffer(outIndex)!!

                        if (bufferInfo.size > 0 && muxerStarted) {
                            buf.position(bufferInfo.offset)
                            buf.limit(bufferInfo.offset + bufferInfo.size)

                            val frameTimeUs = encodedFrameCount * 1_000_000L / frameRate
                            bufferInfo.presentationTimeUs = frameTimeUs

                            muxer!!.writeSampleData(muxerTrackIndex, buf, bufferInfo)
                            encodedFrameCount++
                        }

                        encoder!!.releaseOutputBuffer(outIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                            logDebug("✅ Encoder EOS received, $encodedFrameCount frames encoded")
                        }
                    }
                }
            }
        }

        logDebug("📈 Final stats: $decodedFrameCount decoded, $encodedFrameCount encoded")
    }

    /**
     * ## Drain Encoder After Frame
     * Forces encoder to process the frame just rendered to surface.
     */
    private fun drainEncoderAfterFrame(bufferInfo: MediaCodec.BufferInfo, currentCount: Int): Int {
        var encodedCount = currentCount
        var attempts = 0

        while (attempts < 10) {
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

                        val frameTimeUs = encodedCount * 1_000_000L / frameRate
                        bufferInfo.presentationTimeUs = frameTimeUs

                        muxer!!.writeSampleData(muxerTrackIndex, buf, bufferInfo)

                        if (encodedCount % 30 == 0) {
                            logDebug("💾 Encoded frame $encodedCount @ ${frameTimeUs}µs")
                        }

                        encodedCount++
                    }

                    encoder!!.releaseOutputBuffer(outIndex, false)
                    break // Got a frame, done
                }

                else -> {
                    attempts++
                    try {
                        Thread.sleep(10)
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
     * ## Render Frame with Shader - FIXED VERSION V2
     */
    private fun renderFrameShaderZeroCopy(image: Image, presentationTimeUs: Long) {
        val hBuffer = image.hardwareBuffer ?: return

        val wrapBitmap = Bitmap.wrapHardwareBuffer(
            hBuffer,
            ColorSpace.get(ColorSpace.Named.SRGB)
        ) ?: return

        val frameShader = BitmapShader(
            wrapBitmap,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        )

        runtimeShader?.setInputBuffer("inputFrame", frameShader)

        val canvas = renderNode!!.beginRecording()
        val paint = Paint().apply { shader = runtimeShader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        renderNode!!.endRecording()

        hardwareRenderer!!.createRenderRequest()
            .setVsyncTime(presentationTimeUs * 1000)
            .syncAndDraw()

        // Give GPU time to commit
        try {
            Thread.sleep(16)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        wrapBitmap.recycle()
    }

    /**
     * ## Release Resources
     */
    private fun release() {
        logDebug("🧹 Releasing resources")
        try {
            decoder?.stop()
            encoder?.stop()
            if (muxerStarted) muxer?.stop()
        } catch (e: Exception) {
            logDebug("⚠️ Error during stop: ${e.message}")
        }

        decoder?.release()
        encoder?.release()
        extractor?.release()
        muxer?.release()
        imageReader?.close()
        hardwareRenderer?.destroy()
        encoderSurface?.release()

        logDebug("✅ Resources released")
    }

    private fun logDebug(message: String) {
        if (ENABLE_DEBUG_LOGS) {
            Log.d(TAG, message)
        }
    }
}