package expo.modules.callscreening

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record

/**
 * Request code for the role request started by [ExpoCallScreeningModule.requestCallScreeningRole].
 *
 * Androidx activities reject request codes that do not fit in 16 bits, so this stays below 0x10000.
 */
private const val ROLE_REQUEST_CODE = 0xCA11

/** Status reported when the app currently holds the call screening role. */
private const val STATUS_ENABLED = "enabled"

/** Status reported when the role exists on this device but another app (or nobody) holds it. */
private const val STATUS_DISABLED = "disabled"

/** Status reported when the role API is not reachable at all, so nothing can be said about it. */
private const val STATUS_UNKNOWN = "unknown"

/** One caller identity: the phone number to match, and the label to show when it calls. */
class CallerIdentity : Record {
  @Field var phoneNumber: String = ""

  @Field var label: String = ""
}

/** Thrown when the device predates the role API that call screening is gated behind. */
internal class UnsupportedApiLevelException :
  CodedException("Call screening requires Android 10 (API 29) or newer")

/** Thrown when the device reports no call screening role, for example on a tablet without telephony. */
internal class RoleUnavailableException :
  CodedException("The call screening role is not available on this device")

/** Thrown when a second role request arrives while the system dialog from the first one is still up. */
internal class RequestAlreadyPendingException :
  CodedException("A call screening role request is already in progress")

/** Thrown when the system refuses to open the role request dialog. */
internal class RoleRequestFailedException(cause: Throwable) :
  CodedException("Could not start the call screening role request", cause)

/** Thrown when the system has no screen for granting the display over other apps permission. */
internal class OverlayRequestFailedException(cause: Throwable) :
  CodedException("Could not open the display over other apps settings screen", cause)

/**
 * JavaScript-facing surface of the caller ID module.
 *
 * Everything that reads the call screening role goes through [RoleManager], which only exists from
 * Android 10 (API 29). The module keeps a lower `minSdk` and guards each use at runtime instead, so
 * older devices get `"unknown"` from `getStatus` and a clear rejection from `requestPermission`
 * rather than a crash.
 */
class ExpoCallScreeningModule : Module() {
  /**
   * Promise of the in-flight `requestPermission` call, settled from the `OnActivityResult` handler.
   * Only ever touched on the main thread: the request runs on [Queues.MAIN] and activity results are
   * delivered there too.
   */
  private var pendingRolePromise: Promise? = null

  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("ExpoCallScreening")

    AsyncFunction("setCallerIdentities") { entries: List<CallerIdentity> ->
      CallerIdentityStore(context).save(entries)
    }

    AsyncFunction("reload") {
      // Intentionally a no-op. ExpoCallScreeningService reads SharedPreferences on every incoming
      // call, so there is nothing cached to invalidate. The function exists only to mirror iOS,
      // where CallKit caches the directory and needs an explicit extension reload after a write.
    }

    AsyncFunction("getStatus") {
      callScreeningStatus()
    }

    AsyncFunction("requestPermission") { promise: Promise ->
      requestCallScreeningPermissions(promise)
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("hasOverlayPermission") {
      hasOverlayPermission()
    }

    AsyncFunction("requestOverlayPermission") { promise: Promise ->
      requestOverlayPermission(promise)
    }.runOnQueue(Queues.MAIN)

    OnActivityResult { _, payload ->
      if (payload.requestCode == ROLE_REQUEST_CODE) {
        // The result code only says whether the user confirmed the dialog, not whether the role
        // stuck, so the promise resolves either way and callers re-read getStatus().
        pendingRolePromise?.resolve(null)
        pendingRolePromise = null
      }
    }

    OnDestroy {
      // The JavaScript caller waiting on this promise is gone along with the runtime, so settle it
      // here instead of leaving it pending forever.
      pendingRolePromise?.reject(Exceptions.AppContextLost())
      pendingRolePromise = null
    }
  }

