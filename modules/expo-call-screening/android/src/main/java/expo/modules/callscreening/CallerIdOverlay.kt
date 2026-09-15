package expo.modules.callscreening

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * How long the band stays on screen before it takes itself down, in milliseconds.
 *
 * Only used when [CallStateWatcher] could not start, which makes the timeout the one and only way
 * the band ever comes down on its own.
 */
private const val DISMISS_AFTER_MS = 30_000L

/**
 * How long the band stays on screen when the call state is watched, in milliseconds.
 *
 * The watcher takes the band down as soon as the call ends, so this is a safety net for the case
 * where the idle state never arrives, and it is long enough not to cut a real conversation short.
 */
private const val DISMISS_FALLBACK_MS = 120_000L

/**
 * Distance between the top of the screen and the top of the band, in density independent pixels.
 *
 * Stock dialers put the caller number in the upper third of the incoming call screen, so the band
 * is pushed down far enough to sit under it rather than on top of it.
 */
private const val TOP_OFFSET_DP = 120

/**
 * Draws the caller ID band over whatever is on screen, the way a dedicated caller ID app does.
 *
 * The band is a plain view added straight to the [WindowManager] as a `TYPE_APPLICATION_OVERLAY`
 * window, so it survives on top of the incoming call screen, which belongs to the dialer and cannot
 * be drawn into. Doing this needs the `SYSTEM_ALERT_WINDOW` permission, which the user grants by
 * hand in system settings; `ExpoCallScreeningModule.requestOverlayPermission` sends them there.
 *
 * Only one band exists at a time: [show] replaces the previous one instead of stacking. The band
 * comes down when the user taps it, when the call ends, or when the timer below runs out, whichever
 * happens first. The end of the call comes from [CallStateWatcher], which needs the
 * `READ_PHONE_STATE` permission; without it the band is left to the timer alone.
 *
 * Android 8 (API 26) is the floor, because `TYPE_APPLICATION_OVERLAY` was introduced there. That
 * costs nothing in practice: the screening service that calls this is only ever bound while the app
 * holds `ROLE_CALL_SCREENING`, and that role is Android 10 (API 29) and newer. Older devices log a
 * warning and show nothing rather than falling back to the deprecated `TYPE_PHONE`.
 *
 * Every member is touched from the main thread only. `CallScreeningService.onScreenCall`, the tap
 * listener, the call state callback and the auto-dismiss callback all run there, so nothing here is
 * synchronized.
 */
internal object CallerIdOverlay {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val dismissRunnable = Runnable { dismiss() }
  private val callStateWatcher = CallStateWatcher()

  /** The window manager the current band was added to, kept so it can be removed from the same one. */
  private var windowManager: WindowManager? = null

  /** The band currently on screen, or `null` when nothing is showing. */
  private var overlayView: View? = null

  /**
   * Shows [label] and [phoneNumber] in a band over the current screen.
   *
   * Does nothing beyond a warning when the overlay permission is missing or the device predates
   * API 26, so callers do not have to check either. [context] should be an application context: the
   * band outlives the call that triggered it and must not hold on to a service or activity.
   *
   * The band comes down by itself once the call ends. When that cannot be watched, for example
   * because `READ_PHONE_STATE` was denied, it falls back to the shorter [DISMISS_AFTER_MS] timeout.
   */
  fun show(context: Context, label: String, phoneNumber: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      Log.w(TAG, "Not showing the caller ID overlay: it needs Android 8 (API 26) or newer.")
      return
    }
    if (!Settings.canDrawOverlays(context)) {
      Log.w(TAG, "Not showing the caller ID overlay: the display over other apps permission is off.")
      return
    }
    val manager = context.getSystemService(WindowManager::class.java)
    if (manager == null) {
      Log.w(TAG, "Not showing the caller ID overlay: this context has no window manager.")
      return
    }

    // The band has no parent to inherit layout params from, so the root is inflated detached and the
    // window manager params below take their place.
    val view = LayoutInflater.from(context).inflate(R.layout.expo_call_screening_overlay, null)
    val labelView = view.findViewById<TextView>(R.id.expo_call_screening_label)
    val phoneNumberView = view.findViewById<TextView>(R.id.expo_call_screening_phone_number)
    if (labelView == null || phoneNumberView == null) {
      // Reachable whenever an app overrides the layout, so it is a warning rather than a crash.
      Log.w(
        TAG,
        "Not showing the caller ID overlay: expo_call_screening_overlay is missing expo_call_screening_label " +
          "or expo_call_screening_phone_number."
      )
      return
    }
    labelView.text = label
    phoneNumberView.text = phoneNumber
    view.setOnClickListener { dismiss() }

    // Takes down the previous band first, so the timer started below is the only one pending and
    // cannot remove the band this call is about to add.
    dismiss()

    try {
      manager.addView(view, layoutParams(context))
    } catch (exception: WindowManager.BadTokenException) {
      // The user can revoke the overlay permission between the check above and this call.
      Log.w(TAG, "The system refused the caller ID overlay window.", exception)
      return
    }
    windowManager = manager
    overlayView = view
    // Started only once the band is really on screen, so a refused window never leaves a watcher
    // behind with nothing to dismiss.
    val isWatchingCallState = callStateWatcher.start(context) { dismiss() }
    mainHandler.postDelayed(
      dismissRunnable,
      if (isWatchingCallState) DISMISS_FALLBACK_MS else DISMISS_AFTER_MS
    )
  }

  /**
   * Takes the band down, stops watching the call state and cancels the pending auto-dismiss.
   *
   * Safe to call when nothing is showing and safe to call twice, which matters because the tap
   * listener, the end of the call and the timer race for the same view.
   */
  fun dismiss() {
    mainHandler.removeCallbacks(dismissRunnable)
    callStateWatcher.stop()
    val view = overlayView ?: return
    val manager = windowManager
    overlayView = null
    windowManager = null
    try {
      manager?.removeView(view)
    } catch (exception: IllegalArgumentException) {
      Log.w(TAG, "The caller ID overlay was already detached from the window manager.", exception)
    }
  }

  /** Full-width band pinned below the top of the screen, transparent outside its own bounds. */
  private fun layoutParams(context: Context): WindowManager.LayoutParams {
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      // Not focusable so the dialer keeps receiving key events, not touch modal so taps outside the
      // band still reach the answer and decline buttons, and laid out in screen coordinates so the
      // offset below is measured from the top of the display rather than from the status bar.
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    )
    params.gravity = Gravity.TOP
    params.y = (TOP_OFFSET_DP * context.resources.displayMetrics.density).toInt()
    return params
  }

  private const val TAG = "ExpoCallScreening"
}
