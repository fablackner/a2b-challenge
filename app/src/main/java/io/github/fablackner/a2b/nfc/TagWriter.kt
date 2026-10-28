package io.github.fablackner.a2b.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log

object TagWriter {

    private const val TAG = "TagWriter"

    /**
     * Writes [message] to [tag], formatting the tag to NDEF first if necessary.
     * Must be called off the main thread; returns false if writing failed.
     */
    fun write(tag: Tag, message: NdefMessage): Boolean = try {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.use {
                it.connect()
                it.writeNdefMessage(message)
            }
        } else {
            val formatable = NdefFormatable.get(tag) ?: return false
            formatable.use {
                it.connect()
                it.format(message)
            }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to write tag", e)
        false
    }
}
