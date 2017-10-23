package org.xarcher.nodeWeb

import com.eclipsesource.v8._
import javax.inject.Singleton

import io.circe.Json
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait CommonGen {
  def render(content: String, contextParam: Json = Json.Null): Future[String]
  def addTemplate(templateName: String, content: String): Future[Boolean]
}

trait CommonEngine {

  def renderString: CommonGen

}

@Singleton
class CommonEngineImpl @javax.inject.Inject() (
  dustEngine: DustEngine)(implicit ec: ExecutionContext) extends CommonEngine {

  val logger = LoggerFactory.getLogger(classOf[PlayEngine])

  override def renderString = {
    lazytRenderString
  }

  private lazy val lazytRenderString = {
    defRenderString
  }

  def releaseJSObject(v8Obj: V8Object): Future[Boolean] = {
    dustEngine.dustModule.execV8Job {
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

  private def defRenderString: CommonGen = new CommonGen {

    override def addTemplate(templateName: String, content: String): Future[Boolean] = {
      dustEngine.renderString.addTemplate(templateName, content)
    }

    override def render(content: String, contextParam: Json): Future[String] = {
      val executor = new V8Executor {
        override def exec[T](body: => T): Future[T] = {
          dustEngine.dustModule.execV8Job(body)
        }
      }

      (for {
        v8 <- dustEngine.dustModule.v8F
      } yield {

        var v8Context: V8Object = null

        dustEngine.dustModule.execV8Job {
          v8Context = new V8Object(v8)
          v8Context.add("param", contextParam.noSpaces)
          dustEngine.renderString.render(content, v8Context)
        }.flatten
          .andThen {
            case _: Try[String] =>
              (for {
                _ <- releaseJSObject(v8Context)
              } yield true).recover {
                case e: Exception =>
                  e.printStackTrace
                  false
              }: Future[Boolean]
          }
      })
        .flatten
        .recoverWith {
          case e: Exception =>
            logger.error("渲染 dust 模板发生不可预料的错误", e)
            val newE = new Exception(s"渲染 dust 模板发生不可预料的错误,错误信息\n:${e.getMessage}")
            newE.initCause(e)
            Future.failed(newE)
        }
    }
  }

}