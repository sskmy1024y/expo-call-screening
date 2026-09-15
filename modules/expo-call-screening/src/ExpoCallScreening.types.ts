/** A phone number and its incoming-call display name. */
export type CallerIdentity = {
  /** E.164 number including country code, e.g. `+819012345678`. */
  phoneNumber: string;
  /** Name shown on incoming calls, e.g. `田中 太郎 / Example Inc.`. */
  label: string;
};

/** Extension/role enabled, disabled, or unavailable to query. */
export type CallerIdStatus = 'enabled' | 'disabled' | 'unknown';
