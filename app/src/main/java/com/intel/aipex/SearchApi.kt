package com.intel.aipex

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SearchApi {
    @GET("map-geocode/v2/geocode")
    suspend fun geocode(
        @Header("x-ncp-apigw-api-key-id") clientId: String,
        @Header("x-ncp-apigw-api-key") clientSecret: String,
        @Header("Accept") accept: String = "application/json",
        @Query("query") query: String
    ): SearchResponse
}
interface DirectionApi {
    @GET("map-direction/v1/driving")
    suspend fun getDrivingRoute(
        @Header("x-ncp-apigw-api-key-id") clientId: String,
        @Header("x-ncp-apigw-api-key") clientSecret: String,
        @Query("start") start: String,  // "127.1058342,37.359708"
        @Query("goal") goal: String,     // "129.075986,35.179470"
        @Query("option") option: String
    ): DirectionResponse
}