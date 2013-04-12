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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemProperties;


public class TimeCheckingReceiver extends BroadcastReceiver {
    private static final String TAG = "TimeCheckingReceiver";

    private static final int ONE_DAY = 24 * 60 * 60 * 1000;

    // July 1 2010 (in seconds to match "ro.build.date.utc")
    private static final long GOOGLE_TV_EPOCH_TIME_SECONDS = 1277942;
    private static final long SMALL_TIME_SKEW = 3 * ONE_DAY; // 3 days

    private static final String TV_WELCOME_PACKAGE_NAME =
            "com.google.tv.welcome";
    private static final String MODULAR_SETUP_ACTIVITY_NAME =
            TV_WELCOME_PACKAGE_NAME + ".ModularSetupActivity";
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        if (isTimeInFarPast(System.currentTimeMillis())) {
            showIncorrectTimeUi();
            return;
        }
    }

    static public boolean isTimeInFarPast(long time) {
        long buildDate = SystemProperties.getLong("ro.build.date.utc",
                GOOGLE_TV_EPOCH_TIME_SECONDS) * 1000;
        long cutOffDate = buildDate - SMALL_TIME_SKEW;
        return time < cutOffDate - SMALL_TIME_SKEW;
    }

    private void showIncorrectTimeUi() {
        if (isInSetup()) {
            return;
        }
        showIncorrectTime();
    }

    private void showIncorrectTime() {
        Intent intent = new Intent(mContext, IncorrectTimeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private boolean isInSetup() {
        PackageManager pm = mContext.getPackageManager();
        ComponentName name = new ComponentName(TV_WELCOME_PACKAGE_NAME,
                MODULAR_SETUP_ACTIVITY_NAME);
        return pm.getComponentEnabledSetting(name) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }
}
