/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.google.android.tv.settings.TvSettings.Secure;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

public class TvWirelessSettings extends WirelessSettings {
    private static final String KEY_NETWORK_STATS_CATEGORY =
            "network_stats_category";
    private static final String KEY_ALARM_FOR_NETWORK_DISCONNECTION =
            "alarm_for_network_disconnection";
    private static final String KEY_ALARM_FOR_BAD_PING =
            "alarm_for_bad_ping";
    private static final String KEY_ALARM_FOR_BAD_NETWORK_PERFORMANCE =
            "alarm_for_bad_network_performance";

    private CheckBoxPreference mAlarmForNetworkDisconnectionPref;
    private CheckBoxPreference mAlarmForBadPingPref;
    private CheckBoxPreference mAlarmForBadNetworkPerformancePref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        boolean optIn = Secure.getBoolean(
                getContentResolver(),
                Secure.CHECKIN_USAGE_LOGGING_ENABLED, false);

        mAlarmForNetworkDisconnectionPref = (CheckBoxPreference)
                findPreference(KEY_ALARM_FOR_NETWORK_DISCONNECTION);
        mAlarmForBadPingPref = (CheckBoxPreference)
                findPreference(KEY_ALARM_FOR_BAD_PING);
        mAlarmForBadNetworkPerformancePref = (CheckBoxPreference)
                findPreference(KEY_ALARM_FOR_BAD_NETWORK_PERFORMANCE);

        if (optIn) {
            mAlarmForNetworkDisconnectionPref.setChecked(
                    Secure.getBoolean(getContentResolver(),
                            Secure.ALARM_FOR_NETWORK_DISCONNECTION_ENABLED,
                            true));
            mAlarmForBadPingPref.setChecked(
                    Secure.getBoolean(getContentResolver(),
                            Secure.ALARM_FOR_BAD_PING_ENABLED,
                            true));
            mAlarmForBadNetworkPerformancePref.setChecked(
                    Secure.getBoolean(getContentResolver(),
                            Secure.ALARM_FOR_BAD_NETWORK_PERFORMANCE_ENABLED,
                            true));
        } else {
            PreferenceCategory category = (PreferenceCategory)
                    findPreference(KEY_NETWORK_STATS_CATEGORY);

            category.removePreference(mAlarmForNetworkDisconnectionPref);
            category.removePreference(mAlarmForBadPingPref);
            category.removePreference(mAlarmForBadNetworkPerformancePref);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(
            PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAlarmForNetworkDisconnectionPref) {
            Secure.putBoolean(getContentResolver(),
                    Secure.ALARM_FOR_NETWORK_DISCONNECTION_ENABLED,
                    mAlarmForNetworkDisconnectionPref.isChecked());
        } else if (preference == mAlarmForBadPingPref) {
            Secure.putBoolean(getContentResolver(),
                    Secure.ALARM_FOR_BAD_PING_ENABLED,
                    mAlarmForBadPingPref.isChecked());
        } else if (preference == mAlarmForBadNetworkPerformancePref) {
            Secure.putBoolean(getContentResolver(),
                    Secure.ALARM_FOR_BAD_NETWORK_PERFORMANCE_ENABLED,
                    mAlarmForBadNetworkPerformancePref.isChecked());
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
