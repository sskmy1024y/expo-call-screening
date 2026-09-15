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

// Activity request codes must fit in 16 bits.
private const val ROLE_REQUEST_CODE = 0xCA11

private const val STATUS_ENABLED = "enabled"

private const val STATUS_DISABLED = "disabled"

private const val STATUS_UNKNOWN = "unknown"

class CallerIdentity : Record {
  @Field var phoneNumber: String = ""

  @Field var label: String = ""
}

internal class UnsupportedApiLevelException :
  CodedException("Call screening requires Android 10 (API 29) or newer")

internal class RoleUnavailableException :
  CodedException("The call screening role is not available on this device")

internal class RequestAlreadyPendingException :
  CodedException("A call screening role request is already in progress")

internal class RoleRequestFailedException(cause: Throwable) :
  CodedException("Could not start the call screening role request", cause)

internal class OverlayRequestFailedException(cause: Throwable) :
  CodedException("Could not open the display over other apps settings screen", cause)

class ExpoCallScreeningModule : Module() {
  // Accessed only on the main thread, including activity results.
  private var pendingRolePromise: Promise? = null

  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("ExpoCallScreening")

    AsyncFunction("setCallerIdentities") { entries: List<CallerIdentity> ->
      CallerIdentityStore(context).save(entries)
    }

    AsyncFunction("reload") {
      // No reload needed: the service reads the store on each call.
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
        // Callers recheck getStatus(); accepting the dialog does not guarantee the role is held.
        pendingRolePromise?.resolve(null)
        pendingRolePromise = null
      }
    }

    OnDestroy {
      pendingRolePromise?.reject(Exceptions.AppContextLost())
      pendingRolePromise = null
    }
  }

  private fun callScreeningStatus(): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return STATUS_UNKNOWN
    }
    // Report lost context as unknown rather than rejecting.
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

  // READ_PHONE_STATE only controls early overlay dismissal; denial must not block the role request.
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

  // Recheck pending state: another request may have arrived during the phone permission dialog.
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

  private fun hasOverlayPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return true
    }
    return Settings.canDrawOverlays(context)
  }

  // Settings does not report a result; callers recheck permission on foreground return.
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
