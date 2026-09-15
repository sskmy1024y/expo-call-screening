package expo.modules.callscreening

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// The screening service also reads this store without a running React Native runtime.
internal class CallerIdentityStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  // Commit synchronously on the Expo background queue before resolving the JS promise.
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

  // Digit stripping does not reconcile domestic numbers with country-code-prefixed numbers.
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

    fun normalize(phoneNumber: String) = phoneNumber.filter(Char::isDigit)
  }
}
