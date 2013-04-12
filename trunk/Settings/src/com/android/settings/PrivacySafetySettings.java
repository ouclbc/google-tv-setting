/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import com.google.android.tv.provider.VideoHistoryContract.Visits;
import com.google.android.tv.settings.TvSettings;
import com.google.android.tv.settings.TvSettings.Secure;
import com.google.tv.preference.Preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.backup.IBackupManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;

/**
 * Settings for controlling privacy and content safety.
 */
public class PrivacySafetySettings extends SettingsPreferenceFragment
        implements OnSharedPreferenceChangeListener {
    private static final String TAG = "PrivacySafetySettings";

    /** UI keys for preference widgets */
    private static final String CHECKIN_LOGGING_ENABLED_KEY = "checkin_logging_enabled";
    private static final String ENABLE_VIDEO_HISTORY_KEY = "enable_video_history";
    private static final String CLEAR_VIDEO_HISTORY_KEY = "clear_video_history";
    private static final String SAFE_SEARCH_LEVEL_KEY = "safe_search_level";
    private static final String SAFETY_CATEGORY_KEY = "safety_category";
    private static final String PERSONAL_CATEGORY_KEY = "personal_category";

    private CheckBoxPreference mCheckinLoggingEnabled;
    private CheckBoxPreference mEnableVideoHistory;
    private Preference mClearVideoHistory;
    private ListPreference mSafeSearchLevel;

    private static final int DIALOG_ID_CONFIRM_ENABLE_VIDEO_HISTORY = 0;
    public static final String EXTRA_ENABLE_VIDEO_HISTORY_ONLY = "enable_video_history_only";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.privacy_safety_settings);

        final PreferenceScreen screen = getPreferenceScreen();

        initUI();
    }

    private void initUI() {
        mCheckinLoggingEnabled = (CheckBoxPreference) findPreference(CHECKIN_LOGGING_ENABLED_KEY);
        mSafeSearchLevel = (ListPreference) findPreference(SAFE_SEARCH_LEVEL_KEY);
        mEnableVideoHistory = (CheckBoxPreference) findPreference(ENABLE_VIDEO_HISTORY_KEY);
        mEnableVideoHistory.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean enabledValue = (Boolean) newValue;
                if (enabledValue) {
                    showDialog(DIALOG_ID_CONFIRM_ENABLE_VIDEO_HISTORY);
                    return false;
                } else {
                    TvSettings.Secure.putBoolean(getContentResolver(),
                            TvSettings.Secure.VIDEO_HISTORY_ENABLED, false);
                    return true;
                }
            }
        });
        mClearVideoHistory = findPreference(CLEAR_VIDEO_HISTORY_KEY);
    }

    @Override
    public void onResume() {
        super.onResume();
        final Activity activity = getActivity();
        mCheckinLoggingEnabled.setChecked(Secure.getBoolean(
                getContentResolver(), Secure.CHECKIN_USAGE_LOGGING_ENABLED, false));
        mSafeSearchLevel.setValue(Preferences.getSafeSearchLevel(activity).name());
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        mEnableVideoHistory.setChecked(Secure.getBoolean(
                getContentResolver(), Secure.VIDEO_HISTORY_ENABLED, false));
        setClearVideoHistoryEnabled();

        if (activity.getIntent().getBooleanExtra(EXTRA_ENABLE_VIDEO_HISTORY_ONLY, false)) {
            showDialog(DIALOG_ID_CONFIRM_ENABLE_VIDEO_HISTORY);
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ID_CONFIRM_ENABLE_VIDEO_HISTORY:
                return buildConfirmEnableVideoHistoryDialog();
        }
        return null;
    }

    private Dialog buildConfirmEnableVideoHistoryDialog() {
        final boolean isHistoryOnly =
                (getActivity().getIntent().getBooleanExtra(EXTRA_ENABLE_VIDEO_HISTORY_ONLY, false));
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enable_video_history_title)
                .setMessage(isHistoryOnly ? R.string.confirm_video_history_only_message
                        : R.string.confirm_video_history_message)
                .setPositiveButton(R.string.button_confirm,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mEnableVideoHistory.setChecked(true);
                                Secure.putBoolean(getContentResolver(),
                                        Secure.VIDEO_HISTORY_ENABLED, true);
                                dialog.dismiss();
                                if (isHistoryOnly) {
                                    PrivacySafetySettings.this.finish();
                                }
                            }
                        })
                .setNegativeButton(R.string.dlg_cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mEnableVideoHistory.setChecked(false);
                                Secure.putBoolean(getContentResolver(),
                                        Secure.VIDEO_HISTORY_ENABLED, false);
                                dialog.dismiss();
                                if (isHistoryOnly) {
                                    PrivacySafetySettings.this.finish();
                                }
                            }
                        })
                .create();
    }

    public void onSharedPreferenceChanged(SharedPreferences preferences,
            String key) {
        if (key.equals(CHECKIN_LOGGING_ENABLED_KEY)) {
            final boolean enabledValue = mCheckinLoggingEnabled.isChecked();
            Secure.putBoolean(getContentResolver(),
                    Secure.CHECKIN_USAGE_LOGGING_ENABLED, enabledValue);
        } else if (key.equals(SAFE_SEARCH_LEVEL_KEY)) {
            final String levelName = mSafeSearchLevel.getValue();
            Preferences.SafeSearchLevel levelEnum;
            try {
                levelEnum = Preferences.SafeSearchLevel.valueOf(levelName);
            } catch (IllegalArgumentException e) {
                levelEnum = Preferences.DEFAULT_SAFE_SEARCH_LEVEL;
            }
            Preferences.setSafeSearchLevel(getActivity(), levelEnum);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferences, Preference preference) {
        if (CLEAR_VIDEO_HISTORY_KEY.equals(preference.getKey())) {
            clearVideoHistory();
            return true;
        }

        return super.onPreferenceTreeClick(preferences, preference);
    }

    /**
     * Enables or disables "Clear video history" menu item.
     */
    private void setClearVideoHistoryEnabled() {
        // Execute off UI thread
        new AccessVideoHistoryTask(false).execute();
    }

    /**
     * Clears video history in database, then re-queries the history and
     * enables/disables "Clear video history" menu item depending on result.
     */
    private void clearVideoHistory() {
        // Execute off UI thread
        new AccessVideoHistoryTask(true).execute();
    }

    /**
     * Async task to query database for video history records and enable/disable
     * "Clear video history" menu item if any records exist.
     * Optionally it can delete video history records before the query.
     */
    private class AccessVideoHistoryTask extends AsyncTask<Void, Void, Boolean> {
        private final boolean mPerformDelete;

        /**
         * Constructor.
         *
         * @param performDelete true indicates that records will be deleted,
         *        false means that it will be query only.
         */
        AccessVideoHistoryTask(boolean performDelete) {
            this.mPerformDelete = performDelete;
        }

        @Override
        protected void onPreExecute () {
            // Disable while background task is performed
            mClearVideoHistory.setEnabled(false);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (mPerformDelete) {
                // Delete everything
                getContentResolver().delete(Visits.VISITS_URI, null, null);
            }
            // Check actual records in database
            return hasHistoryRecords();
        }

        @Override
        protected void onPostExecute(Boolean enabled) {
            // Enable or disable depending on existence of history records
            mClearVideoHistory.setEnabled(enabled);
        }

        /**
         * @return true if any video history records exist
         */
        private boolean hasHistoryRecords() {
            Cursor cursor = null;
            boolean hasRecords = false;
            try {
                cursor = getContentResolver().query(Visits.VISITS_URI, null,
                        null, null, null);
                hasRecords = cursor != null && cursor.moveToNext();
            } finally {
              if (cursor != null) {
                  cursor.close();
              }
            }
            return hasRecords;
        }
    }

}
