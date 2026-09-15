import CallKit
import ExpoModulesCore

/// Info.plist keys written into the host app by the `expo-call-screening` config plugin.
private enum InfoPlistKey {
  /// App Group shared by the app and the Call Directory Extension, e.g. `group.com.example.callerid`.
  static let appGroup = "ExpoCallScreeningAppGroup"
  /// Bundle identifier of the Call Directory Extension, e.g. `com.example.callerid.CallDirectory`.
  static let extensionBundleIdentifier = "ExpoCallScreeningExtensionBundleIdentifier"
}

/// Key under which the identities are stored in the shared `UserDefaults`.
/// `CallDirectoryHandler` reads the very same key, so the two must stay in sync.
private let sharedStorageKey = "callerIdentities"

/// Field names of a single stored identity. Kept as a dictionary rather than
/// `Codable` so the extension can decode it without sharing any Swift code.
private enum StorageField {
  static let phoneNumber = "phoneNumber"
  static let label = "label"
}

/// A single caller identity as it arrives from JavaScript.
struct CallerIdentity: Record {
  /// Digits of the number to identify. See `CallDirectoryHandler` for the
  /// normalization rules: callers should pass numbers with a country code.
  @Field var phoneNumber: String = ""

  /// Text that iOS shows on the incoming call screen.
  @Field var label: String = ""
}

/// Thrown when the config plugin did not write a key into the host app's `Info.plist`.
final class MissingInfoPlistKeyException: GenericException<String> {
  override var reason: String {
    "Missing `\(param)` in Info.plist. Add the `expo-call-screening` config plugin to your Expo config "
      + "and re-run `npx expo prebuild`."
  }
}

/// Thrown when the App Group exists in the config but is not provisioned for this build.
final class UnavailableAppGroupException: GenericException<String> {
  override var reason: String {
    "Unable to open the shared UserDefaults suite `\(param)`. Make sure the App Group capability "
      + "is enabled for both the app and the Call Directory Extension."
  }
}

/// Thrown when CallKit refuses to reload the extension.
final class ReloadExtensionException: GenericException<String> {
  override var reason: String {
    "Failed to reload the Call Directory Extension `\(param)`. It is usually disabled in "
      + "Settings > Phone > Call Blocking & Identification."
  }
}

public class ExpoCallScreeningModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoCallScreening")

    // Replaces the stored identities. Nothing reaches the call screen until
    // `reload()` asks CallKit to re-run the extension.
    AsyncFunction("setCallerIdentities") { (entries: [CallerIdentity]) throws in
      let defaults = try sharedDefaults()
      let serialized = entries.map { entry in
        [StorageField.phoneNumber: entry.phoneNumber, StorageField.label: entry.label]
      }
      defaults.set(serialized, forKey: sharedStorageKey)
    }

    // Asks CallKit to re-run the extension so it picks up the stored identities.
    // Rejects while the extension is disabled in Settings.
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

    // Whether the user has enabled the extension in Settings.
    // A CallKit failure is reported as `"unknown"` rather than a rejection:
    // "we cannot tell" is exactly what the three-state contract describes.
    AsyncFunction("getStatus") { (promise: Promise) throws in
      let identifier = try extensionBundleIdentifier()

      CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(withIdentifier: identifier) { status, error in
        if let error {
          NSLog("[ExpoCallScreening] Unable to read the status of `%@`: %@", identifier, error.localizedDescription)
        }
        promise.resolve(statusString(for: status))
      }
    }

    // iOS has no permission prompt for Call Directory Extensions. The user has to
    // switch the extension on manually, so this opens
    // Settings > Phone > Call Blocking & Identification.
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

    // iOS draws the label itself from the Call Directory Extension, so there is
    // no overlay window and nothing to grant. Reporting `true` keeps call sites
    // free of `Platform.OS` checks: the Android-only gate is simply always open.
    AsyncFunction("hasOverlayPermission") { () -> Bool in
      true
    }

    // Counterpart of `hasOverlayPermission`: with no overlay there is no
    // settings page to send the user to, so this resolves without doing work.
    AsyncFunction("requestOverlayPermission") { (promise: Promise) in
      promise.resolve()
    }
  }
}

// MARK: - Configuration

/// Reads a non-empty string written into the host app's `Info.plist` by the config plugin.
private func infoPlistString(forKey key: String) throws -> String {
  guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String, !value.isEmpty else {
    throw MissingInfoPlistKeyException(key)
  }
  return value
}

private func extensionBundleIdentifier() throws -> String {
  try infoPlistString(forKey: InfoPlistKey.extensionBundleIdentifier)
}

/// The `UserDefaults` suite shared with the Call Directory Extension.
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
