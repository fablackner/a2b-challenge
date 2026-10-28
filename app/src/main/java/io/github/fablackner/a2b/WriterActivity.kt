package io.github.fablackner.a2b

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import io.github.fablackner.a2b.data.A2bDatabase
import io.github.fablackner.a2b.data.OnlineTracks
import io.github.fablackner.a2b.data.Track
import io.github.fablackner.a2b.databinding.ActivityWriterBinding
import io.github.fablackner.a2b.nfc.FinishTag
import io.github.fablackner.a2b.nfc.StartTag
import io.github.fablackner.a2b.nfc.TagWriter
import io.github.fablackner.a2b.nfc.toNdefMessage
import io.github.fablackner.a2b.ui.SatelliteMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.random.Random

/**
 * Creates a new track: drag marker B to the finish, scan the finish tag,
 * drag marker A to the start, name the track, scan the start tag.
 *
 * The altitude difference between the two scan positions is estimated from
 * the barometer (averaged over the last [ALTITUDE_SAMPLES] samples).
 */
class WriterActivity : AppCompatActivity() {

    private enum class Step { SCAN_FINISH, SCAN_START, DONE }

    private lateinit var binding: ActivityWriterBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcPendingIntent: PendingIntent

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var markerA: Marker? = null
    private var markerB: Marker? = null

