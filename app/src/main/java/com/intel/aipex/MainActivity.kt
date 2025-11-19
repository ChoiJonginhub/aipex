package com.intel.aipex

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.google.android.gms.location.LocationServices
import com.intel.aipex.ui.theme.AipexTheme
import com.naver.maps.geometry.LatLng
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AipexTheme {
                MainScreen()
            }
        }
    }

}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "홈", Icons.Default.Home)
    object Search : Screen("search", "검색", Icons.Default.Search)
    object Settings : Screen("settings", "설정", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
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
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Search.route) { SearchScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
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
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.glaux),
            contentDescription = "App Logo",
            modifier = Modifier.size(160.dp)
        )
    }
}

@Composable
fun HomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home Screen")
    }
}

@Composable
fun SearchScreen(
    searchModel: SearchViewModel = viewModel(),
    directModel: DirectionViewModel = viewModel(),
) {
    val searchResults by searchModel.searchResults.collectAsState()
    val routeResult by directModel.route.collectAsState()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    // 초기 위치: null 이면 카메라 이동하지 않음
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    // 위치 가져오기 (권한이 이미 허용되어 있다고 가정)
    var showDialog by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<GeocodeAddress?>(null) }
    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                }
            }
        } catch (e: SecurityException) {
            // 권한 없을 경우 처리
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 🔍 검색 입력창
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("목표 지점을 입력하세요") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "search"
                )
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 🔍 검색 버튼
        Button(
            onClick = { searchModel.search(query) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("검색")
        }
        Spacer(modifier = Modifier.height(20.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(searchResults) { item ->
                SearchResultCard(item = item) {
                    selectedDestination = item
                    showDialog = true
                }
            }
            item {
                routeResult?.let { result ->
                    DirectionResultCard(item = result)
                }
            }
        }
    }
    // 다이얼로그 UI
    if (showDialog && selectedDestination != null) {
        val dest = selectedDestination!!
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("길찾기") },
            text = { Text("이 주소로 길찾기를 실행할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    directModel.requestRoute(
                        startLat = currentLocation?.latitude ?: 0.0,
                        startLng = currentLocation?.longitude ?: 0.0,
                        endLat = dest.y?.toDoubleOrNull() ?: 0.0,
                        endLng = dest.x?.toDoubleOrNull() ?: 0.0
                    )
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun SettingsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Setting Screen")
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Home,
        Screen.Search,
        Screen.Settings
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
fun SearchResultCard(item: GeocodeAddress,
                     onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            item.roadAddress?.let {
                Text("도로명 주소: $it")
            }
            item.jibunAddress?.let {
                Text("지번 주소: $it")
            }
            Text("위도: ${item.y}")
            Text("경도: ${item.x}")
        }

    }
}

@Composable
fun DirectionResultCard(item: Traoptimal?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("요약: ${item?.summary}")
            Text("경로: ${item?.path}")
        }

    }
}