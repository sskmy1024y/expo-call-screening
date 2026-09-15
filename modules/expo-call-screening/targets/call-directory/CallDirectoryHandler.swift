import CallKit
import Foundation

/// Key the host app writes into the shared `UserDefaults` suite.
/// Must stay in sync with `ios/ExpoCallScreeningModule.swift`; the extension shares no
/// code with the app, so the contract is duplicated rather than imported.
private let sharedStorageKey = "callerIdentities"

/// Field names of a single stored identity.
private enum StorageField {
  static let phoneNumber = "phoneNumber"
  static let label = "label"
}

/// One entry ready to be handed to CallKit.
private struct IdentificationEntry {
  let phoneNumber: CXCallDirectoryPhoneNumber
  let label: String
}

/// Proof-of-concept fallback, used only while the app has never written a list.
/// It makes a fresh install show something on the call screen before the app has
/// stored any identities. A list the app deliberately cleared stays cleared, so
/// this never resurrects a sample entry. Drop the constant and the `??` below to
/// make the extension storage-only.
/// Entries must already be sorted ascending, like everything CallKit receives.
private let fallbackEntries: [IdentificationEntry] = [
  IdentificationEntry(phoneNumber: 81312345678, label: "Expo Caller ID sample")
]

/// Supplies caller identities to iOS.
///
/// iOS runs this extension out of process, on its own schedule, and only while the
/// user has enabled it under Settings > Phone > Call Blocking & Identification.
/// `CXCallDirectoryManager.reloadExtension` from the app asks for a new run.
class CallDirectoryHandler: CXCallDirectoryProvider {
  override func beginRequest(with context: CXCallDirectoryExtensionContext) {
    context.delegate = self

    // Every run publishes the complete list, so an incremental request starts by
    // discarding what a previous run added.
    if context.isIncremental {
      context.removeAllIdentificationEntries()
    }

    for entry in identificationEntries() {
      context.addIdentificationEntry(
        withNextSequentialPhoneNumber: entry.phoneNumber,
        label: entry.label
      )
    }

    context.completeRequest()
  }

  // MARK: - Entries

  private func identificationEntries() -> [IdentificationEntry] {
    return storedIdentificationEntries() ?? fallbackEntries
  }

  /// Reads the list the app shared, or `nil` when there is no list to read: the App
  /// Group is unreachable, or nothing has been written to it yet. An empty array is
  /// a list, so clearing the identities from the app really does clear the call screen.
  private func storedIdentificationEntries() -> [IdentificationEntry]? {
    guard
      let appGroup = sharedAppGroupIdentifier(),
      let defaults = UserDefaults(suiteName: appGroup),
      let storedItems = defaults.array(forKey: sharedStorageKey) as? [[String: String]]
    else {
      return nil
    }

    var seenPhoneNumbers = Set<CXCallDirectoryPhoneNumber>()
    var entries: [IdentificationEntry] = []

    for item in storedItems {
      guard
        let rawPhoneNumber = item[StorageField.phoneNumber],
        let label = item[StorageField.label],
        let phoneNumber = normalizedPhoneNumber(from: rawPhoneNumber),
        // Duplicates are dropped for the same reason the list is sorted below:
        // CallKit rejects a run whose numbers are not strictly ascending.
        seenPhoneNumbers.insert(phoneNumber).inserted
      else {
        continue
      }

      entries.append(IdentificationEntry(phoneNumber: phoneNumber, label: label))
    }

    // CallKit requires the entries of a single run in ascending order.
    return entries.sorted { $0.phoneNumber < $1.phoneNumber }
  }

  /// Turns a stored phone number into the `Int64` CallKit matches against.
  ///
  /// Every non-digit is stripped, so `+81 3-1234-5678` becomes `81312345678`.
  /// CallKit compares the full number including the country code, which means
  /// callers must store numbers in E.164 form. A domestic number such as
  /// `03-1234-5678` normalizes to `312345678` once the leading zero is lost, and
  /// will never match a real call.
  private func normalizedPhoneNumber(from rawPhoneNumber: String) -> CXCallDirectoryPhoneNumber? {
    let digits = rawPhoneNumber.filter { $0.isASCII && $0.isNumber }

    // The failable initializer also rejects numbers too large for `Int64`.
    guard let phoneNumber = CXCallDirectoryPhoneNumber(digits), phoneNumber > 0 else {
      return nil
    }
    return phoneNumber
  }

  // MARK: - App Group

  /// The App Group shared with the host app, by convention `group.<host bundle id>`.
  ///
  /// This extension's own bundle identifier is `<host bundle id>.CallDirectory`, so
  /// dropping the last dot-separated component yields the host bundle identifier.
  /// That mirrors the default the config plugin computes.
  ///
  /// Careful: a project that overrides `ios.appGroup` in the plugin props breaks this
  /// derivation. The extension then reads an empty suite and quietly falls back to
  /// `fallbackEntries`.
  private func sharedAppGroupIdentifier() -> String? {
    guard let bundleIdentifier = Bundle.main.bundleIdentifier else {
      return nil
    }

    let hostBundleIdentifier = bundleIdentifier.split(separator: ".").dropLast().joined(separator: ".")
    guard !hostBundleIdentifier.isEmpty else {
      return nil
    }
    return "group.\(hostBundleIdentifier)"
  }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
  func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
    // iOS discards the whole run when this fires. The usual causes are entries that
    // are not strictly ascending and an extension that exceeded its memory budget.
    NSLog("[ExpoCallScreening] Call Directory request failed: %@", error.localizedDescription)
  }
}
