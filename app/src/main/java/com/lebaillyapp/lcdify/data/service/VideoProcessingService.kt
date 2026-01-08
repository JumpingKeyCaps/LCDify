package com.lebaillyapp.lcdify.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.RawRes
import com.lebaillyapp.lcdify.R
import com.lebaillyapp.lcdify.domain.ProcessingProgress
import com.lebaillyapp.lcdify.domain.ProcessingState
import com.lebaillyapp.lcdify.domain.ShaderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Service qui gère TOUT le pipeline de traitement vidéo
 *
 * Pipeline:
 * 1. Extraction métadonnées
 * 2. Décodage frame par frame (MediaMetadataRetriever)
 * 3. Application shader GPU (RuntimeShader)
 * 4. Encodage H.264 (MediaCodec)
 * 5. Muxing final (MediaMuxer)
 */
class VideoProcessingService(private val context: Context) {

    private val shaderSource: String by lazy {
        context.resources.openRawResource(R.raw.retroboy_shader_opti_ds)
            .bufferedReader()
            .use { it.readText() }
    }

    @Volatile
    private var isCancelled = false

    // ========  cache shader ========
    private var runtimeShader: RuntimeShader? = null
    private var shaderPaint: Paint? = null
    private var cachedWidth = -1
    private var cachedHeight = -1

    private fun ensureShader(width: Int, height: Int) {
        if (runtimeShader != null && width == cachedWidth && height == cachedHeight) return

        runtimeShader = RuntimeShader(shaderSource).apply {
            setFloatUniform("res", width.toFloat(), height.toFloat())
            setFloatUniform("imgRes", width.toFloat(), height.toFloat())
        }

        shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = runtimeShader
        }

