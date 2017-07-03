package org.xarcher.nodeWeb

import javax.inject.Singleton

import play.api.Configuration

trait TemplateConfigure {
  val isNodeProd: Boolean
  val isTemplateSync: Boolean
}

@Singleton
class TemplateConfigureImpl @javax.inject.Inject() (
    configure: Configuration
) extends TemplateConfigure {
  override val isNodeProd = configure.getAndValidate[String]("djx.node.init.type", Set("prod", "dev")) == "prod"
  override val isTemplateSync = configure.getOptional[Boolean]("djx.dust.template.sync").getOrElse(false)
}