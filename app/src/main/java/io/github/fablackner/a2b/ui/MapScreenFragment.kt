package io.github.fablackner.a2b.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.fablackner.a2b.R
import io.github.fablackner.a2b.data.A2bDatabase
import io.github.fablackner.a2b.data.OnlineTracks
import io.github.fablackner.a2b.data.Track
import io.github.fablackner.a2b.nfc.StartTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * The satellite map. Initially it shows every track this device knows about and
 * every track published online; after a start tag is scanned it zooms onto
 * that track with a little flyover.
 */
class MapScreenFragment : Fragment(R.layout.fragment_map) {

    private var mapView: MapView? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val trackOverlays = mutableListOf<Overlay>()
    private var focusedTrack: StartTag? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) enableMyLocation()
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        SatelliteMap.init(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        focusedTrack = savedInstanceState?.let {
            BundleCompat.getParcelable(it, STATE_FOCUSED_TRACK, StartTag::class.java)
        }

        val mapView = view.findViewById<MapView>(R.id.mapView)
        this.mapView = mapView
        SatelliteMap.setUp(mapView)
        mapView.controller.setZoom(INITIAL_ZOOM)
        mapView.controller.setCenter(DEFAULT_POSITION)
        enableMyLocation()

        val focused = focusedTrack
        if (focused != null) {
            focusOnTrack(focused)
        } else {
            showKnownTracks()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        myLocationOverlay?.disableMyLocation()
        mapView?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_FOCUSED_TRACK, focusedTrack)
    }

    override fun onDestroyView() {
        mapView?.onDetach()
        mapView = null
        myLocationOverlay = null
        trackOverlays.clear()
        super.onDestroyView()
    }

    /** Zooms onto [track] and plays the flyover animation towards the finish. */
    fun focusOnTrack(track: StartTag) {
        focusedTrack = track
        val mapView = this.mapView ?: return

        mapView.overlays.removeAll(trackOverlays)
        trackOverlays.clear()
        val start = GeoPoint(track.startLat, track.startLng)
        val finish = GeoPoint(track.finishLat, track.finishLng)
        addTrack(mapView, track.name, start, finish)

        mapView.doOnLayout {
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(listOf(start, finish)), false, BOUNDS_PADDING_PX)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(FLYOVER_DELAY_MS)
            // Turn the map so the run heads "up" the screen, then glide onto the finish.
            this@MapScreenFragment.mapView?.controller?.animateTo(
                finish, FLYOVER_ZOOM, FLYOVER_DURATION_MS, -start.bearingTo(finish).toFloat()
            )
        }
    }

    /** Shows the tracks known locally right away, then adds the ones published online. */
    private fun showKnownTracks() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val localTracks = A2bDatabase.get(context).trackDao().getAll()
            if (!showTracks(localTracks, moveCamera = true)) return@launch

            val onlineTracks = OnlineTracks.sync(context, localTracks) ?: return@launch
            val localStamps = localTracks.mapTo(HashSet()) { it.stamp }
            showTracks(onlineTracks.filter { it.stamp !in localStamps }, moveCamera = localTracks.isEmpty())
        }
    }

    /** Draws [tracks]; returns false (drawing nothing) once a track has been focused. */
    private fun showTracks(tracks: List<Track>, moveCamera: Boolean): Boolean {
        val mapView = this.mapView ?: return false
        if (focusedTrack != null) return false

        for (track in tracks) {
            addTrack(
                mapView,
                track.name,
                GeoPoint(track.startLat, track.startLng),
                GeoPoint(track.finishLat, track.finishLng),
            )
        }
        if (moveCamera) {
            tracks.firstOrNull()?.let {
                mapView.controller.setZoom(TRACKS_ZOOM)
                mapView.controller.setCenter(GeoPoint(it.startLat, it.startLng))
            }
        }
        return true
    }

    private fun addTrack(mapView: MapView, name: String, start: GeoPoint, finish: GeoPoint) {
        val overlays = SatelliteMap.trackOverlays(mapView, name, start, finish)
        trackOverlays += overlays
        mapView.overlays.addAll(overlays)
        mapView.invalidate()
    }

    private fun enableMyLocation() {
        val mapView = this.mapView ?: return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        if (myLocationOverlay != null) return

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView).also {
            it.enableMyLocation()
            mapView.overlays.add(it)
        }
    }

    companion object {
        private const val STATE_FOCUSED_TRACK = "focusedTrack"
        private val DEFAULT_POSITION = GeoPoint(48.210033, 16.363449) // Vienna
        private const val INITIAL_ZOOM = 5.0
        private const val TRACKS_ZOOM = 12.0
        private const val BOUNDS_PADDING_PX = 200
        private const val FLYOVER_DELAY_MS = 2000L
        private const val FLYOVER_DURATION_MS = 3000L
        private const val FLYOVER_ZOOM = 18.0
    }
}
