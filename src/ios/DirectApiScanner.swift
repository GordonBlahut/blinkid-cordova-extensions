import Microblink
import UIKit
import MobileCoreServices

@objc(DirectApiScanner) class DirectApiScanner: CDVPlugin, UIImagePickerControllerDelegate, UINavigationControllerDelegate, MBScanningRecognizerRunnerDelegate {
  var recognizerCollection: MBRecognizerCollection?
  var recognizerRunner: MBRecognizerRunner?
  var lastCommand: CDVInvokedUrlCommand?

  static let RESULT_LIST = "resultList"
  static let CANCELLED = "cancelled"

  @objc(scanWithPhotoLibrary:)
  func scanWithPhotoLibrary(command: CDVInvokedUrlCommand) {
    lastCommand = command

    var pluginResult = CDVPluginResult(
      status: CDVCommandStatus_ERROR
    )

    let jsonRecognizerCollection = sanitizeDictionary(command.arguments[0] as? [String: Any] ?? [:])
    let jsonLicenses = sanitizeDictionary(command.arguments[1] as? [String: Any] ?? [:])

    setLicense(jsonLicenses)
    recognizerCollection = MBRecognizerSerializers.sharedInstance().deserializeRecognizerCollection(jsonRecognizerCollection)

    setupRecognizerRunner()
    openImagePicker()
  }

  func sanitizeDictionary(_ dictionary: [String: Any]) -> [String: Any] {
    var dict = dictionary
    for key in dict.keys {
      if dict[key] is NSNull {
        dict[key] = nil
      }
    }

    return dict
  }

  func setLicense(_ jsonLicense: [String: Any]) {
    if let showTimeLimitedLicenseKeyWarning = jsonLicense["showTimeLimitedLicenseKeyWarning"] as? Bool {
      MBMicroblinkSDK.sharedInstance().showLicenseKeyTimeLimitedWarning = showTimeLimitedLicenseKeyWarning
    }

    let iosLicense = jsonLicense["ios"] as! String

    if let licensee = jsonLicense["licensee"] as? String {
      MBMicroblinkSDK.sharedInstance().setLicenseKey(iosLicense, andLicensee: licensee)
    }
    else {
      MBMicroblinkSDK.sharedInstance().setLicenseKey(iosLicense)
    }
  }

  func openImagePicker() {
        let imagePicker = UIImagePickerController()
        imagePicker.sourceType = .photoLibrary
        imagePicker.mediaTypes = [kUTTypeImage as String]
        imagePicker.allowsEditing = false
        imagePicker.delegate = self
        imagePicker.modalPresentationStyle = .overCurrentContext

        viewController.present(imagePicker, animated: true) {() -> Void in }
    }

    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [String : Any]) {
        let mediaType = info[UIImagePickerControllerMediaType] as! String

        // Handle image
        if mediaType.isEqual(kUTTypeImage as String) {
            let originalImage = info[UIImagePickerControllerOriginalImage] as! UIImage
            processImageRunner(originalImage)
        }

        picker.dismiss(animated: true) {() -> Void in }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
      picker.dismiss(animated: true) {() -> Void in }

      let resultDict: [AnyHashable: Any] = [
          DirectApiScanner.CANCELLED: true
      ]

      let pluginResult = CDVPluginResult(
        status: CDVCommandStatus_OK,
        messageAs: resultDict
      )

      sendPluginResult(pluginResult!)
    }

    func setupRecognizerRunner() {
        recognizerRunner = MBRecognizerRunner(recognizerCollection: recognizerCollection!)
        recognizerRunner?.scanningRecognizerRunnerDelegate = self
    }

    func processImageRunner(_ originalImage: UIImage?) {
        var image: MBImage? = nil

        if let anImage = originalImage {
            image = MBImage(uiImage: anImage)
        }

        image?.cameraFrame = true
        image?.orientation = MBProcessingOrientation.left

        let _serialQueue = DispatchQueue(label: "blinkid-cordova-extensions")

        _serialQueue.async(execute: {() -> Void in
            self.recognizerRunner?.processImage(image!)
        })
    }

    func recognizerRunner(_ recognizerRunner: MBRecognizerRunner, didFinishScanningWith state: MBRecognizerResultState) {
      DispatchQueue.main.async(execute: {() -> Void in
        var results = [[AnyHashable: Any]?](repeating: nil, count: self.recognizerCollection!.recognizerList.count)

        let maxIndex = max(self.recognizerCollection!.recognizerList.count - 1, 0)

        for index in 0...maxIndex {
          results[index] = self.recognizerCollection!.recognizerList[index].serializeResult()
        }

        let resultDict: [AnyHashable: Any] = [
          DirectApiScanner.CANCELLED: false,
          DirectApiScanner.RESULT_LIST: results
        ]

        let pluginResult = CDVPluginResult(
          status: CDVCommandStatus_OK,
          messageAs: resultDict
        )

        self.sendPluginResult(pluginResult!)
      })
    }

  func sendPluginResult(_ pluginResult: CDVPluginResult) {
    self.commandDelegate.send(pluginResult, callbackId:lastCommand!.callbackId)

    self.recognizerCollection = nil
    self.recognizerRunner = nil
  }
}
