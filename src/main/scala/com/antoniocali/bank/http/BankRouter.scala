package com.antoniocali.bank.http
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import com.antoniocali.bank.actors.PersistentBankAccount.{Command, Response}
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.language.postfixOps

case class BankAccountCreateRequest(user: String, balance: Double) {
  def toCommand(replyTo: ActorRef[Response]): Command =
    Command.CreateBankAccount(
      user = user,
      initialBalance = balance,
      replyTo = replyTo
    )
}

case class FailureResponse(detail: String)

class BankRouter(bank: ActorRef[Command])(implicit system: ActorSystem[_]) {
  import scala.concurrent.duration._
  implicit val timeout: Timeout = Timeout(2 seconds)

  def createBankAccount(request: BankAccountCreateRequest): Future[Response] =
    bank.ask(replyTo => request.toCommand(replyTo))

  def getBankAccount(id: String): Future[Response] =
    bank.ask(replyTo => Command.GetBankAccount(id, replyTo))
  /*
    POST /bank/
      Payload: bank account creation request as JSON
      Response:
        201 CREATED
        Location: /bank/{uuid}

    GET /bank/{uuid}
      Payload: none
      Response:
        200 OK
        JSON repr of bank account details
   */

  val routes =
    pathPrefix("bank") {
      pathEndOrSingleSlash {
        post {
          // parse the payload
          entity(as[BankAccountCreateRequest]) { request =>
            /*
                - convert request into a Command for bank actor
                - send command to the bank
                - expect a reply
             */
            onSuccess(createBankAccount(request)) {
              // - send back a HTTP Response

              case Response.BankAccountCreatedResponse(id) =>
                respondWithHeader(Location(s"/bank/$id")) {
                  complete(StatusCodes.Created)
                }
            }
          }
        }
      } ~
        path(Segment) { id =>
          /*
              - send command to the bank
              - expect a reply
              - send back HTTP response
           */
          get {
            onSuccess(getBankAccount(id)) {
              case Response.GetBankAccountResponse(Some(account)) =>
                complete(account)
              case Response.GetBankAccountResponse(None) =>
                complete(
                  StatusCodes.NotFound,
                  FailureResponse(s"Bank $id not found")
                )
            }

          }
        }
    }

}
