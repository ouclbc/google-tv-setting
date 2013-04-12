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

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.TimePicker;

import com.android.settings.DateTimeSettings;
import com.android.settings.Settings;
import com.google.android.tv.settings.CountryPicker;
import com.google.android.tv.settings.CountryPicker.CountryInfo;
import com.google.android.tv.settings.TvSettings;

import java.util.Calendar;

public class DateTimeLocationSettings extends DateTimeSettings
        implements OnSharedPreferenceChangeListener,
                TimePickerDialog.OnTimeSetListener,
                DatePickerDialog.OnDateSetListener,
                LocationSetterDialog.OnLocationSetListener {
    private static final String KEY_LOCATION_SECTION = "location_section";
    private static final String KEY_COUNTRY = "country";
    private static final String KEY_LOCATION = "location";

    private static final int DIALOG_LOCATION_SETTER = 1000;

    // TODO: figure out what to do with zip code if country is not US.
    private ListPreference mCountryPref;
    private Preference mLocationPref;

    private LocationSetterDialog mLocationSetter;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        PreferenceManager manager = getPreferenceManager();
        PreferenceScreen preferenceScreen = manager.inflateFromResource(getActivity(),
              R.xml.date_time_section_header, null);
        preferenceScreen.setOrderingAsAdded(true);
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
            getPreferenceScreen().getPreference(i).setOrder(Preference.DEFAULT_ORDER);
            preferenceScreen.addPreference(getPreferenceScreen().getPreference(i));
        }

        setPreferenceScreen(
                manager.inflateFromResource(
                        getActivity(),
                        R.xml.location_section,
                        preferenceScreen));

        initUI();
    }

    private void initUI() {
        setupCountryPreference();
        mLocationPref = findPreference(KEY_LOCATION);
        if (getResources().getBoolean(R.bool.ask_for_zip_code)) {
            updateLocationSummary();
        } else {
            ((PreferenceCategory) findPreference(KEY_LOCATION_SECTION)).
                    removePreference(mLocationPref);
        }
    }

    private void setupCountryPreference() {
        final ArrayAdapter<CountryInfo> adapter = CountryPicker.constructAdapter(getActivity());
        final int countryCount = adapter.getCount();

        mCountryPref = (ListPreference) findPreference(KEY_COUNTRY);
        if (countryCount > 1) {
            final String[] countryCodes = new String[countryCount];
            final String[] countryNames = new String[countryCount];
            final String currentCountryCode = CountryPicker.getCountryCode();
            int currentCountryIndex = -1;

            for (int index = 0; index < countryCount; index++) {
                final CountryInfo info = adapter.getItem(index);
                countryNames[index] = info.getName();
                countryCodes[index] = info.getCode();
                if (currentCountryIndex < 0 && currentCountryCode.equals(countryCodes[index])) {
                    currentCountryIndex = index;
                }
            }

            mCountryPref.setEntries(countryNames);
            mCountryPref.setEntryValues(countryCodes);
            mCountryPref.setValueIndex(currentCountryIndex);
            mCountryPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final String newCountryCode = (String) newValue;
                    CountryPicker.updateCountryOverride(newCountryCode);
                    setCountryPreferenceSummary(
                            countryNames[mCountryPref.findIndexOfValue(newCountryCode)]);
                    return true;
                }
            });
            if (currentCountryIndex >= 0) {
                setCountryPreferenceSummary(countryNames[currentCountryIndex]);
            }
        } else {
            if (countryCount == 1) {
                setCountryPreferenceSummary(adapter.getItem(0).getName());
            }
            mCountryPref.setEnabled(false);
        }
    }

    private void setCountryPreferenceSummary(String countryName) {
        if (mCountryPref != null) {
            if (!TextUtils.isEmpty(countryName)) {
                mCountryPref.setSummary(countryName);
            } else {
                mCountryPref.setSummary("");
            }
        }
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int day) {
        super.onDateSet(view, year, month, day);
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        super.onTimeSet(view, hourOfDay, minute);
    }

    @Override
    public void onLocationSet(String location) {
        if (location != null) {
            location.trim();
            android.provider.Settings.Secure.putString(this.getContentResolver(),
                    TvSettings.Secure.LOCATION_LINEUP_LOOKUP, location);
            updateLocationSummary();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key.equals(KEY_LOCATION)) {
            super.onSharedPreferenceChanged(preferences, "date_format");
        } else {
            super.onSharedPreferenceChanged(preferences, key);
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_LOCATION_SETTER: {
            mLocationSetter = new LocationSetterDialog(getActivity(), this);
            return mLocationSetter;
        }
        default:
            return super.onCreateDialog(id);
        }
    }

    // TODO: Uncomment bellow, when the onPrepareDialog method
    // in the super class, DateTimeSettings, is uncommented,
    /*
    @Override
    public void onPrepareDialog(int id, Dialog d) {
        switch (id) {
        case DIALOG_LOCATION_SETTER: {
            mLocationSetter.setLocation(getZipCode());
            break;
        }
        default:
            super.onPrepareDialog(id);
            break;
        }
    }
    */
    @Override
    public boolean onPreferenceTreeClick(
            PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mLocationPref) {
            showDialog(DIALOG_LOCATION_SETTER);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void updateLocationSummary() {
        mLocationPref.setSummary(getZipCode());
    }

    /**
     * Helper function that uses location manager to determine the ZIP code of
     * this device.
     * @return ZIP code if known, empty string if unknown.
     */
    private String getZipCode() {
        String zipCode = android.provider.Settings.Secure.getString(
                this.getContentResolver(), TvSettings.Secure.LOCATION_LINEUP_LOOKUP);
        return zipCode;
    }
}
