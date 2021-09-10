package dev.ostrander.musicquiz.actor

import ackcord.APIMessage.MessageCreate
import ackcord.DiscordClient
import ackcord.data.OutgoingEmbed
import ackcord.data.OutgoingEmbedFooter
import ackcord.data.OutgoingEmbedThumbnail
import ackcord.data.TextGuildChannel
import ackcord.data.User
import ackcord.data.UserId
import ackcord.data.VoiceGuildChannel
import ackcord.syntax.MessageSyntax
import ackcord.syntax.TextChannelSyntax
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.ostrander.musicquiz.model.Song
import dev.ostrander.musicquiz.store.GameStore
import dev.ostrander.musicquiz.store.SongStore
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object Game {
  sealed trait Command
  case object NewSong extends Command
  case class InGame(message: MessageCreate) extends Command {
    def user: Option[User] = message.message.authorUser(message.cache.current)
    def isUser: Boolean = user.exists(_.isUser)
    def username: String = user.map(_.username).getOrElse("Unidentifiable User")
  }
  case class Answer(message: MessageCreate, result: AnswerResult) extends Command
  case class Timeout(songNumber: Int) extends Command
  case object EndGame extends Command

  sealed trait Correct
  case object Artist extends Correct
  case object Title extends Correct

  case class AnswerResult(corrects: Set[Correct]) {
    def incorrect: Boolean = corrects.isEmpty
    def diff(actual: List[Correct]): AnswerResult = AnswerResult(corrects -- actual)
  }
  object AnswerResult {
    def apply(song: Song, content: String): AnswerResult =
      AnswerResult(
        List(
          Some(Artist).filter(_ => song.isArtist(content)),
          Some(Title).filter(_ => song.isTitle(content)),
        ).flatten.toSet,
      )
  }

  private[this] val gameLength = 15
  private[this] val medals = Map(0 -> "🥇", 1 -> "🥈", 2 -> "🥉")
  case class Score(value: Map[UserId, Int]) {
    def formattedScore: String = value.toList.filter(_._2 > 0).sortBy(-_._2).zipWithIndex.map {
      case ((id, score), i) =>
        val place = medals.get(i).getOrElse(s"#${i + 1}")
        val spacing = if (medals.contains(i)) "\n" else ""
        s"$place - <@${id.asString}> - $score pts$spacing"
    }.mkString("\n")
    def songEmbed(song: Song, songNum: Int): OutgoingEmbed =
      OutgoingEmbed(
        title = Some(s"**That was: ${song.title} by ${song.artists.map(_.name).mkString(" & ")}**"),
        thumbnail = Some(OutgoingEmbedThumbnail(song.albumCoverUrl)),
        description = Some(
          s"__**LEADERBOARD**__\n\n$formattedScore",
        ),
        footer = Some(OutgoingEmbedFooter(s"Music Quiz - track $songNum/$gameLength")),
      )
    def endGameEmbed: OutgoingEmbed =
      OutgoingEmbed(
        title = Some("**Music Quiz Ranking**"),
        description = Some(formattedScore),
      )
  }

  val startEmbed = OutgoingEmbed(
    title = Some("🎶 **The Music Quiz will start shortly!**"),
    description = Some(
      s""" | This game will have $gameLength song previews, 30 seconds per song.
          |
          | You'll have to guess the artist name and the song name.
          |
          | + 1 point for the song name
          | + 1 point for the artist name
          | + 3 points for both
          |
          | 🔥 Sit back and relax, the music quiz is starting in **10 seconds!**""".stripMargin('|'),
    ),
  )
  val countdownUrl = "https://www.youtube.com/watch?v=SXNgdClqhME"

  val playerManager: AudioPlayerManager = new DefaultAudioPlayerManager
  AudioSourceManagers.registerRemoteSources(playerManager)

  def apply(
    client: DiscordClient,
    textChannel: TextGuildChannel,
    voiceChannel: VoiceGuildChannel,
    gameStore: GameStore,
    songStore: SongStore,
  )(implicit ec: ExecutionContext): Future[(FiniteDuration, Behavior[Command])] = {
    val joinChannel = client.joinChannel(voiceChannel.guildId, voiceChannel.id, playerManager.createPlayer())
    val loadTrack = client.loadTrack(playerManager, countdownUrl).map {
      case at: AudioTrack => at
      case _ => sys.error("Failed to load starting track")
    }
    val songsFuture: Future[List[Song]] = songStore.getRandomSongs(gameLength)
    val tracksFuture =
      songsFuture.flatMap { songs =>
        Future.sequence {
          songs.map(s =>
            client.loadTrack(playerManager, s.preview).map {
              case at: AudioTrack => at
              case _ => sys.error("Failed to load audio track")
            },
          )
        }
      }

    for {
      player <- joinChannel
      track <- loadTrack
      songs <- songsFuture
      tracks <- tracksFuture
    } yield {
      player.startTrack(track, false)
      client.setPlaying(voiceChannel.guildId, true)
      client.requests.singleFuture(textChannel.sendMessage(embed = Some(startEmbed)))
      (
        FiniteDuration(track.getDuration(), TimeUnit.MILLISECONDS),
        GameContext(client, textChannel, voiceChannel, gameStore, player, songs, tracks).start,
      )
    }
  }

  case class GameContext(
    client: DiscordClient,
    textChannel: TextGuildChannel,
    voiceChannel: VoiceGuildChannel,
    gameStore: GameStore,
    audioPlayer: AudioPlayer,
    songs: List[Song],
    quizTracks: List[AudioTrack],
  ) {
    case class QuestionState(number: Int, titleCorrect: Option[UserId], artistCorrect: Option[UserId]) {
      lazy val song: Song = songs(number)
      def previous: Option[Song] = if (number == 0) None else Some(songs(number - 1))
    }

    def start(implicit executionCtx: ExecutionContext): Behavior[Command] = behavior(Score(Map.empty), QuestionState(0, None, None))
    def behavior(
      score: Score,
      state: QuestionState,
    )(implicit executionCtx: ExecutionContext): Behavior[Command] =
      Behaviors.receive {
        case (ctx, NewSong) =>
          val songMessage = state.previous match {
            case None => Future.unit
            case Some(previous) =>
              val embed = textChannel.sendMessage(embed = Some(score.songEmbed(previous, state.number)))
              client.requests.singleFuture(embed).map(_ => ())
          }
          if (state.number < quizTracks.size) {
            val track = quizTracks(state.number)
            audioPlayer.startTrack(track, false)
            ctx.scheduleOnce(
              FiniteDuration(track.getDuration(), TimeUnit.MILLISECONDS),
              ctx.self,
              Timeout(state.number),
            )
          } else
            ctx.pipeToSelf(songMessage)(_ => EndGame)
          Behaviors.same
        case (ctx, InGame(mc)) =>
          ctx.self ! Answer(mc, AnswerResult(state.song, mc.message.content))
          Behaviors.same
        case (ctx, Answer(mc, result)) =>
          val alreadyGotten = List(state.artistCorrect.map(_ => Artist), state.titleCorrect.map(_ => Title)).flatten
          val actualResult = result.diff(alreadyGotten)
          val userId = mc.message.authorUserId
          ctx.log.info(s"Received answer: ${mc.message.content} with corrects ${actualResult.corrects}")
          if (actualResult.incorrect) {
            client.requests.singleFuture(mc.message.createReaction("❌"))
            val newScore = userId.map(id =>
              if (score.value.contains(id)) score
              else score.copy(value = score.value + (id -> 0))
            ).getOrElse(score)
            behavior(newScore, state)
          } else {
            client.requests.singleFuture(mc.message.createReaction("✅"))
            val (newState, scoreToAdd) =
              if (actualResult.corrects(Artist) && actualResult.corrects(Title))
                state.copy(titleCorrect = userId, artistCorrect = userId) -> 3
              else if (actualResult.corrects(Artist))
                state.copy(artistCorrect = userId) -> (if (state.titleCorrect == userId) 2 else 1)
              else state.copy(titleCorrect = userId) -> (if (state.artistCorrect == userId) 2 else 1)
            userId.foreach(id =>
              client.requests.singleFuture(
                textChannel.sendMessage(s"<@${id.asString}> Correct! You earn **$scoreToAdd pts**"),
              ),
            )
            val newScore = userId.map(id =>
              Score(score.value + (id -> (score.value.get(id).getOrElse(0) + scoreToAdd))),
            ).getOrElse(score)

            if (newState.artistCorrect.isDefined && newState.titleCorrect.isDefined) {
              ctx.self ! NewSong
              behavior(newScore, QuestionState(state.number + 1, None, None))
            } else
              behavior(newScore, newState)
          }
        case (ctx, Timeout(songNumber)) =>
          if (songNumber != state.number) Behaviors.same
          else {
            ctx.self ! NewSong
            behavior(score, QuestionState(state.number + 1, None, None))
          }
        case (ctx, EndGame) =>
          val embed = textChannel.sendMessage(embed = Some(score.endGameEmbed))
          client.requests.singleFuture(embed)
          gameStore.saveGame(voiceChannel.guildId, score)
          client.leaveChannel(voiceChannel.guildId, destroyPlayer = true)
          audioPlayer.destroy()
          Behaviors.stopped
      }
  }
}
