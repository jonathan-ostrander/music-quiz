package dev.ostrander.musicquiz.stats

import dev.ostrander.musicquiz.store.GameScore

object GameStats {
  case class LeaderboardEntry(userId: String, gamesWon: Int, gamesPlayed: Int, totalPoints: Int)

  case class Leaderboard(entries: List[LeaderboardEntry]) {
    private[this] val sorted = entries.sortBy(-1*_.gamesWon)
    private[this] val medals = Map(0 -> "🥇", 1 -> "🥈", 2 -> "🥉")
    
    def formatted: String = sorted.toList.zipWithIndex.map {
      case (LeaderboardEntry(id, gamesWon, gamesPlayed, totalPoints), i) =>
        val place = medals.get(i).getOrElse(s"#${i + 1}")
        val spacing = if (medals.contains(i)) "\n" else ""
        s"$place - <@$id> - $gamesWon won, $gamesPlayed played, $totalPoints total pts$spacing"
    }.mkString("\n")
  }

  private[this] case class Position(score: GameScore, place: Int, participants: Int)
  def stats(scores: List[GameScore]): List[LeaderboardEntry] =
    scores
      .groupBy(_.gameId)
      .toList
      .filter(_._2.filter(_.score > 0).length > 1)
      .map(_._2)
      .flatMap { sl =>
        val allScores = sl.map(_.score).maxOption.map { winningScore =>
          sl.map(gs => gs -> (gs.score == winningScore))
        }.getOrElse(Nil)
        allScores
      }
      .groupBy(_._1.userId)
      .toList
      .map {
        case (userId, gameScores) =>
          gameScores.foldLeft(LeaderboardEntry(userId, 0, 0, 0)) {
            case (acc, (score, won)) =>
              val newWon = if (won) acc.gamesWon + 1 else acc.gamesWon
              LeaderboardEntry(userId, newWon, acc.gamesPlayed + 1, acc.totalPoints + score.score)
          }
      }
}
