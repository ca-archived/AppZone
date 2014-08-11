#AppZone
![AppZone Icon](server-web/app/img/AppZone-Icon_200.png?raw=true)

> Internal AppStore for Android and iOS apps. 
> Supports publish newest builds from Jenkins or API. Supports OTA downloads and optional authentication with LDAP.

![Screenshot](http://i.imgur.com/KBRmuVCl.png)
![Screenshot](http://i.imgur.com/lRtTSGPl.png)


# AppZone Server

## Description
AppZone provides a simple interface for publishing Android and iOS apps through a simple web interface.
Apps can be published on AppZone using simple [REST commands](/server-api/doc/api.md) or with the [Jenkins plugin](/jenkins-plugin).

![overview.png](overview.png?raw=true).

## Components

Location       | Note
-------------- | ------------------------------
jenkins-plugin | This is the Jenkins plugin for Appzone. It is written in Java and uses Apache Maven for lifecycle management. It make use of the AppZone API.
server-api | This is the Appzone API server. It is written in Scala and makes use of sbt and Lift. It also requires a MongoDB backend.
server-web | This is the static web frontend for Appzone. It is written in Javascript.

## Requirements

* mongodb. Any 2.4.7 compatible version should do.
* Java JDK 1.7
* If you plan on building the Jenkins plugin, you will need Apache Maven, as well. Any 3.x version or newer should do.

## Default settings

Default settings can be edited in [run.properties](run.properties). This includes MongoDB configuration, API host and port, as well as authentication settings such as option LDAP settings.

Please make sure that your host name is included in the hosts file for your machine. Java based host name lookup will **fail** if it cannot lookup your IP from your host name. 

## Simple build & run everything
    ./run.sh start

Open your browser at [http://localhost:8080](http://localhost:8080).

To configure the domain, ports for various subsystem and mongodb edit `run.properties`. You can also edit the security settings in there while you are at it.

## Run the nicer way

### Run API
First you will need a Tomcat or Jetty server running. The API server allows for uploading of artifacts via a REST API interface. This is used internally by the Jenkins plugin, but it can also be called from your own program or used with curl.

    cd server-api/
    ./sbt package
    cp target/.../...war <your_tomcat>/webapps/appzone.war

To configure mongodb, either edit the source, or create a default.props file in src/main/resources/props.

### Run WEB
Edit the SERVER variable at the top of [server-web/app/js/config/config.js](server-web/app/js/config/config.js) to point to your running API. Then serve server-web as a static website. Preferably through nginx. Otherwise you can run:

	cd server-web/
	python -m SimpleHTTPServer
	# or: sudo python -m SimpleHTTPServer 80

## Build & run in sbt
    cd server-api/
    ./sbt
    > container:start
    > ~ ;copy-resources;aux-compile

Now open the site's [root page](http://localhost:8080/) in your browser.

## Jenkins plugin
Running the following command creates a .hpi file in the target directory, that can be installed in Jenkins in the plugin manager (advanced tab).

    cd jenkins-plugin
    mvn package

## Manual uploads
Manual uploading of an artifact is possible if you make use of the API and curl.  Refer to the [API](/server-api/doc/api.md) documentation for the URL paths required for this. Some common examples are provided below. 

### Create a project

   curl --interface 127.0.0.1 -sLX POST http://127.0.0.1:8081/app -d id=exampleApp -d name=exampleAppName
   {"ios":[],"name":"exampleAppName","id":"exampleApp","android":[]}    

### Get a list of projects

   curl --interface 127.0.0.1 -sLX GET http://127.0.0.1:8081/apps
   [{"ios":[],"name":"exampleAppName","id":"exampleApp","android":[]}]

### Uploading an artifact

    curl -X POST http://127.0.0.1:8081/app/exampleApp/android -F version=0.1.1 -F apk=@Downloads/Test_android-Test_dev.apk
    {"ios":[],"name":"exampleAppName","id":"exampleApp","android":[{"lastUpdateDate":"2014-05-02T11:44:36+0900","changelog":"","versionCode":1,"version":"0.1.1","id":"_default","hasIcon":true}]

### Download an artifact

    curl --interface 127.0.0.1 -sLX GET http://127.0.0.1:8081/app/exampleApp/android

## Libraries
Appzone uses the following libraries.

### server-api

Library | Version
------- | -------
Lift | 2.6-M2
Scalatra | 2.1.1
logback-classic | 1.1.1
xpp3 | 1.1.4c
jzlib | 1.1.1
commons-io | 1.3.2
commons-net | 3.2
jetty-webapp | 8.1.7.v20120910
javax.servlet | 3.0.0.v201112011016

### server-web

Library | Version
------- | -------
ionic | 0.9.18-alpha
lodash | 2.4.1
angular-http-auth | 1.2.1
angular-translate | 1.1.1

### jenkins-plugin

Library | Version
------- | -------
dd-plist | 1.0
xmlunit | 1.3
snakeyaml | 1.7
xpp3 | 1.1.4c

## License
    Copyright 2013 CyberAgent

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
