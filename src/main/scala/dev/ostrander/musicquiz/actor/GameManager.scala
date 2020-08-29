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
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

object GameManager {
  sealed trait Command
  case class CreateGame(textChannel: TextGuildChannel, voiceChannel: VoiceGuildChannel) extends Command
  case class CreateGameWithBehavior(cg: CreateGame, duration: FiniteDuration, behavior: Behavior[Game.Command])
      extends Command
  case class GameCreated(createGame: CreateGame, ref: ActorRef[Game.Command]) extends Command
  case class Message(mc: MessageCreate) extends Command
  case class EndGame(textChannel: TextGuildChannel) extends Command
  case class Reset(textChannel: TextGuildChannel) extends Command

  def apply(client: DiscordClient)(implicit ec: ExecutionContext): Behavior[Command] = {
    def behavior(games: Map[TextChannelId, ActorRef[Game.Command]]): Behavior[Command] =
      Behaviors.receive[Command] {
        case (ctx, cg @ CreateGame(tc, vc)) =>
          ctx.pipeToSelf(Game(client, tc, vc)) {
            case Success(d -> behavior) => CreateGameWithBehavior(cg, d, behavior)
            case Failure(exception) => sys.error(exception.toString)
          }
          Behaviors.same
        case (ctx, CreateGameWithBehavior(cg, duration, behavior)) =>
          val ref = ctx.spawnAnonymous(behavior)
          ctx.watchWith(ref, EndGame(cg.textChannel))
          ctx.scheduleOnce(duration, ref, Game.NewSong)
          ctx.self ! GameCreated(cg, ref)
          Behaviors.same
        case (ctx, GameCreated(cg, ref)) =>
          cg.textChannel match {
            case tc: TextChannel => behavior(games + (tc.id -> ref))
            case _ => Behaviors.same
          }
        case (ctx, Message(mc)) =>
          if (!mc.message.authorUser(mc.cache.current).exists(_.bot.exists(identity)))
            games.get(mc.message.channelId).foreach(_ ! Game.InGame(mc))
          Behaviors.same
        case (ctx, EndGame(tc)) =>
          behavior(games - tc.id)
        case (ctx, Reset(tc)) =>
          games.get(tc.id).foreach(_ ! Game.EndGame)
          behavior(games - tc.id)
      }
    behavior(Map.empty)
  }
}
