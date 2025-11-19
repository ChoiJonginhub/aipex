package com.intel.aipex

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

var url = "https://maps.apigw.ntruss.com/"
class SearchRepository {
    private val api: SearchApi = Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SearchApi::class.java)

    suspend fun searchLocation(query: String): List<GeocodeAddress> {

        val response = api.geocode(
            clientId = "2uhw3mfawu",
            clientSecret = "ifkouBlR107SBkygMqgrnTVMu2Lketu9JuwevrLH",
            query = query
        )
        return response.addresses
    }
}

class DirectionRepository {

    private val api = Retrofit.Builder()
        .baseUrl(url)
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
            clientId = "2uhw3mfawu",
            clientSecret = "ifkouBlR107SBkygMqgrnTVMu2Lketu9JuwevrLH",
            start = "$startLng,$startLat",
            goal = "$endLng,$endLat",
            option = "traavoidcaronly"
        )
        return response.route?.traoptimal?.firstOrNull()
    }
}