package com.intel.aipex
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.intel.aipex.ui.theme.AipexTheme
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.LocationTrackingMode
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.PolylineOverlay
import com.naver.maps.map.util.FusedLocationSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
class MainActivity : ComponentActivity() {
    private lateinit var locationSource: FusedLocationSource
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationSource = FusedLocationSource(
            this,
            LOCATION_PERMISSION_REQUEST_CODE
        )
        locationSource.isCompassEnabled=true
        enableEdgeToEdge()
        setContent {
            AipexTheme {
                MainScreen(locationSource)
            }
        }
    }
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "홈", Icons.Default.LocationOn)
    object Search : Screen("search", "검색", Icons.Default.Search)
    object Recording : Screen("recording", "녹화", Icons.Default.PlayArrow)
}
@Composable
fun MainScreen(locationSource: FusedLocationSource) {
    val context = LocalContext.current.applicationContext
    val navController = rememberNavController()
    val mapModel: MapSearchViewModel = viewModel(
        factory = MapSearchViewModelFactory(context)
    )
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                SplashScreen {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            }
            composable("navigation") { NavigationScreen(navController = navController, locationSource = locationSource, mapModel = mapModel) }
            composable(Screen.Home.route) { HomeScreen(locationSource = locationSource, mapModel = mapModel) }
            composable(Screen.Search.route) { SearchScreen(navController = navController, locationSource = locationSource, mapModel = mapModel) }
            composable(Screen.Recording.route) { RecordingScreen(mapModel = mapModel) }
        }
    }
}
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500L)  // 1.5초 로고 표시
        onTimeout()
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.logo_2),
            contentDescription = "App Logo",
            modifier = Modifier.size(400.dp)
        )
    }
}
@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun HomeScreen(locationSource: FusedLocationSource, mapModel: MapSearchViewModel) {
    // 2. 버튼 텍스트 상태 관리 (ON/OFF 상태를 시각적으로 보여주기 위함)
    var isWakeUpActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        mapModel.initWakeGrpc()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NaverMap(
            locationSource = locationSource,
            properties = MapProperties(
                locationTrackingMode = LocationTrackingMode.Follow,
            ),
            uiSettings = MapUiSettings(
                isLocationButtonEnabled = true,
            )
        )
        // 4. 버튼 추가 (지도 위에 오버레이)
        Button(
            onClick = {
                // 버튼 클릭 시 상태 토글
                isWakeUpActive = !isWakeUpActive
                // gRPC 호출: 상태에 따라 다른 스크립트를 호출한다고 가정
                if (isWakeUpActive) {
                    // "ON" 상태일 때: 스크립트 실행 요청
                    mapModel.sendWakeSign()
                    Log.d("GrpcButton", "WakeUp ON (gRPC Triggered)")
                } else {
                    // "OFF" 상태일 때: 스크립트 중지 요청 (만약 서버에 중지 기능이 있다면)
                    mapModel.sendWakeSign()
                    Log.d("GrpcButton", "WakeUp OFF (gRPC Triggered)")
                }
            },
            // 버튼을 화면 오른쪽 하단에 배치
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            // 상태에 따라 버튼 텍스트 변경
            Text(if (isWakeUpActive) "HMD OFF" else "HMD ON")
        }
    }
}
@Composable
fun SearchScreen(
    navController: NavController,
    locationSource: FusedLocationSource,
    mapModel: MapSearchViewModel
) {
    val openResults by mapModel.openSearchResult.collectAsState()
    val geoResults by mapModel.geocodeResult.collectAsState()
    val routeResult by mapModel.routeResult.collectAsState()
    var query by remember { mutableStateOf("") }
    // 위치 제공자
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var dialogStage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val loc = locationSource.lastLocation
            loc?.let {
                currentLocation = LatLng(it.latitude, it.longitude)
            }
        } catch (_: SecurityException) {
            // 권한 없으면 처리
        }
    }
    /* 길찾기 결과 NavigationScreen 이동 */
    LaunchedEffect(routeResult) {
        if (routeResult != null && currentLocation != null) {
            navController.navigate("navigation")
        }
    }
    // 전체 화면 UI
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 🔍 검색 입력창
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("어디로 갈까요?") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "search")
            }
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { mapModel.searchPlace(query) },modifier = Modifier.fillMaxWidth()) {
            Text("검색")
        }
        Spacer(Modifier.height(10.dp))
        // 검색 결과 리스트 (Open → Geo)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // OpenAPI 결과 표시
            item {
                if (openResults.isNotEmpty()) {
                    Text("검색 결과", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(openResults) { item ->
                SearchResultCard(item = item) {
                    // 주소 선택 → geocode 실행
                    val address = item.address ?: item.roadAddress ?: return@SearchResultCard
                    mapModel.geocode(address)
                    dialogStage = 2
                }
            }
        }
    }
    // route search dialog
    if (dialogStage == 2 && geoResults.isNotEmpty()) {
        val dest = geoResults[0]
        AlertDialog(
            onDismissRequest = { dialogStage = 0 },
            title = { Text("길찾기") },
            text = {
                Text("해당 위치로 안내해 드릴까요?\n")
            },
            confirmButton = {
                TextButton(onClick = {
                    if(currentLocation != null){
                        mapModel.requestRouteTo(
                            startLocation = currentLocation!!,
                            destLat = dest.y?.toDoubleOrNull() ?: 0.0,
                            destLng = dest.x?.toDoubleOrNull() ?: 0.0
                        )
                        dialogStage = 0
                    }
                }) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogStage = 0 }) {
                    Text("취소")
                }
            }
        )
    }
}
@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun NavigationScreen(
    navController: NavController,
    locationSource: FusedLocationSource,
    mapModel: MapSearchViewModel
) {
    val routeResult by mapModel.routeResult.collectAsState()
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    val nextGuide by mapModel.nextGuide.collectAsState()
    val nextGuidePoint by mapModel.nextGuidePoint.collectAsState()
    val route = routeResult?.path?.first()
    var heading by remember { mutableFloatStateOf(0f) }
    var gpsHeading by remember { mutableFloatStateOf(0f) }
    var prevLocation by remember { mutableStateOf<Location?>(null) }
    var speed by remember { mutableFloatStateOf(0f) }
    val destinationPoint = remember(routeResult) {
        routeResult?.path?.lastOrNull()
    }
    val targetBearing = remember(currentLocation, nextGuidePoint) {
        if (currentLocation != null && nextGuidePoint != null) {
            calculateBearing(
                currentLocation!!.latitude,
                currentLocation!!.longitude,
                nextGuidePoint!![1],
                nextGuidePoint!![0]
            )
        } else null
    }
    val directionDelta = remember(heading, targetBearing) {
        if (targetBearing != null)
            getDirectionDelta(heading, targetBearing)
        else 0f
    }
    if (route == null) {
        Text("경로 데이터 없음")
        return
    }
    LaunchedEffect(Unit) {
        mapModel.initGrpc()
        while (true) {
            try {
                val loc = locationSource.lastLocation
                if (loc != null) {
                    val lat = loc.latitude
                    val lng = loc.longitude
                    // 이동속도 계산
                    prevLocation?.let { prev ->
                        val d = prev.distanceTo(loc) // 미터
                        val t = (loc.time - prev.time) / 1000f // 초
                        if (t > 0 && d < 50) { // 순간 튐 방지
                            speed = d / t // m/s
                        }
                    }
                    prevLocation = loc
                    currentLocation?.let {
                        gpsHeading = calculateHeading(
                            prevLat = it.latitude, it.longitude,
                            currLat = lat, currLng = lng
                        )
                    }
                    currentLocation = LatLng(lat, lng)
                    // ViewModel 현재 위치 저장
                    mapModel.updateCurrentLocation(lat, lng)
                    // 다음 안내 갱신
                    mapModel.updateNextGuide(lat, lng)
                    heading = loc.bearing
                }
            } catch (_: SecurityException) { }
            delay(1000L) // 1초마다 GPS 체크
        }
    }
    //다음 지점 남은 거리 계산
    val remainingDistance by remember(currentLocation, nextGuidePoint) {
        derivedStateOf {
            if (currentLocation != null && nextGuidePoint != null) {
                val start = Location("").apply {
                    latitude = currentLocation!!.latitude
                    longitude = currentLocation!!.longitude
                }
                val end = Location("").apply {
                    latitude = nextGuidePoint!![1]
                    longitude = nextGuidePoint!![0]
                }
                start.distanceTo(end).toInt()
            } else null
        }
    }
    // 목적지까지 남은 거리 & ETA 계산
    val etaMinutes = remember(currentLocation, speed) {
        if (destinationPoint != null && currentLocation != null && speed > 0.5f) {
            val dest = Location("").apply {
                latitude = destinationPoint[1]
                longitude = destinationPoint[0]
            }
            val curr = Location("").apply {
                latitude = currentLocation!!.latitude
                longitude = currentLocation!!.longitude
            }
            val remain = curr.distanceTo(dest) // meters
            val sec = remain / speed
            (sec / 60).toInt()
        } else null
    }
    LaunchedEffect(nextGuide, remainingDistance) {
        if(nextGuide!=null && remainingDistance!=null) {
            mapModel.sendGrpc(
                instruction = nextGuide?.instructions,
                distance = remainingDistance,
                heading = gpsHeading.toInt(),
                speed = speed,
                eta = etaMinutes,
                type = nextGuide?.type
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp)
    ) {
        Text("Navigation 안내", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            text = nextGuide?.instructions ?: "경로를 따라 이동",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (remainingDistance != null)
                "다음 안내 지점 ${remainingDistance}m 남음"
            else
                "거리 계산 중...",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(text = "현재 방향(센서): ${heading.toInt()}°")
        Text(text = "현재 방향(gps): ${gpsHeading.toInt()}°")
        DirectionArrow(directionDelta)
        Spacer(Modifier.height(5.dp))
        Box(modifier = Modifier.height(300.dp) , contentAlignment = Alignment.Center){
            NaverMap(
                locationSource = locationSource,
                properties = MapProperties(
                    locationTrackingMode = LocationTrackingMode.Face,
                ),
                uiSettings = MapUiSettings(
                    isScrollGesturesEnabled = false,
                    isZoomGesturesEnabled = false,
                    isTiltGesturesEnabled = false,
                    isRotateGesturesEnabled = false,
                    isStopGesturesEnabled = false,
                    isScaleBarEnabled = false,
                    isZoomControlEnabled = false
                )
            ){
                // Polyline 표시
                routeResult?.path?.let { path ->
                    val latLngList = path.map { LatLng(it[1], it[0]) } // [lat, lng] 순서로 변환
                    PolylineOverlay(
                        coords = latLngList,
                        color = Color.Blue
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        // 속도
        Text("현재 속도: ${"%.1f".format(speed * 3.6f)} km/h")
        // ETA
        Text("ETA: ${etaMinutes ?: "--"} 분")
        Spacer(Modifier.height(5.dp))
        Button(onClick = {
            mapModel.clearRoute()
            navController.popBackStack()
        }) {
            Text("종료")
        }
    }
}
@Composable
fun RecordingScreen(mapModel: MapSearchViewModel) {
    val context = LocalContext.current.applicationContext
    val frameBitmap by mapModel.currentFrame.collectAsState()
    var isRecording by remember { mutableStateOf(false) }
    val outputPath = mapModel.createVideoFile()

    val snackbarHostState = remember { SnackbarHostState() } // Material3용
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        mapModel.startVideoStream()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            // 영상 출력
            if (frameBitmap != null) {
                Image(
                    bitmap = frameBitmap!!.asImageBitmap(),
                    contentDescription = "Camera Stream",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("영상 수신 대기중...", color = Color.Gray)
            }

            // 녹화 버튼
            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                onClick = {
                    if (!isRecording) {
                        val path = outputPath
                        val w = frameBitmap?.width ?: 640
                        val h = frameBitmap?.height ?: 480
                        mapModel.startRecording(path, w, h)

                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("녹화를 시작합니다.")
                        }
                    } else {
                        mapModel.stopRecording()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("녹화를 종료했습니다.")
                        }
                    }
                    isRecording = !isRecording
                }
            ) {
                if (isRecording) {
                    Image(
                        painter = painterResource(id = R.drawable.stop),
                        contentDescription = "Stop",
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.record),
                        contentDescription = "Record",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Home, Screen.Search, Screen.Recording
    )
    NavigationBar {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(Screen.Home.route) { saveState = true }
                    }
                },
                icon = {
                    Icon(screen.icon, contentDescription = screen.label)
                },
                label = { Text(screen.label) }
            )
        }
    }
}
@Composable
fun SearchResultCard(
    item: OpenSearchItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title.replace("<b>", "").replace("</b>", ""),
                style = MaterialTheme.typography.titleMedium
            )
            item.category?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Spacer(Modifier.height(6.dp))
            item.roadAddress?.let {
                Text(
                    text = "도로명: $it",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item.address?.let {
                Text(
                    text = "지번: $it",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
fun calculateHeading(
    prevLat: Double, prevLng: Double, currLat: Double, currLng: Double
): Float {
    val lat1 = Math.toRadians(prevLat)
    val lon1 = Math.toRadians(prevLng)
    val lat2 = Math.toRadians(currLat)
    val lon2 = Math.toRadians(currLng)
    val dLon = lon2 - lon1
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) -
            sin(lat1) * cos(lat2) * cos(dLon)
    var bearing = Math.toDegrees(atan2(y, x))
    // 0~360 범위로 변환
    if (bearing < 0) {
        bearing += 360.0
    }
    return bearing.toFloat()
}
fun calculateBearing(
    currentLat: Double, currentLng: Double, targetLat: Double, targetLng: Double
): Float {
    val start = Location("").apply {
        latitude = currentLat
        longitude = currentLng
    }
    val end = Location("").apply {
        latitude = targetLat
        longitude = targetLng
    }
    return start.bearingTo(end) // 결과: 0~360 (북=0, 동=90, 남=180, 서=270)
}
fun getDirectionDelta(userHeading: Float, targetBearing: Float): Float {
    var diff = (targetBearing - userHeading + 360) % 360
    if (diff > 180) diff -= 360   // -180 ~ 180 범위로 조정
    return diff
}
@Composable
fun DirectionArrow(directionDelta: Float) {
    Icon(
        painter = painterResource(id = R.drawable.arrows),
        contentDescription = "Direction Arrow",
        modifier = Modifier
            .size(60.dp)
            .graphicsLayer {
                rotationZ = directionDelta  // 화살표 회전
            }
    )
}
