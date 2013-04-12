/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import com.google.android.tv.common.TvIntent;
import com.google.android.tv.settings.TvSettings;

public class TvDeviceInfoSettings extends DeviceInfoSettings {
    private static final String KEY_DEVICE_NAME_SECTION = "device_name_settings";
    private static final int DIALOG_DEVICE_NAME_SETTER = 1000;

    private DeviceNameSetterDialog mDeviceNameSetter;
    private Preference mDeviceNamePref;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDeviceName();
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mDeviceNamePref = findPreference(KEY_DEVICE_NAME_SECTION);
        updateDeviceName();
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(TvIntent.ACTION_USER_DEVICE_NAME_UPDATED);
        getActivity().registerReceiver(mReceiver, filter);
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mReceiver);
        super.onPause();
    }

    private void updateDeviceName() {
        mDeviceNamePref.setSummary(
                TvSettings.System.getUserDeviceName(getActivity()));
    }

    @Override
    public boolean onPreferenceTreeClick(
            PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mDeviceNamePref) {
            showDialog(DIALOG_DEVICE_NAME_SETTER);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_DEVICE_NAME_SETTER:
            mDeviceNameSetter = new DeviceNameSetterDialog(getActivity(),
                    (String) mDeviceNamePref.getSummary(),
                    new DeviceNameSetterDialog.OnDeviceNameSetListener() {
                public void onDeviceNameSet(String name) {
                    if (name != null) {
                        name.trim();
                        TvSettings.System.setUserDeviceName(getActivity(), name);
                    }
                }
            });
            mDeviceNameSetter.setOnCancelListener(null);
            mDeviceNameSetter.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.cancel), (OnClickListener) null);
            return mDeviceNameSetter;

        default:
            return super.onCreateDialog(id);
        }
    }
}
