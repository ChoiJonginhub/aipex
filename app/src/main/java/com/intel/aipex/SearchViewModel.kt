package com.intel.aipex

import GrpcClient
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naver.maps.geometry.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapSearchViewModel(
    private val openRepo: OpenSearchRepository = OpenSearchRepository(),
    private val geocodeRepo: SearchRepository = SearchRepository(),
    private val directionRepo: DirectionRepository = DirectionRepository()
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

    private var currentGuideIndex = 0

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

    fun initGrpc() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                grpcClient = GrpcClient()
                grpcClient?.startStream()
            } catch (e: Exception) {
                Log.e("NavigationViewModel", "gRPC init failed: $e")
            }
        }
    }
    fun sendGrpc(instruction: String?, distance: Int?) {
        grpcClient?.sendNavigationInfo(instruction, distance)
    }

    override fun onCleared() {
        grpcClient?.close()
        super.onCleared()
    }
}
