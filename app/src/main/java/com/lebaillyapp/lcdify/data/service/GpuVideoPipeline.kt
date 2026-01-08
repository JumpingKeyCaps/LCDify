package com.lebaillyapp.lcdify.data.service

import android.content.Context
import android.graphics.*
import android.hardware.HardwareBuffer
import android.media.*
import android.view.Surface
import androidx.annotation.RawRes
import com.lebaillyapp.lcdify.domain.ShaderConfig
import java.io.File

/**
 * Pipeline Vidéo GPU - Android 13+
 * Décodage direct -> AGSL Shader -> Encodage H.264
 * Version Zero-Copy avec correction du lien HardwareBuffer
 */
class GpuVideoPipeline(
    private val context: Context,
    @RawRes private val videoRes: Int,
    private val outputFile: File,
    private val shaderSource: String,
    private val config: ShaderConfig,
    private val onProgress: (currentFrame: Int, totalFrames: Int) -> Unit,
    private val isCancelled: () -> Boolean
) {

    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    private var encoderSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var hardwareRenderer: HardwareRenderer? = null
    private var renderNode: RenderNode? = null
    private var runtimeShader: RuntimeShader? = null

    private var videoTrackIndex = -1
    private var muxerTrackIndex = -1
    private var muxerStarted = false

    private var width = 0
    private var height = 0
    private var totalFrames = 0

    fun process() {
        try {
            setupExtractor()
            setupEncoder()
            setupShaderRenderer()
            setupDecoder()
            runPipeline()
        } finally {
            release()
        }
    }

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
                    val frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    totalFrames = ((durationUs / 1_000_000.0) * frameRate).toInt()
                    break
                }
            }
        }
        require(videoTrackIndex >= 0) { "No video track found" }
    }

    private fun setupEncoder() {
        val format = extractor!!.getTrackFormat(videoTrackIndex)
        val frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE, 30)

        val encodeFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
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
    }

    private fun setupShaderRenderer() {
        renderNode = RenderNode("LcdifyRenderNode").apply { setPosition(0, 0, width, height) }
        hardwareRenderer = HardwareRenderer().apply {
            setContentRoot(renderNode)
            setSurface(encoderSurface)
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
    }

    private fun setupDecoder() {
        val format = extractor!!.getTrackFormat(videoTrackIndex)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        )
        decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply {
            configure(format, imageReader!!.surface, null, 0)
            start()
        }
    }

    private fun runPipeline() {
        val bufferInfo = MediaCodec.BufferInfo()
        var extractorDone = false
        var decoderDone = false
        var encoderDone = false
        var frameCount = 0

        while (!encoderDone && !isCancelled()) {
            if (!extractorDone) {
                val inputIndex = decoder!!.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val buf = decoder!!.getInputBuffer(inputIndex)!!
                    val size = extractor!!.readSampleData(buf, 0)
                    if (size < 0) {
                        decoder!!.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        extractorDone = true
                    } else {
                        decoder!!.queueInputBuffer(inputIndex, 0, size, extractor!!.sampleTime, 0)
                        extractor!!.advance()
                    }
                }
            }

            if (!decoderDone) {
                val outIndex = decoder!!.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val presentationTimeUs = bufferInfo.presentationTimeUs

                    decoder!!.releaseOutputBuffer(outIndex, true)

                    val img = imageReader!!.acquireLatestImage()
                    img?.let {
                        renderFrameShaderZeroCopy(it, presentationTimeUs)
                        it.close()
                        frameCount++
                        if (frameCount % 10 == 0) onProgress(frameCount, totalFrames)
                    }

                    if (eos) {
                        decoderDone = true
                        encoder!!.signalEndOfInputStream()
                    }
                }
            }

            while (true) {
                val outIndex = encoder!!.dequeueOutputBuffer(bufferInfo, 0)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrackIndex = muxer!!.addTrack(encoder!!.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val buf = encoder!!.getOutputBuffer(outIndex)!!
                        if (bufferInfo.size > 0 && muxerStarted) {
                            buf.position(bufferInfo.offset)
                            buf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer!!.writeSampleData(muxerTrackIndex, buf, bufferInfo)
                        }
                        encoder!!.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                    else -> break
                }
            }
        }
    }

    private fun renderFrameShaderZeroCopy(image: Image, presentationTimeUs: Long) {
        val hBuffer = image.hardwareBuffer ?: return

        // Wrap sans copie : on crée un shader que AGSL peut comprendre
        val wrapBitmap = Bitmap.wrapHardwareBuffer(hBuffer, ColorSpace.get(ColorSpace.Named.SRGB)) ?: return
        val frameShader = BitmapShader(wrapBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        runtimeShader?.setInputBuffer("inputFrame", frameShader)

        val canvas = renderNode!!.beginRecording()
        val paint = Paint().apply { shader = runtimeShader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        renderNode!!.endRecording()

        hardwareRenderer!!.createRenderRequest()
            .setVsyncTime(presentationTimeUs * 1000)
            .syncAndDraw()

        wrapBitmap.recycle() // Libère uniquement le wrapper
    }

    private fun release() {
        try { decoder?.stop(); encoder?.stop(); if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
        decoder?.release(); encoder?.release(); extractor?.release(); muxer?.release()
        imageReader?.close(); hardwareRenderer?.destroy(); encoderSurface?.release()
    }
}