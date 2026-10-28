package io.github.fablackner.a2b.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import io.github.fablackner.a2b.R
import io.github.fablackner.a2b.databinding.FragmentInfoBinding
import io.github.fablackner.a2b.nfc.StartTag

/**
 * Shows the track details after the start tag has been scanned.
 */
class InfoFragment : Fragment(R.layout.fragment_info) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val track = BundleCompat.getParcelable(requireArguments(), ARG_TRACK, StartTag::class.java)
            ?: return
        val binding = FragmentInfoBinding.bind(view)

        binding.trackNameTV.text = track.name
        binding.descriptionTV.text = track.description
        binding.horizontalDistanceTV.text = getString(R.string.distance_format, track.horizontalMeters)
        binding.verticalDistanceTV.text = getString(R.string.distance_format, track.verticalMeters)
    }

    companion object {
        private const val ARG_TRACK = "track"

        fun newInstance(track: StartTag): InfoFragment = InfoFragment().apply {
            arguments = bundleOf(ARG_TRACK to track)
        }
    }
}
