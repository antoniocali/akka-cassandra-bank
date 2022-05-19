package com.antoniocali.bank.http
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.model.headers.Location
import com.antoniocali.bank.actors.PersistentBankAccount.{Command, Response}
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Future

case class BankAccountCreateRequest(user: String, balance: Double)

class BankRouter(bank: ActorRef[Command]) {

  def createBankAccount(request: BankAccountCreateRequest): Future[Response] =
    ???

  /*
    POST /bank/
      Payload: bank account creation request as JSON
      Response:
        201 CREATED
        Location: /bank/{uuid}

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
                - send back a HTTP Response
             */
            onSuccess(createBankAccount(request)) {
              case Response.BankAccountCreatedResponse(id) =>
                respondWithHeader(Location(s"/bank/$id")) {
                  complete(StatusCodes.Created)
                }
            }
          }
        }
      }
    }

}
