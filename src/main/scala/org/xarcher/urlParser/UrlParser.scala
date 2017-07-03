package org.xarcher.urlParser

import org.parboiled2._

import scala.util.{ Failure, Success, Try }

class UrlParser(val input: ParserInput) extends Parser {

  def wspStr(s: String): Rule0 = rule {
    zeroOrMore(' ') ~ str(s) ~ zeroOrMore(' ')
  }

  def PrefixText = rule { zeroOrMore(CharPredicate.All -- EOI -- '{' -- '/') }
  def SuffixText = rule { zeroOrMore(CharPredicate.All -- EOI -- '{' -- '/') }
  def CommonText: Rule1[Capture] = {
    rule {
      wspStr("") ~
        capture(oneOrMore(CharPredicate.All -- EOI -- '}' -- '{' -- ':' -- ' ')) ~
        wspStr(":") ~
        capture(oneOrMore(CharPredicate.All -- EOI -- '}' -- '{' -- ' ')) ~
        wspStr("") ~>
        { (s: String, t: String) => Capture(s, t) }
    }
  }
  def Brace = rule { "{" ~ CommonText ~ "}" }

  def CaptureBrance: Rule1[UrlAbs] = rule {
    capture(PrefixText) ~ optional(Brace) ~ capture(SuffixText) ~ EOI ~>
      { (prefix: String, captureOpt: Option[Capture], suffix: String) =>
        captureOpt.map { capture =>
          if (prefix.isEmpty && suffix.isEmpty) {
            FullPath(capture)
          } else if (!prefix.isEmpty && !suffix.isEmpty) {
            NestPath(prefix, capture, suffix)
          } else if (!prefix.isEmpty) {
            PrefixPath(prefix, capture)
          } else {
            SuffixPath(suffix, capture)
          }
        }.getOrElse(FullUrl(prefix + suffix))
      }
  }

}

class ParamParser(val input: ParserInput) extends Parser {

  def wspStr(s: String): Rule0 = rule {
    zeroOrMore(' ') ~ str(s) ~ zeroOrMore(' ')
  }

  //def PrefixText = rule { zeroOrMore(CharPredicate.All -- EOI -- '{' -- '/') }
  //def SuffixText = rule { zeroOrMore(CharPredicate.All -- EOI -- '{' -- '/') }
  def CommonText: Rule1[Capture] = {
    rule {
      wspStr("") ~
        capture(oneOrMore(CharPredicate.All -- EOI -- '}' -- '{' -- ':' -- ' ')) ~
        wspStr(":") ~
        capture(oneOrMore(CharPredicate.All -- EOI -- '}' -- '{' -- ' ')) ~
        wspStr("") ~>
        { (s: String, t: String) => Capture(s, t) }
    }
  }
  def Brace = rule {
    wspStr("") ~
      "{" ~ CommonText ~ "}" ~
      wspStr("")
  }

  def CaptureBrance: Rule1[Option[Capture]] = rule {
    optional(Brace) ~ EOI
  }

}

object ParamParser {
  def parse(urlSnippet: String): Try[Option[Capture]] = new ParamParser(urlSnippet).CaptureBrance.run()

  def parsePlan(url: String): Either[Throwable, List[Capture]] = {
    val lastIndex = url.lastIndexOf('?')
    if (lastIndex < 0) {
      Right(Nil)
    } else {
      val dropPlan = url.drop(lastIndex + 1)
      dropPlan.split('&').toList.foldLeft(Right(List.empty): Either[Throwable, List[Capture]]) { (result, snippet) =>
        result match {
          case ex @ Left(_) => ex
          case Right(abs) =>
            ParamParser.parse(snippet) match {
              case Success(urlA) => Right(abs ::: urlA.toList)
              case Failure(e) => Left(e)
            }
        }
      }
    }
    //new UrlParser(urlSnippet).CaptureBrance.run()
  }
}

object UrlParser extends App {
  def parse(urlSnippet: String) = new UrlParser(urlSnippet).CaptureBrance.run()

  def parsePlan(url: String): Either[Throwable, List[UrlAbs]] = {
    //url parser 的方案字符串如果以 / 开头，去掉 /
    val dropPlan = url.dropWhile(_ == '/')
    val dropPlan1 = dropPlan.takeWhile(_ != '?')
    dropPlan1.split('/').toList.foldLeft(Right(List.empty): Either[Throwable, List[UrlAbs]]) { (result, snippet) =>
      result match {
        case ex @ Left(_) => ex
        case Right(abs) =>
          UrlParser.parse(snippet) match {
            case Success(urlA) => Right(abs ::: urlA :: Nil)
            case Failure(e) => Left(e)
          }
      }
    }
    //new UrlParser(urlSnippet).CaptureBrance.run()
  }
}