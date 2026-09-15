package expo.modules.callscreening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Looks up incoming numbers in [CallerIdentityStore] and surfaces the stored label.
 *
 * This proof of concept never blocks or silences anything: every call is answered with a default
 * [CallResponse], which allows it through untouched. The only visible effect is the band
 * [CallerIdOverlay] draws over the incoming call screen with the matched label.
 *
 * The system binds this service through the `android.telecom.CallScreeningService` intent filter that
 * the `withAndroidCallScreening` config plugin writes into the app manifest. It is only ever called
 * while the app holds the `ROLE_CALL_SCREENING` role.
 */
class ExpoCallScreeningService : CallScreeningService() {
  private val identityStore by lazy { CallerIdentityStore(applicationContext) }

  /**
   * Called on the main thread once per screened call.
   *
   * Every path out of this method must respond exactly once, otherwise the telecom stack holds the
   * call until its screening timeout expires.
   */
  override fun onScreenCall(callDetails: Call.Details) {
    if (!isIncoming(callDetails)) {
      allow(callDetails)
      return
    }

    // `handle` is a `tel:` URI, so the scheme-specific part is the raw number the carrier sent.
    val phoneNumber = callDetails.handle?.schemeSpecificPart
    if (phoneNumber.isNullOrEmpty()) {
      Log.i(TAG, "Screened an incoming call with no readable number.")
      allow(callDetails)
      return
    }

    // The number itself is deliberately kept out of the log; only the lookup outcome is recorded.
    val label = identityStore.findLabel(phoneNumber)
    if (label == null) {
      Log.i(TAG, "No caller identity stored for the incoming number.")
    } else {
      Log.i(TAG, "Matched the incoming number to the label \"$label\".")
      // The application context outlives this service, which the telecom stack unbinds as soon as
      // the call is answered or rejected, while the band stays up for a while longer.
      CallerIdOverlay.show(applicationContext, label, phoneNumber)
    }

    allow(callDetails)
  }

  /** Lets the call proceed exactly as if no screening service were installed. */
  private fun allow(callDetails: Call.Details) {
    respondToCall(callDetails, CallResponse.Builder().build())
  }

  /**
   * Reports whether this is an incoming call.
   *
   * `Call.Details.getCallDirection` only exists from Android 10 (API 29), while
   * [CallScreeningService] itself goes back to API 24. Before API 29 the platform only screened
   * incoming calls, so the missing getter is safe to treat as "incoming".
   */
  private fun isIncoming(callDetails: Call.Details): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return callDetails.callDirection == Call.Details.DIRECTION_INCOMING
    }
    return true
  }

  private companion object {
    const val TAG = "ExpoCallScreening"
  }
}
