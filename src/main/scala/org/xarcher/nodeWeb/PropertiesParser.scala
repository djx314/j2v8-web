package org.xarcher.nodeWeb

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import collection.JavaConverters._

case class PropertyBody(property: Element, children: List[PropertyBody])

object PropertiesParser {

  def parse(html: String): String = {
    capture(parseHtml(html)).map(s => convertProBody(s).toString()).mkString("\n")
  }

  def parseHtml(html: String): Element = {
    Jsoup.parse(html).body()
  }

  def capture(inElement: Element): List[PropertyBody] = {
    if (inElement.getElementsByClass("djx-ubw-properties").size > 0) {
      val childrenEle = inElement.children()
      val lastChild = childrenEle.last()
      if (lastChild.className().indexOf("djx-ubw-properties") >= 0) {
        val headList = childrenEle.asScala.toList.dropRight(1)
        List(PropertyBody(lastChild, headList.flatMap(s => capture(s))))
      } else {
        inElement.children().asScala.toList.flatMap(capture)
      }
    } else {
      List.empty[PropertyBody]
    }
  }

  def convertProBody(body: PropertyBody): Element = {
    /*println(body.property.toString())
    val copyElement = Jsoup.parseBodyFragment(body.property.toString()).body()
    println(copyElement.toString())*/
    body.children.foreach { s =>
      body.property.appendChild(convertProBody(s))
    }
    body.property
  }

}