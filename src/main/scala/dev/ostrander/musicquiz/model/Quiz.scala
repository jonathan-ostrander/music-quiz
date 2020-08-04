package dev.ostrander.musicquiz.model

import scala.io.Source
import scala.util.Random
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat
import spray.json.enrichString
import org.apache.commons.text.similarity.JaccardSimilarity

case class Artist(name: String, href: String) {
  def isMatch(value: String): Boolean = (name :: name.split("&").toList).toList.exists(Quiz.isCorrect(_, value))
}

case class Song(
  artists: List[Artist],
  title: String,
  preview: String,
  url: String,
  albumCoverUrl: String,
) {
  def songOptions = title :: title.split('/').toList ++ title.split('(').toList.filterNot(t => t.contains("feat") || t.contains("with"))

  def isArtist(value: String): Boolean = artists.exists(_.isMatch(value))
  def isTitle(value: String): Boolean = songOptions.exists(Quiz.isCorrect(_, value))
}

object Quiz {
  implicit val artistFormat: JsonFormat[Artist] = jsonFormat2(Artist.apply)
  implicit val songFormat: JsonFormat[Song] = jsonFormat5(Song.apply)

  val songs: List[Song] = Source.fromResource("songs.json").getLines.mkString("\n").parseJson.convertTo[List[Song]]

  def random(n: Int): List[Song] = Random.shuffle(songs).take(n)

  private[this] val jaccard = new JaccardSimilarity()
  private[this] val threshold: Double = 0.69
  def isCorrect(answer: String, guess: String): Boolean = jaccard(answer.toLowerCase(), guess.toLowerCase()) >= threshold
}
