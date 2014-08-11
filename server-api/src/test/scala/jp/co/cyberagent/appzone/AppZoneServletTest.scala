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

import org.scalatra.test.scalatest.ScalatraSuite
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterEach
import net.liftweb.mongodb.MongoDB
import net.liftweb.util.Props
import net.liftweb.mongodb.DefaultMongoIdentifier
import net.liftweb.mongodb.Upsert
import net.liftweb.json.JsonDSL._
import com.mongodb._
import net.liftweb.json.JsonParser
import net.liftweb.json.JArray
import net.liftweb.json.JObject
import java.io.File
import scala.collection.Map

class AppZoneServletTest extends ScalatraSuite with FunSuite with BeforeAndAfterEach {
  addServlet(classOf[AppZoneServlet], "/*")
  val DEFAULT_RELEASE_ID = "_default"
  val ANDROID_APK_FILE = new File(getClass.getResource("/android-icon.apk").toURI)
  val IOS_IPA_FILE = new File(getClass.getResource("/ios.ipa").toURI)

  val app = App.createRecord
  app.id.set("testid")
  app.name.set("Test Name")

  val app2 = App.createRecord
  app2.id.set("testid2")
  app2.name.set("a Test Name")

  override def beforeAll() {
    super.beforeAll()
    val server = new ServerAddress(
      Props.get("mongo.host", "127.0.0.1"),
      Props.getInt("mongo.port", 27017))
    MongoDB.defineDb(DefaultMongoIdentifier, new MongoClient(server), "appzone-test")
  }

  override def beforeEach() {
    super.beforeEach()
    // Remove all apps
    App.useColl(col => col.remove(new BasicDBObject()))

    // Add test app
    App.update("id" -> app.id.asJValue, app, Upsert)
    App.update("id" -> app2.id.asJValue, app2, Upsert)
  }

  test("GET /apps should return the apps") {
    get("/apps") {
      status should equal(200)
      val json = JsonParser.parse(body)
      json.isInstanceOf[JArray] should equal(true)
      json.asInstanceOf[JArray].values.length should equal(2)
    }
  }
  test("GET /apps should return the apps sorted") {
    get("/apps") {
      val apps = JsonParser.parse(body).asInstanceOf[JArray]
      apps(0).asInstanceOf[JObject].values("name") should equal("a Test Name")
    }
  }

  def checkAppsContainsRelease(platform: String, releaseId: String) = {
    get("/apps") {
      val apps = JsonParser.parse(body).asInstanceOf[JArray]
      apps.values.length should equal(2)
      val android = apps(1).asInstanceOf[JObject].values(platform).asInstanceOf[List[AppPlatformEntry]]
      val release = android(0).asInstanceOf[Map[String, Object]]
      release("id") should equal(releaseId)
      release("versionCode") should equal(1)
    }
  }
  test("POST /app/:id/android should add the release") {
    post("/app/testid/android", Map("version" -> "1.0"), Map("apk" -> ANDROID_APK_FILE)) {
      status should equal(200)
    }
    checkAppsContainsRelease("android", DEFAULT_RELEASE_ID)
  }
  test("POST /app/:id/android should add changelog if sent") {
    post("/app/testid/android", Map("version" -> "1.0", "changelog" -> "Some change (username)"), Map("apk" -> ANDROID_APK_FILE)) {
      status should equal(200)
    }
    get("/app/testid") {
      val releaseId = DEFAULT_RELEASE_ID
      val testApp = JsonParser.parse(body).asInstanceOf[JObject]
      val android = testApp.values("android").asInstanceOf[List[AppPlatformEntry]]
      val release = android(0).asInstanceOf[Map[String, Object]]
      release("id") should equal(releaseId)
      release("changelog") should equal("[1.0]\nSome change (username)")
    }
  }
  test("POST /app/:id/android should add changelog to top") {
    post("/app/testid/android", Map("version" -> "1.0", "changelog" -> "Some change (username)"), Map("apk" -> ANDROID_APK_FILE)) {
      status should equal(200)
    }
    post("/app/testid/android", Map("version" -> "1.1", "changelog" -> "Some other change (username)"), Map("apk" -> ANDROID_APK_FILE)) {
      status should equal(200)
    }
    get("/app/testid") {
      val releaseId = DEFAULT_RELEASE_ID
      val testApp = JsonParser.parse(body).asInstanceOf[JObject]
      val android = testApp.values("android").asInstanceOf[List[AppPlatformEntry]]
      val release = android(0).asInstanceOf[Map[String, Object]]
      release("id") should equal(releaseId)
      release("changelog") should equal("[1.1]\nSome other change (username)\n[1.0]\nSome change (username)")
    }
  }
  test("POST /app/:id/android/:releaseId should add the release with given releaseId") {
    val releaseId = "production"
    post("/app/" + app.id.get + "/android/" + releaseId, Map("version" -> "1.0"), Map("apk" -> ANDROID_APK_FILE)) {
      status should equal(200)
    }
    checkAppsContainsRelease("android", releaseId)
  }
  test("POST /app/:id/ios/:releaseId should add the release with given releaseId") {
    val releaseId = "production"
    post("/app/" + app.id.get + "/ios/" + releaseId, Map("version" -> "1.0"), Map("ipa" -> IOS_IPA_FILE)) {
      status should equal(200)
    }
    checkAppsContainsRelease("ios", releaseId)
    get("/app/" + app.id.get + "/ios/" + releaseId + "/manifest") {
      status should equal(200)
      body should include("<string>Sample</string>")
      body should include("<string>0.1.0 (80)</string>")
      body should include("<string>com.company.appname</string>")
    }
  }
  test("DELETE /app/:id should delete the app") {
    delete("/app/" + app2.id.get) {
      status should equal(200)
    }
    get("/apps") {
      val apps = JsonParser.parse(body).asInstanceOf[JArray]
      apps.values.length should equal(1)
      apps(0).asInstanceOf[JObject].values("id") should equal(app.id.get)
    }
  }
  def testDelete(platform: String, releaseId: String) {
    delete("/app/" + app.id.get + "/" + platform + "/" + releaseId) {
      status should equal(200)
    }
    get("/app/" + app.id.get) {
      val jsonApp = JsonParser.parse(body).asInstanceOf[JObject]
      val android = jsonApp.values(platform).asInstanceOf[List[AppPlatformEntry]]
      android.length should equal(0)
    }
  }
  test("DELETE /app/:id/android/:releaseId should delete the release") {
    val releaseId = "development"
    post("/app/" + app.id.get + "/android/" + releaseId, Map("version" -> "1.0"), Map("apk" -> ANDROID_APK_FILE)) {
      status should equal(200)
    }
    testDelete("android", releaseId)
  }
  test("DELETE /app/:id/ios/:releaseId should delete the release") {
    val releaseId = "development"
    post("/app/" + app.id.get + "/ios/" + releaseId, Map("version" -> "1.0"), Map("ipa" -> IOS_IPA_FILE)) {
      status should equal(200)
    }
    testDelete("ios", releaseId)
  }
  test("OPTIONS /app/:id should respond with cors information") {
    options("/app/" + app.id.get) {
      status should equal(200)
      header.contains("Access-Control-Allow-Origin") should equal(true)
      header.contains("Access-Control-Allow-Methods") should equal(true)
    }
  }
}
