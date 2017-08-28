package org.xarcher.nodeWeb

import scala.concurrent.Future

trait V8Executor {

  def exec[T](body: => T): Future[T]

}