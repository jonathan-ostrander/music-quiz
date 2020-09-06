package dev.ostrander.musicquiz.store

import ackcord.data.GuildId
import dev.ostrander.musicquiz.actor.Game
import io.getquill.PostgresAsyncContext
import io.getquill.SnakeCase
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait GameStore {
  def saveGame(
    guildId: GuildId,
    score: Game.Score,
  ): Future[Unit]

  def gamesForGuild(
    guildId: GuildId,
  ): Future[List[GameScore]]
}

object GameStore {
  def apply(ctx: PostgresAsyncContext[SnakeCase])(implicit executionCtx: ExecutionContext): GameStore = new GameStore {
    import ctx._

    def runBatch(scores: List[GameScore]): Future[RunBatchActionResult] =
      run(liftQuery(scores).foreach(score => query[GameScore].insert(score)))

    override def saveGame(guildId: GuildId, score: Game.Score): Future[Unit] =
      runBatch(GameScore.fromMap(guildId = guildId, map = score.value)).map(_ => ())

    override def gamesForGuild(guildId: ackcord.data.GuildId): Future[List[GameScore]] =
      run(query[GameScore].filter(_.guildId == lift(guildId.asString)))
  }
}
