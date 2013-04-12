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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Dialog to get a location based on  ZIP codes.
 */
public class LocationSetterDialog extends AlertDialog
        implements OnClickListener {
    private static final String TAG = "LocationSetterDialog";

    public interface OnLocationSetListener {
        void onLocationSet(String location);
    }

    private static final int US_ZIP_CODE_LENGTH = 5;
    private static final String[] US_COUNTRY_CODE_LIST = {"US", "PR"};

    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String UPDATE_STATIC_LOCATION_ACTION =
            "com.google.tv.location.UPDATE_STATIC_LOCATION";

    private Context mContext;
    private View mView;
    private String mLastZipCodeChecked;
    private boolean mLastZipCodeGood;
    private boolean mCheckIfUsTerritory;

    private final OnLocationSetListener mCallBack;

    private Address mAddress;

    protected LocationSetterDialog(Context context,
            OnLocationSetListener callBack) {
        super(context);

        mCallBack = callBack;
        mContext = context;

        setTitle(mContext.getText(R.string.locate));
        setButton(DialogInterface.BUTTON_NEGATIVE,
                mContext.getText(R.string.cancel), (OnClickListener) null);
        setButton(DialogInterface.BUTTON_POSITIVE,
                mContext.getString(R.string.set), this);

        LayoutInflater factory = LayoutInflater.from(context);
        mView = factory.inflate(R.layout.location_entry_view, null);
        setView(mView);
        setIcon(0);
        setupViewControls();

        mCheckIfUsTerritory = mContext.getResources().getBoolean(
                R.bool.check_if_us_territory);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (DialogInterface.BUTTON_POSITIVE == which) {
            if (mCallBack != null) {
                mCallBack.onLocationSet(getLocation());
            }
            if (mAddress != null) {
                updateStaticLocation(mAddress);
            }
        }
    }

    public void setLocation(String location) {
        EditText locationEditText = (EditText) mView.findViewById(R.id.zip_code);
        if (location != null) {
            locationEditText.setText(location);
            updateStatusBasedOnInput(locationEditText.getText(), false);
        }
    }

    public String getLocation() {
        EditText locationEditText = (EditText) mView.findViewById(R.id.zip_code);
        return locationEditText.getText().toString();
    }

    private void updateStaticLocation(Address address) {
        Intent updateLocationIntent = new Intent(UPDATE_STATIC_LOCATION_ACTION);
        Bundle extras = new Bundle();
        extras.putDouble(KEY_LATITUDE, address.getLatitude());
        extras.putDouble(KEY_LONGITUDE, address.getLongitude());
        updateLocationIntent.putExtras(extras);
        mContext.startService(updateLocationIntent);
    }

    private void setupViewControls() {
        EditText locationEditText = (EditText) mView.findViewById(R.id.zip_code);
        locationEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                updateStatusBasedOnInput(s, false);
            }

            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                // do nothing
            }

            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                // do nothing
            }
        });

        locationEditText.setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    EditText locationEditText = (EditText) v;
                    updateStatusBasedOnInput(
                            locationEditText.getEditableText(), true);
                }
            }
        });
    }

    private void updateStatusBasedOnInput(Editable s, boolean showError) {
        String zipCode = s.toString();
        boolean locationValid = zipCode.length() == US_ZIP_CODE_LENGTH;
        setPositiveButtonEnabled(locationValid && mLastZipCodeGood);
        EditText locationEditText = (EditText) findViewById(R.id.zip_code);
        if (showError) {
            locationEditText.setError(locationValid ? null :
                    mContext.getString(R.string.not_a_zip_code));
        }

        if (locationValid) {
            if (mLastZipCodeChecked == null ||
                    !mLastZipCodeChecked.equals(zipCode)) {
                LookUpZipCodeAsyncTask task = new LookUpZipCodeAsyncTask();
                task.execute(zipCode);
            }
        } else {
            mLastZipCodeChecked = null;
            mLastZipCodeGood = false;
            TextView determinedLocation = (TextView) findViewById(
                    R.id.determined_location);
            determinedLocation.setText(null);
        }
    }

    private String getLocationFromAddress(Address address) {
        if (address != null) {
            StringBuilder locationBuilder = new StringBuilder();
            if (address.getLocality() != null) {
                locationBuilder.append(address.getLocality());
                locationBuilder.append(", ");
            } else if (address.getSubLocality() != null) {
                locationBuilder.append(address.getSubLocality());
                locationBuilder.append(", ");
            }
            locationBuilder.append(address.getAdminArea());
            return locationBuilder.toString();
        } else {
            return null;
        }
    }

    private void setPositiveButtonEnabled(boolean enabled) {
        getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(enabled);
    }

    /**
     * Async task to lookup the address for a given zip code.  Needs to be done
     * in a separate thread because the call to server might take time before
     * it can update the UI.
     */
    private class LookUpZipCodeAsyncTask
            extends AsyncTask<String, Void, Address> {

        @Override
        protected void onPreExecute() {
            showCheckLocationProgress(true);
            setDeterminedLocation(mContext.getText(R.string.checking_location));
            super.onPreExecute();
        }

        @Override
        protected Address doInBackground(String... params) {
            String zipCode = params[0];
            return lookUpZipCode(zipCode);
        }

        private boolean isUsCountry(String countryCode) {
            for (String code : US_COUNTRY_CODE_LIST) {
                if (code.equals(countryCode))
                    return true;
            }
            return false;
        }

        /**
         * Given a string, return true if it is a valid ZIP code, false if not.<p>
         * TODO(jefflu): Pass locale into this function and determine if ZIP code
         * is valid based on the locale.  This will be necessary for i18n.
         */
        private Address lookUpZipCode(String zipCodeCandidate) {
            Geocoder geocoder = new Geocoder(mContext, Locale.US);
            List<Address> addresses = null;
            Address address = null;
            mLastZipCodeChecked = zipCodeCandidate;
            try {
                addresses = geocoder.getFromLocationName(zipCodeCandidate, 1);
                if (addresses.isEmpty()) {
                    Log.e(TAG, "No results returned for ZIP code: " +
                            zipCodeCandidate);
                } else {
                    address = addresses.get(0);
                    if (address.getAdminArea() == null
                            || (mCheckIfUsTerritory && !isUsCountry(address.getCountryCode()))
                            || !zipCodeCandidate.equals(address.getPostalCode())) {
                        address = null;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Problem with location: " + zipCodeCandidate, e);
            }
            return address;
        }

        @Override
        protected void onPostExecute(Address result) {
            showCheckLocationProgress(false);
            mAddress = result;
            if (result == null) {
                setDeterminedLocation(mContext.getText(R.string.no_location_found));
                setPositiveButtonEnabled(false);
                mLastZipCodeGood = false;
            } else {
                setDeterminedLocation(getLocationFromAddress(result));
                setPositiveButtonEnabled(true);
                mLastZipCodeGood = true;
            }

            super.onPostExecute(result);
        }

        @Override
        protected void onCancelled() {
            showCheckLocationProgress(false);
            setDeterminedLocation(null);

            super.onCancelled();
        }

        private void showCheckLocationProgress(boolean visible) {
            findViewById(R.id.checking_location_progress).setVisibility(
                    visible ? View.VISIBLE : View.GONE);
        }

        private void setDeterminedLocation(CharSequence text) {
            TextView determinedLocation = (TextView) findViewById(
                    R.id.determined_location);
            determinedLocation.setText(text);
        }
    }
}
