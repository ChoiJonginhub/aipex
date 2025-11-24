package com.intel.aipex

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

var open_url = "https://openapi.naver.com/"
var ncp_url = "https://maps.apigw.ntruss.com/"
var open_id = "zaOBCBPWT6Wo4dmJJ6Zm"
var open_secret = "8qIocDkbGA"
var ncp_id = "2uhw3mfawu"
var ncp_secret = "ifkouBlR107SBkygMqgrnTVMu2Lketu9JuwevrLH"

class OpenSearchRepository {
    private val api: OpenSearchApi = Retrofit.Builder()
        .baseUrl(open_url)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenSearchApi::class.java)
    suspend fun search(query: String): List<OpenSearchItem> {
        val response = api.search(
            clientId = open_id,
            clientSecret = open_secret,
            query = query
        )
        return response.items
    }
}
class SearchRepository {
    private val api: SearchApi = Retrofit.Builder()
        .baseUrl(ncp_url)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SearchApi::class.java)
    suspend fun searchLocation(query: String): List<GeocodeAddress> {
        val response = api.geocode(
            clientId = ncp_id,
            clientSecret = ncp_secret,
            query = query
        )
        return response.addresses
    }
}

class DirectionRepository {
    private val api = Retrofit.Builder()
        .baseUrl(ncp_url)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DirectionApi::class.java)
    suspend fun getRoute(
        startLng: Double,
        startLat: Double,
        endLng: Double,
        endLat: Double
    ): Traoptimal? {
        val response = api.getDrivingRoute(
            clientId = ncp_id,
            clientSecret = ncp_secret,
            start = "$startLng,$startLat",
            goal = "$endLng,$endLat"
        )
        return response.route?.traoptimal?.firstOrNull()
    }
}