  /**
   * Returns [STATUS_ENABLED], [STATUS_DISABLED] or [STATUS_UNKNOWN] for the call screening role.
   *
   * `unknown` covers "this Android version has no role API", "this device has no such role" and
   * "the runtime context is gone". In each case the app cannot become a call screener, or cannot
   * find out whether it already is, and there is nothing to offer the user.
   */
  private fun callScreeningStatus(): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return STATUS_UNKNOWN
    }
    // Deliberately not the throwing `context` accessor: the third status exists so that "cannot
    // tell" reaches JavaScript as a value rather than as a rejected promise.
    val reactContext = appContext.reactContext ?: return STATUS_UNKNOWN
    val roleManager = reactContext.getSystemService(RoleManager::class.java) ?: return STATUS_UNKNOWN
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
      return STATUS_UNKNOWN
    }
    return if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
      STATUS_ENABLED
    } else {
      STATUS_DISABLED
    }
  }

  /**
   * Asks for the `READ_PHONE_STATE` runtime permission, then opens the role dialog either way.
   *
   * That permission is used for one thing only: watching the call state so that the caller ID band
   * closes the moment the call ends. Denying it costs the band nothing but its early dismissal,
   * which falls back to a timeout, so the answer is not even read here. The role is what call
   * screening actually depends on, and it is requested whatever the user said.
   *
   * The version check and the single request rule are applied here rather than left to
   * [requestCallScreeningRole], so that a device that cannot screen calls at all, or a second call
   * arriving while the role dialog is up, is turned away before any permission dialog appears. An
   * app without a permissions manager goes straight to the role request.
   *
   * Runs on the main thread, and so does the answer: the permissions manager delivers its result
   * from the activity that asked, or inline when there is no activity to ask with.
   */
  private fun requestCallScreeningPermissions(promise: Promise) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      promise.reject(UnsupportedApiLevelException())
      return
    }
    if (pendingRolePromise != null) {
      promise.reject(RequestAlreadyPendingException())
      return
    }
    val permissions = appContext.permissions
    if (permissions == null) {
      requestCallScreeningRole(promise)
      return
    }
    permissions.askForPermissions(
      { requestCallScreeningRole(promise) },
      Manifest.permission.READ_PHONE_STATE
    )
  }

  /**
   * Opens the system dialog that asks the user to make this app the call screener.
   *
   * [promise] is parked in [pendingRolePromise] and resolved once the dialog closes, whichever button
   * the user pressed. A dismissed dialog is a normal outcome rather than an error, so the caller
   * learns what happened by calling `getStatus` again.
   *
   * The guards below repeat the ones in [requestCallScreeningPermissions] because the state can move
   * in between: two requests can both pass that check while the first is still waiting on the
   * `READ_PHONE_STATE` dialog, and only one of them can own the role request.
   */
  private fun requestCallScreeningRole(promise: Promise) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      promise.reject(UnsupportedApiLevelException())
      return
    }
    if (pendingRolePromise != null) {
      promise.reject(RequestAlreadyPendingException())
      return
    }
    val activity = appContext.currentActivity
    if (activity == null) {
      promise.reject(Exceptions.MissingActivity())
      return
    }
    val roleManager = activity.getSystemService(RoleManager::class.java)
    if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
      promise.reject(RoleUnavailableException())
      return
    }

    pendingRolePromise = promise
    try {
      val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
      activity.startActivityForResult(intent, ROLE_REQUEST_CODE)
    } catch (exception: Throwable) {
      pendingRolePromise = null
      promise.reject(RoleRequestFailedException(exception))
    }
  }

  /**
   * Reports whether the app may draw the caller ID band over other apps.
   *
   * Always `true` below Android 6 (API 23), where `SYSTEM_ALERT_WINDOW` is granted at install time
   * and there is nothing left to ask for.
   */
  private fun hasOverlayPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return true
    }
    return Settings.canDrawOverlays(context)
  }

  /**
   * Opens the system settings screen where the user turns on "display over other apps".
   *
   * Unlike the role dialog this is a full settings screen rather than a modal, and it reports
   * nothing back when the user leaves it, so [promise] resolves as soon as the screen is open.
   * Callers read the outcome by calling `hasOverlayPermission` again once they are back. Resolves
   * straight away when the permission is already granted.
   */
  private fun requestOverlayPermission(promise: Promise) {
    if (hasOverlayPermission()) {
      promise.resolve(null)
      return
    }
    val activity = appContext.currentActivity
    if (activity == null) {
      promise.reject(Exceptions.MissingActivity())
      return
    }

    try {
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${activity.packageName}")
      )
      activity.startActivity(intent)
    } catch (exception: Throwable) {
      promise.reject(OverlayRequestFailedException(exception))
      return
    }
    promise.resolve(null)
  }
}
