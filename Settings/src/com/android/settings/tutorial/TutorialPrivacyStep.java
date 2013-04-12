/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings.tutorial;

import com.android.settings.R;
import com.google.android.tv.settings.TvSettings;

import android.app.AlertDialog;
import android.content.Context;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;

class TutorialPrivacyStep extends TutorialStep {

    private View mView;

    private CheckBox mCheckBox;

    @Override
    public View getStepView(final Context context, ViewGroup parent) {
        if (mView == null) {
            mView = LayoutInflater.from(context).inflate(R.layout.tutorial_step_privacy, parent,
                    false);
            mCheckBox = (CheckBox) mView.findViewById(R.id.privacy_checkbox);
            mCheckBox.setChecked(getStoredPrivacyValue(context));
            Button learnMore = (Button) mView.findViewById(R.id.learn_more);
            learnMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.post(new Runnable() {
                        @Override
                        public void run() {
                            showExplainText(context);
                        }
                    });
                }
            });
        }
        return mView;
    }

    @Override
    public void onAdvanceStep(Context context) {
        TvSettings.Secure.putBoolean(context.getContentResolver(),
                TvSettings.Secure.CHECKIN_USAGE_LOGGING_ENABLED, mCheckBox.isChecked());
    }

    /**
     * Getting the currently stored privacy value. If no values have been stored
     * yet, get the default privacy value.
     */
    private boolean getStoredPrivacyValue(Context context) {
        return TvSettings.Secure.getBoolean(context.getContentResolver(),
                TvSettings.Secure.CHECKIN_USAGE_LOGGING_ENABLED, false);
    }

    private void showExplainText(Context context) {
        new AlertDialog.Builder(context)
                .setMessage(R.string.tutorial_privacy_explanation)
                .setCancelable(true)
                .show();
    }
}
