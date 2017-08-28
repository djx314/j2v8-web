package org.xarcher.nodeWeb

import com.eclipsesource.v8._
import java.nio.file.Paths
import java.util.UUID

import org.slf4j.LoggerFactory
import org.xarcher.nodeWeb.modules.CopyHelper

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Properties, Success }

trait NodeJSModule extends AutoCloseable {

  protected val dustExecution: ExecutionContext
  implicit val defaultExecutionContext: ExecutionContext

  val logger = LoggerFactory.getLogger(classOf[NodeJSModule])

  val temDir = {
    val tempPath = Properties.tmpDir
    //存放不同版本 node 文件的根目录
    val nodeRootDirName = "play_dust_nodejs_temp"
    val dirName = UUID.randomUUID().toString
    val tempJSRoot = Paths.get(tempPath, nodeRootDirName)
    tempJSRoot.resolve(dirName)
  }

  val helperKeys: List[String]

  lazy val helperNamesF: Future[V8Array] = {
    mixRunTime.map(_._4)
  }

  lazy val copyAssetsAction: Future[Boolean] = Future {
    CopyHelper.copyFromClassPath("net/scalax/node_environment/assets", temDir)
    CopyHelper.copyFromClassPath("META-INF/resources/webjars/dustjs-linkedin/2.7.2", Paths.get(temDir.toString, "node_modules/dustjs-linkedin"))
    true
  }

  protected lazy val mixRunTime: Future[(NodeJS, V8, V8Object, V8Array)] = {
    copyAssetsAction.flatMap { (_: Boolean) =>
      execV8Job {
        val nodeJS = NodeJS.createNodeJS()
        val v8 = nodeJS.getRuntime

        val helperNames = new V8Array(v8)
        helperKeys.foreach(s => helperNames.push(s))
        v8.add("helperNames", helperNames)

        val module = nodeJS.require(temDir.resolve("play-dust-bridge.js").toFile)
        (nodeJS, v8, module, helperNames)
      }
    }
  }

  lazy val nodeJSF: Future[NodeJS] = {
    mixRunTime.map(_._1)
  }

  lazy val v8F: Future[V8] = {
    mixRunTime.map(_._2)
  }

  lazy val moduleF: Future[V8Object] = {
    mixRunTime.map(_._3)
  }

  def execV8Job[T](job: => T): Future[T] = {
    Future {
      job
    }(dustExecution)
  }

  protected def releaseHeplerNames = helperNamesF.flatMap {
    heplerNames =>
      execV8Job {
        heplerNames.release()
      }.andThen {
        case Success(_: Unit) =>
          logger.info("回收 helper names 全局对象完毕")
        case Failure(e) =>
          logger.info("回收 helper names 全局对象发生错误", e)
      }
  }.recover {
    case _ =>
      logger.info("helper names 全局对象未正确初始化，跳过回收")
  }

  protected def releaseModule = moduleF.flatMap {
    module =>
      execV8Job {
        module.release()
      }.andThen {
        case Success(_: Unit) =>
          logger.info("回收 dust 模块资源完毕")
        case Failure(e) =>
          logger.info("回收 dust 模块资源发生错误", e)
      }
  }.recover {
    case _ =>
      logger.info("dust 模块资源未正确初始化，跳过回收")
  }

  protected def releaseNodeJS = nodeJSF.flatMap {
    nodeJS =>
      execV8Job {
        nodeJS.release()
      }.andThen {
        case Success(_: Unit) =>
          logger.info("回收 NodeJS 全局对象完毕")
        case Failure(e) =>
          logger.info("回收 NodeJS 全局对象发生错误", e)
      }
  }.recover {
    case _ =>
      logger.info("NodeJS 全局对象未正确初始化，跳过回收")
  }

  protected def releaseV8 = v8F.flatMap {
    v8 =>
      execV8Job {
        v8.release()
      }.andThen {
        case Success(_: Unit) =>
          logger.info("回收 v8 全局对象完毕")
        case Failure(e) =>
          logger.info("回收 v8 全局对象发生错误", e)
      }
  }.recover {
    case _ =>
      logger.info("v8 全局对象未正确初始化，跳过回收")
  }

  override def close: Unit = {
    logger.info("开始回收 node 资源")

    val closeAction: Future[Unit] = (for {
      (_: Unit) <- releaseHeplerNames
      (_: Unit) <- releaseModule
      (_: Unit) <- releaseNodeJS
      (_: Unit) <- releaseV8
    } yield {
      ()
    }).andThen {
      case Success(_: Unit) =>
        logger.info("j2v8 资源回收完毕")
      case Failure(e) =>
        logger.error("j2v8 资源回收出现未能处理的异常", e)
    }

    Await.result(closeAction, scala.concurrent.duration.Duration.Inf): Unit
  }

}

object NodeJSModule {

  def create(helperKeys: List[String])(dustExecution: ExecutionContext)(defaultExecutionContext: ExecutionContext): NodeJSModule = {
    val helperKeys1 = helperKeys
    val dustExecution1 = dustExecution
    val defaultExecutionContext1 = defaultExecutionContext
    val module = new NodeJSModule {
      override protected val dustExecution = dustExecution1
      override val helperKeys = helperKeys1
      override val defaultExecutionContext = defaultExecutionContext1
    }
    module.moduleF
    module
  }

}