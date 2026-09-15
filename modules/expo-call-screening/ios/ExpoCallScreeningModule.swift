import CallKit
import ExpoModulesCore

private enum InfoPlistKey {
  static let appGroup = "ExpoCallScreeningAppGroup"
  static let extensionBundleIdentifier = "ExpoCallScreeningExtensionBundleIdentifier"
}

// Shared storage contract with targets/call-directory/CallDirectoryHandler.swift.
private let sharedStorageKey = "callerIdentities"

private enum StorageField {
  static let phoneNumber = "phoneNumber"
  static let label = "label"
}

struct CallerIdentity: Record {
  @Field var phoneNumber: String = ""

  @Field var label: String = ""
}

final class MissingInfoPlistKeyException: GenericException<String> {
  override var reason: String {
    "Missing `\(param)` in Info.plist. Add the `expo-call-screening` config plugin to your Expo config "
      + "and re-run `npx expo prebuild`."
  }
}

final class UnavailableAppGroupException: GenericException<String> {
  override var reason: String {
    "Unable to open the shared UserDefaults suite `\(param)`. Make sure the App Group capability "
      + "is enabled for both the app and the Call Directory Extension."
  }
}

final class ReloadExtensionException: GenericException<String> {
  override var reason: String {
    "Failed to reload the Call Directory Extension `\(param)`. It is usually disabled in "
      + "Settings > Phone > Call Blocking & Identification."
  }
}

public class ExpoCallScreeningModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoCallScreening")

    // CallKit reads the new list on the next extension reload.
    AsyncFunction("setCallerIdentities") { (entries: [CallerIdentity]) throws in
      let defaults = try sharedDefaults()
      let serialized = entries.map { entry in
        [StorageField.phoneNumber: entry.phoneNumber, StorageField.label: entry.label]
      }
      defaults.set(serialized, forKey: sharedStorageKey)
    }

    // Reload rejects while the extension is disabled.
    AsyncFunction("reload") { (promise: Promise) throws in
      let identifier = try extensionBundleIdentifier()

      CXCallDirectoryManager.sharedInstance.reloadExtension(withIdentifier: identifier) { error in
        if let error {
          promise.reject(ReloadExtensionException(identifier).causedBy(error))
        } else {
          promise.resolve()
        }
      }
    }

    // Status lookup failures are represented as unknown.
    AsyncFunction("getStatus") { (promise: Promise) throws in
      let identifier = try extensionBundleIdentifier()

      CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(withIdentifier: identifier) { status, error in
        if let error {
          NSLog("[ExpoCallScreening] Unable to read the status of `%@`: %@", identifier, error.localizedDescription)
        }
        promise.resolve(statusString(for: status))
      }
    }

    // iOS requires the user to enable the extension manually in Settings.
    AsyncFunction("requestPermission") { (promise: Promise) in
      CXCallDirectoryManager.sharedInstance.openSettings { error in
        if let error {
          promise.reject(UnexpectedException(error))
        } else {
          promise.resolve()
        }
      }
    }
    .runOnQueue(.main)

    // iOS draws caller ID natively, so no overlay permission is needed.
    AsyncFunction("hasOverlayPermission") { () -> Bool in
      true
    }

    AsyncFunction("requestOverlayPermission") { (promise: Promise) in
      promise.resolve()
    }
  }
}

// MARK: - Configuration

private func infoPlistString(forKey key: String) throws -> String {
  guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String, !value.isEmpty else {
    throw MissingInfoPlistKeyException(key)
  }
  return value
}

private func extensionBundleIdentifier() throws -> String {
  try infoPlistString(forKey: InfoPlistKey.extensionBundleIdentifier)
}

private func sharedDefaults() throws -> UserDefaults {
  let appGroup = try infoPlistString(forKey: InfoPlistKey.appGroup)

  guard let defaults = UserDefaults(suiteName: appGroup) else {
    throw UnavailableAppGroupException(appGroup)
  }
  return defaults
}

private func statusString(for status: CXCallDirectoryManager.EnabledStatus) -> String {
  switch status {
  case .enabled:
    return "enabled"
  case .disabled:
    return "disabled"
  case .unknown:
    return "unknown"
  @unknown default:
    return "unknown"
  }
}
