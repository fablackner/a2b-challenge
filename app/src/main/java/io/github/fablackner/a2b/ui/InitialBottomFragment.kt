package io.github.fablackner.a2b.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.github.fablackner.a2b.R
import io.github.fablackner.a2b.databinding.FragmentInitialBottomBinding

/**
 * The idle-state bottom card: shows whether NFC is ready and hosts the menu button.
 */
class InitialBottomFragment : Fragment(R.layout.fragment_initial_bottom) {

    private var binding: FragmentInitialBottomBinding? = null

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) return
            when (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)) {
                NfcAdapter.STATE_OFF -> binding?.newrunIV?.setImageResource(R.drawable.turn_on_nfc)
                NfcAdapter.STATE_ON -> binding?.newrunIV?.setImageResource(R.drawable.scanning)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentInitialBottomBinding.bind(view)

        val nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())
        binding?.newrunIV?.setImageResource(
            when {
                nfcAdapter == null -> R.drawable.not_supported
                !nfcAdapter.isEnabled -> R.drawable.turn_on_nfc
                else -> R.drawable.scanning
            }
        )

        ContextCompat.registerReceiver(
            requireContext(),
            nfcStateReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onDestroyView() {
        requireContext().unregisterReceiver(nfcStateReceiver)
        binding = null
        super.onDestroyView()
    }
}
