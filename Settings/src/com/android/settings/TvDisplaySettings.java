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

import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class TvDisplaySettings extends DisplaySettings {
    private static final String TAG = "TvDisplaySettings";

    private static final String HDMI_DEVICE_PATH = "/devices/virtual/switch/hdmi";

    private static final String FB_RESOLUTION_PATH = "/data/system/fb.resolution";

    private static final String KEY_SCREEN_RESOLUTION = "screen_resolution";

    private static final String KEY_SCREEN_BRIGHTNESS = "brightness";

    private static final String KEY_SCREEN_SLEEP = "screen_timeout";

    private static final String KEY_SCREEN_ROTATE = "accelerometer";


    private static String getSysDevicePath(String file) {
        return "/sys" + HDMI_DEVICE_PATH + "/" + file;
    }

    private static String readStringFromFile(String filename) {
        try {
            FileReader file = new FileReader(filename);
            BufferedReader reader = new BufferedReader(file);
            String line = reader.readLine();
            reader.close();
            return line;
        } catch (Exception e) {
            // Don't care about errors.
        }
        return null;
    }

    private void writeStringToFile(String filename, String line) {
        writeStringToFile(filename, line, false);
    }

    private void writeStringToFile(String filename, String line, boolean ignoreError) {
        try {
            File file = new File(filename);
            FileWriter writer = new FileWriter(file);
            writer.write(line);
            writer.close();
        } catch (IOException e) {
            if (!ignoreError) {
                Log.w(TAG, "could not write file", e);
            }
        }
    }

    static private boolean hasResolutionChangeSupport() {
        File file = new File(getSysDevicePath("modelist"));
        return file.exists();
    }

    class PreferenceHandler {
        private ListPreference mPreference;

        private String mSummaryTemplate;

        private CharSequence[] mEntries;

        private CharSequence[] mValues;

        public PreferenceHandler(int resId, String prefKey, String summaryTemplate) {
            mSummaryTemplate = summaryTemplate;

            addPreferencesFromResource(resId);
            mPreference = (ListPreference) findPreference(prefKey);
            mPreference.setOnPreferenceChangeListener(TvDisplaySettings.this);

            mEntries = mPreference.getEntries().clone();
            mValues = mPreference.getEntryValues().clone();

            updatePreference();
        }

        public String getKey() {
            return mPreference.getKey();
        }

        private int readScreenResolution() {
            int index = 0;
            String resolution = readStringFromFile(FB_RESOLUTION_PATH);
            try {
                index = Integer.parseInt(resolution);
            } catch (NumberFormatException e) {
                Log.w(TAG, "cannot read screen resolution setting");
            }
            return index;
        }

        private void filterUnsupportedModes(ListPreference pref) {
            // Ignore error in case there is no driver support to read current supported modes.
            String modeList = readStringFromFile(getSysDevicePath("modelist"));
            if (modeList != null) {
                Log.d(TAG, "mode list: " + modeList);
                // Populate preference list with supported resolutions.
                final Resources res = getResources();
                ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
                ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
                revisedValues.add(mValues[0]);
                revisedEntries.add(mEntries[0]);
                for (int i = 1; i < mEntries.length; ++i) {
                    if (modeList.contains(mEntries[i])) {
                        Log.d(TAG, "Has " + mEntries[i]);
                        revisedValues.add(mValues[i]);
                        revisedEntries.add(mEntries[i]);
                    }
                }
                pref.setEntries(revisedEntries.toArray(new CharSequence[revisedValues.size()]));
                pref.setEntryValues(revisedValues.toArray(new CharSequence[revisedValues.size()]));
            }
        }

        private void updateSummary(int index) {
            // Show current display mode in the summary text
            CharSequence summary = mEntries[index];
            if (index == 0) {
                // Ignore error in case there is no driver support to read current mode.
                String mode = readStringFromFile(getSysDevicePath("mode"));
                if (mode != null) {
                    String[] chunks = mode.split("x|i|p");
                    if (chunks.length >= 2) {
                        summary = String.format(mSummaryTemplate, chunks[0], chunks[1]);
                    }
                }
            } else {
                Log.d(TAG, "update summary " + summary);
            }
            mPreference.setSummary(summary);
        }

        private boolean isValidIndex(ListPreference pref, int index) {
            boolean validIndex = false;
            CharSequence[] values = pref.getEntryValues();
            try {
                for (CharSequence seq : values) {
                    if (Integer.parseInt((String) seq) == index) {
                        return true;
                    }
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "resolution value index is invalid");
            }
            return false;
        }

        public void updatePreference() {
            filterUnsupportedModes(mPreference);
            int index = readScreenResolution();
            // Validate index
            if (!isValidIndex(mPreference, index)) {
                index = 0;
            }
            // mark the appropriate item in the preferences list
            mPreference.setValueIndex(index);
            updateSummary(index);
        }

        public boolean setPreference(String resolution) {
            int value = Integer.parseInt(resolution);
            Log.d(TAG, "write resolution " + value);
            if (value != readScreenResolution()) {
                writeStringToFile(FB_RESOLUTION_PATH, Integer.toString(value));
                // Notify driver about resolution setting change;
                // ignore error in case there is no driver support.
                writeStringToFile(getSysDevicePath("resolution"), Integer.toString(value), true);
                updatePreference();
            }
            return true;
        }
    }

    private PreferenceHandler mResolutionSetting;

    private class HdmiEventObserver extends UEventObserver {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            String attribute = event.get("ATTRIBUTE");
            if ("resolution".equals(attribute)) {
                Log.d(TAG, "Refresh resolution");
            } else if ("mode".equals(attribute) || "modelist".equals(attribute)) {
                Log.d(TAG, "Refresh mode list");
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mResolutionSetting != null) {
                            mResolutionSetting.updatePreference();
                        }
                    }
                });
            }
            Log.d(TAG, "ATTRIBUTE " + event.get("ATTRIBUTE"));
            Log.d(TAG, "SWITCH_STATE " + event.get("SWITCH_STATE"));
        }
    }

    private HdmiEventObserver mHdmiObserver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Resources res = getResources();

        PreferenceScreen prefScreen = getPreferenceScreen();
        prefScreen.removePreference(findPreference(KEY_SCREEN_BRIGHTNESS));
        prefScreen.removePreference(findPreference(KEY_SCREEN_SLEEP));
        prefScreen.removePreference(findPreference(KEY_SCREEN_ROTATE));

        if (hasResolutionChangeSupport()) {
            mResolutionSetting = new PreferenceHandler(R.xml.resolution_settings,
                    KEY_SCREEN_RESOLUTION, res.getString(R.string.screen_resolution_summary_auto));
            observeHdmiState();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mResolutionSetting != null) {
            observeHdmiState();
            mResolutionSetting.updatePreference();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mHdmiObserver != null) {
            mHdmiObserver.stopObserving();
            mHdmiObserver = null;
        }
    }

    private void observeHdmiState() {
        // Watch for HDMI change if the HDMI switch exists.
        if (mHdmiObserver != null && new File(getSysDevicePath("resolution")).exists()) {
            mHdmiObserver = new HdmiEventObserver();
            mHdmiObserver.startObserving("DEVPATH=" + HDMI_DEVICE_PATH);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (mResolutionSetting != null && key.equals(mResolutionSetting.getKey())) {
            return mResolutionSetting.setPreference((String) objValue);
        }
        return super.onPreferenceChange(preference, objValue);
    }
}
