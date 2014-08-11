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

import javax.naming.directory.DirContext
import javax.naming.directory.InitialDirContext
import javax.naming.Context
import java.util.Hashtable
import net.liftweb.util.Props
import org.slf4j.LoggerFactory

trait AuthenticationSupport {
  val logger = LoggerFactory.getLogger(getClass)

  def doAuth(username: String, password: String): Boolean = {
    Props.get("auth.source", "").toLowerCase match {
      case "simple" => loginSimple(username, password)
      case "ldap" => loginLdap(username, password)
      case _ => false
    }
  }

  def loginSimple(username: String, password: String): Boolean = {
    username == Props.get("auth.simple.username", null) &&
      password == Props.get("auth.simple.password", null)
  }

  def loginLdap(username: String, password: String): Boolean = {
    var success = false
    var errors: List[String] = Nil

    def tryLoginLdap(base: String): Boolean = {
      val url: String = Props.get(base + ".url", null)
      val principal: String = Props.get(base + ".principal", null) format (username)

      try {
        val env: Hashtable[String, String] = new Hashtable[String, String]()

        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
        env.put(Context.PROVIDER_URL, url)

        env.put(Context.SECURITY_AUTHENTICATION, "simple")
        env.put(Context.SECURITY_PRINCIPAL, principal)
        env.put(Context.SECURITY_CREDENTIALS, password)

        val ctx: DirContext = new InitialDirContext(env)
        val result = ctx != null

        if (ctx != null)
          ctx.close()

        result
      } catch {
        case e: Exception => {
          val message = "LDAP login failure for " + principal + "@" + url + ": " + e.getMessage
          errors = message :: errors
          false
        }
      }
    }

    // Trying to log in
    for (i <- 0 to 5) {
      val base = if (i == 0) {
        "auth.ldap"
      } else {
        "auth.ldap." + i
      }

      if (Props.props.contains(base + ".url")) {
        success = success || tryLoginLdap(base)
      }
    }

    if (!success) {
      for (e <- errors) {
        logger.info(e)
      }
    }

    success
  }
}