        cachedWidth = width
        cachedHeight = height
    }
    // =======================================

    /**
     * Point d'entrée principal - Retourne un Flow qui émet les états
     */
    fun processVideo(
        @RawRes videoRes: Int,
        config: ShaderConfig
    ): Flow<ProcessingState> = flow {
        isCancelled = false
        val startTime = System.currentTimeMillis()

        try {
            // 1. Extraire les métadonnées
            emit(ProcessingState.Decoding(ProcessingProgress(0, 0, 0)))

            val metadata = extractMetadata(videoRes)
            val totalFrames = metadata.totalFrames
            val frameRate = metadata.frameRate
            val durationUs = metadata.durationUs

            // 2. Créer le fichier de sortie
            val outputFile = createOutputFile()

            // 3. Initialiser l'encodeur et le muxer
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val encoder = createEncoder(metadata.width, metadata.height, frameRate)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            var videoTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            // 4. Initialiser le retriever pour extraire les frames
            val retriever = MediaMetadataRetriever()
            val afd = context.resources.openRawResourceFd(videoRes)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            // 5. Traiter frame par frame
            val microsecondsPerFrame = 1_000_000L / frameRate
            var frameIndex = 0
            var currentTimeUs = 0L

            while (currentTimeUs < durationUs && coroutineContext.isActive && !isCancelled) {

                // Extraire le frame à ce timestamp
                val originalBitmap = retriever.getFrameAtTime(
                    currentTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (originalBitmap != null) {

                    // Appliquer le shader
                    val processedBitmap = applyShaderToBitmap(originalBitmap, config)
                    originalBitmap.recycle()

                    // Dessiner sur l'input surface de l'encodeur
                    val canvas = inputSurface.lockCanvas(null)
                    canvas.drawBitmap(processedBitmap, 0f, 0f, null)
                    inputSurface.unlockCanvasAndPost(canvas)
                    processedBitmap.recycle()

                    // Encoder le frame
                    val result = drainEncoder(encoder, bufferInfo, muxer, videoTrackIndex, muxerStarted, false)
                    if (!muxerStarted && result.trackIndex >= 0) {
                        videoTrackIndex = result.trackIndex
                        muxerStarted = true
                    }

                    // Émettre progression **allégée** toutes les 10 frames
                    if (frameIndex % 10 == 0 || frameIndex == totalFrames - 1) {
                        val progress = ProcessingProgress(
                            currentFrame = frameIndex,
                            totalFrames = totalFrames,
                            elapsedTimeMs = System.currentTimeMillis() - startTime
                        )
                        emit(ProcessingState.Decoding(progress))
                        emit(ProcessingState.Processing(progress))
                        emit(ProcessingState.Encoding(progress))
                    }

                    frameIndex++
                }

                currentTimeUs += microsecondsPerFrame
            }

            // 6. Finaliser l'encodage
            encoder.signalEndOfInputStream()
            drainEncoder(encoder, bufferInfo, muxer, videoTrackIndex, muxerStarted, true)

            // 7. Cleanup
            retriever.release()
            encoder.stop()
            encoder.release()
            inputSurface.release()
            muxer.stop()
            muxer.release()

            // Reset shader cache (optionnel mais propre)
            runtimeShader = null
            shaderPaint = null
            cachedWidth = -1
            cachedHeight = -1

            if (isCancelled) {
                outputFile.delete()
                emit(ProcessingState.Error("Processing cancelled by user"))
            } else {
                // 8. Succès !
                val totalTime = System.currentTimeMillis() - startTime
                emit(ProcessingState.Success(Uri.fromFile(outputFile), totalTime))
            }

        } catch (e: Exception) {
            emit(ProcessingState.Error(
                message = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    /**
     * Applique le shader LCD sur un bitmap
     */
    private suspend fun applyShaderToBitmap(
        inputBitmap: Bitmap,
        config: ShaderConfig
    ): Bitmap = withContext(Dispatchers.Default) {

        val width = inputBitmap.width
        val height = inputBitmap.height

        ensureShader(width, height)

        val shader = runtimeShader!!
        val paint = shaderPaint!!

        // Input bitmap shader (ça change à chaque frame)
        val bitmapShader = BitmapShader(inputBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setInputBuffer("inputFrame", bitmapShader)

        // Uniforms dynamiques
        shader.setFloatUniform("scaleFactor", config.scaleFactor)
        shader.setFloatUniform("ditheringStrength", config.ditheringStrength)
        shader.setFloatUniform("gridIntensity", config.gridIntensity)
        shader.setFloatUniform("gridSize", config.gridSize)

        // Palette
        config.palette.forEachIndexed { index, color ->
            shader.setFloatUniform(
                "palette$index",
                color.red, color.green, color.blue, color.alpha
            )
        }

        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        outputBitmap
    }

    /**
     * Extrait les métadonnées de la vidéo
     */
    private fun extractMetadata(@RawRes videoRes: Int): VideoMetadata {
        val extractor = MediaExtractor()
        val afd = context.resources.openRawResourceFd(videoRes)
        extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()

        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                trackIndex = i
                break
            }
        }

        require(trackIndex >= 0) { "No video track found" }

        val format = extractor.getTrackFormat(trackIndex)
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        val frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            format.getInteger(MediaFormat.KEY_FRAME_RATE)
        } else 30 // Fallback

        val totalFrames = ((durationUs / 1_000_000.0) * frameRate).toInt()

        extractor.release()

        return VideoMetadata(width, height, durationUs, frameRate, totalFrames)
    }

    /**
     * Crée l'encodeur H.264
     */
    private fun createEncoder(width: Int, height: Int, frameRate: Int): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000) // 6 Mbps
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return encoder
    }

    /**
     * Draine l'encodeur et écrit dans le muxer
     */
    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        trackIndex: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean
    ): EncoderResult {
        var currentTrackIndex = trackIndex
        var currentMuxerStarted = muxerStarted

        val timeout = if (endOfStream) 0L else 10_000L

        while (true) {
            val outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, timeout)

            when {
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (currentMuxerStarted) {
                        throw RuntimeException("Format changed after muxer started")
                    }
                    val newFormat = encoder.outputFormat
                    currentTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    currentMuxerStarted = true
                }

                outputBufferId >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferId)
                        ?: throw RuntimeException("Null output buffer")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && currentMuxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrackIndex, outputBuffer, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferId, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }

                else -> break
            }
        }

        return EncoderResult(currentTrackIndex, currentMuxerStarted)
    }

    /**
     * Crée le fichier de sortie dans Movies/LCDify/
     */
    private fun createOutputFile(): File {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val lcdifyDir = File(moviesDir, "LCDify")

        if (!lcdifyDir.exists()) {
            lcdifyDir.mkdirs()
        }

        val fileName = "lcdify_${System.currentTimeMillis()}.mp4"
        return File(lcdifyDir, fileName)
    }

    /**
     * Annule le traitement en cours
     */
    fun cancel() {
        isCancelled = true
    }

    // Data classes internes
    private data class VideoMetadata(
        val width: Int,
        val height: Int,
        val durationUs: Long,
        val frameRate: Int,
        val totalFrames: Int
    )

    private data class EncoderResult(
        val trackIndex: Int,
        val muxerStarted: Boolean
    )
}
