package org.xarcher.nodeWeb

import javax.inject.Singleton

import play.api.Configuration

trait TemplateConfigure {
  val isNodeProd: Boolean = true
  val isTemplateSync: Boolean
}

@Singleton
class TemplateConfigureImpl @javax.inject.Inject() (
    configure: Configuration
) extends TemplateConfigure {
  //override val isNodeProd = configure.getAndValidate[String]("play.dust.node.init.type", Set("prod", "dev")) == "prod"
  override val isTemplateSync = configure.getOptional[Boolean]("play.dust.template.sync").getOrElse(false)
}