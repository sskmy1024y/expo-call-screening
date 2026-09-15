export type ExpoCallScreeningPluginProps = {
  ios?: {
    /** App Group shared by the host app and the Call Directory Extension. Defaults to `group.<ios.bundleIdentifier>`. */
    appGroup?: string;
    /** Bundle identifier of the Call Directory Extension. Defaults to `<ios.bundleIdentifier>.CallDirectory`. */
    extensionBundleIdentifier?: string;
  };
  android?: {
    /**
     * Path, relative to the project root, of a layout XML that replaces the default overlay.
     * It must contain TextViews with ids `expo_call_screening_label` and `expo_call_screening_phone_number`.
     */
    overlayLayout?: string;
  };
};
