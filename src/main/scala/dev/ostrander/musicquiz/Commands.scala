package dev.ostrander.musicquiz

import ackcord.commands.CommandController
import ackcord.commands.MessageParser.RemainingAsString
import ackcord.commands.NamedCommand
import ackcord.data.OutgoingEmbed
import ackcord.requests.Requests
import ackcord.syntax.TextChannelSyntax
import akka.actor.typed.ActorRef
import dev.ostrander.musicquiz.actor.GameManager
import dev.ostrander.musicquiz.stats.GameStats
import dev.ostrander.musicquiz.store.GameStore

trait Commands {
  def apply(): List[NamedCommand[RemainingAsString]]
}

object Commands {
  def apply(
    requests: Requests,
    gameActor: ActorRef[GameManager.Command],
    store: GameStore,
  ): List[NamedCommand[RemainingAsString]] = {
    val controller: Commands = new CommandController(requests) with Commands {
      val game: NamedCommand[RemainingAsString] =
        GuildVoiceCommand.named(Seq("!"), Seq("start"), mustMention = false).parsing[RemainingAsString].withSideEffects { r =>
          gameActor ! GameManager.CreateGame(r.textChannel, r.voiceChannel)
        }

      val reset: NamedCommand[RemainingAsString] =
        GuildCommand.named(Seq("!"), Seq("reset"), mustMention = false).parsing[RemainingAsString].withSideEffects { r =>
          gameActor ! GameManager.Reset(r.textChannel)
        }

      val leaderboard: NamedCommand[RemainingAsString] =
        GuildCommand.named(Seq("!"), Seq("leaderboard"), mustMention = false).parsing[RemainingAsString].withSideEffects { r =>
          store.gamesForGuild(r.guild.id).map { gss =>
            val embed = OutgoingEmbed(
              title = Some("__**HISTORIC LEADERBOARD**__"),
              description = Some(GameStats.Leaderboard(GameStats.stats(gss)).formatted),
            )
            requests.singleFuture(r.textChannel.sendMessage(embed = Some(embed)))
          }
        }

      override def apply(): List[NamedCommand[RemainingAsString]] = game :: reset :: leaderboard :: Nil
    }
    controller()
  }
}
