package dev.ostrander.musicquiz.model

import scala.io.Source
import scala.util.Random
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat
import spray.json.enrichString

case class Song(
  artist: String,
  song: String,
  preview: String,
) {
  def isArtist(value: String): Boolean = true
  def isTitle(value: String): Boolean = true
}

object Quiz {
  implicit val songFormat: JsonFormat[Song] = jsonFormat3(Song.apply)

  val songs: List[Song] = Source.fromResource("songs.json").getLines.mkString("\n").parseJson.convertTo[List[Song]]

  def random(n: Int): List[Song] = Random.shuffle(songs).take(n)
}
