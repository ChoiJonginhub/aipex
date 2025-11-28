package com.intel.aipex

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer

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
            if (!isStarted) {
                trackIndex = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                isStarted = true
            }
            val encodeBuffer = encoder.getOutputBuffer(outputIndex)
            if (bufferInfo.size > 0 && encodeBuffer != null) {
                encodeBuffer.position(bufferInfo.offset)
                encodeBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(trackIndex, encodeBuffer, bufferInfo)
            }
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
                // --- U, V (4픽셀마다 저장: NV21 형식) ---
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = v.toByte() // V 먼저
                    yuv[uvIndex++] = u.toByte() // 그 다음 U
                }
            }
        }

        return yuv
    }
    fun stop() {
        encoder.stop()
        encoder.release()
        muxer.stop()
        muxer.release()
    }
}
