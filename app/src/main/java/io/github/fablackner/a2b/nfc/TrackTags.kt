package io.github.fablackner.a2b.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * The NFC tags are the app's real database.
 *
 * A track consists of two physical tags sharing a random positive `stamp`:
 *  - the START tag stores the track itself (stamp is positive on the wire)
 *  - the FINISH tag stores the leaderboard (stamp is negated on the wire,
 *    which is also how the two tag types are told apart)
 *
 * Values are packed as raw binary payloads (TNF_UNKNOWN records) to fit on
 * small tags like the NTAG213. Legacy finish tags written by v2 of the app
 * carry three extra (obfuscated email) records at the end; they are ignored
 * when reading and dropped on the next write.
 */

@Parcelize
data class StartTag(
    val stamp: Int,
    val name: String,
    val description: String,
    val horizontalMeters: Int,
    val verticalMeters: Int,
    val startLat: Double,
    val startLng: Double,
    val finishLat: Double,
    val finishLng: Double,
) : Parcelable {
    init {
        require(stamp > 0) { "start tag stamp must be positive" }
    }

    fun toPayloads(): List<ByteArray> = listOf(
        intPayload(stamp),
        stringPayload(name),
        stringPayload(description),
        intPayload(horizontalMeters),
        intPayload(verticalMeters),
        doublePayload(startLat),
        doublePayload(startLng),
        doublePayload(finishLat),
        doublePayload(finishLng),
    )

    companion object {
        const val RECORD_COUNT = 9

        fun fromPayloads(payloads: List<ByteArray>): StartTag? {
            if (payloads.size < RECORD_COUNT) return null
            val stamp = readInt(payloads[0]) ?: return null
            if (stamp <= 0) return null
            return StartTag(
                stamp = stamp,
                name = readString(payloads[1]),
                description = readString(payloads[2]),
                horizontalMeters = readInt(payloads[3]) ?: return null,
                verticalMeters = readInt(payloads[4]) ?: return null,
                startLat = readDouble(payloads[5]) ?: return null,
                startLng = readDouble(payloads[6]) ?: return null,
                finishLat = readDouble(payloads[7]) ?: return null,
                finishLng = readDouble(payloads[8]) ?: return null,
            )
        }
    }
}

@Parcelize
data class LeaderboardEntry(
    val timeSeconds: Double,
    val date: String,
    val name: String,
) : Parcelable {
    val isEmpty: Boolean get() = timeSeconds < 0

    companion object {
        val EMPTY = LeaderboardEntry(-1.0, "--.--.----", "------------")
    }
}

@Parcelize
data class FinishTag(
    val stamp: Int,
    val first: LeaderboardEntry,
    val second: LeaderboardEntry,
    val third: LeaderboardEntry,
) : Parcelable {
    init {
        require(stamp > 0) { "finish tag stamp must be positive" }
    }

    /**
     * Returns the leaderboard after a run of [timeSeconds] by [name], or this
     * unchanged leaderboard if the run did not make the top three.
     */
    fun withResult(timeSeconds: Double, name: String, date: String): FinishTag {
        val entry = LeaderboardEntry(timeSeconds, date, name)
        return when {
            first.isEmpty || timeSeconds < first.timeSeconds ->
                copy(first = entry, second = first, third = second)
            second.isEmpty || timeSeconds < second.timeSeconds ->
                copy(second = entry, third = second)
            third.isEmpty || timeSeconds < third.timeSeconds ->
                copy(third = entry)
            else -> this
        }
    }

    fun rankOf(name: String): Int? = when (name) {
        first.takeUnless { it.isEmpty }?.name -> 1
        second.takeUnless { it.isEmpty }?.name -> 2
        third.takeUnless { it.isEmpty }?.name -> 3
        else -> null
    }

    fun toPayloads(): List<ByteArray> = buildList {
        add(intPayload(-stamp))
        for (entry in listOf(first, second, third)) {
            add(doublePayload(entry.timeSeconds))
            add(stringPayload(entry.date))
            add(stringPayload(entry.name))
        }
    }

    companion object {
        const val RECORD_COUNT = 10

        fun empty(stamp: Int): FinishTag = FinishTag(
            stamp, LeaderboardEntry.EMPTY, LeaderboardEntry.EMPTY, LeaderboardEntry.EMPTY
        )

        fun fromPayloads(payloads: List<ByteArray>): FinishTag? {
            if (payloads.size < RECORD_COUNT) return null
            val stamp = readInt(payloads[0]) ?: return null
            if (stamp >= 0) return null

            fun entryAt(offset: Int): LeaderboardEntry? {
                val time = readDouble(payloads[offset]) ?: return null
                return LeaderboardEntry(time, readString(payloads[offset + 1]), readString(payloads[offset + 2]))
            }

            return FinishTag(
                stamp = -stamp,
                first = entryAt(1) ?: return null,
                second = entryAt(4) ?: return null,
                third = entryAt(7) ?: return null,
            )
        }
    }
}

// --- payload primitives (kept free of android.nfc types so they are unit-testable) ---

private fun intPayload(value: Int): ByteArray =
    ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()

private fun doublePayload(value: Double): ByteArray =
    ByteBuffer.allocate(Double.SIZE_BYTES).putDouble(value).array()

private fun stringPayload(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8)

private fun readInt(payload: ByteArray): Int? =
    if (payload.size == Int.SIZE_BYTES) ByteBuffer.wrap(payload).int else null

private fun readDouble(payload: ByteArray): Double? =
    if (payload.size == Double.SIZE_BYTES) ByteBuffer.wrap(payload).double else null

private fun readString(payload: ByteArray): String = String(payload, StandardCharsets.UTF_8)

// --- NdefMessage adapters ---

private fun List<ByteArray>.toNdefMessage(): NdefMessage = NdefMessage(
    map { NdefRecord(NdefRecord.TNF_UNKNOWN, ByteArray(0), ByteArray(0), it) }.toTypedArray()
)

fun StartTag.toNdefMessage(): NdefMessage = toPayloads().toNdefMessage()

fun FinishTag.toNdefMessage(): NdefMessage = toPayloads().toNdefMessage()

fun NdefMessage.toStartTagOrNull(): StartTag? = StartTag.fromPayloads(records.map { it.payload })

fun NdefMessage.toFinishTagOrNull(): FinishTag? = FinishTag.fromPayloads(records.map { it.payload })
