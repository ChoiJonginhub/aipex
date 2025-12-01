package com.intel.aipex

import GrpcClient
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naver.maps.geometry.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapSearchViewModel(
    context: Context,
    private val openRepo: OpenSearchRepository = OpenSearchRepository(context),
    private val geocodeRepo: SearchRepository = SearchRepository(context),
    private val directionRepo: DirectionRepository = DirectionRepository(context)
) : ViewModel() {
    // 1. 검색 결과(OpenAPI)
    private val _openSearchResult = MutableStateFlow<List<OpenSearchItem>>(emptyList())
    val openSearchResult: StateFlow<List<OpenSearchItem>> = _openSearchResult
    // 2. 지오코드 결과(위경도 리스트)
    private val _geocodeResult = MutableStateFlow<List<GeocodeAddress>>(emptyList())
    val geocodeResult: StateFlow<List<GeocodeAddress>> = _geocodeResult
    // 3. 경로 결과(Directions)
    private val _routeResult = MutableStateFlow<Traoptimal?>(null)
    val routeResult: StateFlow<Traoptimal?> = _routeResult
    // 현재 위치 보관
    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation
    // 다음 경로 안내
    private val _nextGuide = MutableStateFlow<Guidence?>(null)
    val nextGuide: StateFlow<Guidence?> = _nextGuide
    private val _nextGuidePoint = MutableStateFlow<List<Double>?>(null)
    val nextGuidePoint: StateFlow<List<Double>?> = _nextGuidePoint
    // video recording
    // 최신 프레임을 보관하는 상태
    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame

    private var currentGuideIndex = 0
    //grpc setting
    //private var host = "192.168.137.184"//sung
    private var host = "10.42.0.1"//aipexHs
    private var port = 50052
    //private var videoHost = "192.168.137.195"
    private var videoHost = "10.42.0.128"
    private var videoPort = 50055
    private var wakeupHost = "10.42.0.128"
    private var wakeupPort = 50050

    fun updateCurrentLocation(lat: Double, lng: Double) {
        _currentLocation.value = lat to lng
    }

    /**
     * ★ 1단계: OpenAPI 장소 검색
     */
    fun searchPlace(query: String) {
        viewModelScope.launch {
            val result = openRepo.search(query)
            _openSearchResult.value = result
        }
    }

    /**
     * ★ 2단계: 지번주소 기반 Geocode로 위경도 변환
     */
    fun geocode(address: String) {
        viewModelScope.launch {
            val result = geocodeRepo.searchLocation(address)
            _geocodeResult.value = result
        }
    }

    /**
     * ★ 3단계: 현재 위치 → 목적지 길찾기
     */
    fun requestRouteTo(startLocation: LatLng, destLat: Double, destLng: Double) {
        updateCurrentLocation(startLocation.latitude, startLocation.longitude)
        viewModelScope.launch {
            val (startLat, startLng) = _currentLocation.value
                ?: return@launch
            val result = directionRepo.getRoute(
                startLng = startLng,
                startLat = startLat,
                endLng = destLng,
                endLat = destLat
            )
            _routeResult.value = result
        }
    }
    //다음 안내 갱신
    fun updateNextGuide(currentLat: Double, currentLng: Double) {
        val route = _routeResult.value ?: return
        val guideList = route.guide
        val path = route.path
        if (currentGuideIndex >= guideList.size) {
            _nextGuide.value = null
            return
        }
        val currentGuide = guideList[currentGuideIndex]
        val guidePointIdx = currentGuide.pointIndex
        val p = path.getOrNull(guidePointIdx) ?: return
        // 현재 안내 지점의 좌표
        val guideLat = p[1]
        val guideLng = p[0]
        // 거리 계산
        val distance = haversine(currentLat, currentLng, guideLat, guideLng)
        // 🔥 30m 이내로 접근하면 다음 안내로 이동
        if (distance < 30.0) {
            currentGuideIndex++
            if (currentGuideIndex < guideList.size) {
                _nextGuide.value = guideList[currentGuideIndex]
                _nextGuidePoint.value = path[currentGuideIndex]
            } else {
                _nextGuide.value = null // 안내 종료
                _nextGuidePoint.value = null
            }
        } else {
            // 아직 해당 안내 지점 도달 전 → 그대로 유지
            _nextGuide.value = currentGuide
            _nextGuidePoint.value = p
        }
    }
    //거리 계산
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
    fun clearRoute() {
        _routeResult.value = null
        _nextGuide.value = null
    }
    //grpc connect
    private var grpcClient: GrpcClient? = null
    private var wakeClient: WakeGrpcClient? = null
    fun initGrpc() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                grpcClient = GrpcClient(host, port)
            } catch (e: Exception) {
                Log.e("NavigationViewModel", "gRPC init failed: $e")
            }
        }
    }
    fun initWakeGrpc() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                wakeClient = WakeGrpcClient(wakeupHost, wakeupPort)
            } catch (e: Exception) {
                Log.e("NavigationViewModel", "gRPC wake init failed: $e")
            }
        }
    }
    fun sendWakeSign(){
        wakeClient?.sendSign()
    }
    fun sendGrpc(instruction: String?, distance: Int?, heading: Int?, speed: Float?, eta: Int?, type: Int?) {
        grpcClient?.sendNavigationInfo(instruction, distance, heading, speed, eta, type)
    }
    // video receiver
    private var vGrpcClient: VideoGrpcClient? = null
    private var recorder: VideoRecorder? = null
    private var isRecording = false
    fun startVideoStream() {
        vGrpcClient?.close()
        vGrpcClient = VideoGrpcClient(videoHost, videoPort)
        vGrpcClient?.startReceiving { bitmap ->
            _currentFrame.value = bitmap   // 프레임 업데이트
            // 녹화 중이면 안전하게 프레임 전송
            if (isRecording) {
                try {
                    recorder?.encodeFrame(bitmap)
                } catch (e: IllegalStateException) {
                    Log.e("VideoRecording", "Encoder state error: ${e.message}")
                }
            }
        }
    }
    fun createVideoFile(): String {
        // MediaStore의 표준 Movies 디렉토리 경로를 가져옵니다. (공개적으로 접근 가능한 경로)
        // API 레벨과 관계없이 Android의 표준 공용 디렉토리입니다.
        val movieDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        // 폴더가 없으면 생성합니다.
        if (!movieDir.exists()) {
            // 생성에 실패하면 예외를 던집니다.
            if (!movieDir.mkdirs()) {
                throw IllegalStateException("Cannot create public Movies directory: ${movieDir.absolutePath}")
            }
        }
        val fileName = "record_${System.currentTimeMillis()}.mp4"
        return File(movieDir, fileName).absolutePath
    }
    /** 녹화 시작 */
    fun startRecording(path: String, width: Int, height: Int) {
        // 기존 recorder 종료
        recorder?.stop()
        // [중요] VideoRecorder 초기화
        recorder = VideoRecorder(path, width, height)
        isRecording = true
        Log.d("MapSearchViewModel", "Recording started. Output Path: $path")
    }
    /** 녹화 종료 */
    fun stopRecording() {
        isRecording = false
        try {
            recorder?.stop()
        } catch (e: IllegalStateException) {
            Log.e("VideoRecording", "Stop failed: ${e.message}")
        } finally {
            recorder = null
        }
    }
    override fun onCleared() {
        try {
            vGrpcClient?.close()
        } catch (_: Exception) {}
        try {
            grpcClient?.close()
        } catch (_: Exception) {}
        try {
            wakeClient?.close()
        } catch (_: Exception) {}
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        super.onCleared()
    }
}
