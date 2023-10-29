package ilchev.stefan.callblocker

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.ContactsContract
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BlockPredicate(sharedPreferences: SharedPreferences) : (Context, String) -> Boolean {

	var isBlockNonContacts = sharedPreferences.getBoolean(KEY_BLOCK_NON_CONTACTS, false)

	var isExcludeContacts = sharedPreferences.getBoolean(KEY_EXCLUDE_CONTACTS, false)

	var regex = sharedPreferences.getString(KEY_REGEX, null) ?: ""
		set(value) {
			value.matches(value.toRegex())
			field = value
		}

	var isMatches = sharedPreferences.getString(KEY_MATCHES, null)?.toBooleanStrictOrNull()

	fun put(
		editor: SharedPreferences.Editor
	): SharedPreferences.Editor = editor.putBoolean(KEY_BLOCK_NON_CONTACTS, isBlockNonContacts)
		.putBoolean(KEY_EXCLUDE_CONTACTS, isExcludeContacts)
		.putString(KEY_REGEX, regex)
		.putString(KEY_MATCHES, isMatches?.toString())

	override fun invoke(context: Context, phoneNumber: String): Boolean {
		val isContact by lazy { context.isContact(phoneNumber) }
		if (isBlockNonContacts && isContact == false) return true
		if (isExcludeContacts && isContact == true) return false
		val isMatches = isMatches ?: return false
		return isMatches == phoneNumber.matches(regex.toRegex())
	}

	companion object {

		private const val KEY_BLOCK_NON_CONTACTS = "block_non_contacts"

		private const val KEY_EXCLUDE_CONTACTS = "exclude_contacts"

		private const val KEY_REGEX = "regex"

		private const val KEY_MATCHES = "matches"

		private fun Context.isContact(
			phoneNumber: String
		) = try {
			val task = Callable {
				contentResolver?.query(
					Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)),
					arrayOf(ContactsContract.PhoneLookup._ID),
					null,
					null,
					null
				)?.use { it.moveToFirst() }
			}
			val factory = Executors.defaultThreadFactory()
			val executor = Executors.newSingleThreadExecutor {
				factory.newThread(it).apply {
					if (!isDaemon) {
						isDaemon = true
					}
				}
			}
			val future = try {
				executor.submit(task)
			} finally {
				executor.shutdown()
			}
			try {
				future.get(3_000L, TimeUnit.MILLISECONDS)
			} catch (ignored: TimeoutException) {
				future.cancel(true)
				null
			}
		} catch (ignored: Throwable) {
			null
		}
	}
}
