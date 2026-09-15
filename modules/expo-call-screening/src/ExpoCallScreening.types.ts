/**
 * A single phone number and the name to display for it on an incoming call.
 */
export type CallerIdentity = {
  /**
   * The phone number to match. E.164 is strongly recommended, for example
   * `+819012345678`, because iOS matches Call Directory entries on the
   * normalised number and rejects anything it cannot parse as a digit string.
   */
  phoneNumber: string;
  /**
   * The text shown on the incoming call screen, for example `田中 太郎` or
   * `Example Inc.`.
   */
  label: string;
};

/**
 * Whether the OS is currently using the identities registered by this module.
 *
 * - `enabled`: iOS reports the Call Directory Extension as enabled, or Android
 *   reports that the app holds the call screening role.
 * - `disabled`: the extension or role exists but the user has not turned it on.
 * - `unknown`: the platform could not be queried, for example on an iOS
 *   simulator or an Android version without a call screening role.
 */
export type CallerIdStatus = 'enabled' | 'disabled' | 'unknown';
