package org.xarcher.nodeWeb

import com.eclipsesource.v8.{ V8, V8Object }
import io.circe.Json
import org.slf4j.LoggerFactory
import play.api.mvc.{ AnyContent, Request }

import scala.concurrent.{ ExecutionContext, Future }

trait AsyncResult extends AutoCloseable {
  val result: Either[Exception, V8Object]
  override def close: Unit
}

trait AsyncContent {

  val logger = LoggerFactory.getLogger(classOf[AsyncContent])

  def exec(content: Json, request: Request[AnyContent], v8Executor: V8Executor, v8: V8)(implicit ec: ExecutionContext): Future[AsyncResult]

  protected def releaseJSObject(v8Obj: V8Object, v8Executor: V8Executor)(implicit ec: ExecutionContext): Future[Boolean] = {
    v8Executor.exec {
      if (v8Obj ne null) {
        v8Obj.release()
        true
      } else {
        logger.warn("对象未正确创建，取消回收")
        true
      }
    }.recover {
      case e: Exception =>
        logger.warn("回收 js 对象出现错误", e)
        false
    }
  }

}

trait JsonContent extends AsyncContent {

  def execJson(content: Json, request: Request[AnyContent]): Future[Either[Exception, Json]]

  override def exec(content: Json, request: Request[AnyContent], v8Executor: V8Executor, v8: V8)(implicit ec: ExecutionContext): Future[AsyncResult] = {
    execJson(content, request).flatMap {
      case Left(e) =>
        Future.successful {
          new AsyncResult {
            override val result = Left(e)
            override def close: Unit = ()
          }
        }
      case Right(result) =>
        var v8Object: V8Object = null
        def closeJob = releaseJSObject(v8Object, v8Executor)

        v8Executor.exec {
          v8Object = new V8Object(v8)
          v8Object.add("_originalJson", result.noSpaces)
          new AsyncResult {
            override val result = Right(v8Object)
            override def close: Unit = closeJob
          }
        }
    }
  }

}

trait StringContent extends AsyncContent {

  def execString(content: Json, request: Request[AnyContent]): Future[Either[Exception, String]]

  override def exec(content: Json, request: Request[AnyContent], v8Executor: V8Executor, v8: V8)(implicit ec: ExecutionContext): Future[AsyncResult] = {
    execString(content, request).flatMap {
      case Left(e) =>
        Future.successful {
          new AsyncResult {
            override val result = Left(e)
            override def close: Unit = ()
          }
        }
      case Right(result) =>
        var v8Object: V8Object = null
        def closeJob = releaseJSObject(v8Object, v8Executor)

        v8Executor.exec {
          v8Object = new V8Object(v8)
          v8Object.add("_originalValue", result)
          new AsyncResult {
            override val result = Right(v8Object)
            override def close: Unit = closeJob
          }
        }
    }
  }

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