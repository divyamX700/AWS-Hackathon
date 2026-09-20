package com.sankatsetu.app.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sankatsetu.app.maps.GeoMath
import com.sankatsetu.app.ui.theme.ConsoleReadoutStyle
import com.sankatsetu.app.ui.theme.SankatSetuColors
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import com.sankatsetu.app.maps.MapTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * The Map tab — a real, downloaded-once, genuinely-offline-afterward area
 * around the person, not a live-tiles-over-the-internet map (which would
 * be useless the moment it's actually needed). See
 * `docs/adr/0020-offline-maps.md` (once written) for the full reasoning:
 * why a 2km radius, why raster tiles via `osmdroid` rather than a native
 * vector-tile SDK, and why this has to be downloaded before a crisis, not
 * during one.
 *
 * `osmdroid`'s `MapView`/`CacheManager` are real Android `View`/`AsyncTask`
 * objects, not Compose-native — this screen owns constructing and driving
 * them directly (via [AndroidView]) rather than pushing that down into
 * [MapViewModel], which only owns the download *decision* and its result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // osmdroid requires a real user agent before any tile request — the
    // default empty value gets some tile servers to silently reject
    // requests. Must run before the first MapView is constructed below.
    // Configuration is a process-wide singleton; `remember(Unit)` just
    // keeps this from re-running on every recomposition, not a
    // correctness requirement.
    remember(Unit) { org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Granted or not, startDownload()'s own LocationProvider check reports
        // PermissionDenied cleanly either way — no need to branch here.
        viewModel.startDownload()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(MapTileSource.streets)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        mapView = this
                    }
                }
            )

            when (val current = state) {
                is MapUiState.Idle -> DownloadPrompt(
                    onDownloadClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                )

                is MapUiState.RequestingLocation -> StatusOverlay {
                    CircularProgressIndicator(color = SankatSetuColors.SignalBlue)
                    Text("Finding your location…", style = MaterialTheme.typography.bodyMedium)
                }

                is MapUiState.PreparingDownload -> {
                    LaunchedEffect(current) {
                        val view = mapView
                        // Center the LIVE map on the person right away so it
                        // doesn't sit blank/wherever it last was — but the
                        // actual bulk download below deliberately does NOT
                        // reuse this MapView. Real on-device finding: a
                        // CacheManager built from a live MapView visibly
                        // drags that MapView's own camera across zoom levels
                        // while it works internally, which looks exactly
                        // like a runaway multi-hundred-km download even
                        // though the actual downloaded area (verified by
                        // logging the real BoundingBox) was correctly a 2km
                        // radius the whole time. The MapTileProviderBase/
                        // ITileSource-based constructor below has no MapView
                        // at all, so it can't touch anyone's camera.
                        view?.controller?.setCenter(GeoPoint(current.lat, current.lon))
                        val box = GeoMath.boundingBoxForRadius(current.lat, current.lon, current.radiusMeters)
                        val boundingBox = BoundingBox(box.maxLat, box.maxLon, box.minLat, box.minLon)
                        val cacheManager = CacheManager(MapTileSource.streets, SqlTileWriter(), MapViewModel.ZOOM_MIN, MapViewModel.ZOOM_MAX)
                        cacheManager.downloadAreaAsyncNoUI(
                            context,
                            boundingBox,
                            MapViewModel.ZOOM_MIN,
                            MapViewModel.ZOOM_MAX,
                            object : CacheManager.CacheManagerCallback {
                                override fun onTaskComplete() {
                                    viewModel.onDownloadComplete(current.lat, current.lon)
                                }
                                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                                    viewModel.onDownloadProgress(progress, currentZoomLevel, zoomMin, zoomMax)
                                }
                                override fun downloadStarted() = Unit
                                override fun setPossibleTilesInArea(total: Int) = Unit
                                override fun onTaskFailed(errors: Int) {
                                    viewModel.onDownloadFailed()
                                }
                            }
                        )
                    }
                    StatusOverlay {
                        CircularProgressIndicator(color = SankatSetuColors.SignalBlue)
                        Text("Starting download…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is MapUiState.Downloading -> StatusOverlay {
                    // Real on-device finding: osmdroid's own upfront tile-count
                    // estimate (used as the percentage's denominator) can run
                    // low against the actual count at the finest zoom level,
                    // so the raw value legitimately exceeds 100 before the
                    // download actually finishes. Clamping the *display* is
                    // the honest fix — the download itself isn't stuck, the
                    // estimate was just optimistic.
                    val displayPercent = current.progressPercent.coerceIn(0, 100)
                    LinearProgressIndicator(
                        progress = { displayPercent / 100f },
                        modifier = Modifier.fillMaxWidth(0.7f),
                        color = SankatSetuColors.SignalBlue
                    )
                    Text(
                        "Downloading… $displayPercent% (zoom ${current.currentZoom} of ${current.zoomMax})",
                        style = ConsoleReadoutStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is MapUiState.Ready -> {
                    LaunchedEffect(current.area) {
                        mapView?.controller?.setCenter(GeoPoint(current.area.centerLat, current.area.centerLon))
                    }
                    ReadyBadge(
                        radiusMeters = current.area.radiusMeters,
                        onRedownload = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                }

                is MapUiState.Error -> StatusOverlay {
                    Text(current.message, style = MaterialTheme.typography.bodyMedium, color = SankatSetuColors.StatusCritical)
                    TextButton(onClick = { viewModel.retry() }) { Text("Try again") }
                }
            }
        }
    }
}

/** A centered card over the (empty, online-tile-only) map, offering the one real action this screen has before anything is downloaded. */
@Composable
private fun DownloadPrompt(onDownloadClick: () -> Unit) {
    StatusOverlay {
        Text("No offline map for this area yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Downloads roughly a 2km radius around you, once, while you have a real internet connection. Fully usable with no signal afterward.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onDownloadClick) { Text("Download offline map") }
    }
}

/** A small corner badge once an area is downloaded — the map itself is the main content at this point, this is just the honest "yes, this is really offline now" confirmation plus a way to refresh it. */
@Composable
private fun ReadyBadge(radiusMeters: Double, onRedownload: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(3.dp))
                .padding(12.dp)
        ) {
            Text(
                "Downloaded — ${(radiusMeters / 1000).toInt()}km radius, viewable offline",
                style = ConsoleReadoutStyle,
                color = SankatSetuColors.StatusSafe
            )
            TextButton(onClick = onRedownload) { Text("Re-download for here") }
        }
    }
}

@Composable
private fun StatusOverlay(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(3.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}
