package org.xarcher.urlParser

import scala.collection.mutable.ListBuffer

sealed abstract trait UrlAbs

case class FullPath(capture: Capture) extends UrlAbs
case class PrefixPath(prefix: String, capture: Capture) extends UrlAbs
case class SuffixPath(suffix: String, capture: Capture) extends UrlAbs
case class NestPath(prefix: String, capture: Capture, suffix: String) extends UrlAbs
case class FullUrl(url: String) extends UrlAbs

case class Capture(path: String, typeRef: String)

case class InfoWrap(subUrl: String, path: String, typeRef: String)
case class ParseResult(infos: List[InfoWrap], queryCapture: List[Capture], tail: List[String])

case class ParseFailed(message: String) extends Exception(message)

class UrlParserImpl(val snippets: List[String], val urlAbs: List[UrlAbs], val queryPlans: List[Capture]) {

  override def toString = s"输入 url 段：$snippets，输入 url 识别序列：$urlAbs"

  def parseMainUrl: Either[Exception, ParseResult] = {
    val listBuffer = ListBuffer.empty[InfoWrap]

    val si = snippets.iterator
    val ui = urlAbs.iterator
    try {
      while (ui.hasNext) {
        val s = si.next()
        val u = ui.next()
        u match {
          case FullPath(capture) =>
            listBuffer += InfoWrap(s, capture.path, capture.typeRef)
          case PrefixPath(prefix, capture) =>
            if (s.startsWith(prefix)) {
              listBuffer += InfoWrap(s.drop(prefix.length), capture.path, capture.typeRef)
            } else {
              throw (ParseFailed(""))
            }
          case SuffixPath(suffix, capture) =>
            if (s.endsWith(suffix)) {
              listBuffer += InfoWrap(s.dropRight(suffix.length), capture.path, capture.typeRef)
            } else {
              throw (ParseFailed(""))
            }
          case NestPath(prefix, capture, suffix) =>
            if (s.length > (prefix.length + suffix.length) && s.startsWith(prefix) && s.endsWith(suffix)) {
              listBuffer += InfoWrap(s.drop(prefix.length).dropRight(suffix.length), capture.path, capture.typeRef)
            } else {
              throw (ParseFailed(""))
            }
          case FullUrl(url) =>
            if (!(s == url)) {
              throw (ParseFailed(""))
            }
        }
      }
      if (si.hasNext) {
        throw (ParseFailed(""))
      }
      Right(ParseResult(listBuffer.toList, queryPlans, si.toList))
    } catch {
      case e: Exception =>
        //e.printStackTrace
        Left(e)
    }
  }

}

object UrlDecoder {

  /**
   * 根据 UrlAbs 解析 url
   * @param url 需要解析的 url
   * @param urlAbs url 解析方案的抽象
   * @return 解析结果
   */
  def input(url: String, urlAbs: List[UrlAbs], queryPlans: List[Capture]): UrlParserImpl = {
    //url 必须以 / 开头，如果不是，去掉 / 前面的部分
    new UrlParserImpl(url.dropWhile(_ == '/').split('/').toList, urlAbs, queryPlans)
  }

  /**
   * 根据字符串输入输出 url parse 结果
   * @param url 要解析的 url
   * @param plan 要解析的 url 解析方案（解析成解析方案后用于解析 url）
   * @return 解析结果
   */
  def simpleInput(url: String, plan: String): Either[Throwable, ParseResult] = {
    UrlParser.parsePlan(plan) match {
      case Right(urlResult) =>
        ParamParser.parsePlan(plan) match {
          case Right(paramResult) =>
            input(url, urlResult, paramResult).parseMainUrl
          case Left(ex) => Left(ex)
        }
      case Left(ex) => Left(ex)
    }
  }

}