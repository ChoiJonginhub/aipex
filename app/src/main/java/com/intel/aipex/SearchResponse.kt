package com.intel.aipex

data class SearchResponse (
    val status: String,
    val meta: GeocodeMeta,
    val addresses: List<GeocodeAddress>
)

data class GeocodeMeta(
    val totalCount: Int
)
data class GeocodeAddress(
    val roadAddress: String?,
    val jibunAddress: String?,
    val englishAddress: String?,
    val x: String?,   // 경도
    val y: String?    // 위도
)

data class DirectionResponse(
    val code: Int,
    val message: String,
    val route: DirectionRoute?
)

data class DirectionRoute(
    val traoptimal: List<Traoptimal>
)

data class Traoptimal(
    val summary: Summary,
    val path: List<List<Double>>  // [ [경도,위도], [경도,위도], ... ]
)

data class Summary(
    val distance: Int,
    val duration: Int,
    val start: LocationPoint,
    val goal: LocationPoint
)

data class LocationPoint(
    val location: List<Double>
)