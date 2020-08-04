package dev.ostrander.musicquiz.model

import scala.io.Source
import scala.util.Random
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat
import spray.json.enrichString
import org.apache.commons.text.similarity.JaccardSimilarity


case class Song(
  artist: String,
  song: String,
  preview: String,
) {
  val artistOptions = artist :: artist.split(" & ").toList ++ artist.split(" Featuring ").toList
  val songOptions = song :: song.split("/").toList ++ song.split("(").toList

  def isArtist(value: String): Boolean = artistOptions.exists(Quiz.isCorrect(_, value))
  def isTitle(value: String): Boolean = songOptions.exists(Quiz.isCorrect(_, value))
}

object Quiz {
  implicit val songFormat: JsonFormat[Song] = jsonFormat3(Song.apply)

  val songs: List[Song] = Source.fromResource("songs.json").getLines.mkString("\n").parseJson.convertTo[List[Song]]

  def random(n: Int): List[Song] = Random.shuffle(songs).take(n)

  private[this] val jaccard = new JaccardSimilarity()
  private[this] val threshold: Double = 0.69
  def isCorrect(answer: String, guess: String): Boolean = jaccard(answer.toLowerCase(), guess.toLowerCase()) >= threshold
}
