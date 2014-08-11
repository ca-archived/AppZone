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

import java.awt.image.BufferedImage
import java.io._
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import javax.imageio.{ImageReader, ImageIO}
import com.dd.plist.{NSString, PropertyListParser, NSDictionary, NSArray}
import com.kylinworks.IPngConverter
import org.apache.commons.io.IOUtils
import org.scalatra.servlet.FileItem
import javax.imageio.stream.{MemoryCacheImageInputStream, ImageInputStream}

class IOSBuilder(ipaFile: FileItem) {
  val tempFile = File.createTempFile("appzone", "")
  var tempIconInFile: File = null
  var tempIconOutFile: File = null
  ipaFile.write(tempFile)
  val zip = new ZipFile(tempFile)
  var plist: NSDictionary = null
  openPlist()

  def openPlist(): Unit = {
    val entry: ZipEntry = lookupFile("Info.plist")
    val plistInputStream = zip.getInputStream(entry)
    plist = PropertyListParser.parse(plistInputStream).asInstanceOf[NSDictionary]
  }

  def cleanup() = {
    zip.close()
    if (tempFile != null) {
      tempFile.delete()
    }
    if (tempIconInFile != null) {
      tempIconInFile.delete()
    }
    if (tempIconOutFile != null) {
      tempIconOutFile.delete()
    }
  }

  def lookupFile(path: String): ZipEntry = {
    var entry: ZipEntry = null
    val entries = zip.entries()
    while (entries.hasMoreElements && entry == null) {
      val element = entries.nextElement()
      val name = element.getName
      val internalPath = name.substring(name.indexOf(".app/") + 5)
      if (internalPath.equals(path)) {
        entry = element
      }
    }
    entry
  }

  def createManifest(url: String): InputStream = {
    val manifest = IOSBuilder.getManifestTemplate
      .replace("${url}", url)
      .replace("${CFBundleName}", plist.objectForKey("CFBundleName").toString)
      .replace("${CFBundleIdentifier}", plist.objectForKey("CFBundleIdentifier").toString)
      .replace("${CFBundleShortVersionString}", plist.objectForKey("CFBundleShortVersionString").toString + " (" + plist.objectForKey("CFBundleVersion").toString + ")")

    new ByteArrayInputStream(manifest.getBytes)
  }

  def getIcon(size: Int): InputStream = {
    var icons : NSArray = null
    // Try "Icon files (iOS 5)" first
    var finder = plist.objectForKey("CFBundleIcons").asInstanceOf[NSDictionary]
    if (finder != null) {
      finder = finder.objectForKey("CFBundlePrimaryIcon").asInstanceOf[NSDictionary]
      if (finder != null) {
        icons = finder.objectForKey("CFBundleIconFiles").asInstanceOf[NSArray]
      }
    }
    // Fall back to "Icon files"
    if (icons == null) {
      icons = plist.objectForKey("CFBundleIconFiles").asInstanceOf[NSArray]
    }
    if (icons == null) {
      return null
    }
    val sorted = new java.util.TreeMap[Int, ZipEntry]()
    for (icon <- icons.getArray) {
      val entry = lookupFile(icon.toString())
      if (entry != null) {
        sorted.put(size - getIconSize(entry.getName, convertXcodePng(zip.getInputStream(entry))), entry)
      }
    }
    // Try exact match or closest smaller image first
    var entry = sorted.ceilingEntry(0)
    if (entry != null) {
      return convertXcodePng(zip.getInputStream(entry.getValue))
    }
    // Otherwise, try a bigger size
    entry = sorted.lowerEntry(0)
    if (entry != null) {
      return convertXcodePng(zip.getInputStream(entry.getValue))
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

  // Incredible waste of resources
  def convertXcodePng(in: InputStream): InputStream = {
    tempIconInFile = File.createTempFile("appzone", "icon-in.png")
    val out = new FileOutputStream(tempIconInFile)
    IOUtils.copy(in, out)
    tempIconOutFile = File.createTempFile("appzone", "icon-out.png")
    val converter = new IPngConverter(tempIconInFile, tempIconOutFile)
    converter.convert()
    return new FileInputStream(tempIconOutFile)
  }
}

object IOSBuilder {
  def getManifestTemplate: String = {
    IOUtils.toString(getClass().getResourceAsStream("/OTAManifestTemplate.plist"))
  }
}
