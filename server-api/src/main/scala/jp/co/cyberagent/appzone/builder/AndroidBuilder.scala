/*
 * Copyright (C) 2013 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.appzone.builder

import org.scalatra.servlet.FileItem
import java.awt.image.BufferedImage
import java.io.{IOException, InputStream, File}
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import javax.imageio.ImageIO
import jp.co.cyberagent.appzone.util.AndroidUtils
import scala.util.matching.Regex
import scala.collection.JavaConverters._
import javax.imageio.stream.MemoryCacheImageInputStream

class AndroidBuilder(packagedApp: FileItem) {
  val tempFile = File.createTempFile("appzone", "")
  packagedApp.write(tempFile)
  val zip = new ZipFile(tempFile)
  var manifest: String = null
  openManifest()

  def openManifest(): Unit = {
    val entry: ZipEntry = zip.getEntry("AndroidManifest.xml")
    val is = zip.getInputStream(entry)
    val buffer = new Array[Byte](is.available())
    is.read(buffer)
    manifest = AndroidUtils.decompressXML(buffer)
  }

  def cleanup() = {
    zip.close()
    if (tempFile != null) {
      tempFile.delete()
    }
  }

  def lookupManifestString(name: String): String = {
    var start = manifest.indexOf(name + "=\"")
    if (start == -1) {
      return ""
    }
    start += name.length + 2
    val end = manifest.indexOf("\"", start)
    val value = manifest.substring(start, end)
    if (value.startsWith("resourceID 0x")) {
      val resId = Integer.parseInt(value.substring(13), 16)
      return AndroidUtils.getStringFromResource(tempFile, resId)
    }
    value
  }

  def loadManifestFileSet(name: String): Set[String] = {
    var start = manifest.indexOf(name + "=\"")
    if (start == -1) {
      return Set()
    }
    start += name.length + 2
    val end = manifest.indexOf("\"", start)
    val value = manifest.substring(start, end)
    if (value.startsWith("resourceID 0x")) {
      val resId = Integer.parseInt(value.substring(13), 16)
      return AndroidUtils.getFileSetFromResource(tempFile, resId).asScala.toSet
    }
    Set()
  }

  def getIcon(size: Int): InputStream = {
    val icons = loadManifestFileSet("icon")
    val sorted = new java.util.TreeMap[Int, ZipEntry]()
    for (icon <- icons) {
      val entry = zip.getEntry(icon + ".png")
      if (entry != null) {
        sorted.put(size - getIconSize(entry.getName, zip.getInputStream(entry)), entry)
      }
    }
    // Try exact match or closest smaller image first
    var entry = sorted.ceilingEntry(0)
    if (entry != null) {
      return zip.getInputStream(entry.getValue)
    }
    // Otherwise, try a bigger size
    entry = sorted.lowerEntry(0)
    if (entry != null) {
      return zip.getInputStream(entry.getValue)
    }
    // Finally, sadness
    null
  }

  // TODO baseclass
  def getIconSize(name: String, in: InputStream): Int = {
    val pos = name.lastIndexOf(".")
    if (pos == -1) {
      throw new IOException("Icon is missing extension: " + name)
    }
    val suffix = name.substring(pos + 1)
    val it = ImageIO.getImageReadersBySuffix(suffix)
    if (it.hasNext) {
      val reader = it.next()
      try {
        val stream = new MemoryCacheImageInputStream(in)
        reader.setInput(stream)
        return reader.getWidth(reader.getMinIndex)
      }
      finally {
        reader.dispose()
      }
    }
    throw new IOException("Unsupported image type: " + suffix)
  }
}
