require 'json'

# Version, author and license are read from package.json so the npm package and
# the pod can never drift apart.
package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'ExpoCallScreening'
  s.version        = package['version']
  s.summary        = 'Caller ID for Expo apps, backed by a CallKit Call Directory Extension.'
  s.description    = 'Stores caller identities in a shared App Group and drives the CallKit ' \
                     'Call Directory Extension that displays them on incoming calls.'
  s.author         = package['author']
  s.license        = package['license']
  s.homepage       = package['homepage'] || 'https://www.npmjs.com/package/expo-call-screening'
  s.platforms      = {
    :ios => '15.1'
  }
  s.source         = { git: package.dig('repository', 'url') || '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'

  # CXCallDirectoryManager lives in CallKit. The extension target links CallKit
  # separately via `targets/call-directory/expo-target.config.js`.
  s.frameworks = 'CallKit'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  # Scoped to this `ios/` directory on purpose: the extension sources under
  # `targets/` must not be compiled into the app target.
  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
