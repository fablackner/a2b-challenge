package io.github.fablackner.a2b

import android.os.Bundle
import android.text.InputFilter
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.github.fablackner.a2b.data.UserPrefs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.settingsContainer, SettingsFragment())
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<EditTextPreference>(UserPrefs.KEY_NICKNAME)?.setOnBindEditTextListener { editText ->
                editText.filters = arrayOf(InputFilter.LengthFilter(NICKNAME_MAX_LENGTH))
            }
        }

        companion object {
            private const val NICKNAME_MAX_LENGTH = 12
        }
    }
}
