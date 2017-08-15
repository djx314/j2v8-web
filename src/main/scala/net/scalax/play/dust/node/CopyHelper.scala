package org.xarcher.nodeWeb.modules

import java.io.{ IOException, InputStream }
import java.net.{ JarURLConnection, URL }
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._
import java.util.Date
import java.util.jar.JarFile

import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

object CopyHelper {

  val logger = LoggerFactory.getLogger("dustjs.file.copy.helper")

  def copyFromClassPath(path: String, targetRoot: Path)(implicit ec: ExecutionContext): Future[Boolean] = Future {
    Files.createDirectories(targetRoot)
    val classPathStr = path
    val sourURLs = getClass.getClassLoader.getResources(classPathStr).asScala.toStream
    sourURLs.map { sourURL =>
      val date = new Date()
      sourURL match {
        case s: URL if "file" == s.getProtocol =>
          copyDirectory(Paths.get(sourURL.toURI), targetRoot)
        case s: URL if "jar" == s.getProtocol =>
          val jarFile = s.openConnection().asInstanceOf[JarURLConnection].getJarFile
          copyFilesFromJarFile(jarFile, classPathStr, targetRoot)
      }
      val waste = new Date().getTime - date.getTime
      logger.info(s"由\n${sourURL}\n复制文件到\n${targetRoot.toUri.toURL}\n以初始化 node 环境，复制耗时${waste}ms")
    }.toList
    true
  }

  def copyDirectory(source: Path, dest: Path): Path = {
    Files.walkFileTree(
      source,
      new SimpleFileVisitor[Path]() {

        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          // 在目标文件夹中创建dir对应的子文件夹
          val subDir = if (0 == dir.compareTo(source)) dest else dest.resolve(dir.subpath(source.getNameCount(), dir.getNameCount()))
          Files.createDirectories(subDir)
          FileVisitResult.CONTINUE
        }

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.copy(file, dest.resolve(file.subpath(source.getNameCount(), file.getNameCount())))
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          super.postVisitDirectory(dir, exc)
        }
      }
    )
  }

  def copyInputStreamToFile(input: InputStream, path: Path): Long = {
    try {
      Files.createDirectories(path.getParent)
      Files.copy(input, path)
    } finally {
      if (input != null) {
        input.close()
      }
    }
  }

  def copyFilesFromJarFile(jarFile: JarFile, prefix: String, targetRoot: Path): List[Long] = {
    val entries = jarFile.entries()
    val scalaEntries = entries.asScala.toStream
    scalaEntries.filter(s => s.getName.startsWith(prefix) && (!s.isDirectory)).map { entry =>
      val inputS: InputStream = getClass.getClassLoader.getResourceAsStream(entry.getName)
      val entryPath = Paths.get(targetRoot.toFile.getCanonicalPath, entry.getName.drop(prefix.size))
      copyInputStreamToFile(inputS, entryPath)
    }.toList
  }

}