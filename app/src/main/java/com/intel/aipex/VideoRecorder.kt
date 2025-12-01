package com.intel.aipex

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log

class VideoRecorder(
    private val outputPath: String,
    private val width: Int,
    private val height: Int
) {
    private val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val encoder: MediaCodec =
        MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private var trackIndex = -1
    private var isStarted = false
    init {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width, height
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )

        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    fun encodeFrame(bitmap: Bitmap) {
        val yuvData = bitmapToNV21(bitmap) // YUV 변환 함수 필요
        val inputBufferIndex = encoder.dequeueInputBuffer(10000)

        if (inputBufferIndex >= 0) {
            val buffer = encoder.getInputBuffer(inputBufferIndex)
            buffer?.clear()
            buffer?.put(yuvData)
            encoder.queueInputBuffer(
                inputBufferIndex,
                0,
                yuvData.size,
                System.nanoTime() / 1000,
                0
            )
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)

        while (outputIndex >= 0) {
            val encodeBuffer = encoder.getOutputBuffer(outputIndex)
            // 1. 초기 CODEC_CONFIG 처리
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                // 이 버퍼는 트랙 정보이므로 Muxer에 쓰지 않고, Muxer를 시작합니다.
                if (!isStarted) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    isStarted = true
                    Log.d("VideoRecorder", "Muxer started and track added.")
                }
                encoder.releaseOutputBuffer(outputIndex, false)
                outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                continue // 다음 버퍼 처리
            }
            // 2. Muxer가 시작되지 않았다면 데이터 버퍼는 무시
            // (이 코드는 CODEC_CONFIG 처리 후 실행되어야 합니다.)
            if (!isStarted) {
                // Muxer가 아직 시작되지 않았다면, config 프레임이 오지 않았거나 오류가 발생한 것.
                // 이 프레임은 유효한 데이터가 아니므로 릴리즈만 하고 다음 프레임을 기다립니다.
                encoder.releaseOutputBuffer(outputIndex, false)
                outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                continue
            }
            // 3. 실제 데이터 버퍼 쓰기
            if (bufferInfo.size > 0 && encodeBuffer != null) {
                encodeBuffer.position(bufferInfo.offset)
                encodeBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(trackIndex, encodeBuffer, bufferInfo)
            }
            // 4. 버퍼 릴리즈 및 다음 버퍼 대기
            encoder.releaseOutputBuffer(outputIndex, false)
            outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
        }
    }
    fun bitmapToNV21(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height
        var r: Int
        var g: Int
        var b: Int
        var y: Int
        var u: Int
        var v: Int

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[j * width + i]
                r = (color shr 16) and 0xFF
                g = (color shr 8) and 0xFF
                b = color and 0xFF
                // --- YUV 변환 공식 ---
                y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                // --- 범위 보정 ---
                y = y.coerceIn(0, 255)
                u = u.coerceIn(0, 255)
                v = v.coerceIn(0, 255)
                // --- Y 저장 ---
                yuv[yIndex++] = y.toByte()
                // --- U, V (4픽셀마다 저장: NV12 형식) ---
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = u.toByte() // U 먼저
                    yuv[uvIndex++] = v.toByte() // 그 다음 V
                }
            }
        }
        return yuv
    }
    fun stop() {
        try{
            if(isStarted){
                encoder.stop()
                encoder.release()
                muxer.stop()
                muxer.release()
            } else {
                encoder.stop()
                encoder.release()
                muxer.release()
            }
        } catch (e: IllegalStateException) {
            Log.e("Recorder", "Stop failed: ${e.message}")
            try {
                muxer.release()
            }catch (_:Exception) { }
        }
    }
}
