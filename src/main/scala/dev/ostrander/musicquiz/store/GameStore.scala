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
}

object GameStore {
  def apply(ctx: PostgresAsyncContext[SnakeCase])(implicit executionCtx: ExecutionContext): GameStore = new GameStore {
    import ctx._

    def runBatch(scores: List[GameScore]): Future[RunBatchActionResult] =
      run(liftQuery(scores).foreach(score => query[GameScore].insert(score)))

    override def saveGame(guildId: GuildId, score: Game.Score): Future[Unit] =
      runBatch(GameScore.fromMap(guildId = guildId, map = score.value)).map(_ => ())
  }
}
