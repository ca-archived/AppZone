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
import net.liftweb.mongodb.record.field.ObjectIdPk
import java.util.Date

case class Feedback private() extends MongoRecord[Feedback] with ObjectIdPk[Feedback] {
  def meta = Feedback

  object appId extends StringField(this, 20)
  object appType extends StringField(this, 10)
  object feedback extends StringField(this, "")
  object date extends StringField(this, "")

  def setDateToNow() = date.set(Feedback.DATE_FORMAT.format(new Date))
}

object Feedback extends Feedback with MongoMetaRecord[Feedback] {
  val DATE_FORMAT = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
}