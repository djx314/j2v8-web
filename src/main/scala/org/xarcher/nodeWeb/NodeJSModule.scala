package org.xarcher.nodeWeb

import com.eclipsesource.v8._

import java.nio.file.Paths
import java.util.UUID

import org.slf4j.LoggerFactory
import org.xarcher.nodeWeb.modules.CopyHelper

import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Properties, Success, Try }

trait NodeJSModule {

  protected def applicationLifecycle: ApplicationLifecycle
  protected def dustExecution: ExecutionContext
  protected implicit val defaultExecutionContext: ExecutionContext

  val logger = LoggerFactory.getLogger(classOf[DustEngine])

  val temDir = {
    val tempPath = Properties.tmpDir
    //存放不同版本 node 文件的根目录
    val nodeRootDirName = "enuma_nodejs_temp"
    val dirName = UUID.randomUUID().toString
    val tempJSRoot = Paths.get(tempPath, nodeRootDirName)
    tempJSRoot.resolve(dirName)
  }

  lazy val nodeJSF: Future[NodeJS] = {
    CopyHelper.copyFromClassPath("org/xarcher/nodeEnv/assets", temDir).flatMap { _ =>
      Future {
        NodeJS.createNodeJS()
      }(dustExecution)
    }
  }

  lazy val v8F: Future[V8] = {
    nodeJSF.map { nodeJS =>
      nodeJS.getRuntime
    }(dustExecution)
  }

  lazy val moduleF: Future[V8Object] = {
    nodeJSF.map { nodeJS =>
      nodeJS.require(temDir.resolve("play-dust-bridge.js").toFile)
    }(dustExecution)
  }

  applicationLifecycle.addStopHook { () =>
    logger.info("开始回收 node 资源")
    moduleF.map {
      module =>
        Try {
          module.release()
        } match {
          case Success(_) =>
            logger.info("回收 dust 模块资源完毕")
          case Failure(e) =>
            logger.info("回收 dust 模块资源发生错误", e)
        }
    }(dustExecution).recover {
      case _ =>
        logger.info("dust 模块资源未正确初始化，跳过回收")
    }.flatMap { (_: Unit) =>
      nodeJSF.map {
        nodeJS =>
          Try {
            nodeJS.release()
          } match {
            case Success(_) =>
              logger.info("回收 nodeJS 全局对象完毕")
            case Failure(e) =>
              logger.info("回收 nodeJS 全局对象发生错误", e)
          }
      }(dustExecution).recover {
        case _ =>
          logger.info("nodeJS 全局对象未正确初始化，跳过回收")
      }.flatMap { (_: Unit) =>
        v8F.map {
          v8 =>
            Try {
              v8.release()
            } match {
              case Success(_) =>
                logger.info("回收 v8 全局对象完毕")
              case Failure(e) =>
                logger.info("回收 v8 全局对象发生错误", e)
            }
        }(dustExecution).recover {
          case _ =>
            logger.info("v8 全局对象未正确初始化，跳过回收")
        }
      }.map { (_: Unit) =>
        logger.info("j2v8 资源回收完毕")
      }
    }: Future[Unit]

  }

}