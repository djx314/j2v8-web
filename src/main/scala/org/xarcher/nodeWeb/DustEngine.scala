package org.xarcher.nodeWeb

import com.eclipsesource.v8._

import java.nio.charset.Charset
import java.nio.file.Files

import javax.inject.Singleton

import org.xarcher.urlParser.{ InfoWrap, ParseResult }

import play.api.mvc.{ AnyContent, Request }
import play.api.inject.ApplicationLifecycle

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

trait AsyncContentCols {
  val contentMap: Map[String, AsyncContent]
}

trait DustGen {

  def render(content: String, param: String, request: Request[AnyContent], parseResult: ParseResult, contents: AsyncContentCols, serverDataContent: AsyncContent, isDebug: Boolean): Future[String]

}

trait DustEngine {

  def renderString: Future[DustGen]

}

@Singleton
class DustEngineImpl @javax.inject.Inject() (
    applicationLifecycle1: ApplicationLifecycle,
    templateConfigure: TemplateConfigure,
    //common: ControllerComponents,
    dustExecutionWrap: DustExecution
)(implicit ec: ExecutionContext) extends NodeJSModule with DustEngine {

  override def applicationLifecycle = applicationLifecycle1

  override def dustExecution = dustExecutionWrap.singleThread
  override val defaultExecutionContext = ec

  def renderString = {
    if (templateConfigure.isNodeProd) {
      lazytRenderString
    } else {
      defRenderString
    }
  }

  private lazy val lazytRenderString = defRenderString

  def addPropertyTemplateString(v8: V8) = {
    val propertyTemplateString = Files.readAllLines(temDir.resolve("propertyTemp.html"), Charset.forName("utf-8")).asScala.toList.mkString("\n")
    Future {
      v8.add("propertyTemplateString", propertyTemplateString)
    }(dustExecution)
  }

  private def defRenderString: Future[DustGen] = (for {
    nodeJS <- nodeJSF
    v8 <- v8F
    _ = addPropertyTemplateString(v8)
    module <- moduleF
  } yield {
    (nodeJS, v8, module)
  }).flatMap {
    case (nodeJS, v8, module) =>
      val paramConvert = Future {

        val tempStr1 = Files.readAllLines(temDir.resolve("totalTemplate.html"), Charset.forName("utf-8")).asScala.toList.mkString("\n")
        module.executeJSFunction("addTemplate", "dbQuery", tempStr1)

        new DustGen {

          override def render(content: String, param: String, request: Request[AnyContent], parseResult: ParseResult, contents: AsyncContentCols, serverDataContent: AsyncContent, isDebug: Boolean): Future[String] = {
            var execQuery: V8Function = null
            var v8Query: V8Object = null
            var v8Request: V8Object = null
            var successCallback: V8Function = null
            var failureCallback: V8Function = null
            var v8Promise: V8Object = null
            var serverDataQuery: V8Function = null

            Future {
              v8Query = new V8Object(v8)
              val queryMap: Map[String, AsyncContent] = contents.contentMap.map { case (key, cntentApply) => key -> cntentApply }

              execQuery = new V8Function(v8, new JavaCallback {
                override def invoke(receiver: V8Object, parameters: V8Array): Object = {
                  val key = parameters.getString(0)
                  val param = parameters.getString(1)
                  val callback = parameters.getObject(2)
                  queryMap.get(key) match {
                    case Some(query) =>
                      query.exec(param, request).map { s =>
                        s match {
                          case Left(e) =>
                            throw e
                          case Right(data) =>
                            callback.executeJSFunction("callback", data)
                        }
                      }(dustExecution).andThen {
                        case Success(_) =>
                          callback.release()
                        case scala.util.Failure(e) =>
                          e.printStackTrace()
                          callback.release()
                      }(dustExecution)
                      null
                    case _ =>
                      callback.release()
                      throw new Exception(s"找不到键：${key}对应的查询方案")
                      null
                  }
                }

              })

              serverDataQuery = new V8Function(v8, new JavaCallback {
                override def invoke(receiver: V8Object, parameters: V8Array): Object = {
                  val serverData = parameters.getString(0)
                  val callback = parameters.getObject(1)
                  serverDataContent.exec(serverData, request).map { s =>
                    s match {
                      case Left(e) =>
                        throw e
                      case Right(data) =>
                        callback.executeJSFunction("callback", data)
                    }
                  }(dustExecution).andThen {
                    case Success(_) =>
                      callback.release()
                    case scala.util.Failure(e) =>
                      e.printStackTrace()
                      callback.release()
                  }(dustExecution)
                  null
                }

              })

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
              v8Query.add("serverData", serverDataQuery)

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
                      e.printStackTrace()
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
                      e.printStackTrace()
                      null
                  }
                }
              })

              v8Promise.add("success", successCallback)
              v8Promise.add("failure", failureCallback)

              module.executeJSFunction("outPut", content, param, isDebug.asInstanceOf[Object], v8Query, v8Promise)
              resultFuture
            }(dustExecution).flatMap(identity)(dustExecution)
              .andThen {
                case _ =>
                  Future {
                    nodeJS.handleMessage()

                    def closeJSAssets(v8Obj: V8Object): Boolean = {
                      if (v8Obj ne null) Try {
                        v8Obj.release()
                        true
                      } match {
                        case Failure(e) =>
                          logger.warn("回收 js 对象出现错误", e)
                          false
                        case Success(s) => s
                      }
                      else {
                        logger.warn("对象未正确创建，取消回收")
                        true
                      }
                    }

                    closeJSAssets(execQuery)
                    closeJSAssets(serverDataQuery)
                    closeJSAssets(v8Query)
                    closeJSAssets(v8Request)
                    closeJSAssets(successCallback)
                    closeJSAssets(failureCallback)
                    closeJSAssets(v8Promise)
                  }(dustExecution).andThen {
                    case Failure(e) =>
                      e.printStackTrace
                  }
              }
          }

        }
      }(dustExecution)

      paramConvert
  }

}