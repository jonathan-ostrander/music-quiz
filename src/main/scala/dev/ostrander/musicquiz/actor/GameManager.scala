package dev.ostrander.musicquiz.actor

import ackcord.APIMessage.MessageCreate
import ackcord.DiscordClient
import ackcord.data.TextChannel
import ackcord.data.TextChannelId
import ackcord.data.TextGuildChannel
import ackcord.data.VoiceGuildChannel
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

object GameManager {
  sealed trait Command
  case class CreateGame(textChannel: TextGuildChannel, voiceChannel: VoiceGuildChannel) extends Command
  case class GameCreated(createGame: CreateGame, ref: ActorRef[Game.Command]) extends Command
  case class Message(mc: MessageCreate) extends Command
  case class EndGame(textChannel: TextGuildChannel) extends Command

  def apply(client: DiscordClient)(implicit ec: ExecutionContext): Behavior[Command] = {
    def behavior(games: Map[TextChannelId, ActorRef[Game.Command]]): Behavior[Command] = 
      Behaviors.receive[Command] {
        case (ctx, cg@CreateGame(tc, vc)) =>
          ctx.log.info("Received start message")
          ctx.pipeToSelf(Game(client, tc, vc)) {
            case Success(d -> behavior) =>
              ctx.log.info("SPAWNED GAME")
              val ref = ctx.spawn(behavior, tc.id.asString)
              ctx.scheduleOnce(d, ref, Game.NewSong)
              GameCreated(cg, ref)
            case Failure(exception) => sys.error(exception.toString)
          }
          Behaviors.same
        case (ctx, GameCreated(cg, ref)) =>
          cg.textChannel match {
            case tc: TextChannel =>
              ctx.log.info(s"Game created in ${tc.id}")
              behavior(games + (tc.id -> ref))
            case _ => Behaviors.same
          }
        case (ctx, Message(mc)) =>
          if (!mc.message.authorUser(mc.cache.current).exists(_.bot.exists(identity))) {
            ctx.log.info(s"Message received in ${mc.message.channelId}")
            ctx.log.info(s"Current games: $games")
            games.get(mc.message.channelId).foreach(_ ! Game.InGame(mc))
          }
          Behaviors.same
        case (ctx, EndGame(tc)) =>
          // End game message
          // games.get(mc.message.channelId).foreach(_ ! mc)
          behavior(games - tc.id)
      }
    behavior(Map.empty)
  }
}
