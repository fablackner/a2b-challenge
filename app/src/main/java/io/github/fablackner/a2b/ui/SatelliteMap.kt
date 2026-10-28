package io.github.fablackner.a2b.ui

import android.content.Context
import android.graphics.Paint
import androidx.core.content.ContextCompat
import io.github.fablackner.a2b.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Satellite map setup shared by the race and writer screens.
 *
 * Uses osmdroid with Esri World Imagery tiles: no API key, account or billing
 * needed, as long as the imagery attribution stays visible on the map.
 */
object SatelliteMap {

    private const val POLYLINE_WIDTH_PX = 12f
    private const val POLYLINE_COLOR = 0xFF000000.toInt()

    private val ESRI_WORLD_IMAGERY = object : OnlineTileSourceBase(
        "EsriWorldImagery", 0, 19, 256, "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Imagery © Esri, Maxar, Earthstar Geographics, and the GIS User Community",
    ) {
        override fun getTileURLString(index: Long): String =
            "$baseUrl${MapTileIndex.getZoom(index)}/${MapTileIndex.getY(index)}/${MapTileIndex.getX(index)}"
    }

    /** Configures osmdroid; call before inflating a [MapView]. */
    fun init(context: Context) {
        val appContext = context.applicationContext
        Configuration.getInstance().apply {
            userAgentValue = appContext.packageName
            osmdroidBasePath = File(appContext.filesDir, "osmdroid")
            osmdroidTileCache = File(appContext.cacheDir, "osmdroid/tiles")
        }
    }

    /** Turns [mapView] into a satellite map with pinch zoom and the imagery attribution. */
    fun setUp(mapView: MapView) {
        mapView.setTileSource(ESRI_WORLD_IMAGERY)
        mapView.setMultiTouchControls(true)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.isVerticalMapRepetitionEnabled = false
        mapView.minZoomLevel = 3.0
        mapView.overlays.add(CopyrightOverlay(mapView.context))
    }

    fun marker(mapView: MapView, position: GeoPoint, title: String, iconRes: Int): Marker =
        Marker(mapView).apply {
            this.position = position
            this.title = title
            icon = ContextCompat.getDrawable(mapView.context, iconRes)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

    /** The overlays drawing one track: its start and finish markers joined by a line. */
    fun trackOverlays(mapView: MapView, name: String, start: GeoPoint, finish: GeoPoint) = listOf(
        Polyline(mapView).apply {
            setPoints(listOf(start, finish))
            outlinePaint.color = POLYLINE_COLOR
            outlinePaint.strokeWidth = POLYLINE_WIDTH_PX
            outlinePaint.strokeCap = Paint.Cap.ROUND
            infoWindow = null
        },
        marker(mapView, start, name, R.drawable.marker_a),
        marker(mapView, finish, name, R.drawable.marker_b),
    )
}
