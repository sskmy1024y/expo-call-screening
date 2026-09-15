import CallKit
import Foundation

// Shared storage contract with ios/ExpoCallScreeningModule.swift.
private let sharedStorageKey = "callerIdentities"

private enum StorageField {
  static let phoneNumber = "phoneNumber"
  static let label = "label"
}

private struct IdentificationEntry {
  let phoneNumber: CXCallDirectoryPhoneNumber
  let label: String
}

class CallDirectoryHandler: CXCallDirectoryProvider {
  override func beginRequest(with context: CXCallDirectoryExtensionContext) {
    context.delegate = self

    // Publish the full list even for incremental requests.
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
    guard
      let appGroup = sharedAppGroupIdentifier(),
      let defaults = UserDefaults(suiteName: appGroup),
      let storedItems = defaults.array(forKey: sharedStorageKey) as? [[String: String]]
    else {
      return []
    }

    var seenPhoneNumbers = Set<CXCallDirectoryPhoneNumber>()
    var entries: [IdentificationEntry] = []

    for item in storedItems {
      guard
        let rawPhoneNumber = item[StorageField.phoneNumber],
        let label = item[StorageField.label],
        let phoneNumber = normalizedPhoneNumber(from: rawPhoneNumber),
        // CallKit rejects duplicate numbers.
        seenPhoneNumbers.insert(phoneNumber).inserted
      else {
        continue
      }

      entries.append(IdentificationEntry(phoneNumber: phoneNumber, label: label))
    }

    // CallKit requires the entries of a single run in ascending order.
    return entries.sorted { $0.phoneNumber < $1.phoneNumber }
  }

  // CallKit matches the full number including country code; callers must supply E.164 numbers.
  private func normalizedPhoneNumber(from rawPhoneNumber: String) -> CXCallDirectoryPhoneNumber? {
    let digits = rawPhoneNumber.filter { $0.isASCII && $0.isNumber }

    // The failable initializer also rejects numbers too large for `Int64`.
    guard let phoneNumber = CXCallDirectoryPhoneNumber(digits), phoneNumber > 0 else {
      return nil
    }
    return phoneNumber
  }

  // MARK: - App Group

  private func sharedAppGroupIdentifier() -> String? {
    guard let appGroup = Bundle.main.object(forInfoDictionaryKey: "ExpoCallScreeningAppGroup") as? String,
      !appGroup.isEmpty else {
      return nil
    }
    return appGroup
  }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
  func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
    NSLog("[ExpoCallScreening] Call Directory request failed: %@", error.localizedDescription)
  }
}
