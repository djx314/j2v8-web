package org.xarcher.nodeWeb

import com.eclipsesource.v8._
import javax.inject.Singleton

import org.slf4j.LoggerFactory
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

trait AsyncContentCols {
  val contentMap: Map[String, AsyncContent]
}

trait DustGen {

  def render(content: String, context: V8Object): Future[String]
  def addTemplate(templateName: String, content: String): Future[Boolean]

}

trait DustEngine {

  def renderString: DustGen
  def dustModule: NodeJSModule

}

@Singleton
class DustEngineImpl @javax.inject.Inject() (
  applicationLifecycle: ApplicationLifecycle,
  templateConfigure: TemplateConfigure,
  asyncContents: AsyncContentCols,
  dustExecutionWrap: DustExecution)(implicit ec: ExecutionContext) extends DustEngine {

  val logger = LoggerFactory.getLogger(classOf[DustEngine])

  //override def applicationLifecycle = applicationLifecycle1

  override lazy val dustModule: NodeJSModule = {
    val module = NodeJSModule.create(asyncContents.contentMap.keys.toList)(dustExecutionWrap.singleThread)(ec)
    applicationLifecycle.addStopHook { () =>
      module.close
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

  private def defRenderString: DustGen = new DustGen {

    override def addTemplate(templateName: String, content: String): Future[Boolean] = {
      dustModule.moduleF.flatMap { module =>
        dustModule.execV8Job {
          module.executeJSFunction("addTemplate", templateName, content)
          logger.info(s"添加了名为:$templateName 的模板")
          true
        }
      }.recover {
        case e: Exception =>
          logger.info("添加模板发生错误", e)
          false
      }
    }

    override def render(content: String, context: V8Object): Future[String] = {
      (for {
        nodeJS <- dustModule.nodeJSF
        v8 <- dustModule.v8F
        //_ <- addPropertyTemplateStringAction
        module <- dustModule.moduleF
        //_ <- addTemplateAction
      } yield {

        var successCallback: V8Function = null
        var failureCallback: V8Function = null
        var v8Promise: V8Object = null
        //var remoteJsonQuery: V8Function = null

        dustModule.execV8Job {

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
                  promise.failure(e)
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
                  promise.failure(e)
                  null
              }
            }
          })

          v8Promise.add("success", successCallback)

          v8Promise.add("failure", failureCallback)

          module.executeJSFunction("outPut", content, context, v8Promise)
          resultFuture
        }.flatten
          .andThen {
            case _: Try[String] =>
              (dustModule.execV8Job {
                nodeJS.handleMessage()
              }.flatMap { _ =>
                for {
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
    }
  }

}