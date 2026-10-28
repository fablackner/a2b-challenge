package io.github.fablackner.a2b

import io.github.fablackner.a2b.nfc.FinishTag
import io.github.fablackner.a2b.nfc.LeaderboardEntry
import io.github.fablackner.a2b.nfc.StartTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class TrackTagsTest {

    private val startTag = StartTag(
        stamp = 903673529,
        name = "FountainTrackSanSeb",
        description = "The finish is at the corner beside the fountain!",
        horizontalMeters = 231,
        verticalMeters = -7,
        startLat = 43.317306,
        startLng = -1.980269,
        finishLat = 43.317686,
        finishLng = -1.977481,
    )

    @Test
    fun `start tag round trip`() {
        assertEquals(startTag, StartTag.fromPayloads(startTag.toPayloads()))
    }

    @Test
    fun `finish tag round trip`() {
        val board = FinishTag(
            stamp = 42,
            first = LeaderboardEntry(61.2, "14.07.2026", "speedy"),
            second = LeaderboardEntry(75.0, "01.01.2026", "slowpoke"),
            third = LeaderboardEntry.EMPTY,
        )
        assertEquals(board, FinishTag.fromPayloads(board.toPayloads()))
    }

    @Test
    fun `empty finish tag has three empty places`() {
        val board = FinishTag.empty(7)
        assertEquals(true, board.first.isEmpty)
        assertEquals(true, board.second.isEmpty)
        assertEquals(true, board.third.isEmpty)
    }

    @Test
    fun `start and finish tags are told apart by the stamp sign`() {
        assertNull(FinishTag.fromPayloads(startTag.toPayloads()))
        assertNull(StartTag.fromPayloads(FinishTag.empty(7).toPayloads()))
    }

    @Test
    fun `new best time shifts the leaderboard down`() {
        val board = FinishTag(
            stamp = 1,
            first = LeaderboardEntry(60.0, "01.01.2026", "anna"),
            second = LeaderboardEntry(70.0, "02.01.2026", "ben"),
            third = LeaderboardEntry(80.0, "03.01.2026", "cleo"),
        )

        val updated = board.withResult(55.0, "dana", "14.07.2026")

        assertEquals("dana", updated.first.name)
        assertEquals("anna", updated.second.name)
        assertEquals("ben", updated.third.name)
    }

    @Test
    fun `slower time than all three leaves the leaderboard unchanged`() {
        val board = FinishTag(
            stamp = 1,
            first = LeaderboardEntry(60.0, "01.01.2026", "anna"),
            second = LeaderboardEntry(70.0, "02.01.2026", "ben"),
            third = LeaderboardEntry(80.0, "03.01.2026", "cleo"),
        )

        assertEquals(board, board.withResult(90.0, "dana", "14.07.2026"))
    }

    @Test
    fun `result fills empty places from the top`() {
        val updated = FinishTag.empty(1).withResult(99.0, "dana", "14.07.2026")

        assertEquals("dana", updated.first.name)
        assertEquals(true, updated.second.isEmpty)
        assertEquals(true, updated.third.isEmpty)
    }

    @Test
    fun `rank is found by nickname`() {
        val board = FinishTag(
            stamp = 1,
            first = LeaderboardEntry(60.0, "01.01.2026", "anna"),
            second = LeaderboardEntry(70.0, "02.01.2026", "ben"),
            third = LeaderboardEntry.EMPTY,
        )

        assertEquals(1, board.rankOf("anna"))
        assertEquals(2, board.rankOf("ben"))
        assertNull(board.rankOf("cleo"))
    }

    @Test
    fun `legacy v2 finish tags with trailing email records are still readable`() {
        val board = FinishTag(
            stamp = 42,
            first = LeaderboardEntry(61.2, "14.07.2026", "speedy"),
            second = LeaderboardEntry.EMPTY,
            third = LeaderboardEntry.EMPTY,
        )
        val legacyPayloads = board.toPayloads() + List(3) {
            "56878871792089610777786766377776751577".toByteArray(StandardCharsets.UTF_8)
        }

        assertEquals(board, FinishTag.fromPayloads(legacyPayloads))
    }

    @Test
    fun `garbage payloads are rejected`() {
        val tooShort = listOf(ByteBuffer.allocate(4).putInt(1).array())
        assertNull(StartTag.fromPayloads(tooShort))
        assertNull(FinishTag.fromPayloads(tooShort))
        assertNull(StartTag.fromPayloads(List(9) { ByteArray(1) }))
        assertNull(FinishTag.fromPayloads(List(10) { ByteArray(3) }))
    }
}
