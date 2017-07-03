package org.xarcher.nodeWeb.modules

import com.google.inject.AbstractModule
import org.xarcher.nodeWeb._

class Module extends AbstractModule {
  def configure() = {

    bind(classOf[DustExecution])
      .to(classOf[DustExecutionImpl])

    bind(classOf[TemplateConfigure])
      .to(classOf[TemplateConfigureImpl])

    bind(classOf[DustEngine])
      .to(classOf[DustEngineImpl])

  }
}