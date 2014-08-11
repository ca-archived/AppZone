angular.module('app.services', [])

  .provider('$language', function () {
    var userLocale = navigator.language || navigator.userLanguage || 'en-US';
    this.$get = function () {
      return {
        userLocale: userLocale,
        userLanguage: userLocale.substring(0, 2)
      };
    }
  })

  .factory('os', function ($cookieStore) {
    var os = {};

    os.detected = function () {
      if (navigator.userAgent.indexOf('iPhone') != -1) {
        return 'ios';
      } else if (navigator.userAgent.indexOf('Android') != -1) {
        return 'android';
      } else {
        return 'other';
      }
    }();

    var storedSelectedOS = $cookieStore.get('selectedOS');

    if (storedSelectedOS) {
      os.selectedValue = storedSelectedOS;
    } else {
      if (os.detected === 'other') {
        os.selectedValue = 'android';
      } else {
        os.selectedValue = os.detected;
      }
    }

    os.selected = function (newValue) {
      if (newValue) {
        os.selectedValue = newValue;
        $cookieStore.put('selectedOS', newValue);
        console.log('OS changed to: ' + newValue);
      }
      return this.selectedValue;
    };

    return os;
  })

  .factory('AppItemsService', function ($resource, $q) {
    var AppItemList = $resource(Config.apiServer + '/apps', {}, {
      cache: true
    });
    var cachedItems;
    return {
      getNameById: function (itemId) {
        var item = _.find(cachedItems, {id: itemId});
        return item ? item.name : '';
      },
      get: function () {
        var q = $q.defer();

        if (cachedItems) {
          q.resolve(cachedItems);
        } else {
          AppItemList.query({},
            function (items) {
              cachedItems = _.filter(items, function (item) {
                return item.id.indexOf('_') !== 0;
              });
              q.resolve(cachedItems);
            },
            function (err) {
              q.reject(err);
            });
        }
        return q.promise;
      }
    }
  })

  .factory('Alerts', function ($rootScope, $sce) {
    var alerts = [];
    return {
      get: function () {
        return alerts;
      },
      message: function (message, title) {
        alerts.push({message: $sce.trustAsHtml(message), title: title});
      },
      warn: function (message, title) {
        alerts.push({type: 'warning', message: $sce.trustAsHtml(message), title: title});
      },
      error: function (message, title) {
        alerts.push({type: 'danger', message: $sce.trustAsHtml(message), title: title});
      },
      success: function (message, title) {
        alerts.push({type: 'success', message: $sce.trustAsHtml(message), title: title});
      },
      info: function (message, title) {
        alerts.push({type: 'info', message: $sce.trustAsHtml(message), title: title});
      },
      clear: function (index) {
        alerts.splice(index, 1);
      },
      clearAll: function () {
        alerts = [];
      }
    }
  })

  .factory('AppReleasesService', function ($resource, $q) {
    return $resource(Config.apiServer + '/app/:id');
  })

  .factory('AppLoginService', function ($resource) {
    return $resource(Config.apiServer + '/auth');
  });
