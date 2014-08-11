angular.module('app.controllers', [])

  .controller('TopCtrl', function($scope, os) {
    $scope.leftMenuEnabled = os.detected === 'other';
  })

  .controller('AppCtrl', function ($scope, $resource, os, AppItemsService, AppReleasesService, Alerts) {
    // TODO: Controller gets created 2 times

    if (os.detected === 'other') {
      $scope.leftButtons = [
        {
          type: 'button-icon',
          content: '<i class="icon ion-navicon"></i>',
          tap: function () {
            $scope.sideMenuController.toggleLeft();
          }
        }
      ];
    }

    $scope.rightButtons = [
      {
        type: 'button-icon',
        content: '<i class="icon ion-ios7-minus-outline"></i>',
        tap: function () {
          $scope.showDelete = !$scope.showDelete;
        }
      }
    ];

    $scope.filterOS = function (item) {
      return (os.selected() === 'ios' && !_.isEmpty(item.ios)) ||
        (os.selected() === 'android' && !_.isEmpty(item.android));
    };

    AppItemsService.get().then(function (items) {
      $scope.appItems = items;
    }, function(reason) {
      Alerts.error(reason.data, 'Error');
    });

    $scope.numberOfApps = function (item) {
      return item[os.selected()].length;
    };

    $scope.iconUrl = function (item) {
      var newestRelease = item[os.selected()].reduce(function(a, b) {
        return a.lastUpdateDate > b.lastUpdateDate ? a : b;
      });
      if (newestRelease.hasIcon) {
        return Config.apiServer + '/app/' + item.id + '/' + os.selected() + '/' + newestRelease.id + '/icon.png';
      }
      return null;
    };

    $scope.onItemDelete = function (item) {
      if (confirm('Are you sure you want to delete the entire ' + item.name + ' app?')) {
        AppReleasesService.delete({id: item.id}).$promise.then(function (response) {
          $scope.appItems.splice($scope.appItems.indexOf(item), 1);
        }, function (reason) {
          Alerts.error(reason.data);
        });
      }
    };
  })


  .controller('ReleasesCtrl', function ($scope, $resource, $routeParams, $route, os, Alerts, $sce, $filter, AppReleasesService, AppItemsService, $location, $window) {
    $scope.rightButtons = [
      {
        type: 'button-icon',
        content: '<i class="icon ion-ios7-minus-outline"></i>',
        tap: function () {
          $scope.showDelete = !$scope.showDelete;
        }
      }
    ];

    $scope.leftButtons = [
      {
        type: 'button-icon',
        content: '<i class="icon ion-ios7-arrow-back"></i>',
        tap: function () {
          $location.path('#/');
          //$scope.sideMenuController.toggleLeft();
        }
      }
    ];

    var item = AppReleasesService.get({id: $routeParams.itemId}, function () {
      if (_.isEmpty($scope.name)) {
        $scope.name = item.name;
      }
      $scope.releaseItems = item[os.selected()];
    }, function(reason) {
      Alerts.error(reason.data, 'Error');
    });

    $scope.name = AppItemsService.getNameById($routeParams.itemId);

    $scope.selectedOSName = function () {
      switch (os.selected()) {
      case 'ios':
        return 'iOS';
      case 'android':
        return 'Android';
      }
      return 'Other OS';
    };

    $scope.showWithNewLines = function (text) {
      return $filter('newLines')(text);
//    return $sce.trustAsHtml($filter('newLines')(text));
    };

    $scope.showChangeLog = false;

    $scope.toggleChangeLog = function (releaseItem) {
      releaseItem.showChangeLog = !releaseItem.showChangeLog;
    };

    $scope.downloadLink = function (releaseItem, $event) {
      $event.stopPropagation();
      window.location = Config.apiServer + '/app/' + item.id + '/' + os.selected() + '/' + releaseItem.id;
    };

    $scope.iconUrl = function (releaseItem) {
      if (releaseItem.hasIcon) {
        return Config.apiServer + '/app/' + item.id + '/' + os.selected() + '/' + releaseItem.id + '/icon.png';
      }
      return null;
    };

    $scope.feedbackEnabled = Config.feedbackEnabled;
    $scope.feedbackText = '';

    $scope.sendFeedback = function () {
      var AppFeedback = $resource(Config.apiServer + '/app/' + item.id + '/' + os.selected() + '/feedback');

      var feedback = new AppFeedback();
      feedback.feedback = $scope.feedbackText;

      var saved = feedback.$save().then(function (response) {
        console.log(response);
      }, function (reason) {
        Alerts.error(reason.data);
      });
    };

    // TODO: Add delete release
    $scope.deleteRelease = function (index, $event) {
      $event.stopPropagation();

      var releaseItem = $scope.releaseItems[index];
      console.log('Deleting release ', releaseItem);
      var DeleteRelease = $resource(Config.apiServer + '/app/' + item.id + '/' + os.selected() + '/' + releaseItem.id);

      if (confirm('Are you sure you want to delete the ' + releaseItem.id + ' release?')) {
        DeleteRelease.delete().$promise.then(function (response) {
          $scope.releaseItems.splice(index, 1);
        }, function (reason) {
          Alerts.error(reason.data);
        });
      }
    };

    $scope.activeOS = os.selectedValue;

    $scope.$watch('activeOS', function (newValue, oldValue) {
      if (newValue !== oldValue) {
        $scope.releaseItems = item[os.selected()];
      }
    });

    $scope.makeArrayFromString = function (str) {
      return str.split('\n');
    }
  })


  .controller('LoginCtrl', function ($scope, AppLoginService, Alerts) {
    $scope.login = function () {
      var appLogin = new AppLoginService();
      appLogin.username = $scope.username;
      appLogin.password = $scope.password;

      appLogin.$save(function (response) {
        $scope.goToUrl('/');
      }, function (reason) {
        Alerts.error(reason.data, 'Login Error (' + reason.status + ')');
      });
    }
  })

  .controller('MenuCtrl', function ($scope, os) {
    $scope.selectedOS = os.selected();

    $scope.$watch('selectedOS', function (newVal) {
      os.selected(newVal);
    });
  });
