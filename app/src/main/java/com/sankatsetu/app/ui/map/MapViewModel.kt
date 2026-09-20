package com.sankatsetu.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sankatsetu.app.maps.DownloadedArea
import com.sankatsetu.app.maps.LocationProvider
import com.sankatsetu.app.maps.LocationResult
import com.sankatsetu.app.maps.MapAreaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MapUiState {
    data object Idle : MapUiState()
    data object RequestingLocation : MapUiState()
    data class PreparingDownload(val lat: Double, val lon: Double, val radiusMeters: Double) : MapUiState()
    data class Downloading(val progressPercent: Int, val currentZoom: Int, val zoomMin: Int, val zoomMax: Int) : MapUiState()
    data class Ready(val area: DownloadedArea) : MapUiState()
    data class Error(val message: String) : MapUiState()
}

/**
 * Backs the Map tab — see `docs/adr/0020-offline-maps.md` (once written).
 * This ViewModel owns the decision ("what area, at what radius, should be
 * downloaded") and the persisted result; the actual `osmdroid` `MapView`/
 * `CacheManager` mechanics live in `MapScreen.kt` since they're real
 * Android `View` objects with no meaningful ViewModel-layer equivalent —
 * the screen reports progress back here via [onDownloadProgress] etc.
 * rather than this class reaching into UI-layer view objects itself.
 */
class MapViewModel(
    private val locationProvider: LocationProvider,
    private val mapAreaStore: MapAreaStore
) : ViewModel() {
    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Idle)
    val uiState: StateFlow<MapUiState> = _uiState

    init {
        mapAreaStore.get()?.let { _uiState.value = MapUiState.Ready(it) }
    }

    /** Call after the location permission has been requested (granted or not — [LocationProvider] itself reports PermissionDenied cleanly either way). */
    fun startDownload() {
        viewModelScope.launch {
            _uiState.value = MapUiState.RequestingLocation
            when (val result = locationProvider.getCurrentFix()) {
                is LocationResult.Fix -> _uiState.value = MapUiState.PreparingDownload(result.latitude, result.longitude, RADIUS_METERS)
                LocationResult.PermissionDenied -> _uiState.value = MapUiState.Error("Location access is needed to download a map for your area.")
                LocationResult.NoProviderAvailable -> _uiState.value = MapUiState.Error("No location provider is available on this device.")
                LocationResult.TimedOut -> _uiState.value = MapUiState.Error("Could not get a location fix in time. A clearer view of the sky helps GPS lock on faster.")
            }
        }
    }

    fun onDownloadProgress(progressPercent: Int, currentZoom: Int, zoomMin: Int, zoomMax: Int) {
        _uiState.value = MapUiState.Downloading(progressPercent, currentZoom, zoomMin, zoomMax)
    }

    fun onDownloadComplete(centerLat: Double, centerLon: Double) {
        val area = DownloadedArea(centerLat, centerLon, RADIUS_METERS, System.currentTimeMillis())
        mapAreaStore.save(area)
        _uiState.value = MapUiState.Ready(area)
    }

    fun onDownloadFailed() {
        _uiState.value = MapUiState.Error("The download failed partway through. Check your connection and try again.")
    }

    fun retry() {
        _uiState.value = MapUiState.Idle
    }

    companion object {
        const val RADIUS_METERS = 2000.0
        // Vector-tile services generate real detail only up to z14 and derive
        // higher zooms client-side (see the size-estimate research this
        // feature was planned against); osmdroid's raster tiles have no such
        // shortcut, so z12-z17 is a deliberate, real trade: z12 for enough
        // context to reorient after panning, z17 for street-level, without
        // paying for the far denser z18+ tile count a 2km radius doesn't need.
        const val ZOOM_MIN = 12
        const val ZOOM_MAX = 17
    }
}
