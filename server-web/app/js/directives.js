angular.module('app.directives', [])

  .directive('alert', function () {
    return {
      restrict: 'EA',
      controller: function ($scope, $attrs) {
        $scope.closeable = 'close' in $attrs;
      },
      template:
        '<div class="card alert" ng-class="type && \'alert-\' + type">' +
          '<div ng-if="title" class="item item-divider" ng-bind="title"></div>' +
          '<div class="item item-text-wrap">' +
            '<button ng-show="closeable" type="button" class="close" ng-click="close()">&times;</button>' +
            '<div ng-if="message" ng-bind-html="message"></div>' +
            '<div ng-transclude></div>' +
          '</div>' +
        '</div>',
      transclude: true,
      replace: true,
      scope: {
        message: '=',
        title: '=',
        type: '=',
        close: '&'
      }
    };
  })


// https://gist.github.com/yoshokatana/7947233
// https://github.com/angular/angular.js/issues/1460
//
// autofill catcher (super hacky, abandon all hope ye who enter here --np)
  .directive('autofill', ['$timeout', function ($timeout) {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function (scope, elem, attrs) {
        var ownInput = false;

        // trigger an input 500ms after loading the page (fixes chrome and safari autofill)
        $timeout(function () {
          angular.element(elem[0]).triggerHandler('input');
        }, 500);

        // listen for pertinent events to trigger input on form element
        // use timeout to ensure val is populated before triggering 'input'
        // ** 'change' event fires for Chrome
        // ** 'DOMAttrModified' fires for Firefox 22.0
        // ** 'keydown' for Safari  6.0.1
        // ** 'propertychange' for IE
        elem.on('change DOMAttrModified keydown propertychange', function () {
          $timeout(function () {
            angular.element(elem[0]).triggerHandler('input');
          }, 0);
        });

        // tell other inputs to trigger (fixes firefox and ie9+ autofill)
        elem.on('input', function () {
          if (ownInput === false) scope.$emit('loginform.input');
        });

        // catches event and triggers if another input fired it.
        scope.$on('loginform.input', function (e) {
          e.stopPropagation();
          ownInput = true;
          angular.element(elem[0]).triggerHandler('input');
          ownInput = false;
        });
      }
    }
  }]);