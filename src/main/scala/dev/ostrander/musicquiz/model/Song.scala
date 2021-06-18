package dev.ostrander.musicquiz.model

import org.apache.commons.text.similarity.JaccardSimilarity
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat

case class Song(
  id: String,
  artists: List[Song.Artist],
  title: String,
  preview: String,
  url: String,
  albumCoverUrl: String,
) {
  def songOptions = (
    title ::
      title.split('/').toList ++
      title.split('(').toList.filterNot(t => t.contains("feat") || t.contains("with")) ++
      title.split('-').toList ++
      title.split(')').toList.filterNot(t => t.contains("feat") || t.contains("with")) ++
      title.split('[').toList.filterNot(t => t.contains("feat") || t.contains("with")) ++
      title.split(']').toList.filterNot(t => t.contains("feat") || t.contains("with"))
  )
    .flatMap(t => t :: t.filter(_.isLetterOrDigit) :: Nil)
    .filter(_.length > 1)

  def isArtist(value: String): Boolean = artists.exists(_.isMatch(value))
  def isTitle(value: String): Boolean = songOptions.exists(Song.isCorrect(_, value))
}

object Song {
  case class Artist(name: String, href: String) {
    def isMatch(value: String): Boolean =
      (name :: name.split("&").toList ++ name.split("and").toList)
        .toList
        .flatMap(name => name :: name.filter(_.isLetterOrDigit) :: Nil)
        .exists(Song.isCorrect(_, value))
  }

  implicit val artistFormat: JsonFormat[Artist] = jsonFormat2(Artist.apply)
  implicit val songFormat: JsonFormat[Song] = jsonFormat6(Song.apply)

  private[this] val jaccard = new JaccardSimilarity()
  private[this] val threshold: Double = 0.8
  def isCorrect(answer: String, guess: String): Boolean =
    jaccard(answer.toLowerCase(), guess.toLowerCase()) >= threshold
}
