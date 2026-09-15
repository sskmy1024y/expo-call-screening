package expo.modules.callscreening

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log

// Main-thread only. Telecom unbinds the screening service before the call ends.
internal class CallStateWatcher {
  // A closure keeps the API 31 TelephonyCallback type out of fields on older devices.
  private var unregister: (() -> Unit)? = null

  private var sawActiveCall = false

  fun start(context: Context, onIdle: () -> Unit): Boolean {
    stop()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      Log.w(TAG, "Not watching the call state: it needs Android 10 (API 29) or newer.")
      return false
    }
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      Log.w(TAG, "Not watching the call state: the READ_PHONE_STATE permission is missing.")
      return false
    }
    val manager = context.getSystemService(TelephonyManager::class.java)
    if (manager == null) {
      Log.w(TAG, "Not watching the call state: this device has no telephony manager.")
      return false
    }

    unregister = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        watchWithTelephonyCallback(context, manager, onIdle)
      } else {
        watchWithPhoneStateListener(manager, onIdle)
      }
    } catch (exception: RuntimeException) {
      // OEM telephony stacks may throw RuntimeException; fall back to timed dismissal.
      Log.w(TAG, "The system refused the call state registration.", exception)
      return false
    }
    return true
  }

  fun stop() {
    val stopWatching = unregister ?: return
    unregister = null
    sawActiveCall = false
    try {
      stopWatching()
    } catch (exception: RuntimeException) {
      Log.w(TAG, "The system refused to undo the call state registration.", exception)
    }
  }

  private fun watchWithTelephonyCallback(
    context: Context,
    manager: TelephonyManager,
    onIdle: () -> Unit
  ): () -> Unit {
    val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
      override fun onCallStateChanged(state: Int) = handleState(state, onIdle)
    }
    manager.registerTelephonyCallback(context.mainExecutor, callback)
    return { manager.unregisterTelephonyCallback(callback) }
  }

  @Suppress("DEPRECATION")
  private fun watchWithPhoneStateListener(
    manager: TelephonyManager,
    onIdle: () -> Unit
  ): () -> Unit {
    val listener = object : PhoneStateListener() {
      override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleState(state, onIdle)
    }
    manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    return { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
  }

  // Registration may initially report IDLE before ringing; ignore it until a call is observed.
  private fun handleState(state: Int, onIdle: () -> Unit) {
    if (state != TelephonyManager.CALL_STATE_IDLE) {
      sawActiveCall = true
      return
    }
    if (sawActiveCall) {
      onIdle()
    }
  }

  private companion object {
    const val TAG = "ExpoCallScreening"
  }
}
