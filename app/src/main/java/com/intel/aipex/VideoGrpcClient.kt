package com.intel.aipex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.datastore.core.Closeable
import data_types.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlin.getValue
import compute.VideoRecordServiceGrpcKt
import java.util.concurrent.TimeUnit

class VideoGrpcClient(
    private val host: String,
    private val port: Int
) : Closeable {
    private val channel: ManagedChannel =
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
    private val stub by lazy { VideoRecordServiceGrpcKt.VideoRecordServiceCoroutineStub(channel) }
    private var collectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    /** 서버에서 카메라 프레임 스트림을 받아 처리하는 함수 */
    fun startReceiving(onFrameReceived: (Bitmap) -> Unit) {
        collectJob = scope.launch {
            try {
                // 초기 요청(1개)
                val request = ClientMessage.getDefaultInstance()
                // 서버 스트리밍 시작
                val responseFlow = stub.receiveFrames(request)
                responseFlow.collect { serverMessage ->
                    when (serverMessage.messageTypeCase) {
                        ServerMessage.MessageTypeCase.CAMERA_FRAME -> {
                            val frame = serverMessage.cameraFrame
                            val jpegBytes = frame.imageData.toByteArray()
                            val bitmap = BitmapFactory.decodeByteArray(
                                jpegBytes,
                                0,
                                jpegBytes.size
                            )
                            if (bitmap != null) {
                                onFrameReceived(bitmap)
                            } else {
                                Log.e("VideoGrpc", "Bitmap decode failed")
                            }
                        }
                        else -> {
                            Log.d("VideoGrpc", "Other message: $serverMessage")
                        }
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
