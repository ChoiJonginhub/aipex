package com.intel.aipex

import android.util.Log
import wakemeup.WakeUpServiceGrpcKt
import wakemeup.Wakeup
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.TimeUnit

class WakeGrpcClient(
    private val host: String,
    private val port: Int
)  : Closeable {
    private val channel: ManagedChannel =
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
    private val stub by lazy { WakeUpServiceGrpcKt.WakeUpServiceCoroutineStub(channel) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun sendSign() { // 필요한 인자 추가
        scope.launch {
            try {
                val request = Wakeup.WakeUpRequest.newBuilder()
                    .setScriptName("")
                    .setArgs("")
                    .build()
                val response = stub.triggerScript(request) // proto에 정의된 'TriggerScript' 호출
                Log.d("WakeupClient", "gRPC Response: success=${response.success}, msg=${response.message}, pid=${response.processId}")
            } catch (e: Exception) {
                // Android Logcat에서는 e.message 대신 e.toString()을 사용하여 전체 예외 정보를 확인하는 것이 좋습니다.
                Log.e("WakeupClient", "gRPC error: ${e.toString()}", e)
            }
        }
    }
    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}