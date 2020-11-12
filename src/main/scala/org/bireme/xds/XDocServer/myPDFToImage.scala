/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.xds.XDocServer

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.tools.imageio.ImageIOUtil

import scala.util.{Failure, Success, Try}

// Copied and adapted from Apache class PDFToImage (library PDFBox)

object myPDFToImage {
  {
    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    //val rootLogger = LoggerFactory.getLogger("log4j.logger.org.apache.pdfbox")
    val logbackRootLogger = rootLogger.asInstanceOf[Logger]
    logbackRootLogger.setLevel(Level.INFO)
    val rootLogger2 = LoggerFactory.getLogger("org.apache.pdfbox")
    val logbackRootLogger2 = rootLogger2.asInstanceOf[Logger]
    logbackRootLogger2.setLevel(Level.INFO)

    val javaVersion: String = System.getProperty("java.version")
    if (javaVersion.compareTo("1.8.0_191") < 0) {
      System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
      System.setProperty("apple.awt.UIElement", "true")
      Class.forName("sun.java2d.cmm.kcms.KcmsServiceProvider")
    }
  }

  def convert(doc: InputStream): Option[InputStream] = {
    Try {
      val quality = 1.0f
      val dpi = 48//96
      val document: PDDocument = PDDocument.load(doc)
      val renderer = new PDFRenderer(document)

      renderer.setSubsamplingAllowed(false)

      val image: BufferedImage = renderer.renderImageWithDPI(0, dpi, ImageType.RGB)
      document.close()
      val output = new ByteArrayOutputStream()
      if (ImageIOUtil.writeImage(image, "jpg", output, dpi, quality))
        Some(new ByteArrayInputStream(output.toByteArray))
      else None
    } match {
      case Success(opt) => opt
      case Failure(ex) =>
        println(s"--- PDFToImage convertion ERROR: $ex")
        None
    }
  }
}
