package org.xarcher.nodeWeb

import java.io.File
import java.util.UUID
import javax.inject.Singleton

import com.eclipsesource.v8._
import org.slf4j.LoggerFactory
import org.xarcher.nodeWeb.modules.CopyHelper
import org.xarcher.urlParser.{ InfoWrap, ParseResult }
import play.api.mvc.{ AnyContent, Request }
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.io.Source
import scala.util.{ Failure, Success, Try }

trait AsyncContentCols {
  val contentMap: Map[String, Request[AnyContent] => AsyncContent]
}

trait DustGen {

  def render(content: String, param: String, request: Request[AnyContent], parseResult: ParseResult, contents: AsyncContentCols, isDebug: Boolean): Future[String]

}

trait DustEngine {

  def renderString: Future[DustGen]

}

@Singleton
class DustEngineImpl @javax.inject.Inject() (
    applicationLifecycle: ApplicationLifecycle,
    templateConfigure: TemplateConfigure,
    //common: ControllerComponents,
    _ec: ExecutionContext,
    dustExecution: DustExecution
) extends DustEngine {

  val logger = LoggerFactory.getLogger(classOf[DustEngine])

  private implicit val ec = _ec

  def renderString = {
    if (templateConfigure.isNodeProd) {
      lazytRenderString
    } else {
      defRenderString
    }
  }

  val tempPath = System.getProperty("java.io.tmpdir")
  val dirName = UUID.randomUUID().toString
  val tempJSRoot = new File(tempPath, "enuma_nodejs_temp")
  val temDir = new File(tempJSRoot, dirName)

  def genNodeJS = {
    CopyHelper.copyFromClassPath(List("org", "xarcher", "nodeEnv", "assets"), temDir.toPath).flatMap { (_: Boolean) =>
      var nodeJS: NodeJS = null
      var v8: V8 = null
      var module: V8Object = null

      Future {
        nodeJS = NodeJS.createNodeJS()
        v8 = nodeJS.getRuntime

        val propertyTemplateString = Source.fromFile(new File(temDir, "propertyTemp.html"), "utf-8").getLines().mkString("\n")
        v8.add("propertyTemplateString", propertyTemplateString)
        module = nodeJS.require(new File(temDir, "dustTest.js"))
        (nodeJS, v8, module)
      }(dustExecution.singleThread)
        .andThen {
          case _ =>
            applicationLifecycle.addStopHook { () =>
              Future {
                logger.info("开始回收 node 资源")
                if (module ne null) Try {
                  module.release()
                  logger.info("回收 dust 模块资源完毕")
                }
                else {
                  logger.info("dust 模块资源未正确初始化，跳过回收")
                }
                if (nodeJS ne null) Try {
                  nodeJS.release()
                  logger.info("回收 nodejs 对象完毕")
                }
                else {
                  logger.info("nodejs 对象未正确初始化，跳过回收")
                }
                if (v8 ne null) Try {
                  v8.release()
                  logger.info("回收 v8 全局对象完毕")
                }
                else {
                  logger.info("v8 全局对象未正确初始化，跳过回收")
                }
                ()
              }(dustExecution.singleThread).andThen {
                case Failure(e) =>
                  logger.error("回收 j2v8 资源时出现错误", e)
                case Success(_: Unit) =>
                  logger.info("j2v8 资源回收完毕")
              }
            }

        }
    }
  }

  private lazy val lazytRenderString = defRenderString

  private def defRenderString: Future[DustGen] = genNodeJS.flatMap {
    case (nodeJS, v8, module) =>
      val paramConvert = Future {

        val tempStr1 = Source.fromFile(new File(temDir, "totalTemplate.html"), "utf-8").getLines().mkString("\n")
        module.executeJSFunction("addTemplate", "dbQuery", tempStr1)

        new DustGen {

          override def render(content: String, param: String, request: Request[AnyContent], parseResult: ParseResult, contents: AsyncContentCols, isDebug: Boolean): Future[String] = {
            var execQuery: V8Function = null
            var v8Query: V8Object = null
            var v8Request: V8Object = null
            var successCallback: V8Function = null
            var failureCallback: V8Function = null
            var v8Promise: V8Object = null

            Future {
              v8Query = new V8Object(v8)
              val queryMap: Map[String, AsyncContent] = contents.contentMap.map { case (key, cntentApply) => key -> cntentApply(request) }
              execQuery = new V8Function(v8, new JavaCallback {
                override def invoke(receiver: V8Object, parameters: V8Array): Object = {
                  val key = parameters.getString(0)
                  val param = parameters.getString(1)
                  val callback = parameters.getObject(2)
                  queryMap.get(key) match {
                    case Some(query) =>
                      query.exec(param).map { s =>
                        s match {
                          case Left(e) =>
                            throw e
                          case Right(data) =>
                            callback.executeJSFunction("callback", data)
                        }
                      }(dustExecution.singleThread).andThen {
                        case Success(_) =>
                          callback.release()
                        case scala.util.Failure(e) =>
                          e.printStackTrace()
                          callback.release()
                      }(dustExecution.singleThread)
                      null
                    case _ =>
                      callback.release()
                      throw new Exception(s"找不到键：${key}对应的查询方案")
                      null
                  }
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
            }(dustExecution.singleThread).flatMap(identity)(dustExecution.singleThread)
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
                    closeJSAssets(v8Query)
                    closeJSAssets(v8Request)
                    closeJSAssets(successCallback)
                    closeJSAssets(failureCallback)
                    closeJSAssets(v8Promise)
                  }(dustExecution.singleThread).andThen {
                    case Failure(e) =>
                      e.printStackTrace
                  }
              }
          }

        }
      }(dustExecution.singleThread)

      paramConvert
  }

}