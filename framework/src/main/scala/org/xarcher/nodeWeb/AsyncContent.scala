package org.xarcher.nodeWeb

import io.circe.Decoder
import io.circe.parser
import io.circe.syntax._
import io.circe.generic.auto._
import net.scalax.fsn.slick.model.JsonOut
import play.api.mvc.{ AnyContent, Request }

import scala.concurrent.{ ExecutionContext, Future }

trait AsyncContent {

  val request: Request[AnyContent]

  implicit val ec: ExecutionContext

  def exec(content: String): Future[Either[Exception, String]]

}

abstract class AsyncContentImpl(override val request: Request[AnyContent])(override implicit val ec: ExecutionContext) extends AsyncContent

case class AsyncParam[T](param: T, slickParam: String, isDebug: Boolean)

trait AsyncJsonContent extends AsyncContent {

  type ParamType
  implicit val decoder: Decoder[ParamType]
  def execJson(data: AsyncParam[ParamType]): Future[Either[Exception, String]]

  override def exec(content: String): Future[Either[Exception, String]] = {
    parser.parse(content) match {
      case Left(s) =>
        Future successful Left(s)
      case Right(s) =>
        s.as[AsyncParam[ParamType]](implicitly[Decoder[AsyncParam[ParamType]]]) match {
          case Left(s) =>
            Future successful Left(s)
          case Right(s) =>
            execJson(s)
        }
    }
  }

}

trait AsyncJsonContentImpl[T] extends AsyncJsonContent {
  override type ParamType = T
}

//case class PageParam[T](param: T, slickParam: Option[SlickParam])

trait AsyncJsonOutContent[T] extends AsyncJsonContentImpl[T] {

  val db: slick.basic.BasicBackend#Database
  //type SimpleParamType = T
  //implicit val simpleDecoder: Decoder[T]
  //override val decoder = implicitly[Decoder[PageParam[T]]]
  def ubw(data: T): Future[JsonOut]

  override def execJson(data: AsyncParam[T]): Future[Either[Exception, String]] = {
    ubw(data.param).flatMap { s =>
      //throw new Exception("哈哈哈哈哈")
      db.run(s.data.apply(SlickParamUtils.parse(data.slickParam)).resultAction).map { t =>
        /*if (data.isDebug) {
          Map("data" -> t._1.asJson, "properties" -> s.properties.asJson).asJson.noSpaces
        } else {
          Map("data" -> t._1.asJson).asJson.noSpaces
        }*/
        Map("data" -> t.data.asJson, "size" -> t.sum.asJson, "properties" -> s.properties.asJson).asJson.noSpaces
      }.map(s => Right(s): Either[Exception, String])
    }.recover {
      case e: Exception =>
        e.printStackTrace()
        Left(e): Either[Exception, String]
    }
  }

}

object AsyncContent {

  def jsonOutContent[T](jsonOut: T => Future[JsonOut], db1: slick.basic.BasicBackend#Database, request1: Request[AnyContent])(implicit decoder1: Decoder[T], ec1: ExecutionContext): AsyncJsonOutContent[T] = {

    new AsyncJsonOutContent[T] {
      override val request = request1
      override val ec = ec1
      override val decoder = decoder1
      override val db = db1
      override def ubw(data: T): Future[JsonOut] = jsonOut(data)
    }

  }

  trait AsyncHelper {
    self =>
    val db: slick.basic.BasicBackend#Database
    implicit val ec: ExecutionContext
    val request: Request[AnyContent]

    def gen[T](jsonOut: T => Future[JsonOut])(implicit decoder1: Decoder[T]): AsyncJsonOutContent[T] = {
      jsonOutContent(jsonOut, db, request)(decoder1, ec)
    }
  }

  def helper(db1: slick.basic.BasicBackend#Database, request1: Request[AnyContent], ec1: ExecutionContext): AsyncHelper = {
    new AsyncHelper {
      override val db = db1
      override val ec = ec1
      override val request = request1
    }
  }

}