package com.intel.aipex

import android.location.Location
import android.os.Bundle
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
import androidx.compose.ui.graphics.vector.ImageVector
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

class MainActivity : ComponentActivity() {
    private lateinit var locationSource: FusedLocationSource
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationSource = FusedLocationSource(
            this,
            LOCATION_PERMISSION_REQUEST_CODE
        )
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
    val navController = rememberNavController()
    val mapModel: MapSearchViewModel = viewModel()
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
            composable(Screen.Home.route) { HomeScreen(locationSource = locationSource) }
            composable(Screen.Search.route) { SearchScreen(navController = navController, locationSource = locationSource, mapModel = mapModel) }
            composable("navigation") { NavigationScreen(navController = navController, locationSource = locationSource, mapModel = mapModel) }
            composable(Screen.Recording.route) { RecordingScreen() }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500L)  // 1.5초 로고 표시
        onTimeout()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_2),
            contentDescription = "App Logo",
            modifier = Modifier.size(300.dp)
        )
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun HomeScreen(locationSource: FusedLocationSource,) {
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
    var dialogStage by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            // lastLocation을 직접 가져오기
            val loc = locationSource.lastLocation
            loc?.let {
                currentLocation = LatLng(it.latitude, it.longitude)
            }
        } catch (e: SecurityException) {
            // 권한 없으면 처리
        }
    }
    /* 🔥 길찾기 결과가 들어오면 NavigationScreen으로 이동 */
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
            placeholder = { Text("검색어를 입력하세요") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "search")
            }
        )
        Spacer(Modifier.height(10.dp))
        // 🔍 검색 버튼
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
                    // 주소 선택 → 지오코드 실행
                    val address = item.address ?: item.roadAddress ?: return@SearchResultCard
                    mapModel.geocode(address)
                    dialogStage = 2
                }
            }
            // ─────────────── 길찾기 결과 ───────────────
            /*item {
                Spacer(Modifier.height(10.dp))
                if (geoResults.isNotEmpty() && routeResult != null) {
                    DirectionResultCard(item = routeResult!!)
                }
            }*/
        }
    }
    // 길찾기 다이얼로그
    if (dialogStage == 2 && geoResults.isNotEmpty()) {
        val dest = geoResults[0]
        AlertDialog(
            onDismissRequest = { dialogStage = 0 },
            title = { Text("길찾기") },
            text = {
                Text("해당 위치까지 길찾기를 실행할까요?\n")
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
    if (route == null) {
        Text("경로 데이터 없음")
        return
    }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val loc = locationSource.lastLocation
                if (loc != null) {
                    val lat = loc.latitude
                    val lng = loc.longitude
                    currentLocation = LatLng(lat, lng)
                    // ViewModel에도 현재 위치 저장
                    mapModel.updateCurrentLocation(lat, lng)
                    // 다음 안내 갱신
                    mapModel.updateNextGuide(lat, lng)
                }
            } catch (_: SecurityException) { }
            delay(1000L) // 1초마다 GPS 체크
        }
    }
    // 🔥 다음 안내지점까지 남은 거리 계산
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
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp)
    ) {
        Text("🚗 네비게이션 안내", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        // 🔥 다음 안내 문구
        Text(
            text = nextGuide?.instructions ?: "경로를 따라 이동하세요",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (remainingDistance != null)
                "다음 안내 지점까지 ${remainingDistance}m 남음"
            else
                "거리 계산 중...",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(20.dp))
        Box(modifier = Modifier.height(300.dp), contentAlignment = Alignment.Center){
            NaverMap(
                locationSource = locationSource,
                properties = MapProperties(
                    locationTrackingMode = LocationTrackingMode.Face,
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
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            mapModel.clearRoute()
            navController.popBackStack()
        }) {
            Text("종료")
        }
    }
}
@Composable
fun RecordingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("비디오 녹화 관련 기능 제공할 예정. 파이에서 스트리밍하는 영상 수신해서 녹화 후 저장")
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Home,
        Screen.Search,
        Screen.Recording
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

@Composable
fun DirectionResultCard(
    item: Traoptimal
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "경로 탐색 결과",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "거리: ${item.summary.distance} m",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "도착 예상 시간: ${(item.summary.duration / 60000)} 분",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "경로 안내",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(6.dp))
            item.guide.forEach { g ->
                Text(
                    text = "- ${g.instructions} (${g.distance}m)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
