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

package jp.co.cyberagent.appzone.field

import net.liftweb.mongodb.record.BsonRecord
import net.liftweb.mongodb.record.field.MongoMapField
import com.mongodb.DBObject
import net.liftweb.mongodb.record.BsonMetaRecord
import com.mongodb.BasicDBObject
import net.liftweb.common.Box
import net.liftweb.common.Full
import net.liftweb.json.JField
import net.liftweb.json.JObject
import net.liftweb.json.JValue
import net.liftweb.json.JNothing
import net.liftweb.json.JNull
import net.liftweb.common.Empty
import net.liftweb.record.FieldHelpers
import java.util.HashMap

class BsonRecordMapField[OwnerType <: BsonRecord[OwnerType], SubRecordType <: BsonRecord[SubRecordType]](rec: OwnerType, valueMeta: BsonMetaRecord[SubRecordType])(implicit mf: Manifest[SubRecordType])
  extends MongoMapField[OwnerType, SubRecordType](rec: OwnerType) {

  import scala.collection.JavaConversions._

  override def asDBObject: DBObject = {
    val javaMap = new HashMap[String, DBObject]()
    for ((key, element) <- value) {
      javaMap.put(key, element.asDBObject)
    }
    val dbl = new BasicDBObject(javaMap)
    dbl
  }

  override def setFromDBObject(dbo: DBObject): Box[Map[String, SubRecordType]] = {
    val mapResult: Map[String, SubRecordType] = (for ((key, dboEl) <- dbo.toMap.toSeq) yield (key.asInstanceOf[String], valueMeta.fromDBObject(dboEl.asInstanceOf[DBObject]))).toMap
    setBox(Full(mapResult))
  }

  override def asJValue = {
    val fieldList = (for ((key, elem) <- value) yield JField(key, elem.asJValue)).toList
    JObject(fieldList)
  }

  override def setFromJValue(jvalue: JValue) = jvalue match {
    case JNothing | JNull if optional_? => setBox(Empty)
    case JObject(fieldList) =>
      val retrievedMap = fieldList.map {
        field =>
          val key = field.name
          val valRetrieved = valueMeta.fromJValue(field.value) openOr valueMeta.createRecord
          (key, valRetrieved)
      }.toMap
      setBox(Full(retrievedMap))
    case other => setBox(FieldHelpers.expectedA("JObject", other))
  }
}