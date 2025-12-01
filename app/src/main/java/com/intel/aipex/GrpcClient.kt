import android.util.Log
import app_communication.AppComm
import app_communication.AppCommServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.concurrent.TimeUnit
class GrpcClient(
    private val host: String,
    private val port: Int
)  : Closeable {
    private val channel: ManagedChannel =
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
    private val stub by lazy { AppCommServiceGrpcKt.AppCommServiceCoroutineStub(channel) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    /**
     * Navigation 정보를 JSON 형태로 gRPC 서버로 전달 (Unary RPC)
     */
    fun sendNavigationInfo(instruction: String?, distance: Int?, heading: Int?, speed: Float?, eta: Int?, type: Int?) {
        scope.launch {
            try {
                val json = """
                    {
                      "instruction": "${instruction ?: ""}",
                      "remaining_distance": ${distance ?: -1},
                      "heading": ${heading ?: -1},
                      "speed": ${speed ?: 0.0},
                      "eta": ${eta ?: -1},
                      "type": ${type ?: 0}
                    }
                """.trimIndent()
                val request = AppComm.JSONRequest.newBuilder()
                    .setJsonPayload(json)
                    .build()
                val response = stub.sendJSON(request)
                Log.d("GrpcClient", "gRPC Response: success=${response.success}, msg=${response.message}")
            } catch (e: Exception) {
                Log.e("GrpcClient", "gRPC error: ${e.message}", e)
            }
        }
    }
    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}