package com.intel.aipex

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

var open_url = "https://openapi.naver.com/"
var ncp_url = "https://maps.apigw.ntruss.com/"
var open_id = "zaOBCBPWT6Wo4dmJJ6Zm"
var open_secret = "8qIocDkbGA"
var ncp_id = "2uhw3mfawu"
var ncp_secret = "ifkouBlR107SBkygMqgrnTVMu2Lketu9JuwevrLH"

class OpenSearchRepository(private val context: Context) {
    private val networkHelper = NetworkHelper(context)
    // 1. 클라이언트는 네트워크 바인딩 없이 생성
    private val baseClient = OkHttpClient.Builder().build()

    private val api: OpenSearchApi = Retrofit.Builder()
        .baseUrl(open_url)
        // 2. 바인딩이 없는 기본 클라이언트 사용
        .client(baseClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenSearchApi::class.java)
    suspend fun search(query: String): List<OpenSearchItem> {
        // 3. 호출 직전에 사용할 네트워크를 결정
        val useNetwork =
            if (networkHelper.isWifiHasInternet()) null
            else networkHelper.getMobileNetwork()
        // 4. withBoundNetwork 래퍼 함수를 사용하여 API 호출
        return networkHelper.withBoundNetwork(useNetwork) {
            val response = api.search(
                clientId = open_id,
                clientSecret = open_secret,
                query = query
            )
            // 5. 블록 종료 시, 네트워크 바인딩은 자동으로 해제됨 (bindProcessToNetwork(null))
            response.items
        }
    }
}

class SearchRepository(private val context: Context) {
    private val networkHelper = NetworkHelper(context)
    // 1. 클라이언트는 네트워크 바인딩 없이 생성
    private val baseClient = OkHttpClient.Builder().build()

    private val api: SearchApi = Retrofit.Builder()
        .baseUrl(ncp_url)
        // 2. 바인딩이 없는 기본 클라이언트 사용
        .client(baseClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SearchApi::class.java)
    suspend fun searchLocation(query: String): List<GeocodeAddress> {
        // 3. 호출 직전에 사용할 네트워크를 결정
        val useNetwork =
            if (networkHelper.isWifiHasInternet()) null
            else networkHelper.getMobileNetwork()

        // 4. withBoundNetwork 래퍼 함수를 사용하여 API 호출
        return networkHelper.withBoundNetwork(useNetwork) {
            val response = api.geocode(
                clientId = ncp_id,
                clientSecret = ncp_secret,
                query = query
            )
            // 5. 블록 종료 시, 네트워크 바인딩은 자동으로 해제됨
            response.addresses
        }
    }
}

class DirectionRepository(private val context: Context) {
    private val networkHelper = NetworkHelper(context)
    // 1. 클라이언트는 네트워크 바인딩 없이 생성
    private val baseClient = OkHttpClient.Builder().build()

    private val api = Retrofit.Builder()
        .baseUrl(ncp_url)
        // 2. 바인딩이 없는 기본 클라이언트 사용
        .client(baseClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DirectionApi::class.java)
    suspend fun getRoute(
        startLng: Double,
        startLat: Double,
        endLng: Double,
        endLat: Double,
    ): Traoptimal? {
        // 3. 호출 직전에 사용할 네트워크를 결정
        val useNetwork =
            if (networkHelper.isWifiHasInternet()) null
            else networkHelper.getMobileNetwork()

        // 4. withBoundNetwork 래퍼 함수를 사용하여 API 호출
        return networkHelper.withBoundNetwork(useNetwork) {
            val response = api.getDrivingRoute(
                clientId = ncp_id,
                clientSecret = ncp_secret,
                start = "$startLng,$startLat",
                goal = "$endLng,$endLat",
                //option = "traavoidcaronly",
                lang = "en"
            )
            // 5. 블록 종료 시, 네트워크 바인딩은 자동으로 해제됨
            response.route?.traoptimal?.firstOrNull()
        }
    }
}