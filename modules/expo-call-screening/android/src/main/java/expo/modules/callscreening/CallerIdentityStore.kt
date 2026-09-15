package expo.modules.callscreening

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads and writes the caller identity list that [ExpoCallScreeningModule] fills in from JavaScript and
 * that [ExpoCallScreeningService] looks up on every incoming call.
 *
 * The screening service is bound by the telecom stack and can run while the React Native runtime is
 * not alive, so this class takes a plain [Context] rather than an Expo app context, and the lookup
 * path used by the service touches nothing beyond the Android SDK. `org.json` ships with the
 * platform, which keeps the module free of extra Gradle dependencies.
 *
 * Entries live under a single SharedPreferences key as a JSON array string, so the stored shape
 * mirrors the JavaScript one exactly:
 * `[{"phoneNumber": "+81 90-1234-5678", "label": "Acme Support"}]`.
 */
internal class CallerIdentityStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  /**
   * Replaces every stored identity with [identities].
   *
   * Writes synchronously so that the promise returned to JavaScript only settles once the list is
   * on disk. Callers already run off the main thread, because the Expo DSL dispatches
   * `AsyncFunction` bodies to a background queue.
   */
  fun save(identities: List<CallerIdentity>) {
    val entries = JSONArray()
    identities.forEach { identity ->
      entries.put(
        JSONObject()
          .put(KEY_PHONE_NUMBER, identity.phoneNumber)
          .put(KEY_LABEL, identity.label)
      )
    }
    preferences.edit().putString(KEY_IDENTITIES, entries.toString()).commit()
  }

  /**
   * Returns the label stored for [phoneNumber], or `null` when no entry matches or the matching
   * entry has an empty label.
   *
   * Both sides are normalized by dropping every non-digit character, so `"+81 90-1234-5678"` matches
   * `"819012345678"`. The comparison is deliberately naive and knows nothing about country codes, so
   * `"09012345678"` and `"+819012345678"` are different numbers to this lookup.
   */
  fun findLabel(phoneNumber: String): String? {
    val wanted = normalize(phoneNumber)
    if (wanted.isEmpty()) {
      return null
    }
    val entries = readEntries()
    for (index in 0 until entries.length()) {
      val entry = entries.optJSONObject(index) ?: continue
      if (normalize(entry.optString(KEY_PHONE_NUMBER)) == wanted) {
        return entry.optString(KEY_LABEL).takeIf { it.isNotEmpty() }
      }
    }
    return null
  }

  /** Returns the persisted entries, or an empty array when nothing was stored or the value is corrupt. */
  private fun readEntries(): JSONArray {
    val stored = preferences.getString(KEY_IDENTITIES, null) ?: return JSONArray()
    return try {
      JSONArray(stored)
    } catch (exception: JSONException) {
      Log.w(TAG, "Discarding the stored caller identities because they could not be parsed.", exception)
      JSONArray()
    }
  }

  private companion object {
    const val TAG = "ExpoCallScreening"
    const val PREFERENCES_NAME = "expo_call_screening"
    const val KEY_IDENTITIES = "callerIdentities"
    const val KEY_PHONE_NUMBER = "phoneNumber"
    const val KEY_LABEL = "label"

    /** Strips everything that is not a digit so formatted and raw numbers compare equal. */
    fun normalize(phoneNumber: String) = phoneNumber.filter(Char::isDigit)
  }
}
