package com.antoniocali.bank.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

// a single bank account
class PersistentBankAccount {

  /*
   - fault tolerance
   - auditing
   */

  //  commands
  sealed trait Command
  case class CreateBankAccount(
      user: String,
      initialBalance: Double,
      replyTo: ActorRef[Response]
  ) extends Command
  case class UpdateBalance(
      id: String,
      amount: Double,
      replyTo: ActorRef[Response]
  ) extends Command
  case class GetBankAccount(id: String, replyTo: ActorRef[Response])
      extends Command

  // events = to persiste to Cassandra
  trait Event
  case class BankAccountCreated(bankAccount: BankAccount) extends Event
  case class BalanceUpdated(amount: Double) extends Event

  // state
  case class BankAccount(id: String, user: String, balance: Double)

  //response
  sealed trait Response
  case class BankAccountCreatedResponse(id: String) extends Response
  case class BankAccountBalanceUpdatedResponse(
      maybeBankAccount: Option[BankAccount]
  ) extends Response
  case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount])
      extends Response

  // command handler = message handler => persist event
  // event handler => update state
  // state
  val commandHandler: (BankAccount, Command) => Effect[Event, BankAccount] =
    (state, command) =>
      command match {
        case CreateBankAccount(user, initialBalance, bank) =>
          /*
           - bank creates me
           - bank sends me CreateBankAccount
           - I persist BankAccountCrated
           - I update my state
           - reply back to bank with the BankAccountCreatedResponse
           */
          val id = state.id
          Effect
            .persist(
              BankAccountCreated(
                BankAccount(id, user = user, balance = initialBalance)
              )
            )
            .thenReply(bank)(_ => BankAccountCreatedResponse(id = id))
        case UpdateBalance(_, amount, bank) =>
          val newBalance = state.balance + amount
          // check for withdraw
          if (newBalance < 0) // illegal
            Effect.reply(replyTo = bank)(
              BankAccountBalanceUpdatedResponse(None)
            )
          else {
            Effect
              .persist(BalanceUpdated(amount = amount))
              .thenReply(replyTo = bank)(newState =>
                BankAccountBalanceUpdatedResponse(Some(newState))
              )
          }
        case GetBankAccount(+, bank) =>
          Effect.reply(replyTo = bank)(GetBankAccountResponse(Some(state)))
      }
  val eventHandler: (BankAccount, Event) => BankAccount = (state, event) =>
    event match {
      case BalanceUpdated(amount) =>
        state.copy(balance = state.balance + amount)
      case BankAccountCreated(bankAccount) =>
        bankAccount
    }

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, BankAccount](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = BankAccount(id, "", 0.0), //unused
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )
}
