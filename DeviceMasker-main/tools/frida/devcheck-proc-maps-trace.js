Java.perform(function () {
  var FileInputStream = Java.use("java.io.FileInputStream");
  FileInputStream.$init.overload("java.lang.String").implementation = function (path) {
    if (path && path.indexOf("/proc/") === 0 && path.indexOf("/maps") !== -1) {
      console.log("FileInputStream maps open: " + path);
    }
    return this.$init(path);
  };

  var FileReader = Java.use("java.io.FileReader");
  FileReader.$init.overload("java.lang.String").implementation = function (path) {
    if (path && path.indexOf("/proc/") === 0 && path.indexOf("/maps") !== -1) {
      console.log("FileReader maps open: " + path);
    }
    return this.$init(path);
  };

  var Runtime = Java.use("java.lang.Runtime");
  Runtime.exit.implementation = function (code) {
    console.log("Runtime.exit called with code: " + code);
    return this.exit(code);
  };
});
