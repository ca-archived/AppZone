angular.module('app.filters', [])
  .filter('initial', function () {
    return function (text) {
      return text ? text.substring(0, 1).toUpperCase() : '';
    };
  })

  .filter('capitalize', function () {
    return function (text) {
      return text ? text.substring(0, 1).toUpperCase() + text.substring(1) : '';
    };
  })

  .filter('fromNow', function () {
    return function (dateString) {
      return moment(dateString).fromNow()
    };
  })

  .filter('newLines', function () {
    return function (text) {
      return text.replace(/\n/g, '<br/>');
    }
  });