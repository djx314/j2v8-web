package net.scalax.ubw.database.test

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.slf4j.LoggerFactory
import org.xarcher.urlParser._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

class UrlParserTest extends FlatSpec
  with Matchers
  with EitherValues
  with ScalaFutures
  with BeforeAndAfterAll
  with BeforeAndAfter {

  val t = 10.seconds
  override implicit val patienceConfig = PatienceConfig(timeout = t)
  val logger = LoggerFactory.getLogger(getClass)

  override def beforeAll = {
  }

  override def afterAll = {
  }

  after {
  }

  "url plan" should "parse to UrlAbs" in {
    val urlPlan1 = "/abc/sfew/{abc: String}/r{ name: String }"
    UrlParser.parsePlan(urlPlan1) shouldBe Right(List(FullUrl("abc"), FullUrl("sfew"), FullPath(Capture("abc", "String")), PrefixPath("r", Capture("name", "String"))))

    val urlPlan2 = "/cce{ route: String}abb/{abc: String}/r{ name: String }"
    UrlParser.parsePlan(urlPlan2) shouldBe Right(List(NestPath("cce", Capture("route", "String"), "abb"), FullPath(Capture("abc", "String")), PrefixPath("r", Capture("name", "String"))))

  }

  "url parameters" should "parse to List[Capture]" in {
    val urlPlan1 = "/abc/sfew/{abc: String}/r{ name: String }?{bbce: String}&{fff: Int}"
    ParamParser.parsePlan(urlPlan1) shouldBe Right(List(Capture("bbce", "String"), Capture("fff", "Int")))

    val urlPlan2 = "/{abc: String}/r{ name: String }?    { bbce1111: String } &{    fff: Int }"
    ParamParser.parsePlan(urlPlan2) shouldBe Right(List(Capture("bbce1111", "String"), Capture("fff", "Int")))

    val urlPlan3 = "/{abc: String}/r{ name: String }12    { bbce1111: String } &{    fff: Int }"
    ParamParser.parsePlan(urlPlan3) shouldBe Right(Nil)
  }

  "url" should "parse common url" in {
    UrlDecoder.simpleInput("/abc/sfew/ewabc/rwer/we/rdef/wer", "/abc/sfew/{abc: String}/r{ name: String }/we/rdef/wer") shouldBe
      Right(ParseResult(List(InfoWrap("ewabc", "abc", "String"), InfoWrap("wer", "name", "String")), List.empty, List.empty))

    UrlDecoder.simpleInput("/abc/sfew/ewabc/rwer/we/rdef/wer", "/abc/sfew/{abc: String}/r{ name: String }/we/rdef/wer?{acceff: String} &{bcce: Int}") shouldBe
      Right(ParseResult(
        List(InfoWrap("ewabc", "abc", "String"), InfoWrap("wer", "name", "String")),
        List(Capture("acceff", "String"), Capture("bcce", "Int")),
        List.empty))
  }

}