package ch.ethz.ee.tec.blebeaconlogger;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    /*
     * App version string information
     */
    private static String appVersionInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get version information
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersionInfo = "v" + String.format("%d", packageInfo.versionCode) + " ("
                    + packageInfo.versionName + "), built on ";
            appVersionInfo += new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
                    .format(new java.util.Date(packageInfo.lastUpdateTime));
        } catch (PackageManager.NameNotFoundException e) {
            appVersionInfo = "not available";
            Log.e(TAG, "Package info not found.");
        }

        // load the settings fragment as main content
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsActivityFragment())
                .commit();
    }


    /**
     * Settings fragment class.
     */
    public static class SettingsActivityFragment extends PreferenceFragment implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource and set default if not
            // defined
            addPreferencesFromResource(R.xml.preferences);
            PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);

            // set version information
            ((EditTextPreference) findPreference("pref_about_version")).setText(appVersionInfo);
            ((EditTextPreference) findPreference("pref_about_git_revision")).setText(BuildConfig.GIT);
            Log.i(TAG, "REV: " + appVersionInfo);
            Log.i(TAG, "GIT: " + BuildConfig.GIT);

            // update all summaries
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
                Preference pref = getPreferenceScreen().getPreference(i);
                updateSummary(pref);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);

        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Preference pref = findPreference(key);
            updateSummary(pref);
        }

        /**
         * Update the summary of a given preference.
         *
         * @param pref The preference of which the summary is updated
         */
        public void updateSummary(Preference pref) {
            if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                pref.setSummary(listPref.getEntry());
            } else if (pref instanceof EditTextPreference) {
                EditTextPreference editPref = (EditTextPreference) pref;
                pref.setSummary(editPref.getText());
            } else if (pref instanceof PreferenceCategory) {
                PreferenceCategory cat = (PreferenceCategory) pref;
                for (int i = 0; i < cat.getPreferenceCount(); ++i) {
                    updateSummary(cat.getPreference(i));
                }
            }
        }
    }
}
