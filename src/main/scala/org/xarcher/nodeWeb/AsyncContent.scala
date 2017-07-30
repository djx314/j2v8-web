package org.xarcher.nodeWeb

import io.circe.Json
import play.api.mvc.{ AnyContent, Request }

import scala.concurrent.Future

trait AsyncContent {

  def exec(content: Json, request: Request[AnyContent]): Future[Either[Exception, Json]]

}
//case class AsyncParam[T](param: T, slickParam: String, isDebug: Boolean)
/*trait AsyncJsonContent extends AsyncContent {

  type ParamType
  implicit val decoder: Decoder[ParamType]
  def execJson(data: AsyncParam[ParamType], request: Request[AnyContent]): Future[Either[Exception, String]]

  override def exec(content: String, request: Request[AnyContent]): Future[Either[Exception, String]] = {
    parser.parse(content) match {
      case Left(s) =>
        Future successful Left(s)
      case Right(s) =>
        s.as[AsyncParam[ParamType]](implicitly[Decoder[AsyncParam[ParamType]]]) match {
          case Left(s) =>
            Future successful Left(s)
          case Right(s) =>
            execJson(s, request)
        }
    }
  }

}*/
/*trait AsyncJsonContentImpl[T] extends AsyncContent {
  override type ParamType = T
}
//case class PageParam[T](param: T, slickParam: Option[SlickParam])
trait AsyncJsonOutContent[T] extends AsyncJsonContentImpl[T] {

  val db: slick.basic.BasicBackend#Database
  def ubw(data: T, request: Request[AnyContent]): Future[JsonOut]

  implicit val ec: ExecutionContext

  override def execJson(data: AsyncParam[T], request: Request[AnyContent]): Future[Either[Exception, String]] = {
    ubw(data.param, request).flatMap { s =>
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

  def jsonOutContent[T](jsonOut: (T, Request[AnyContent]) => Future[JsonOut], db1: slick.basic.BasicBackend#Database)(implicit decoder1: Decoder[T], ec1: ExecutionContext): AsyncJsonOutContent[T] = {

    new AsyncJsonOutContent[T] {
      override val decoder = decoder1
      override val db = db1
      override val ec = ec1
      override def ubw(data: T, request: Request[AnyContent]): Future[JsonOut] = jsonOut(data, request)
    }

  }

  trait AsyncHelper {
    self =>
    val db: slick.basic.BasicBackend#Database
    implicit val ec: ExecutionContext

    def gen[T](jsonOut: (T, Request[AnyContent]) => Future[JsonOut])(implicit decoder1: Decoder[T]): AsyncJsonOutContent[T] = {
      jsonOutContent(jsonOut, db)(decoder1, ec)
    }
  }

  def helper(db1: slick.basic.BasicBackend#Database, ec1: ExecutionContext): AsyncHelper = {
    new AsyncHelper {
      override val db = db1
      override val ec = ec1
    }
  }

}*/ 