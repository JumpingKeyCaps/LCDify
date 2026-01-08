package com.lebaillyapp.lcdify.data.service

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import androidx.annotation.RawRes
import java.io.File

class GpuVideoPipeline(
    private val context: Context,
    @RawRes private val videoRes: Int,
    private val outputFile: File,
    private val onProgress: (currentFrame: Int, totalFrames: Int) -> Unit,
    private val isCancelled: () -> Boolean
) {

    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    private var decoderSurfaceTexture: SurfaceTexture? = null
    private var decoderSurface: Surface? = null
    private var encoderInputSurface: Surface? = null

    private var videoTrackIndex = -1
    private var muxerTrackIndex = -1
    private var muxerStarted = false

    fun process() {
        try {
            setupExtractor()
            setupEncoder()
            setupDecoder()
            setupMuxer()
            runPipeline()
        } finally {
            release()
        }
    }

    // ----------------------------------------------------
    // Setup
    // ----------------------------------------------------

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
                    break
                }
            }

            require(videoTrackIndex >= 0) { "No video track found" }
        }
    }

    private fun setupEncoder() {
        val format = extractor!!.getTrackFormat(videoTrackIndex)
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE, 30)

        val encodeFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        encoder = MediaCodec.createEncoderByType(
            MediaFormat.MIMETYPE_VIDEO_AVC
        ).apply {
            configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderInputSurface = createInputSurface()
            start()
        }
    }

    private fun setupDecoder() {
        val format = extractor!!.getTrackFormat(videoTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        decoderSurfaceTexture = SurfaceTexture(0).apply {
            setDefaultBufferSize(
                format.getInteger(MediaFormat.KEY_WIDTH),
                format.getInteger(MediaFormat.KEY_HEIGHT)
            )
        }

        decoderSurface = Surface(decoderSurfaceTexture)

        decoder = MediaCodec.createDecoderByType(mime).apply {
            configure(format, decoderSurface, null, 0)
            start()
        }
    }

    private fun setupMuxer() {
        muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
    }

    // ----------------------------------------------------
    // Pipeline loop (GPU noop for now)
    // ----------------------------------------------------

    private fun runPipeline() {
        val extractor = extractor!!
        val decoder = decoder!!
        val encoder = encoder!!
        val muxer = muxer!!

        val decoderInfo = MediaCodec.BufferInfo()
        val encoderInfo = MediaCodec.BufferInfo()

        var extractorDone = false
        var decoderDone = false
        var encoderDone = false
        var frameIndex = 0

        while (!encoderDone && !isCancelled()) {

            if (!extractorDone) {
                val inputIndex = decoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                    val size = extractor.readSampleData(inputBuffer, 0)

                    if (size < 0) {
                        decoder.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        extractorDone = true
                    } else {
                        decoder.queueInputBuffer(
                            inputIndex, 0, size,
                            extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }

            if (!decoderDone) {
                val outputIndex = decoder.dequeueOutputBuffer(decoderInfo, 10_000)
                if (outputIndex >= 0) {
                    val end =
                        decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                    decoder.releaseOutputBuffer(outputIndex, true)
                    frameIndex++
                    onProgress(frameIndex, -1)

                    if (end) {
                        decoderDone = true
                        encoder.signalEndOfInputStream()
                    }
                }
            }

            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(encoderInfo, 0)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    outIndex >= 0 -> {
                        val buffer = encoder.getOutputBuffer(outIndex)!!
                        if (encoderInfo.size > 0 && muxerStarted) {
                            buffer.position(encoderInfo.offset)
                            buffer.limit(encoderInfo.offset + encoderInfo.size)
                            muxer.writeSampleData(muxerTrackIndex, buffer, encoderInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)

                        if (encoderInfo.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        ) {
                            encoderDone = true
                            break
                        }
                    }

                    else -> break
                }
            }
        }
    }

    // ----------------------------------------------------
    // Cleanup
    // ----------------------------------------------------

    private fun release() {
        extractor?.release()

        decoder?.stop()
        decoder?.release()

        encoder?.stop()
        encoder?.release()

        decoderSurface?.release()
        decoderSurfaceTexture?.release()
        encoderInputSurface?.release()

        if (muxerStarted) muxer?.stop()
        muxer?.release()
    }
}
