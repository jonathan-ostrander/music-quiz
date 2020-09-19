package dev.ostrander.musicquiz.store

import dev.ostrander.musicquiz.model.Song
import dev.ostrander.musicquiz.model.Song.songFormat
import io.getquill.EntityQuery
import io.getquill.PostgresAsyncContext
import io.getquill.SnakeCase
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import spray.json.enrichString

trait SongStore {
  def getRandomSongs(n: Int): Future[List[Song]]
}

object SongStore {
  private[this] case class QuizSongs(
    id: String,
    song: String,
  ) {
    def toSong: Song = song.parseJson.convertTo[Song]
  }

  def apply(ctx: PostgresAsyncContext[SnakeCase])(implicit executionCtx: ExecutionContext): SongStore = new SongStore {
    import ctx._

    override def getRandomSongs(n: Int): Future[List[Song]] =
      run(infix"${query[QuizSongs]} ORDER BY RANDOM() LIMIT ${lift(n)}".as[EntityQuery[QuizSongs]])
        .map(r => r.map(_.toSong))
  }
}
