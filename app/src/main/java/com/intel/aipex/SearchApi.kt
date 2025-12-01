package com.intel.aipex

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface OpenSearchApi {
    @GET("v1/search/local.json")
    suspend fun search(
        @Header("X-Naver-Client-Id") clientId: String,
        @Header("X-Naver-Client-Secret") clientSecret: String,
        @Header("Accept") accept: String = "application/json",
        @Query("query") query: String
    ): OpenSearchResponse
}
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
        @Query("start") start: String,
        @Query("goal") goal: String,
        //@Query("option") option: String,
        @Query("lang") lang: String
    ): DirectionResponse
}