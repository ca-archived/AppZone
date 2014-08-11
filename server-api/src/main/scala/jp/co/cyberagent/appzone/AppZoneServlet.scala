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

package jp.co.cyberagent.appzone

import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import scalate.ScalateSupport
import net.liftweb.json.JsonDSL._
import net.liftweb.mongodb.MongoDB
import net.liftweb.mongodb.DefaultMongoIdentifier
import net.liftweb.mongodb.Upsert
import com.mongodb.gridfs.GridFS
import org.scalatra.BadRequest
import org.scalatra.InternalServerError
import java.io.InputStream
import org.scalatra.servlet.FileItem
import java.net.URLEncoder
import java.lang.String
import java.io.ByteArrayInputStream
import scala.io.Source
import net.liftweb.common.Full
import net.liftweb.json.JObject
import net.liftweb.util.Props
import org.apache.commons.net.util.SubnetUtils
import jp.co.cyberagent.appzone.builder.{IOSBuilder, AndroidBuilder}

class AppZoneServlet extends ScalatraServlet with ScalateSupport with AuthenticationSupport
with JsonHelpers with FileUploadSupport with CorsSupport {
  val DEFAULT_RELEASE = "_default"
  val ICON_SIZE = 80

  before() {
    contentType = "application/json"
    val remoteAddrHeader = request.getHeader("X-Real-IP")
    val remoteAddr = if (request.getRemoteAddr == "127.0.0.1" && remoteAddrHeader != null) {
      remoteAddrHeader
    } else {
      request.getRemoteAddr
    }

    val isInWhiteList = Props.get("auth.whitelist", "").split(",").exists(entry => {
      try {
        remoteAddr.equals(entry) || (entry.contains("/") && new SubnetUtils(entry).getInfo.isInRange(remoteAddr))
      } catch {
        case e: Exception =>
          logger.info("Error while checking whitelist: " + e.getMessage)
          false
      }
    })
    if (request.getMethod.toUpperCase != "OPTIONS" && Props.getBool("auth.enable", defVal = false) && !isInWhiteList) {
      val method = request.getMethod.toUpperCase
      val path = request.getPathInfo
      val isApiKeyValidAccess = (params.contains("api_key") && method.equals("POST")
        && params.get("api_key").get.equals(Props.get("auth.api.key", null)))
      if (!isApiKeyValidAccess && !session.contains("user_id") && !path.equals("/auth")
        && !request.getPathInfo.endsWith("/manifest") && !request.getPathInfo.endsWith("/ipa")) {
        logger.info("Not authenticated access from " + remoteAddr + " to " + request.getPathInfo)
        halt(401)
      }
    }
  }

  post("/auth") {
    Json(new JObject(Nil))
    val username = params.get("username").orElse(Option("")).get.toString
    val password = params.get("password").orElse(Option("")).get.toString

    if (doAuth(username, password)) {
      session.setAttribute("user_id", username)
    } else {
      halt(401)
    }
  }

  ////////
  // /apps
  ////////
  get("/apps") {
    Json(App.findAll.sortBy(app => app.name.get.toLowerCase).map(p => p.asJValue))
  }

  get("/app/:id") {
    val res = App.find(("id" -> params("id")))
    if (res.isEmpty)
      resourceNotFound()
    else
      Json(res.get.asJValue)
  }
  post("/app") {
    val app = createApp(params.get("id").getOrElse(""), params.get("name").getOrElse(""))
    Json(app.asJValue)
  }

  def createApp(id: String, name: String) = {
    val app = App.createRecord
    app.id.set(id)
    app.name.set(name)
    App.update(("id" -> app.id.asJValue), app, Upsert)
    app
  }

  delete("/app/:id") {
    App.delete(("id" -> params("id")))
  }

  ///////////////////
  // /app/:id/android
  ///////////////////
  get("/app/:id/android") {
    getAndroidApk(params("id"), DEFAULT_RELEASE)
  }
  get("/app/:id/android/:releaseId") {
    getAndroidApk(params("id"), params("releaseId"))
  }
  get("/app/:id/android/:releaseId/icon.png") {
    getAndroidIcon(params("id"), params("releaseId"))
  }

  def getAndroidApk(appId: String, releaseId: String) = {
    MongoDB.use(DefaultMongoIdentifier) { db =>
      val fs = new GridFS(db)
      val file = fs.findOne(appId + "/android-" + releaseId + ".apk")
      if (file != null) {
        response.setHeader("Content-Type", "application/vnd.android.package-archive")
        response.setHeader("Content-Disposition", "attachment; filename=\"" + appId + "-" + releaseId + ".apk\"")
        response.setHeader("Content-Length", file.getLength.toString)
        org.scalatra.util.io.copy(file.getInputStream, response.getOutputStream)
      } else {
        resourceNotFound()
      }
    }
  }

  def getAndroidIcon(appId: String, releaseId: String) = {
    MongoDB.use(DefaultMongoIdentifier) { db =>
      val fs = new GridFS(db)
      val file = fs.findOne(appId + "/android-" + releaseId + ".png")
      if (file != null) {
        response.setHeader("Content-Type", "image/png")
        response.setHeader("Content-Length", file.getLength.toString)
        org.scalatra.util.io.copy(file.getInputStream, response.getOutputStream)
      } else {
        resourceNotFound()
      }
    }
  }

  post("/app/:id/android") {
    publishAndroid(params("id"), DEFAULT_RELEASE)
  }

  post("/app/:id/android/:releaseId") {
    publishAndroid(params("id"), params("releaseId"))
  }

  delete("/app/:id/android/:releaseId") {
    val appBox = App.find("id" -> params("id"))
    appBox match {
      case Full(app) => {
        app.android.deleteRelease(params("releaseId"))
        App.update("id" -> params("id"), app)
      }
      case _ => resourceNotFound()
    }
  }

  def publishAndroid(appId: String, releaseId: String) = {
    fileParams.get("apk") match {
      case Some(file) =>
        var builder: AndroidBuilder = null;
        try {
          builder = new AndroidBuilder(file)
          val icon = builder.getIcon(ICON_SIZE)
          storeFile(file, appId + "/android-" + releaseId + ".apk")
          if (icon != null) {
            storeFile(icon, "image/png", appId + "/android-" + releaseId + ".png")
          }
          publishPlatform(appId, releaseId, icon != null, app => app.android)
        }
        catch {
          case e: Exception => {
            e.printStackTrace()
            InternalServerError()
          }
        }
        finally {
          builder.cleanup()
        }
      case None =>
        BadRequest("apk (file) parameter required")
    }
  }

  ///////////////////
  // /app/:id/ios
  ///////////////////
  post("/app/:id/ios") {
    publishIOs(params("id"), DEFAULT_RELEASE)
  }

  post("/app/:id/ios/:releaseId") {
    publishIOs(params("id"), params("releaseId"))
  }

  delete("/app/:id/ios/:releaseId") {
    val appBox = App.find("id" -> params("id"))
    appBox match {
      case Full(app) => {
        app.ios.deleteRelease(params("releaseId"))
        App.update("id" -> params("id"), app)
      }
      case _ => resourceNotFound()
    }
  }

  def publishIOs(appId: String, releaseId: String) = {
    fileParams.get("ipa") match {
      case Some(ipaFile) =>
        var builder: IOSBuilder = null
        try {
          builder = new IOSBuilder(ipaFile)
          val manifest = builder.createManifest(getRequestUrl + "/ipa")
          val icon = builder.getIcon(ICON_SIZE)
          storeFile(ipaFile, appId + "/ios-" + releaseId + ".ipa")
          storeFile(manifest, "application/x-plist", appId + "/ios-" + releaseId + ".manifest")
          if (icon != null) {
            storeFile(icon, "image/png", appId + "/ios-" + releaseId + ".png")
          }
          publishPlatform(appId, releaseId, icon != null, app => app.ios)
        }
        catch {
          case e: Exception => {
            e.printStackTrace()
            InternalServerError()
          }
        }
        finally {
          builder.cleanup()
        }
      case None =>
        BadRequest("ipa (file) parameter required")
    }
  }

  def publishPlatform(appId: String, releaseId: String, hasIcon: Boolean, resList: App => ReleaseList) = {
    def updateApp(app: App) = {
      val releaseList = resList(app)
      val record: AppPlatformEntry = releaseList.getRelease(releaseId).openOr(AppPlatformEntry.createRecord)
      record.version.set(params.getOrElse("version", "NOT SET"))
      record.hasIcon.set(hasIcon)
      record.incrementVersionCode()
      record.setDateToNow()
      params.get("changelog") match {
        case Some(changelog) => record.addChangeLog(new String(changelog.getBytes("iso-8859-1"), "UTF-8"))
        case _ =>
      }
      releaseList.addRelease(releaseId, record)
      App.update(("id" -> appId), app)
      Json(app.asJValue)
    }

    val appRes = App.find(("id" -> appId))
    appRes match {
      case Full(app) => updateApp(app)
      case _ => updateApp(createApp(appId, appId))
    }
  }

  get("/app/:id/ios") {
    redirectIOsItmsServices(params("id"), DEFAULT_RELEASE)
  }

  get("/app/:id/ios/:releaseId") {
    redirectIOsItmsServices(params("id"), params("releaseId"))
  }

  def redirectIOsItmsServices(appId: String, releaseId: String) {
    val url = URLEncoder.encode(urlToDownloadUrl(getRequestUrl) + "/manifest", "UTF-8")
    redirect("itms-services://?action=download-manifest&url=" + url)
  }

  def getRequestUrl = {
    if (request.getHeader("X-Real-Uri") != null) {
      request.getHeader("X-Real-Uri")
    } else {
      request.getRequestURL.toString
    }
  }

  def urlToDownloadUrl(url: String) = {
    val forceDomain = Props.get("https.forcehttpdownload.domain", defVal = null)
    val forceHttpEnabled = Props.getBool("https.forcehttpdownload.enable", defVal = false)
    if (forceHttpEnabled && (forceDomain == null || url.contains(forceDomain))) {
      url.replace("https", "http")
    } else {
      url
    }
  }

  get("/app/:id/ios/manifest") {
    getIOsManifest(params("id"), DEFAULT_RELEASE)
  }

  get("/app/:id/ios/:releaseId/manifest") {
    getIOsManifest(params("id"), params("releaseId"))
  }

  def getIOsManifest(appId: String, releaseId: String) = {
    MongoDB.use(DefaultMongoIdentifier) { db =>
      val fs = new GridFS(db)
      val file = fs.findOne(appId + "/ios-" + releaseId + ".manifest")
      if (file != null) {
        response.setHeader("Content-Type", "text/xml")
        val content = Source.fromInputStream(file.getInputStream).getLines().mkString("\n")
        val url = urlToDownloadUrl(getRequestUrl)
        val contentNew = """<string>.*[\.\/]ipa<\/string>""".r.replaceFirstIn(content, "<string>" + url.substring(0, url.lastIndexOf("/")) + "/ipa</string>")
        val input = new ByteArrayInputStream(contentNew.getBytes("UTF-8"))
        org.scalatra.util.io.copy(input, response.getOutputStream)
      } else {
        resourceNotFound()
      }
    }
  }

  get("/app/:id/ios/ipa") {
    getIOsIpa(params("id"), DEFAULT_RELEASE)
  }

  get("/app/:id/ios/:releaseId/ipa") {
    getIOsIpa(params("id"), params("releaseId"))
  }

  get("/app/:id/ios/:releaseId/icon.png") {
    getIOsIcon(params("id"), params("releaseId"))
  }

  def getIOsIpa(appId: String, releaseId: String) = {
    MongoDB.use(DefaultMongoIdentifier) { db =>
      val fs = new GridFS(db)
      val file = fs.findOne(appId + "/ios-" + releaseId + ".ipa")
      if (file != null) {
        response.setHeader("Content-Type", "application/octet-stream")
        response.setHeader("Content-Disposition", "attachment; filename=\"" + appId + ".ipa\"")
        response.setHeader("Content-Length", file.getLength.toString)
        org.scalatra.util.io.copy(file.getInputStream, response.getOutputStream)
      } else {
        resourceNotFound()
      }
    }
  }

  def getIOsIcon(appId: String, releaseId: String) = {
    MongoDB.use(DefaultMongoIdentifier) { db =>
      val fs = new GridFS(db)
      val file = fs.findOne(appId + "/ios-" + releaseId + ".png")
      if (file != null) {
        response.setHeader("Content-Type", "image/png")
        response.setHeader("Content-Length", file.getLength.toString)
        org.scalatra.util.io.copy(file.getInputStream, response.getOutputStream)
      } else {
        resourceNotFound()
      }
    }
  }

  post("/app/:id/feedback") {
    storeFeedback(params("id"), params("type"), params("feedback"))
  }

  post("/app/:id/android/feedback") {
    storeFeedback(params("id"), "android", params("feedback"))
  }
  post("/app/:id/ios/feedback") {
    storeFeedback(params("id"), "ios", params("feedback"))
  }

  get("/app/:id/feedback") {
    Json(Feedback.findAll(("appId" -> params("id")), ("date" -> -1)).map(p => p.asJValue))
  }

  // For CORS
  options("/*") { handlePreflightRequest() }

  notFound {
    // remove content type in case it was set through an action
    contentType = null
    // Try to render a ScalateTemplate if no route matched
    findTemplate(requestPath) map { path =>
      contentType = "text/html"
      layoutTemplate(path)
    } orElse serveStaticResource() getOrElse resourceNotFound()
  }

  def storeFile(file: FileItem, fileName: String) {
    storeFile(file.getInputStream, file.contentType.getOrElse("application/octet-stream"), fileName)
  }
  def storeFile(manifest: InputStream, contentType: String, fileName: String) {
    MongoDB.use(DefaultMongoIdentifier) { db =>
      val fs = new GridFS(db)
      fs.remove(fileName)
      val inputFile = fs.createFile(manifest)
      inputFile.setFilename(fileName)
      inputFile.setContentType(contentType)
      inputFile.save()
    }
  }

  def storeFeedback(id: String, appType: String, feedback: String) = {
    val feedbackRecord = Feedback.createRecord
    feedbackRecord.appId.set(id)
    feedbackRecord.appType.set(appType)
    feedbackRecord.feedback.set(feedback)
    feedbackRecord.setDateToNow()
    feedbackRecord.save
    Json(feedbackRecord.asJValue)
  }
}
