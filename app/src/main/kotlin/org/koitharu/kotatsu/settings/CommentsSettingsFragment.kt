
package org.koitharu.kotatsu.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import org.koitharu.kotatsu.R

class CommentsSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_comments, rootKey)
    }
}
