package com.intel.aipex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SearchViewModel(
    private val repository: SearchRepository = SearchRepository()
): ViewModel(){
    private val _searchResults = MutableStateFlow<List<GeocodeAddress>>(emptyList())
    val searchResults: StateFlow<List<GeocodeAddress>> = _searchResults
    fun search(query: String) {
        viewModelScope.launch {
            val data = repository.searchLocation(query)
            _searchResults.value = data
        }
    }
}

class DirectionViewModel(
    private val repo: DirectionRepository = DirectionRepository()
) : ViewModel() {

    private val _route = MutableStateFlow<Traoptimal?>(null)
    val route: StateFlow<Traoptimal?> = _route

    fun requestRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
        viewModelScope.launch {
            val data = repo.getRoute(
                startLng = startLng,
                startLat = startLat,
                endLng = endLng,
                endLat = endLat
            )
            _route.value = data
        }
    }
}