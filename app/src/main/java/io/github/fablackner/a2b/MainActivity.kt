package io.github.fablackner.a2b

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import io.github.fablackner.a2b.data.A2bDatabase
import io.github.fablackner.a2b.data.Track
import io.github.fablackner.a2b.data.UserPrefs
import io.github.fablackner.a2b.databinding.ActivityMainBinding
import io.github.fablackner.a2b.nfc.FinishTag
import io.github.fablackner.a2b.nfc.StartTag
import io.github.fablackner.a2b.nfc.TagWriter
import io.github.fablackner.a2b.nfc.toFinishTagOrNull
import io.github.fablackner.a2b.nfc.toNdefMessage
import io.github.fablackner.a2b.nfc.toStartTagOrNull
import io.github.fablackner.a2b.ui.InfoFragment
import io.github.fablackner.a2b.ui.InitialBottomFragment
import io.github.fablackner.a2b.ui.MapScreenFragment
import io.github.fablackner.a2b.ui.ScoreFragment
import io.github.fablackner.a2b.ui.TimerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The race screen. A race is a little state machine driven by tag scans:
 *
 *   IDLE --scan start tag--> TRACK_SCANNED --scan it again--> RACING
 *        --scan matching finish tag--> FINISHED
 *
 * Scanning a finish tag while idle just shows its leaderboard.
 */
class MainActivity : AppCompatActivity() {

    private enum class Phase { IDLE, TRACK_SCANNED, RACING, FINISHED }

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcPendingIntent: PendingIntent

