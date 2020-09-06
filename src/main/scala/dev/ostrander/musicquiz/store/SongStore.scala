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
  def saveSongs(songs: List[Song]): Future[Unit]

  def getRandomSongs(n: Int): Future[List[Song]]
}

object SongStore {
  private[this] case class Songs(song: String) {
    def toSong: Song = song.parseJson.convertTo[Song]
  }
  private[this] object Songs {
    def fromSong(song: Song): Songs = Songs(songFormat.write(song).compactPrint)
  }

  def apply(ctx: PostgresAsyncContext[SnakeCase])(implicit executionCtx: ExecutionContext): SongStore = new SongStore {
    import ctx._

    def runBatch(songs: List[Songs]): Future[RunBatchActionResult] =
      run(liftQuery(songs).foreach(song => query[Songs].insert(song)))

    override def saveSongs(songs: List[Song]): Future[Unit] =
      runBatch(songs.map(Songs.fromSong)).map(_ => ())

    override def getRandomSongs(n: Int): Future[List[Song]] =
      run(infix"${query[Songs]} ORDER BY RANDOM() LIMIT ${lift(n)}".as[EntityQuery[Songs]])
        .map(r => r.map(_.toSong))
  }
}
