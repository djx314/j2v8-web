package org.xarcher.nodeWeb

import com.eclipsesource.v8._
import java.nio.charset.Charset
import java.nio.file.Files
import javax.inject.Singleton

import org.slf4j.LoggerFactory
import org.xarcher.urlParser.{ InfoWrap, ParseResult }
import play.api.mvc.{ AnyContent, Request }
import play.api.inject.ApplicationLifecycle

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Try }

trait AsyncContentCols {
  val contentMap: Map[String, AsyncContent]
}

trait DustGen {

  def render(content: String, param: String, request: Request[AnyContent], parseResult: ParseResult, /*serverDataContent: AsyncContent,*/ isDebug: Boolean): Future[String]

}

trait DustEngine {

  def renderString: DustGen

}

@Singleton
class DustEngineImpl @javax.inject.Inject() (
    applicationLifecycle: ApplicationLifecycle,
    templateConfigure: TemplateConfigure,
    asyncContents: AsyncContentCols,
    dustExecutionWrap: DustExecution
)(implicit ec: ExecutionContext) extends DustEngine {

  val logger = LoggerFactory.getLogger(classOf[DustEngine])

  //override def applicationLifecycle = applicationLifecycle1

  lazy val dustModule = {
    val module = NodeJSModule.create(asyncContents)(dustExecutionWrap.singleThread)(ec)
    applicationLifecycle.addStopHook { () =>
      Future.successful(module.close)
    }
    module
  }
  override def renderString = {
    if (templateConfigure.isNodeProd) {
      lazytRenderString
    } else {
      defRenderString
    }
  }

  private lazy val lazytRenderString = {
    defRenderString
  }

  def addPropertyTemplateString(v8: V8) = {

  }

  lazy val addTemplateAction = dustModule.moduleF.flatMap { module =>
    dustModule.execV8Job {
      val propertyTemplateString = Files.readAllLines(dustModule.temDir.resolve("propertyTemp.html"), Charset.forName("utf-8")).asScala.toList.mkString("\n")
      val tempStr1 = propertyTemplateString
      module.executeJSFunction("addTemplate", "dbQuery", tempStr1)
      tempStr1
    }
  }

  /*lazy val addPropertyTemplateStringAction: Future[String] = dustModule.v8F.flatMap { v8 =>
    dustModule.execV8Job {
      val propertyTemplateString = Files.readAllLines(dustModule.temDir.resolve("propertyTemp.html"), Charset.forName("utf-8")).asScala.toList.mkString("\n")
      v8.add("propertyTemplateString", propertyTemplateString)
      propertyTemplateString
    }
  }*/

  def releaseJSObject(v8Obj: V8Object): Future[Boolean] = {
    dustModule.execV8Job {
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

  private def defRenderString: DustGen = {
    (content: String, param: String, request: Request[AnyContent], parseResult: ParseResult, /*contents: AsyncContentCols, serverDataContent: AsyncContent,*/ isDebug: Boolean) =>
      (for {
        nodeJS <- dustModule.nodeJSF
        v8 <- dustModule.v8F
        //_ <- addPropertyTemplateStringAction
        module <- dustModule.moduleF
        _ <- addTemplateAction
      } yield {

        var execQuery: V8Function = null
        var v8Query: V8Object = null
        var v8Request: V8Object = null
        var successCallback: V8Function = null
        var failureCallback: V8Function = null
        var v8Promise: V8Object = null
        //var remoteJsonQuery: V8Function = null

        dustModule.execV8Job {
          v8Query = new V8Object(v8)
          val queryMap: Map[String, AsyncContent] = asyncContents.contentMap //.map { case (key, cntentApply) => key -> cntentApply }

          execQuery = new V8Function(v8, new JavaCallback {
            override def invoke(receiver: V8Object, parameters: V8Array): Object = {
              val key = parameters.getString(0)
              val param = parameters.getString(1)
              val callback = parameters.getObject(2).asInstanceOf[V8Function]
              val callBackParams = new V8Array(v8)
              queryMap.get(key) match {
                case Some(query) =>
                  (query.exec(io.circe.parser.parse(param).right.get, request).flatMap { s =>
                    dustModule.execV8Job {
                      s match {
                        case Left(e) =>
                          callBackParams.pushNull()
                          callBackParams.push(e.getMessage)
                          callback.call(null, callBackParams)
                        case Right(data) =>
                          callBackParams.push(data.noSpaces)
                          callBackParams.pushNull()
                          callback.call(null, callBackParams)
                      }
                      true
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

          /*remoteJsonQuery = new V8Function(v8, new JavaCallback {
            override def invoke(receiver: V8Object, parameters: V8Array): Object = {
              val serverData = parameters.getString(0)
              val callback = parameters.get(1).asInstanceOf[V8Function]
              val callBackParams = new V8Array(v8)
              (serverDataContent.exec(serverData, request).map { s =>
                s match {
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
              }(dustExecution).andThen {
                case s =>
                  (for {
                    _ <- releaseJSObject(callback)
                    _ <- releaseJSObject(callBackParams)
                  } yield {
                    true
                  }).map { _: Boolean =>
                    s match {
                      case Failure(e) =>
                        logger.error("执行 Remote Json 操作发生未知异常(由于上一层函数已经对外屏蔽错误,所以此错误为不可处理的异常)", e)
                      case _ =>
                    }
                  }
              }): Future[Boolean]
              null
            }
          })*/

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

          v8Query.add("query", execQuery)
          v8Query.add("request", v8Request)
          //v8Query.add("remoteJson", remoteJsonQuery)

          val promise = Promise[String]
          val resultFuture = promise.future
          v8Promise = new V8Object(v8)

          successCallback = new V8Function(v8, new JavaCallback() {
            override def invoke(receiver: V8Object, parameters: V8Array): Object = {
              try {
                promise.success(parameters.getString(0))
                null
              } catch {
                case e: Exception =>
                  logger.error("须处理的 j2v8 运行时错误", e)
                  null
              }
            }
          })

          failureCallback = new V8Function(v8, new JavaCallback() {
            override def invoke(receiver: V8Object, parameters: V8Array): Object = {
              try {
                promise.failure(new Exception(parameters.getString(0)))
                null
              } catch {
                case e: Exception =>
                  logger.error("须处理的 j2v8 运行时错误", e)
                  null
              }
            }
          })

          v8Promise.add("success", successCallback)

          v8Promise.add("failure", failureCallback)

          module.executeJSFunction("outPut", content, param, isDebug: java.lang.Boolean, v8Query, v8Promise)
          resultFuture
        }.flatten
          .andThen {
            case _: Try[String] =>
              (dustModule.execV8Job {
                nodeJS.handleMessage()
              }.flatMap { _ =>
                for {
                  _ <- releaseJSObject(execQuery)
                  //_ <- releaseJSObject(heperNames)
                  _ <- releaseJSObject(v8Query)
                  _ <- releaseJSObject(v8Request)
                  _ <- releaseJSObject(successCallback)
                  _ <- releaseJSObject(failureCallback)
                  _ <- releaseJSObject(v8Promise)
                } yield true
              }.recover {
                case e: Exception =>
                  e.printStackTrace
                  false
              }): Future[Boolean]
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