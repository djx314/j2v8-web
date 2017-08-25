package org.xarcher.nodeWeb

import java.util.concurrent.Executors
import javax.inject.{ Inject, Singleton }

import org.slf4j.LoggerFactory
import play.api.inject.ApplicationLifecycle

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutorService }

@Singleton
class DustExecutionImpl @Inject() (
    applicationLifecycle: ApplicationLifecycle
) extends DustExecution {

  val logger = LoggerFactory.getLogger(classOf[DustExecution])

  override val singleThread: ExecutionContextExecutorService = {
    ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
  }

}

trait DustExecution {
  val singleThread: ExecutionContext
}