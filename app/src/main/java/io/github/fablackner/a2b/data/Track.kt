package io.github.fablackner.a2b.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.fablackner.a2b.nfc.FinishTag
import io.github.fablackner.a2b.nfc.LeaderboardEntry
import io.github.fablackner.a2b.nfc.StartTag

/**
 * A track this device knows about: either created here ([createdByMe]) or
 * learned by scanning its start tag. The leaderboard columns hold the latest
 * state seen on the finish tag.
 */
@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val stamp: Int,
    val name: String,
    val description: String,
    val horizontalMeters: Int,
    val verticalMeters: Int,
    val startLat: Double,
    val startLng: Double,
    val finishLat: Double,
    val finishLng: Double,
    val createdByMe: Boolean,
    @Embedded(prefix = "first_") val first: LeaderboardEntry = LeaderboardEntry.EMPTY,
    @Embedded(prefix = "second_") val second: LeaderboardEntry = LeaderboardEntry.EMPTY,
    @Embedded(prefix = "third_") val third: LeaderboardEntry = LeaderboardEntry.EMPTY,
) {
    fun withLeaderboard(finishTag: FinishTag): Track =
        copy(first = finishTag.first, second = finishTag.second, third = finishTag.third)

    fun leaderboard(): FinishTag = FinishTag(stamp, first, second, third)

    companion object {
        fun from(startTag: StartTag, createdByMe: Boolean): Track = Track(
            stamp = startTag.stamp,
            name = startTag.name,
            description = startTag.description,
            horizontalMeters = startTag.horizontalMeters,
            verticalMeters = startTag.verticalMeters,
            startLat = startTag.startLat,
            startLng = startTag.startLng,
            finishLat = startTag.finishLat,
            finishLng = startTag.finishLng,
            createdByMe = createdByMe,
        )
    }
}
