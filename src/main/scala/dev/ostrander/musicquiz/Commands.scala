package dev.ostrander.musicquiz

import ackcord.commands.CommandController
import ackcord.commands.MessageParser.RemainingAsString
import ackcord.commands.NamedCommand
import ackcord.requests.Requests
import akka.actor.typed.ActorRef
import dev.ostrander.musicquiz.actor.GameManager

class Commands(
  requests: Requests,
  gameActor: ActorRef[GameManager.Command],
) extends CommandController(requests) {
  val game: NamedCommand[RemainingAsString] =
    GuildVoiceCommand.named("!", Seq("start"), mustMention = false).parsing[RemainingAsString].withSideEffects { r =>
      gameActor ! GameManager.CreateGame(r.textChannel, r.voiceChannel)
    }
}
