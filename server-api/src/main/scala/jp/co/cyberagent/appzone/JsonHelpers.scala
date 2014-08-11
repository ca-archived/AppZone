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

trait JsonHelpers {
  import net.liftweb.json._
  import net.liftweb.json.ext._
  import net.liftweb.json.Extraction._

  implicit val formats = DefaultFormats ++ JodaTimeSerializers.all

  object Json {
    def apply(json: JValue, compacting: Boolean) = {
      val doc = render(json)
      if (compacting) compact(doc) else pretty(doc)
    }

    def apply(a: Any): Any = apply(decompose(a), compacting=true)
  }
}