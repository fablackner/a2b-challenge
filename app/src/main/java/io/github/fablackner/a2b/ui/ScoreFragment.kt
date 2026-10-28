package io.github.fablackner.a2b.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import io.github.fablackner.a2b.R
import io.github.fablackner.a2b.databinding.FragmentScoreBinding
import io.github.fablackner.a2b.nfc.FinishTag
import io.github.fablackner.a2b.nfc.LeaderboardEntry

/**
 * Shows the top-three leaderboard of a finish tag. Purely presentational —
 * tag writing and persistence happen in MainActivity before this is shown.
 */
class ScoreFragment : Fragment(R.layout.fragment_score) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val board = BundleCompat.getParcelable(requireArguments(), ARG_BOARD, FinishTag::class.java)
            ?: return
        val binding = FragmentScoreBinding.bind(view)

        bindEntry(board.first, binding.firstPlaceTimeTV, binding.firstPlaceDateTV, binding.firstPlaceNameTV)
        bindEntry(board.second, binding.secondPlaceTimeTV, binding.secondPlaceDateTV, binding.secondPlaceNameTV)
        bindEntry(board.third, binding.thirdPlaceTimeTV, binding.thirdPlaceDateTV, binding.thirdPlaceNameTV)

        binding.restartIB.isVisible = requireArguments().getBoolean(ARG_SHOW_RESTART)
    }

    private fun bindEntry(entry: LeaderboardEntry, timeView: TextView, dateView: TextView, nameView: TextView) {
        timeView.text = if (entry.isEmpty) {
            getString(R.string.time_empty)
        } else {
            val minutes = (entry.timeSeconds / 60).toInt()
            getString(R.string.time_format, minutes, (entry.timeSeconds - minutes * 60).toInt())
        }
        dateView.text = entry.date
        nameView.text = entry.name
    }

    companion object {
        private const val ARG_BOARD = "board"
        private const val ARG_SHOW_RESTART = "showRestart"

        fun newInstance(board: FinishTag, showRestart: Boolean): ScoreFragment = ScoreFragment().apply {
            arguments = bundleOf(ARG_BOARD to board, ARG_SHOW_RESTART to showRestart)
        }
    }
}
