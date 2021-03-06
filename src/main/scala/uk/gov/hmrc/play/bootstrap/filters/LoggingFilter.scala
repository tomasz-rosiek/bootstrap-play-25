/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.bootstrap.filters

import java.util.Date
import javax.inject.Inject

import akka.stream.Materializer
import org.apache.commons.lang3.time.FastDateFormat
import org.joda.time.DateTimeUtils
import play.api.{Logger, LoggerLike}
import play.api.mvc.{Filter, RequestHeader, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.LoggingDetails
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.config.ControllerConfigs

import scala.concurrent.{ExecutionContext, Future}

trait LoggingFilter extends Filter {
  private val dateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSSZZ")

  def controllerNeedsLogging(controllerName: String): Boolean
  implicit def ec: ExecutionContext

  protected def logger:LoggerLike = Logger

  def buildLoggedHeaders(request: RequestHeader): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)

  def apply(next: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    implicit val hc = buildLoggedHeaders(rh)
    val startTime = DateTimeUtils.currentTimeMillis()

    val result = next(rh)

    if (needsLogging(rh)) logString(rh, result, startTime).map(logger.info(_))

    result
  }

  private def needsLogging(request: RequestHeader): Boolean = {
    (for {
      name <- request.tags.get(play.routing.Router.Tags.ROUTE_CONTROLLER)
    } yield controllerNeedsLogging(name)).getOrElse(true)
  }

  private def logString(rh: RequestHeader, result: Future[Result], startTime: Long)(implicit ld: LoggingDetails): Future[String] = {
    val start = dateFormat.format(new Date(startTime))
    def elapsedTime = DateTimeUtils.currentTimeMillis() - startTime

    result.map {
      result => s"${ld.requestChain.value} $start ${rh.method} ${rh.uri} ${result.header.status} ${elapsedTime}ms"
    }.recover {
      case t => s"${ld.requestChain.value} $start ${rh.method} ${rh.uri} ${t} ${elapsedTime}ms"
    }
  }
}

class DefaultLoggingFilter @Inject() (config: ControllerConfigs)
                                     (implicit
                                     override val mat: Materializer,
                                     override val ec: ExecutionContext
                                     ) extends LoggingFilter {

  override def controllerNeedsLogging(controllerName: String): Boolean =
    config.get(controllerName).logging
}