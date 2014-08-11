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

import net.liftweb.mongodb.record.MongoRecord
import net.liftweb.record.field.StringField
import net.liftweb.mongodb.record.MongoMetaRecord
import net.liftweb.mongodb.record.field.BsonRecordField
import net.liftweb.mongodb.record.BsonRecord
import net.liftweb.mongodb.record.BsonMetaRecord
import net.liftweb.record.field.BooleanField
import net.liftweb.record.field.IntField
import net.liftweb.mongodb.record.field.DateField
import java.util.Date
import net.liftweb.json.Serializer
import net.liftweb.json.Formats
import net.liftweb.json.TypeInfo
import net.liftweb.json.JValue
import net.liftweb.json.JObject
import net.liftweb.json.JField
import net.liftweb.json.JString
import net.liftweb.json.MappingException
import net.liftweb.mongodb.Meta
import net.liftweb.json.DateFormat
import jp.co.cyberagent.appzone.field.BsonRecordMapField
import net.liftweb.mongodb.record.field.MongoMapField
import net.liftweb.common.Box
import scala.collection.immutable.Map
import net.liftweb.mongodb.record.field.BsonRecordListField

case class App private () extends MongoRecord[App] {
  def meta = App

  object id extends StringField(this, 20)
  object name extends StringField(this, 20)
  object description extends StringField(this, 255) { override def optional_? = true }

  object android extends ReleaseList(this)
  object ios extends ReleaseList(this)
}

object App extends App with MongoMetaRecord[App]

/////////////////////
class ReleaseList(rec: App) extends BsonRecordListField[App, AppPlatformEntry](rec, AppPlatformEntry) {
  override def defaultValue = List[AppPlatformEntry]()
  def addRelease(releaseId: String, record: AppPlatformEntry) {
    record.id.set(releaseId)
    val index = this.value.indexWhere(release => release.id.value == releaseId)
    this.set(
      index match {
        case -1 => record :: this.value
        case _ => this.value.updated(index, record)
      })
  }
  def getRelease(releaseId: String): Box[AppPlatformEntry] = {
    this.value.find((release) => release.id.value == releaseId)
  }
  def deleteRelease(releaseId: String) {
    this.set(this.value.filter(release => release.id.get != releaseId))
  }
  def keyifyId(id: String) = id.replace(".", "_");
}
/////////////////////
class AppPlatformEntry private () extends BsonRecord[AppPlatformEntry] {
  def meta = AppPlatformEntry

  object id extends StringField(this, 150)
  object version extends StringField(this, 255)
  object versionCode extends IntField(this, 0)
  object lastUpdateDate extends StringField(this, 24)
  object changelog extends StringField(this, 2000)
  object hasIcon extends BooleanField(this, false)

  object releaseName extends StringField(this, 50) { override def optional_? = true }
  object releaseNotes extends StringField(this, 1024) { override def optional_? = true }

  def setDateToNow() = lastUpdateDate.set(AppPlatformEntry.DATE_FORMAT.format(new Date))
  def incrementVersionCode() = versionCode.set(versionCode.get + 1)
  def addChangeLog(change: String) {
    var newChangeLog = "[" + version.get + "]\n" + change
    if (changelog.get.length() > 0)
      newChangeLog = newChangeLog + "\n" + changelog.get
    if (newChangeLog.length() > 2000)
      newChangeLog = newChangeLog.substring(0, 1999)
    changelog.set(newChangeLog)
  }
}
object AppPlatformEntry extends AppPlatformEntry with BsonMetaRecord[AppPlatformEntry] {
  val DATE_FORMAT = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
}