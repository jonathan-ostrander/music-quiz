package dev.ostrander.musicquiz

import ackcord.APIMessage
import ackcord.ClientSettings
import ackcord.gateway.GatewayIntents
import dev.ostrander.musicquiz.actor.GameManager
import dev.ostrander.musicquiz.store.GameStore
import io.getquill.PostgresAsyncContext
import io.getquill.SnakeCase

object MusicQuiz extends App {
  require(args.nonEmpty, "Please provide a token")
  val token = args.head

  val intents = GatewayIntents.All
  val clientSettings = ClientSettings(token, intents = intents)
  import clientSettings.executionContext
  val store = GameStore(new PostgresAsyncContext(SnakeCase, "database"))

  clientSettings.createClient().foreach { client =>
    val game = clientSettings.system.systemActorOf(GameManager(client, store), "Games")

    client.onEventSideEffects { cache =>
      {
        case APIMessage.Ready(_) => clientSettings.system.log.info("Now ready")
        case mc: APIMessage.MessageCreate => game ! GameManager.Message(mc)
      }
    }

    client.commands.bulkRunNamed(Commands(client.requests, game, store): _*)

    client.login()
  }
}
