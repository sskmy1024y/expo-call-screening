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

// Timeout when call-state monitoring is unavailable.
private const val DISMISS_AFTER_MS = 30_000L

// Safety timeout if the call-state watcher never reports IDLE.
private const val DISMISS_FALLBACK_MS = 120_000L

// Place the band below the caller number in typical dialers.
private const val TOP_OFFSET_DP = 120

// Main-thread only; a new band replaces the current one.
internal object CallerIdOverlay {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val dismissRunnable = Runnable { dismiss() }
  private val callStateWatcher = CallStateWatcher()

  private var windowManager: WindowManager? = null

  private var overlayView: View? = null

  // Use an application context: the overlay outlives the screening service.
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

    val view = LayoutInflater.from(context).inflate(R.layout.expo_call_screening_overlay, null)
    val labelView = view.findViewById<TextView>(R.id.expo_call_screening_label)
    val phoneNumberView = view.findViewById<TextView>(R.id.expo_call_screening_phone_number)
    if (labelView == null || phoneNumberView == null) {
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

    // Cancel the previous timer before installing the new band.
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
    // Start watching only after addView succeeds to avoid leaking a listener.
    val isWatchingCallState = callStateWatcher.start(context) { dismiss() }
    mainHandler.postDelayed(
      dismissRunnable,
      if (isWatchingCallState) DISMISS_FALLBACK_MS else DISMISS_AFTER_MS
    )
  }

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

  private fun layoutParams(context: Context): WindowManager.LayoutParams {
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      // Keep dialer key events and touches outside the band working.
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
