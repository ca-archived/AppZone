var x = new Mongo('localhost');
var appzone = x.getDB('appzone');
// printjson(mydb);
// appzone.apps.remove();
// appzone.apps.insert({ "_id" : ObjectId("507d2d7ea6cb0ba724ddfa0c"), "name" : "AppZone", "id" : "appzone", "android" : { "version" : "1.0", "versionCode" : 9, "lastUpdateDate" : "2012-10-24T12:14:01+0900" } })
// appzone.apps.insert({ "_id" : ObjectId("507d2c85a6cb0ba724ddfa07"), "ios" : { "version" : "1.3.0", "versionCode" : 135, "lastUpdateDate" : "2012-11-13T13:47:58+0900" }, "name" : "パシャオク", "id" : "pashaoku", "android" : { "version" : "1.2.0", "versionCode" : 80, "lastUpdateDate" : "2012-11-12T22:56:47+0900" } })
function update(item) {
  print('Migrating: '+item.id);
  function updatePlatform(platform) {
    if (item[platform]) {
      item[platform] = { "_default" : item[platform] };
    }
  }
  updatePlatform('android');
  updatePlatform('ios');
  appzone.apps.update({_id: item._id}, item);
  appzone.apps.find({_id: item._id}).forEach(printjson);
}
appzone.apps.find().forEach(update);