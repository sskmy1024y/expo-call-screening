package expo.modules.callscreening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class ExpoCallScreeningService : CallScreeningService() {
  private val identityStore by lazy { CallerIdentityStore(applicationContext) }

  // Runs on the main thread. Every path must respond to avoid the screening timeout.
  override fun onScreenCall(callDetails: Call.Details) {
    try {
      if (!isIncoming(callDetails)) {
        return
      }

      // `handle` is a `tel:` URI, so the scheme-specific part is the raw number the carrier sent.
      val phoneNumber = callDetails.handle?.schemeSpecificPart
      if (phoneNumber.isNullOrEmpty()) {
        Log.i(TAG, "Screened an incoming call with no readable number.")
        return
      }

      val label = identityStore.findLabel(phoneNumber)
      if (label == null) {
        Log.i(TAG, "No caller identity stored for the incoming number.")
      } else {
        Log.i(TAG, "Matched a caller identity for the incoming number.")
        // The application context outlives this service and its screening response.
        CallerIdOverlay.show(applicationContext, label, phoneNumber)
      }
    } catch (exception: RuntimeException) {
      // Lookup or a custom overlay layout must never prevent the call from proceeding.
      Log.w(TAG, "Unable to display the caller identity.", exception)
    } finally {
      allow(callDetails)
    }
  }

  private fun allow(callDetails: Call.Details) {
    respondToCall(callDetails, CallResponse.Builder().build())
  }

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
