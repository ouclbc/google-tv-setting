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
public class DeviceNameSetterDialog extends AlertDialog
        implements OnClickListener {
    private static final String TAG = "DeviceNameSetterDialog";

    public interface OnDeviceNameSetListener {
        void onDeviceNameSet(String location);
    }

    private Context mContext;
    private View mView;
    private final OnDeviceNameSetListener mCallBack;

    protected DeviceNameSetterDialog(Context context, String name,
            OnDeviceNameSetListener callBack) {
        super(context);

        mCallBack = callBack;
        mContext = context;

        setTitle(mContext.getText(R.string.device_name_settings));
        setButton(DialogInterface.BUTTON_NEGATIVE,
                mContext.getText(R.string.cancel), (OnClickListener) null);
        setButton(DialogInterface.BUTTON_POSITIVE,
                mContext.getString(R.string.set), this);

        LayoutInflater factory = LayoutInflater.from(context);
        mView = factory.inflate(R.layout.device_name_entry_view, null);
        setView(mView);
        setIcon(0);
        setupViewControls(name);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (DialogInterface.BUTTON_POSITIVE == which) {
            if (mCallBack != null) {
                mCallBack.onDeviceNameSet(getDeviceName());
            }
        }
    }

    public String getDeviceName() {
        EditText nameEditText = (EditText) mView.findViewById(R.id.device_name);
        return nameEditText.getText().toString();
    }

    private void setupViewControls(String name) {
        EditText nameEditText = (EditText) mView.findViewById(R.id.device_name);
        nameEditText.setText(name);
        nameEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                setPositiveButtonEnabled(!s.toString().isEmpty());
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

        nameEditText.setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    setPositiveButtonEnabled(!((EditText) v).getEditableText().toString().isEmpty());
                }
            }
        });
    }

    private void setPositiveButtonEnabled(boolean enabled) {
        getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(enabled);
    }
}
