package expo.modules.callscreening

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Reports the moment the call in progress ends, so that [CallerIdOverlay] can take its band down
 * instead of waiting for a timeout.
 *
 * The telecom stack unbinds [ExpoCallScreeningService] as soon as it has responded, long before the
 * call is over, so the end of the call has to come from [TelephonyManager] instead. Two APIs do the
 * same job depending on the version: `registerTelephonyCallback` from Android 12 (API 31), and the
 * deprecated `listen` below it. Both need the `READ_PHONE_STATE` permission, and neither reports
 * anything about the caller that is used here: the legacy callback is handed a phone number, which
 * this class ignores.
 *
 * Both paths deliver the current state right after registration, and that first value can already be
 * `CALL_STATE_IDLE`, for example when the caller hangs up while the band is going up. Taking the band
 * down on it would remove a band nobody has seen yet, so [onIdle] only fires once the phone has been
 * seen ringing or off hook at least once.
 *
 * Every member is touched from the main thread only: [start] and [stop] are called from
 * [CallerIdOverlay], which is main thread only itself, and both registrations are told to deliver
 * there. Nothing here is synchronized.
 */
internal class CallStateWatcher {
  /**
   * Undoes the registration made by [start], or `null` while the watcher is stopped.
   *
   * Keeping the teardown as a closure keeps the `TelephonyCallback` of the Android 12 path out of
   * this class's fields. That type does not exist below API 31, and a field of that type would be
   * resolved on devices that never take the branch which creates it.
   */
  private var unregister: (() -> Unit)? = null

  /** Whether the phone has been ringing or off hook since [start]; see the class documentation. */
  private var sawActiveCall = false

  /**
   * Starts watching the call state and calls [onIdle] on the main thread once the call ends.
   *
   * Returns `false`, after a warning, when the state cannot be watched at all: below Android 10
   * (API 29), without the `READ_PHONE_STATE` permission, or on a device with no telephony. Callers
   * are expected to fall back to a timeout rather than treat that as an error.
   *
   * Calling this twice replaces the previous registration, so [start] and [stop] can be called in
   * any order and as often as the caller likes.
   */
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
      // Deliberately wider than the SecurityException a revoked permission raises: this runs inside
      // ExpoCallScreeningService.onScreenCall, which must still respond to the call, and an OEM
      // telephony stack that throws anything at all here would otherwise hold the call until the
      // screening timeout. Losing the watcher only costs the band its early dismissal.
      Log.w(TAG, "The system refused the call state registration.", exception)
      return false
    }
    return true
  }

  /**
   * Stops watching and forgets that a call was seen.
   *
   * Safe to call when nothing is registered and safe to call twice, which matters because the
   * overlay stops the watcher from both its dismiss paths.
   */
  fun stop() {
    val stopWatching = unregister ?: return
    unregister = null
    sawActiveCall = false
    try {
      stopWatching()
    } catch (exception: RuntimeException) {
      // Same reasoning as the registration above: this is reached from the screening service, where
      // a throw would hold the call. The registration is dropped either way.
      Log.w(TAG, "The system refused to undo the call state registration.", exception)
    }
  }

  /** Registers the Android 12 (API 31) callback and returns the call that unregisters it. */
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

  /**
   * Registers the pre Android 12 listener and returns the call that unregisters it.
   *
   * `PhoneStateListener` and `listen` are deprecated as of API 31 and replaced by the callback
   * above, but they are the only option on API 29 and 30, so the deprecation is suppressed for this
   * function rather than worked around. The listener delivers on the looper of the thread that
   * creates it, which is the main thread here.
   */
  @Suppress("DEPRECATION")
  private fun watchWithPhoneStateListener(
    manager: TelephonyManager,
    onIdle: () -> Unit
  ): () -> Unit {
    val listener = object : PhoneStateListener() {
      // The second parameter is the caller's number. It is deliberately left unread: the watcher
      // only cares that the call ended, and the number never leaves the telephony stack.
      override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleState(state, onIdle)
    }
    manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    return { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
  }

  /** Calls [onIdle] on the first idle state that follows a ringing or off hook one. */
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