    private var phase = Phase.IDLE
    private var currentTrack: StartTag? = null
    private var raceStartRealtime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        @Suppress("SourceLockedOrientationActivity")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val nfcIntent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        nfcPendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, flags)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.topFragment, MapScreenFragment())
                add(R.id.bottomFragment, InitialBottomFragment())
            }
        } else {
            phase = Phase.valueOf(savedInstanceState.getString(STATE_PHASE, Phase.IDLE.name))
            currentTrack = BundleCompat.getParcelable(savedInstanceState, STATE_TRACK, StartTag::class.java)
            raceStartRealtime = savedInstanceState.getLong(STATE_RACE_START)
        }

        LocationHelper.ensureLocationEnabled(this)

        onBackPressedDispatcher.addCallback(this) {
            phase = when (phase) {
                Phase.FINISHED -> Phase.RACING
                Phase.RACING -> Phase.TRACK_SCANNED
                Phase.TRACK_SCANNED -> Phase.IDLE
                Phase.IDLE -> Phase.IDLE
            }
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHASE, phase.name)
        outState.putParcelable(STATE_TRACK, currentTrack)
        outState.putLong(STATE_RACE_START, raceStartRealtime)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            toast(R.string.tag_unsupported)
            return
        }
        val message = ndef.cachedNdefMessage
        if (message == null || message.records.isEmpty()) {
            toast(R.string.tag_empty)
            return
        }

        val startTag = message.toStartTagOrNull()
        val finishTag = message.toFinishTagOrNull()
        when {
            startTag != null -> onStartTagScanned(startTag)
            finishTag != null -> onFinishTagScanned(finishTag, tag)
            else -> toast(R.string.tag_not_a_track)
        }
    }

    private fun onStartTagScanned(startTag: StartTag) {
        when (phase) {
            Phase.IDLE -> showTrackInfo(startTag)
            Phase.TRACK_SCANNED ->
                if (startTag.stamp == currentTrack?.stamp) beginRace() else toast(R.string.different_track)
            Phase.RACING, Phase.FINISHED -> Unit
        }
    }

    private fun showTrackInfo(startTag: StartTag) {
        currentTrack = startTag
        phase = Phase.TRACK_SCANNED

        rememberTrack(startTag)
        (fragmentIn(R.id.topFragment) as? MapScreenFragment)?.focusOnTrack(startTag)
        supportFragmentManager.commit {
            setSlideAnimations()
            replace(R.id.bottomFragment, InfoFragment.newInstance(startTag))
            addToBackStack(null)
        }
    }

    private fun beginRace() {
        raceStartRealtime = SystemClock.elapsedRealtime()
        phase = Phase.RACING
        supportFragmentManager.commit {
            setSlideAnimations()
            replace(R.id.bottomFragment, TimerFragment.newInstance(raceStartRealtime))
            addToBackStack(null)
        }
    }

    private fun onFinishTagScanned(finishTag: FinishTag, tag: Tag) {
        when (phase) {
            // Just peeking at a finish tag: show its leaderboard, change nothing.
            Phase.IDLE -> supportFragmentManager.commit {
                setSlideAnimations()
                replace(R.id.topFragment, ScoreFragment.newInstance(finishTag, showRestart = false))
                addToBackStack(null)
            }
            Phase.RACING -> {
                val track = currentTrack ?: return
                if (finishTag.stamp == track.stamp) {
                    finishRace(track, finishTag, tag)
                } else {
                    toast(R.string.wrong_finish_tag)
                }
            }
            Phase.TRACK_SCANNED, Phase.FINISHED -> Unit
        }
    }

    private fun finishRace(track: StartTag, oldBoard: FinishTag, tag: Tag) {
        phase = Phase.FINISHED
        val seconds = (SystemClock.elapsedRealtime() - raceStartRealtime) / 1000.0
        (fragmentIn(R.id.bottomFragment) as? TimerFragment)?.showFinished(seconds)

        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val newBoard = oldBoard.withResult(seconds, UserPrefs.nickname(this), date)

        lifecycleScope.launch {
            val madeTopThree = newBoard != oldBoard
            if (madeTopThree && UserPrefs.writeScoreToTag(this@MainActivity)) {
                val written = withContext(Dispatchers.IO) { TagWriter.write(tag, newBoard.toNdefMessage()) }
                if (!written) toast(R.string.tag_write_failed)
            }

            val dao = A2bDatabase.get(this@MainActivity).trackDao()
            val existing = dao.findByStamp(track.stamp)
            dao.upsert((existing ?: Track.from(track, createdByMe = false)).withLeaderboard(newBoard))

            supportFragmentManager.commit {
                setSlideAnimations()
                replace(R.id.topFragment, ScoreFragment.newInstance(newBoard, showRestart = true))
                addToBackStack(null)
            }
        }
    }

    /** Saves or refreshes a track we learned about by scanning its start tag. */
    private fun rememberTrack(startTag: StartTag) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = A2bDatabase.get(this@MainActivity).trackDao()
            val existing = dao.findByStamp(startTag.stamp)
            dao.upsert(
                existing?.copy(
                    name = startTag.name,
                    description = startTag.description,
                    horizontalMeters = startTag.horizontalMeters,
                    verticalMeters = startTag.verticalMeters,
                    startLat = startTag.startLat,
                    startLng = startTag.startLng,
                    finishLat = startTag.finishLat,
                    finishLng = startTag.finishLng,
                ) ?: Track.from(startTag, createdByMe = false)
            )
        }
    }

    /** Called by the restart button in the score screen (android:onClick). */
    fun restart(@Suppress("UNUSED_PARAMETER") view: View) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        phase = Phase.IDLE
        currentTrack = null
    }

    /** Called by the menu button (android:onClick). */
    fun showMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_writer -> startActivity(Intent(this, WriterActivity::class.java))
                R.id.menu_mytracks -> startActivity(Intent(this, MyTracksActivity::class.java))
                R.id.menu_myrecords -> startActivity(Intent(this, MyRecordsActivity::class.java))
                R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.menu_about -> startActivity(Intent(this, AboutActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun fragmentIn(@IdRes containerId: Int): Fragment? =
        supportFragmentManager.findFragmentById(containerId)

    private fun androidx.fragment.app.FragmentTransaction.setSlideAnimations() {
        setCustomAnimations(
            R.animator.enter_from_right,
            R.animator.exit_to_left,
            R.animator.enter_from_left,
            R.animator.exit_to_right,
        )
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val STATE_PHASE = "phase"
        private const val STATE_TRACK = "currentTrack"
        private const val STATE_RACE_START = "raceStartRealtime"
    }
}
