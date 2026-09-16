package expo.modules.callscreening

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// The screening service also reads this store without a running React Native runtime.
internal class CallerIdentityStore(context: Context) {
  private val applicationContext = context.applicationContext

  private val preferences =
    applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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

  // Carriers hand the screening service whichever of `09012345678` and `+819012345678` they
  // please, and it need not be the form the entry was saved in, so entries are matched the way
  // the platform matches caller IDs rather than by comparing the strings.
  fun findLabel(phoneNumber: String): String? {
    if (phoneNumber.none(Char::isDigit)) {
      return null
    }
    val entries = readEntries()
    for (index in 0 until entries.length()) {
      val entry = entries.optJSONObject(index) ?: continue
      val stored = entry.optString(KEY_PHONE_NUMBER)
      if (stored.isNotEmpty() && isSameNumber(stored, phoneNumber)) {
        return entry.optString(KEY_LABEL).takeIf { it.isNotEmpty() }
      }
    }
    return null
  }

  private fun isSameNumber(stored: String, incoming: String): Boolean {
    val countryIso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) countryIso() else null
    if (countryIso != null) {
      return PhoneNumberUtils.areSamePhoneNumber(stored, incoming, countryIso)
    }
    // Falls back to a trailing-digit comparison, which reconciles the two forms just as well but
    // also lets short numbers such as internal extensions collide.
    @Suppress("DEPRECATION")
    return PhoneNumberUtils.compare(stored, incoming)
  }

  // Needed to read a number that omits its country code. The SIM's country is the one the entry
  // was most likely saved in; the registered network is the better guess while roaming is off.
  private fun countryIso(): String? {
    val manager = applicationContext.getSystemService(TelephonyManager::class.java) ?: return null
    return manager.simCountryIso?.takeIf { it.isNotEmpty() }
      ?: manager.networkCountryIso?.takeIf { it.isNotEmpty() }
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
  }
}
