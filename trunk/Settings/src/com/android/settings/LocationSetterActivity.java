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

import com.google.android.tv.settings.TvSettings;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.provider.Settings;

/**
 * Activity wrapping LocationSetterDialog that can write values to Settings
 * if launched by external applications.
 */
public class LocationSetterActivity extends Activity {
    LocationSetterDialog mLocationSetter;

    private static final int DIALOG_LOCATION_SETTER = 0;

    @Override
    protected void onResume() {
        super.onResume();
        showDialog(DIALOG_LOCATION_SETTER);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (DIALOG_LOCATION_SETTER == id) {
            mLocationSetter = new LocationSetterDialog(this,
                    new LocationSetterDialog.OnLocationSetListener() {
                public void onLocationSet(String location) {
                    if (location != null) {
                        location.trim();
                        Settings.Secure.putString(getContentResolver(),
                                TvSettings.Secure.LOCATION_LINEUP_LOOKUP,
                                location);
                        setResult(RESULT_OK);
                    } else {
                        setResult(RESULT_CANCELED);
                    }
                    finish();
                }
            });
            mLocationSetter.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
            mLocationSetter.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.cancel), new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
            return mLocationSetter;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (DIALOG_LOCATION_SETTER == id) {
            mLocationSetter.setLocation(getZipCode());
        }
    }

    private String getZipCode() {
        String zipCode = Settings.Secure.getString(this.getContentResolver(),
                TvSettings.Secure.LOCATION_LINEUP_LOOKUP);
        return zipCode;
    }
}
