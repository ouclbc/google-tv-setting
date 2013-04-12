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

import com.google.android.tv.setup.SetupConstants;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Dialog which shows tutorial mode.
 */
public final class TutorialActivity extends Activity {

    private TextView mSteps;
    private TextView mTitle;
    private TextView mText;
    private ImageView mImage;
    private ViewGroup mStepView;

    private Button mBackButton;
    private Button mNextButton;

    /**
     * Total number of tips.
     */
    private int mTipCount;

    private TutorialController mTutorialController;

    private FrameLayout mFrame;

    private boolean mLaunchedFromSettings;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.tutorial_step);
        mSteps = (TextView) findViewById(R.id.tutorial_steps);
        mTitle = (TextView) findViewById(R.id.tutorial_title);
        mText = (TextView) findViewById(R.id.tutorial_text);
        mImage = (ImageView) findViewById(R.id.tutorial_image);
        mFrame = (FrameLayout) findViewById(R.id.frame);
        mStepView = (ViewGroup) findViewById(R.id.tutorial_view);
        mNextButton = (Button) findViewById(R.id.next_button);
        mNextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                advance();
            }
        });
        mBackButton = (Button) findViewById(R.id.back_button);
        mBackButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mLaunchedFromSettings = getIntent().getBooleanExtra(
                getString(R.string.from_settings), false);
        if (mLaunchedFromSettings) {
            // Always show the latest version if launching from settings.
            mTutorialController = new TutorialController(getApplicationContext(),
                    SetupConstants.CURRENT_SETUP_VERSION);
        } else {
            mTutorialController = new TutorialController(getApplicationContext());
        }

        TutorialStep step = mTutorialController.getCurrentTutorialStep();
        if (step == null || mLaunchedFromSettings) {
            mTutorialController.repeatTutorial();
            step = mTutorialController.getCurrentTutorialStep();
        }
        mTipCount = mTutorialController.getTotalTips();
        setStep(step);
    }

    @Override
    public boolean onSearchRequested() {
        // Block search key for all steps.
        return true;
    }

    @Override
    public void onBackPressed() {
        TutorialStep step = mTutorialController.previousStep();
        if (step != null) {
            setStep(step);
        } else if (mLaunchedFromSettings) {
            finish();
        }
    }

    private void advance() {
        mTutorialController.advanceStep();
        TutorialStep step = mTutorialController.getCurrentTutorialStep();
        if (step != null) {
            setStep(step);
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }

    /**
     * Sets the UI to reflect the step.
     */
    private void setStep(TutorialStep step) {
        if (step.getTextResourceId() != 0) {
            if (step.isModelDependentText()) {
                mText.setText(getModelDependentText(step.getTextResourceId()));
            } else {
                mText.setText(step.getTextResourceId());
            }
        } else {
            mText.setText("");
        }
        if (step.getDrawableResourceId() != 0) {
            mImage.setImageResource(step.getDrawableResourceId());
            mImage.setVisibility(View.VISIBLE);
        } else {
            mImage.setVisibility(View.GONE);
        }
        if (step.getBackgroundResourceId() != 0) {
            mFrame.setBackgroundResource(step.getBackgroundResourceId());
        }
        if (step.getTitleResourceId() != 0) {
            mTitle.setText(step.getTitleResourceId());
        }
        View stepView = step.getStepView(this, mStepView);
        mStepView.removeAllViews();
        if (stepView != null) {
            mStepView.addView(stepView);
        }
        int tipNumber = mTutorialController.getTipNumber();
        boolean isLastStep = mTutorialController.isLastStep(step);
        mSteps.setText(getString(R.string.tutorial_steps_number, tipNumber, mTipCount));
        mSteps.setVisibility(step.isTip() ? View.VISIBLE : View.GONE);
        mNextButton.requestFocus();
        mNextButton.setText(isLastStep ? R.string.tutorial_ok : R.string.tutorial_next);
        mBackButton.setVisibility(tipNumber >= 1 ? View.VISIBLE : View.GONE);
    }

    private CharSequence getModelDependentText(int textResourceId) {
        String resourceString = getResources().getString(textResourceId);
        return TextUtils.expandTemplate(resourceString, Build.MODEL);
    }
}
