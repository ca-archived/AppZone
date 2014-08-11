angular.module('app', ['ionic', 'ngRoute', 'ngResource', 'ngCookies', 'ngAnimate', 'http-auth-interceptor',
    'app.services', 'app.directives', 'app.controllers', 'app.filters', 'pascalprecht.translate'])

  .config(function ($compileProvider) {
    // Needed for routing to work
    $compileProvider.aHrefSanitizationWhitelist(/^\s*(https?|ftp|mailto|file|tel):/);
  })

  .config(function ($routeProvider, $locationProvider) {
    $routeProvider
      .when('/home', {
        templateUrl: 'templates/app.html',
        controller: 'AppCtrl'
      })

      .when('/releases/:itemId', {
        templateUrl: 'templates/releases.html',
        controller: 'ReleasesCtrl'
      })

      .when('/login', {
        templateUrl: 'templates/login.html',
        controller: 'LoginCtrl'
      })

      .otherwise({
        redirectTo: '/home'
      });
  })

  .config(function ($httpProvider) {
    $httpProvider.defaults.withCredentials = true;
    $httpProvider.defaults.headers.post["Content-Type"] = "application/x-www-form-urlencoded;charset=utf-8";

    // Override $http service's default transformRequest for using form-data instead of json
    $httpProvider.defaults.transformRequest = [function (data) {
      /**
       * The workhorse; converts an object to x-www-form-urlencoded serialization.
       * @param {Object} obj
       * @return {String}
       */
      var param = function (obj) {
        var query = '';
        var name, value, fullSubName, subName, subValue, innerObj, i;

        for (name in obj) {
          value = obj[name];

          if (value instanceof Array) {
            for (i = 0; i < value.length; ++i) {
              subValue = value[i];
              fullSubName = name + '[' + i + ']';
              innerObj = {};
              innerObj[fullSubName] = subValue;
              query += param(innerObj) + '&';
            }
          }
          else if (value instanceof Object) {
            for (subName in value) {
              subValue = value[subName];
              fullSubName = name + '[' + subName + ']';
              innerObj = {};
              innerObj[fullSubName] = subValue;
              query += param(innerObj) + '&';
            }
          }
          else if (value !== undefined && value !== null) {
            query += encodeURIComponent(name) + '=' + encodeURIComponent(value) + '&';
          }
        }

        return query.length ? query.substr(0, query.length - 1) : query;
      };

      return angular.isObject(data) && String(data) !== '[object File]' ? param(data) : data;
    }];
  })

  .config(function ($translateProvider, $languageProvider) {
    $translateProvider.translations('en', {
      LOGIN_TO_APPZONE: 'Log in to AppZone',
      USER_NAME: 'User name',
      PASSWORD: 'Password',
      LOG_IN: 'Log In'
    });
    $translateProvider.translations('ja', {
      LOGIN_TO_APPZONE: 'AppZoneへようこそ！',
      USER_NAME: 'ユーザ名',
      PASSWORD: 'パスワード',
      LOG_IN: 'ログイン'
    });
    $translateProvider.translations('es', {
      LOGIN_TO_APPZONE: 'Bienvenido a AppZone',
      USER_NAME: 'Usuario',
      PASSWORD: 'Contraseña',
      LOG_IN: 'Iniciar sesión'
    });

    var supportedLangs = ['en', 'ja', 'es'];

    var userLanguage = $languageProvider.$get().userLanguage;
    var supportedLang = (supportedLangs.indexOf(userLanguage) !== -1) ? userLanguage : 'en';

    $translateProvider.preferredLanguage(supportedLang);
    moment.lang($languageProvider.$get().userLanguage);
  })

  .run(function ($rootScope, $location, Alerts, $templateCache, $http) {

    var preloadTemplates = function (templates) {
      angular.forEach(templates, function (path) {
        $http.get(path).success(function (html) {
          $templateCache.put(path, html);
        })
      })
    };

    preloadTemplates([
      'templates/login.html',
      'templates/app.html',
      'templates/releases.html',
    ]);

    $rootScope.alerts = Alerts.get();

    $rootScope.$on("$locationChangeStart", function (event, next, current) {
      if (current !== next) {
        Alerts.clearAll();
      }
    });

    //$rootScope.params = $routeParams;
    $rootScope.is = function (type, value) {
      return angular['is' + type](value);
    };
    $rootScope.empty = function (value) {
      return $.isEmptyObject(value);
    };
    $rootScope.log = function (variable) {
      console.log(variable);
    };
    $rootScope.alert = function (text) {
      alert(text);
    };
    $rootScope.goToUrl = function (url) {
      window.location = url;
    };
    $rootScope.appTitle = Config.title;
    $rootScope.$on('event:auth-loginRequired', function (rejection) {
      // TODO: This is hacky
      var currentRoute = ((rejection || {}).targetScope || {}).actualLocation;

      if (currentRoute === '/login') {
        Alerts.error('Your username or password was entered incorrectly.', 'Authentication Error')
      }
      $location.path('/login');
    });
  });
