package org.bireme.xds.XDocServer

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

object AddExtension extends App {
  private def usage(): Unit = {
    System.err.println("usage: AddExtension <dir> <extension>")
    System.exit(1)
  }

  if (args.length != 2) usage()

  val dir: File = new File(args(0))
  if (!dir.exists() || !dir.isDirectory) throw new IOException("invalid directory")
  val extt: String = args(1).trim
  val ext: String = if (extt.head.equals('.')) extt else s".$extt"

  dir.listFiles().foreach(file => if (file.isDirectory) addExtension(file, ext))

  def addExtension(dir: File,
                   ext: String): Unit = {
    val file: File = new File(dir, dir.getName)
    if (file.exists() && file.isFile) copyFile(file, ext)
  }

  def copyFile(file: File,
               ext: String): Unit = {
    val originalPath: Path = file.toPath
    val copied: Path = Paths.get(file.getCanonicalPath + ext)

    Files.copy(originalPath, copied, StandardCopyOption.REPLACE_EXISTING)
  }
}