    private var step = Step.SCAN_FINISH
    private var stamp = 0
    private var finishAltitude = 0.0

    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private val altitudeSamples = ArrayDeque<Float>()

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) moveActiveMarkerToCurrentLocation() else toast(R.string.permission_denied)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SatelliteMap.init(this)
        binding = ActivityWriterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        @Suppress("SourceLockedOrientationActivity")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        binding.trackNameET.isEnabled = false
        binding.descriptionET.isEnabled = false
        binding.updateMarkerIB.setOnClickListener { moveActiveMarkerToCurrentLocation() }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        binding.scanTagIV.setImageResource(
            when {
                nfcAdapter == null -> R.drawable.not_supported
                nfcAdapter?.isEnabled != true -> R.drawable.turn_on_nfc
                else -> R.drawable.scan_finish_tag
            }
        )
        ContextCompat.registerReceiver(
            this,
            nfcStateReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )

        val nfcIntent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        nfcPendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, flags)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        setUpMap()

        LocationHelper.ensureLocationEnabled(this)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        myLocationOverlay?.enableMyLocation()
        sensorManager?.registerListener(pressureListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null)
    }

    override fun onPause() {
        sensorManager?.unregisterListener(pressureListener)
        nfcAdapter?.disableForegroundDispatch(this)
        myLocationOverlay?.disableMyLocation()
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(nfcStateReceiver)
        binding.mapView.onDetach()
        super.onDestroy()
    }

    private fun setUpMap() {
        val mapView = binding.mapView
        SatelliteMap.setUp(mapView)
        markerB = addDraggableMarker(DEFAULT_POSITION, getString(R.string.marker_finish), R.drawable.marker_b)
        mapView.controller.setZoom(10.0)
        mapView.controller.setCenter(DEFAULT_POSITION)
        moveActiveMarkerToCurrentLocation()
    }

    /** Adds a marker that can be dragged after a long press on it. */
    private fun addDraggableMarker(position: GeoPoint, title: String, iconRes: Int): Marker {
        val mapView = binding.mapView
        val marker = SatelliteMap.marker(mapView, position, title, iconRes).apply {
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) = Unit
                override fun onMarkerDrag(marker: Marker) = Unit
                override fun onMarkerDragEnd(marker: Marker) {
                    mapView.controller.animateTo(marker.position)
                }
            })
        }
        mapView.overlays.add(marker)
        mapView.invalidate()
        return marker
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        when (step) {
            Step.SCAN_FINISH -> writeFinishTag(tag)
            Step.SCAN_START -> writeStartTag(tag)
            Step.DONE -> Unit
        }
    }

    private fun writeFinishTag(tag: Tag) {
        stamp = Random.nextInt(1, Int.MAX_VALUE)
        lifecycleScope.launch {
            val written = withContext(Dispatchers.IO) {
                TagWriter.write(tag, FinishTag.empty(stamp).toNdefMessage())
            }
            if (!written) {
                toast(R.string.tag_write_failed)
                return@launch
            }

            finishAltitude = averageAltitude()
            step = Step.SCAN_START

            binding.scanTagIV.setImageResource(R.drawable.scan_start_tag)
            binding.trackNameET.isEnabled = true
            binding.trackNameET.setHint(R.string.hint_track_name)
            binding.descriptionET.isEnabled = true
            binding.descriptionET.setHint(R.string.hint_description)

            markerB?.isDraggable = false
            markerA = addDraggableMarker(
                markerB?.position ?: DEFAULT_POSITION,
                getString(R.string.marker_start),
                R.drawable.marker_a,
            )
            moveActiveMarkerToCurrentLocation()
        }
    }

    private fun writeStartTag(tag: Tag) {
        val start = markerA?.position ?: return
        val finish = markerB?.position ?: return

        val name = binding.trackNameET.text.toString().trim()
        if (name.isEmpty()) {
            toast(R.string.name_required)
            return
        }

        val horizontal = FloatArray(1).also {
            Location.distanceBetween(start.latitude, start.longitude, finish.latitude, finish.longitude, it)
        }[0].toDouble()
        val vertical = finishAltitude - averageAltitude()

        val startTag = StartTag(
            stamp = stamp,
            name = name,
            description = binding.descriptionET.text.toString().trim(),
            horizontalMeters = Math.round(horizontal).toInt(),
            verticalMeters = Math.round(vertical).toInt(),
            startLat = start.latitude,
            startLng = start.longitude,
            finishLat = finish.latitude,
            finishLng = finish.longitude,
        )

        lifecycleScope.launch {
            val written = withContext(Dispatchers.IO) {
                TagWriter.write(tag, startTag.toNdefMessage())
            }
            if (!written) {
                toast(R.string.tag_write_failed)
                return@launch
            }

            val track = Track.from(startTag, createdByMe = true)
            withContext(Dispatchers.IO) {
                A2bDatabase.get(this@WriterActivity).trackDao().upsert(track)
            }

            step = Step.DONE
            binding.scanTagIV.setImageResource(R.drawable.success)
            binding.updateMarkerIB.setImageResource(R.drawable.success_smily)
            binding.trackNameET.isEnabled = false
            binding.descriptionET.isEnabled = false
            markerA?.isDraggable = false

            if (!OnlineTracks.publish(this@WriterActivity, track)) toast(R.string.track_upload_pending)
        }
    }

    private fun moveActiveMarkerToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        if (myLocationOverlay == null) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView).also {
                it.enableMyLocation()
                binding.mapView.overlays.add(it)
            }
        }

        @SuppressLint("MissingPermission")
        run {
            LocationServices.getFusedLocationProviderClient(this).lastLocation
                .addOnSuccessListener { location ->
                    if (location == null) return@addOnSuccessListener
                    val position = GeoPoint(location.latitude, location.longitude)
                    (if (step == Step.SCAN_FINISH) markerB else markerA)?.position = position
                    binding.mapView.controller.animateTo(position, 18.0, null)
                }
        }
    }

    private fun averageAltitude(): Double =
        if (altitudeSamples.isEmpty()) 0.0 else altitudeSamples.average()

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private val pressureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            altitudeSamples.addLast(
                SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0])
            )
            if (altitudeSamples.size > ALTITUDE_SAMPLES) altitudeSamples.removeFirst()
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) return
            when (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)) {
                NfcAdapter.STATE_OFF -> binding.scanTagIV.setImageResource(R.drawable.turn_on_nfc)
                NfcAdapter.STATE_ON -> binding.scanTagIV.setImageResource(
                    if (step == Step.SCAN_FINISH) R.drawable.scan_finish_tag else R.drawable.scan_start_tag
                )
            }
        }
    }

    companion object {
        private val DEFAULT_POSITION = GeoPoint(48.210033, 16.363449) // Vienna
        private const val ALTITUDE_SAMPLES = 50
    }
}
