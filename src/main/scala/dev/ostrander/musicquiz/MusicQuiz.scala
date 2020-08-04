package dev.ostrander.musicquiz

import ackcord.APIMessage
import ackcord.ClientSettings
import ackcord.gateway.GatewayIntents
import akka.actor.typed.ActorSystem
import dev.ostrander.musicquiz.actor.GameManager

object MusicQuiz extends App {
  require(args.nonEmpty, "Please provide a token")
  val token = args.head

  val clientSettings = ClientSettings(token, intents = GatewayIntents.fromInt(36768832))
  import clientSettings.executionContext

  clientSettings.createClient().foreach { client =>    
    val game = ActorSystem(GameManager(client), "Games")
    val commands = new Commands(client.requests, game)

    client.onEventSideEffects { cache =>
      {
        case APIMessage.Ready(_) => clientSettings.system.log.info("Now ready")
        case mc: APIMessage.MessageCreate =>
          clientSettings.system.log.info(s"received message: ${mc.message.content}")
          game ! GameManager.Message(mc)
      }
    }

    client.commands.runNewNamedCommand(commands.game)

    client.login()
  }
}
