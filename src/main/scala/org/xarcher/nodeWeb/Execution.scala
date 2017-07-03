package org.xarcher.nodeWeb

import java.util.concurrent.Executors
import javax.inject.{ Inject, Singleton }

import org.slf4j.LoggerFactory
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutorService, Future }
/*object Execution {

  val singleThread: ExecutionContextExecutor = {
    ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
  }

}*/
@Singleton
class DustExecutionImpl @Inject() (
    applicationLifecycle: ApplicationLifecycle
) extends DustExecution {

  val logger = LoggerFactory.getLogger(classOf[DustExecution])

  override val singleThread: ExecutionContextExecutorService = {
    ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
  }

  applicationLifecycle.addStopHook { () =>
    singleThread.shutdown()
    logger.info("dust 单线程线程池被关闭")
    Future successful (())
  }

}

trait DustExecution {
  val singleThread: ExecutionContext
}