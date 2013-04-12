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
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {

    // Initial wait time for network connections.
    private static final long WAIT_TIME_FOR_NETWORK_MILLIS = 60000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Schedule a alarm to trigger a receiver to check time setting.
            PendingIntent timeCheckIntent = PendingIntent.getBroadcast(context, 0,
                    new Intent(context, TimeCheckingReceiver.class),
                    PendingIntent.FLAG_CANCEL_CURRENT);

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + WAIT_TIME_FOR_NETWORK_MILLIS,
                    AlarmManager.INTERVAL_HOUR, timeCheckIntent);

            if (android.os.Build.TYPE.equals("user")) {
                // Start AdbSettingsObserverService to monitor adb setting changes
                context.startService(new Intent(context, AdbSettingsObserverService.class));
            }
        }
    }
}
