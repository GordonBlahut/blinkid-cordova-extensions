/* eslint-disable */
var exec = require('cordova/exec');

/**
 * Constructor.
 *
 * @returns {BlinkIDExtensions}
 */
function BlinkIDExtensions() {

};

/**
 * successCallback: callback that will be invoked on successful scan
 * errorCallback: callback that will be invoked on error
 * recognizerCollection: {RecognizerCollection} containing recognizers to use for scanning
 * licenses: object containing:
 *               - base64 license keys for iOS and Android
 *               - optioanl parameter 'licensee' when license for multiple apps is used
 *               - optional flag 'showTimeLimitedLicenseKeyWarning' which indicates
 *                  whether warning for time limited license key will be shown, in format
 *  {
 *      ios: 'base64iOSLicense',
 *      android: 'base64AndroidLicense',
 *      licensee: String,
 *      showTimeLimitedLicenseKeyWarning: Boolean
 *  }
 */
BlinkIDExtensions.prototype.scanWithPhotoLibrary = function (successCallback, errorCallback, recognizerCollection, licenses) {
  if (errorCallback == null) {
      errorCallback = function () {
      };
  }

  if (typeof errorCallback != "function") {
      console.log("BlinkIDScannerExtensions.scanWithPhotoLibrary failure: failure parameter not a function");
      throw new Error("BlinkIDScannerExtensions.scanWithPhotoLibrary failure: failure parameter not a function");
      return;
  }

  if (typeof successCallback != "function") {
      console.log("BlinkIDScanner.scanWithPhotoLibrary failure: success callback parameter must be a function");
      throw new Error("BlinkIDScanner.scanWithPhotoLibrary failure: success callback parameter must be a function");
      return;
  }

  // first invalidate old results
  for (var i = 0; i < recognizerCollection.recognizerArray[i].length; ++i ) {
      recognizerCollection.recognizerArray[i].result = null;
  }

  exec(
      function internalCallback(scanningResult) {
          var cancelled = scanningResult.cancelled;

          if (cancelled) {
              successCallback(true);
          } else {
              var results = scanningResult.resultList;
              if (results.length != recognizerCollection.recognizerArray.length) {
                  console.log("INTERNAL ERROR: native plugin returned wrong number of results!");
                  throw new Error("INTERNAL ERROR: native plugin returned wrong number of results!");
                  errorCallback(new Error("INTERNAL ERROR: native plugin returned wrong number of results!"));
              } else {
                  for (var i = 0; i < results.length; ++i) {
                      // native plugin must ensure types match
                      recognizerCollection.recognizerArray[i].result = recognizerCollection.recognizerArray[i].createResultFromNative(results[i]);
                  }
                  successCallback(false);
              }
          }
      },
      errorCallback, 'BlinkIDExtensionsScanner', 'scanWithPhotoLibrary', [recognizerCollection, licenses]);
};

module.exports = new BlinkIDExtensions();
