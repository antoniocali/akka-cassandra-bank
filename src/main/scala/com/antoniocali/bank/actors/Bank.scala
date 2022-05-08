package com.antoniocali.bank.actors

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout
import com.antoniocali.bank.actors.PersistentBankAccount.{Command, Response}

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Bank {

  // commands

  // events
  sealed trait Event
  case class BankAccountCreated(id: String) extends Event

  // state
  case class State(accounts: Map[String, ActorRef[Command]])

  // command handler
  def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] =
    (state, command) =>
      command match {
        case createCommand @ Command.CreateBankAccount(
              _,
              _,
              _
            ) =>
          val id = UUID.randomUUID().toString
          val newBankAccount = {
            context.spawn(PersistentBankAccount(id = id), name = id)
          }
          Effect
            .persist(BankAccountCreated(id))
            .thenReply(newBankAccount)(_ => createCommand)

        case updateCommand @ Command.UpdateBalance(id, _, replyTo) =>
          state.accounts.get(id) match {
            case Some(account) => Effect.reply(account)(updateCommand)
            case None =>
              Effect.reply(replyTo)(
                Response.BankAccountBalanceUpdatedResponse(None)
              )
          }
        case getBankAccountCommand @ Command.GetBankAccount(id, replyTo) =>
          state.accounts.get(id) match {
            case Some(account) => Effect.reply(account)(getBankAccountCommand)
            case None =>
              Effect.reply(replyTo)(
                Response.GetBankAccountResponse(None)
              )
          }
      }

  // event handler
  def eventHandler(context: ActorContext[Command]): (State, Event) => State =
    (state, event) =>
      event match {
        case BankAccountCreated(id) =>
          val account = context
            .child(id) // exists after command handler but not in recovery mode
            .getOrElse(
              context.spawn(PersistentBankAccount(id = id), name = id)
            )
            .asInstanceOf[ActorRef[Command]]
          state.copy(accounts = state.accounts + (id -> account))
      }

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = State(Map.empty[String, ActorRef[Command]]),
      commandHandler = commandHandler(context = context),
      eventHandler = eventHandler(context = context)
    )
  }
}

object BankPlayground extends App {
  import scala.concurrent.duration._
  val rootBehavior: Behavior[NotUsed] = Behaviors.setup { context =>
    val logger = context.log
    val bank = context.spawn(Bank(), "bank")
    // ask pattern
    val responseHandler = context.spawn(
      Behaviors.receiveMessage[Response] {
        case Response.BankAccountCreatedResponse(id) =>
          logger.info(s"successfully create $id")
          Behaviors.same
        case Response.BankAccountBalanceUpdatedResponse(maybeBankAccount) =>
          maybeBankAccount match {
            case Some(account) => logger.info(s"account $account")
            case None          => logger.error(s"wrong account")
          }
          Behaviors.same
        case Response.GetBankAccountResponse(maybeBankAccount) =>
          maybeBankAccount match {
            case Some(account) => logger.info(s"Retrieved $account")
            case None          => logger.error(s"Error")
          }
          Behaviors.same
      },
      "replyHandler"
    )
    implicit val timeout: Timeout = Timeout(2.seconds)
    implicit val scheduler: Scheduler = context.system.scheduler
    implicit val executionContext: ExecutionContext = context.executionContext
//    bank ! Command.CreateBankAccount("antonio", 10, responseHandler)
    bank ! Command.GetBankAccount(
      "3d2c4735-d8b3-4312-84c5-cbd0b8c76ab5",
      replyTo = responseHandler
    )
    Behaviors.empty
  }
  val system = ActorSystem(rootBehavior, "BankDemo")

}
