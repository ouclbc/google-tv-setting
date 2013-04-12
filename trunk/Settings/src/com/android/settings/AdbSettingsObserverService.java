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

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemService;
import android.provider.Settings;
import android.util.Log;

public final class AdbSettingsObserverService extends Service {
    private static final String LOG_TAG = "AdbSettingsObserverService";

    private class AdbSettingsObserver extends ContentObserver {
        private final String ADBD_SERVICE = "adbd";
        private ContentResolver mContentResolver;

        public AdbSettingsObserver(ContentResolver contentResolver) {
            super(new Handler());
            mContentResolver = contentResolver;
        }

        public void observe() {
            mContentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (Settings.Global.getInt(mContentResolver, Settings.Global.ADB_ENABLED, 0) > 0) {
                if (SystemService.isStopped(ADBD_SERVICE)) {
                    Log.d(LOG_TAG, "ADB is enabled. Running ADB service");
                    SystemService.start(ADBD_SERVICE);
                }
            } else {
                if (SystemService.isRunning(ADBD_SERVICE)) {
                    Log.d(LOG_TAG, "ADB is disabled. Stopping ADB service");
                    SystemService.stop(ADBD_SERVICE);
                }
            }
        }
    }

    private AdbSettingsObserver mAdbSettingsObserver;

    @Override
    public IBinder onBind(Intent arg) {
        return null;
    }

    @Override
    public void onCreate() {
        // Register observer to listen for adb setting changes
        mAdbSettingsObserver = new AdbSettingsObserver(getContentResolver());
        mAdbSettingsObserver.onChange(false);
        mAdbSettingsObserver.observe();
    }
}

