package org.xarcher.nodeWeb

import com.eclipsesource.v8._
import javax.inject.Singleton

import org.slf4j.LoggerFactory
import org.xarcher.urlParser.{InfoWrap, ParseResult}
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

trait PlayGen {
  def render(content: String, request: Request[AnyContent], parseResult: ParseResult, isDebug: Boolean): Future[String]
  def addTemplate(templateName: String, content: String): Future[Boolean]
}

trait PlayEngine {

  def renderString: PlayGen

}

@Singleton
class PlayEngineImpl @javax.inject.Inject() (
  dustEngine: DustEngine,
  asyncContents: AsyncContentCols,
)(implicit ec: ExecutionContext) extends PlayEngine {

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

  private def defRenderString: PlayGen = new PlayGen {

    override def addTemplate(templateName: String, content: String): Future[Boolean] = {
      dustEngine.renderString.addTemplate(templateName, content)
    }

    override def render(content: String, request: Request[AnyContent], parseResult: ParseResult, isDebug: Boolean) = {
      val executor = new V8Executor {
        override def exec[T](body: => T): Future[T] = {
          dustEngine.dustModule.execV8Job(body)
        }
      }

      (for {
        v8 <- dustEngine.dustModule.v8F
      } yield {

        var execQuery: V8Function = null
        var v8Context: V8Object = null
        var v8Request: V8Object = null

        dustEngine.dustModule.execV8Job {
          v8Context = new V8Object(v8)


          val queryMap: Map[String, AsyncContent] = asyncContents.contentMap //.map { case (key, cntentApply) => key -> cntentApply }

          execQuery = new V8Function(v8, new JavaCallback {
            override def invoke(receiver: V8Object, parameters: V8Array): Object = {
              val key = parameters.getString(0)
              val param = parameters.getString(1)
              val callback = parameters.getObject(2).asInstanceOf[V8Function]
              val callBackParams = new V8Array(v8)
              queryMap.get(key) match {
                case Some(query) =>
                  (query.exec(io.circe.parser.parse(param).right.get, request, executor, v8).flatMap { s =>
                    dustEngine.dustModule.execV8Job {
                      s.result match {
                        case Left(e) =>
                          callBackParams.pushNull()
                          callBackParams.push(e.getMessage)
                          callback.call(null, callBackParams)
                        case Right(data) =>
                          callBackParams.push(data)
                          callBackParams.pushNull()
                          callback.call(null, callBackParams)
                      }
                      true
                    }.andThen {
                      case _ =>
                        s.close
                    }
                  }.andThen {
                    case s =>
                      (for {
                        _ <- releaseJSObject(callback)
                        _ <- releaseJSObject(callBackParams)
                      } yield {
                        true
                      }).map { _: Boolean =>
                        s match {
                          case Failure(e) =>
                            logger.error("执行 exec query 操作发生未知异常(由于上一层函数已经对外屏蔽错误,所以此错误为不可处理的异常)", e)
                          case _ =>
                        }
                      }
                  }): Future[Boolean]
                case _ =>
                  for {
                    _ <- releaseJSObject(callback)
                    _ <- releaseJSObject(callBackParams)
                  } yield {
                    true
                  }
                  throw new Exception(s"找不到键：${key}对应的查询方案")
              }
              null
            }

          })

          v8Context.add("scala-query", execQuery)

          v8Request = new V8Object(v8)

          parseResult.infos.foreach {
            case InfoWrap(url, path, typeRef) =>
              typeRef match {
                case "String" => v8Request.add(path, url)
                case "Boolean" => v8Request.add(path, java.lang.Boolean.valueOf(url))
                case "Int" => v8Request.add(path, Integer.valueOf(url))
                case "Double" => v8Request.add(path, java.lang.Double.valueOf(url))
                case _ => throw new Exception("不可识别的参数类型")
              }
          }

          request.queryString.foreach {
            case (key, values) =>
              v8Request.add(key, values(0))
          }

          v8Context.add("request", v8Request)
          v8Context.add("isDebug", isDebug)

          dustEngine.renderString.render(content, v8Context)
        }.flatten
          .andThen {
            case _: Try[String] =>
              (for {
                _ <- releaseJSObject(execQuery)
                _ <- releaseJSObject(v8Request)
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