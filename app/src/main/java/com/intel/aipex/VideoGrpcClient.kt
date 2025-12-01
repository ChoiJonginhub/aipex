package com.intel.aipex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.datastore.core.Closeable
import hud.HubStreaming
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlin.getValue
import hud.HudServiceGrpcKt
import java.util.concurrent.TimeUnit

class VideoGrpcClient(
    private val host: String,
    private val port: Int
) : Closeable {

    private val channel: ManagedChannel =
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()

    private val stub by lazy { HudServiceGrpcKt.HudServiceCoroutineStub(channel) }

    private var collectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** HUD 영상 스트림 받기 */
    fun startReceiving(targetFps: Int = 30, onFrameReceived: (Bitmap) -> Unit) {
        collectJob = scope.launch {
            try {
                // 요청 메시지 생성
                val request = HubStreaming.HudRequest.newBuilder()
                    .setTargetFps(targetFps)
                    .build()

                val responseFlow = stub.streamHud(request)

                responseFlow.collect { frame: HubStreaming.HudFrame ->
                    try {
                        val jpegBytes = frame.jpeg.toByteArray()
                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

                        if (bitmap != null) {
                            onFrameReceived(bitmap)
                        } else {
                            Log.e("VideoGrpc", "Bitmap decode failed")
                        }

                    } catch (e: Exception) {
                        Log.e("VideoGrpc", "Frame decode error: $e")
                    }
                }

            } catch (e: Exception) {
                Log.e("VideoGrpc", "Streaming error: $e")
            }
        }
    }

    override fun close() {
        collectJob?.cancel()
        channel.shutdown().awaitTermination(3, TimeUnit.SECONDS)
    }
}
