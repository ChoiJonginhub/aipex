import android.util.Log
import compute.ComputeServiceGrpcKt
import data_types.Command
import data_types.DetectionResult
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.Closeable
import java.util.concurrent.TimeUnit

class GrpcClient() : Closeable {
    private val channel: ManagedChannel =
        ManagedChannelBuilder.forAddress("ipv4:192.168.100.54", 50051)
            .usePlaintext()
            .build()
    private val stub by lazy { ComputeServiceGrpcKt.ComputeServiceCoroutineStub(channel) }
    private val sendChannel = Channel<Command>(Channel.UNLIMITED)
    private var collectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** gRPC 스트리밍 세션 오픈 */
    fun startStream() {
        collectJob = scope.launch {
            val requestFlow = sendChannel.consumeAsFlow()
            val responseFlow = stub.datastream(requestFlow)
            responseFlow.collect { msg ->
                Log.d("GrpcClient", "Server message: $msg")
            }
        }
    }
    /** Navigation 정보 전송 함수 */
    fun sendNavigationInfo(instruction: String?, distance: Int?) {
        scope.launch {
            val json = """
                {"instruction":"${instruction ?: ""}", "remaining_distance": ${distance ?: -1}}
            """
            val cmd = Command.newBuilder()
                .setDetectionResult(DetectionResult.newBuilder().setJson(json).build())
                .build()
            sendChannel.send(cmd)
        }
    }
    override fun close() {
        collectJob?.cancel()
        sendChannel.close()
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
