package org.xarcher.nodeWeb

import net.scalax.fsn.slick.model.{ ColumnOrder, SlickPage, SlickParam, SlickRange }
import org.parboiled2._

import scala.util.{ Failure, Success }
import scala.language.implicitConversions

case class SlickParamHelper(
    orders: List[ColumnOrder] = Nil,
    drop: Option[Int] = None,
    take: Option[Int] = None,
    pageIndex: Option[Int] = None,
    pageSize: Option[Int] = None
) {
  def addOrder(colName: String, isDesc: Boolean): SlickParamHelper = {
    this.copy(orders = ColumnOrder(colName, isDesc) :: orders)
  }
  def drop(dropNum: Int): SlickParamHelper = this.copy(drop = Option(dropNum))
  def take(takeNum: Int): SlickParamHelper = this.copy(take = Option(takeNum))
  def pageIndex(pageIndexNum: Int): SlickParamHelper = this.copy(pageIndex = Option(pageIndexNum))
  def pageSize(pageSizeNum: Int): SlickParamHelper = this.copy(pageSize = Option(pageSizeNum))

  def toSlickParam: SlickParam = {
    val rangeOpt = drop match {
      case Some(dropNum) => Option(SlickRange(dropNum, take))
      case _ => None
    }
    val pageOpt = for {
      pageIndexNum <- pageIndex
      pageSizeNum <- pageSize
    } yield {
      SlickPage(pageIndexNum, pageSizeNum)
    }
    SlickParam(orders.reverse, rangeOpt, pageOpt)
  }
}

class SlickParamParser(override val input: ParserInput) extends Parser {
  implicit def wspStr(s: String): Rule0 = rule {
    zeroOrMore(' ') ~ str(s) ~ oneOrMore(' ')
  }

  def ascOrDesc = rule { str("asc") | str("desc") }

  def NotBlank = rule { oneOrMore(CharPredicate.All -- ' ' -- EOI) }
  def KeyString = rule { capture(NotBlank) }

  def capAscOrDesc: Rule1[String] = rule { oneOrMore(' ') ~ capture(ascOrDesc) ~ { oneOrMore(' ') | EOI } }
  def emptyAsc: Rule1[String] = rule { { oneOrMore(' ') | EOI } ~> { () => "asc" } }

  def OrderBy: Rule1[SlickParamHelper => SlickParamHelper] =
    rule {
      "order" ~ "by" ~ KeyString ~ { capAscOrDesc | emptyAsc } ~> { (colName: String, isDesc: String) =>
        { param: SlickParamHelper =>
          param.addOrder(colName, if (isDesc == "desc") true else false)
        }
      }
    }

  def Take = rule {
    "take" ~ capture(oneOrMore(CharPredicate.Digit)) ~ { oneOrMore(' ') | EOI } ~> { s: String =>
      { param: SlickParamHelper =>
        param.take(s.toInt)
      }
    }
  }

  def Drop = rule {
    "drop" ~ capture(oneOrMore(CharPredicate.Digit)) ~ { oneOrMore(' ') | EOI } ~> { s: String =>
      { param: SlickParamHelper =>
        param.drop(s.toInt)
      }
    }
  }

  def PageIndex = rule {
    "pageIndex" ~ capture(oneOrMore(CharPredicate.Digit)) ~ { oneOrMore(' ') | EOI } ~> { s: String =>
      { param: SlickParamHelper =>
        param.pageIndex(s.toInt)
      }
    }
  }

  def PageSize = rule {
    "pageSize" ~ capture(oneOrMore(CharPredicate.Digit)) ~ { oneOrMore(' ') | EOI } ~> { s: String =>
      { param: SlickParamHelper =>
        param.pageSize(s.toInt)
      }
    }
  }

  def Converts: Rule1[Seq[SlickParamHelper => SlickParamHelper]] = rule { zeroOrMore { OrderBy | Take | Drop | PageIndex | PageSize } ~ EOI }
}

object SlickParamUtils {

  def parse(param: String): SlickParam = {
    new SlickParamParser(param)
      .Converts
      .run() match {
        case Success(result) =>
          result.foldLeft(SlickParamHelper()) { (helper, convet) =>
            convet.apply(helper)
          }.toSlickParam
        case Failure(e) => throw e
      }
  }

}