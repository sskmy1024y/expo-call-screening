package expo.modules.callscreening

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

// Timeout when call-state monitoring is unavailable.
private const val DISMISS_AFTER_MS = 30_000L

// Safety timeout if the call-state watcher never reports IDLE.
private const val DISMISS_FALLBACK_MS = 120_000L

// Where the band the user dragged is remembered, so the next call reuses that spot.
private const val PREFERENCES_NAME = "expo_call_screening_overlay"

private const val KEY_TOP_OFFSET = "topOffset"

// Main-thread only; a new band replaces the current one.
internal object CallerIdOverlay {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val dismissRunnable = Runnable { dismiss() }
  private val callStateWatcher = CallStateWatcher()

  private var windowManager: WindowManager? = null

  private var overlayView: View? = null

  // Kept so the drag handler can move the window that is already added.
  private var overlayParams: WindowManager.LayoutParams? = null

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
    // A replacement layout may leave the close button out; the timers still dismiss the band.
    view.findViewById<View>(R.id.expo_call_screening_close)?.setOnClickListener { dismiss() }

    // Cancel the previous timer before installing the new band.
    dismiss()

    val params = layoutParams(context, manager)
    try {
      manager.addView(view, params)
    } catch (exception: WindowManager.BadTokenException) {
      // The user can revoke the overlay permission between the check above and this call.
      Log.w(TAG, "The system refused the caller ID overlay window.", exception)
      return
    }
    windowManager = manager
    overlayView = view
    overlayParams = params
    installDragHandler(context, view, manager, params)
    // layoutParams clamped the saved offset without knowing how tall the band would be, which a
    // rotation since the last drag can leave hanging off the bottom. Correct it once it is laid out.
    view.post {
      if (overlayView !== view) {
        return@post
      }
      val corrected = clampTopOffset(context, manager, params.y, view.height)
      if (corrected != params.y) {
        params.y = corrected
        try {
          manager.updateViewLayout(view, params)
        } catch (exception: IllegalArgumentException) {
          Log.w(TAG, "The caller ID overlay could not be moved back on screen.", exception)
        }
      }
    }
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
    overlayParams = null
    try {
      manager?.removeView(view)
    } catch (exception: IllegalArgumentException) {
      Log.w(TAG, "The caller ID overlay was already detached from the window manager.", exception)
    }
  }

  // Vertical only, like the dialers this band sits on top of: horizontal drags would fight the
  // side margins, and the band is full width anyway.
  private fun installDragHandler(
    context: Context,
    view: View,
    manager: WindowManager,
    params: WindowManager.LayoutParams
  ) {
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var startOffset = 0
    var startRawY = 0f
    var isDragging = false

    view.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          startOffset = params.y
          startRawY = event.rawY
          isDragging = false
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val travelled = event.rawY - startRawY
          if (isDragging || abs(travelled) > touchSlop) {
            isDragging = true
            params.y = clampTopOffset(context, manager, startOffset + travelled.toInt(), view.height)
            try {
              manager.updateViewLayout(view, params)
            } catch (exception: IllegalArgumentException) {
              // The band was removed mid-drag; nothing left to move.
              Log.w(TAG, "The caller ID overlay could not be moved.", exception)
            }
          }
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          if (isDragging) {
            preferences(context).edit().putInt(KEY_TOP_OFFSET, params.y).apply()
          }
          true
        }

        else -> false
      }
    }
  }

  private fun layoutParams(context: Context, manager: WindowManager): WindowManager.LayoutParams {
    val resources = context.resources
    val sideMargin = resources.getDimensionPixelSize(R.dimen.expo_call_screening_overlay_side_margin)
    val params = WindowManager.LayoutParams(
      (displayWidth(context, manager) - 2 * sideMargin).coerceAtLeast(1),
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      // Keep dialer key events and touches outside the band working.
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    )
    params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    // Overridable by the app so the band clears its dialer's incoming-call notification, and
    // superseded by wherever the user last dragged the band.
    val preferences = preferences(context)
    val offset = if (preferences.contains(KEY_TOP_OFFSET)) {
      preferences.getInt(KEY_TOP_OFFSET, 0)
    } else {
      resources.getDimensionPixelOffset(R.dimen.expo_call_screening_overlay_top_offset)
    }
    // Height is unknown until the band is laid out, so this only rules out a negative or
    // wildly out-of-range offset; show() tightens it afterwards.
    params.y = clampTopOffset(context, manager, offset, height = 0)
    return params
  }

  // `height` is 0 before the band has been laid out, which only relaxes the lower bound.
  private fun clampTopOffset(
    context: Context,
    manager: WindowManager,
    offset: Int,
    height: Int
  ): Int {
    val limit = (displayHeight(context, manager) - height).coerceAtLeast(0)
    return offset.coerceIn(0, limit)
  }

  private fun displayWidth(context: Context, manager: WindowManager): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      manager.currentWindowMetrics.bounds.width()
    } else {
      context.resources.displayMetrics.widthPixels
    }

  private fun displayHeight(context: Context, manager: WindowManager): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      manager.currentWindowMetrics.bounds.height()
    } else {
      context.resources.displayMetrics.heightPixels
    }

  private fun preferences(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  private const val TAG = "ExpoCallScreening"
}
