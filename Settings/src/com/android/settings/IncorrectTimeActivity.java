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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Bundle;
import android.os.Handler;

/**
 * Activity to show the dialog to let user know the time is incorrect. Also
 * starts off the DateTimeSettings class to allow the user to set the time.
 */
public class IncorrectTimeActivity extends Activity {

    private static final String ACTION_DATE_SETTINGS =
            "android.settings.DATE_SETTINGS";

    private static final int TIME_CHECKING_INTERVAL_MILLIS = 5000;
    private Handler mHandler = new Handler();
    private Runnable mCheckingTimeTask = new Runnable() {
        public void run() {
            if (TimeCheckingReceiver.isTimeInFarPast(System.currentTimeMillis())) {
                mHandler.postDelayed(this, TIME_CHECKING_INTERVAL_MILLIS);
            } else {
                finish();
            }
        }
     };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVisible(false);

        new AlertDialog.Builder(this)
        .setNeutralButton(R.string.dlg_ok, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                showDateTimeSettings();
                finish();
            }
        })
        .setCancelable(false)
        .setOnCancelListener(new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                showDateTimeSettings();
                finish();
            }
        })
        .setTitle(R.string.incorrect_time_dialog_title)
        .setMessage(isNetworkConnected() ? R.string.incorrect_time_dialog_message:
            R.string.incorrect_time_dialog_message_with_network_issues)
        .show();
        if (!isNetworkConnected()) {
            mHandler.postDelayed(mCheckingTimeTask, TIME_CHECKING_INTERVAL_MILLIS);
        }
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mCheckingTimeTask);
        super.onDestroy();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager manager =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (manager == null) {
            return false;
        }
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.getState() == State.CONNECTED) {
            return true;
        }
        return false;
    }

    private void showDateTimeSettings() {
        Intent intent = new Intent(ACTION_DATE_SETTINGS);
        startActivity(intent);
    }
}
