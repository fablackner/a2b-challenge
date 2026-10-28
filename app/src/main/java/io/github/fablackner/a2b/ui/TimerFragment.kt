package io.github.fablackner.a2b.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.fablackner.a2b.R
import io.github.fablackner.a2b.databinding.FragmentTimerBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The running stopwatch. The race start time (in elapsedRealtime) is owned by
 * MainActivity and passed in as an argument, so the clock survives recreation.
 */
class TimerFragment : Fragment(R.layout.fragment_timer) {

    private var binding: FragmentTimerBinding? = null
    private var finalSeconds: Double = NOT_FINISHED

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentTimerBinding.bind(view)

        finalSeconds = savedInstanceState?.getDouble(STATE_FINAL_SECONDS, NOT_FINISHED) ?: NOT_FINISHED
        if (finalSeconds != NOT_FINISHED) {
            showFinished(finalSeconds)
        }

        val startRealtime = requireArguments().getLong(ARG_START_REALTIME)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (finalSeconds == NOT_FINISHED) {
                    setClock((SystemClock.elapsedRealtime() - startRealtime) / 1000.0)
                    delay(100)
                }
            }
        }
    }

    /** Freezes the clock at the official race time and swaps in the success images. */
    fun showFinished(seconds: Double) {
        finalSeconds = seconds
        setClock(seconds)
        binding?.runnerIV?.setImageResource(R.drawable.success_smily)
        binding?.newrunIV?.setImageResource(R.drawable.finished)
    }

    private fun setClock(seconds: Double) {
        val minutes = (seconds / 60).toInt()
        binding?.clockTV?.text =
            getString(R.string.clock_format, minutes, (seconds - minutes * 60).toInt())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble(STATE_FINAL_SECONDS, finalSeconds)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_START_REALTIME = "startRealtime"
        private const val STATE_FINAL_SECONDS = "finalSeconds"
        private const val NOT_FINISHED = -1.0

        fun newInstance(startRealtime: Long): TimerFragment = TimerFragment().apply {
            arguments = bundleOf(ARG_START_REALTIME to startRealtime)
        }
    }
}
