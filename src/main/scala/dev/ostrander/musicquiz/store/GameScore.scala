package dev.ostrander.musicquiz.store

import ackcord.data.GuildId
import ackcord.data.UserId
import org.joda.time.DateTime

case class GameScore(
  guildId: String,
  gameId: String,
  userId: String,
  score: Int,
  endTimestamp: DateTime,
)

object GameScore {
  def fromMap(guildId: GuildId, map: Map[UserId, Int]): List[GameScore] = {
    val endTimestamp = DateTime.now
    val gameId = java.util.UUID.randomUUID.toString
    map.toList.map {
      case (userId, value) => GameScore(guildId.asString, gameId, userId.asString, value, endTimestamp)
    }
  }
}
