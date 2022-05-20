package com.antoniocali.bank.app

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.antoniocali.bank.actors.Bank
import com.antoniocali.bank.actors.PersistentBankAccount.Command
import com.antoniocali.bank.http.BankRouter

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object BankApp extends App {

  def startHttpServer(
      bank: ActorRef[Command]
  )(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    val router = new BankRouter(bank = bank)
    val routes = router.routes

    val httpBindingFuture = Http().newServerAt("localhost", 8080).bind(routes)
    httpBindingFuture.onComplete {
      case Failure(exception) =>
        system.log.error(s"Failed to bind HTTP Server, because $exception")
        system.terminate()
      case Success(binding) =>
        val localAddress = binding.localAddress
        system.log.info(
          s"Server online at https://${localAddress.getAddress}:${localAddress.getPort}"
        )
    }
  }

  trait RootCommand
  case class RetrieveBankActor(replyTo: ActorRef[ActorRef[Command]])
      extends RootCommand

  val rootBehavior: Behavior[RootCommand] = Behaviors.setup { context =>
    val bankActor = context.spawn(Bank(), "bank")

    Behaviors.receiveMessage { case RetrieveBankActor(replyTo) =>
      replyTo ! bankActor
      Behaviors.same
    }
  }

  implicit val system: ActorSystem[RootCommand] =
    ActorSystem(rootBehavior, "BankSystem")
  implicit val timeout: Timeout = Timeout(2.seconds)
  implicit val ec: ExecutionContext = system.executionContext

  val bankActorFuture: Future[ActorRef[Command]] =
    system.ask(replyTo => RetrieveBankActor(replyTo = replyTo))

  bankActorFuture.foreach(startHttpServer)

}
