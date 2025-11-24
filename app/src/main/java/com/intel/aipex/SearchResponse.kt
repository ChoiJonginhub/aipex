package com.intel.aipex

//openApi Search
data class OpenSearchResponse(
    val lastBuildDate: String,
    val total: Int,
    val start: Int,
    val display: Int,
    val items: List<OpenSearchItem>
)
data class OpenSearchItem(
    val title: String,
    val link: String?,
    val category: String?,
    val description: String?,
    val telephone: String?,
    val address: String?,
    val roadAddress: String?,
    val mapx: String?,
    val mapy: String?
)
//Geocode
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
//direction
data class DirectionResponse(
    val code: Int,
    val message: String,
    val currentDateTime: String?,
    val route: DirectionRoute?
)

data class DirectionRoute(
    val traoptimal: List<Traoptimal>
)

data class Traoptimal(
    val summary: Summary,
    val path: List<List<Double>>,
    val section: List<RouteSection>,
    val guide: List<Guidence>
)

data class Summary(
    val start: LocationPoint,
    val goal: LocationPoint,
    val distance: Int,
    val duration: Long,           // ← Int 초과 위험 → Long
    val departureTime: String?,
    val bbox: List<List<Double>>,
    val tollFare: Int?,
    val taxiFare: Int?,
    val fuelPrice: Int?
)

data class LocationPoint(
    val location: List<Double>,
    val dir: Int? = null           // ← goal에만 등장하므로 optional
)

data class RouteSection(
    val pointIndex: Int,
    val pointCount: Int,
    val distance: Int,
    val name: String?,
    val congestion: Int?,
    val speed: Int?
)

data class Guidence(
    val pointIndex: Int,
    val type: Int,
    val instructions: String,
    val distance: Int,
    val duration: Int
)