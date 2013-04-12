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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.View;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for tutorial steps.
 *
 * @author justinkoh@google.com (Justin Koh)
 */
final class TutorialController {

    /**
     * XML parsing constants
     */
    private static final String TAG_STEP = "step";

    private static final String LOG_TAG = "TutorialController";

    /**
     * Tutorial steps are stored individually by key.
     * <p>
     * These are stored on an account basis.
     */
    private static final String PREF_KEY_TUTORIAL_PREFIX = "tutorial_";

    public static final int NOT_SET = -1;

    private final SharedPreferences mPreferences;

    private Context mContext;

    private List<TutorialStep> mSteps;

    private int mSetupVersion;

    /**
     * Constructor.
     */
    TutorialController(Context context) {
        this(context, Settings.Secure.getInt(context.getContentResolver(),
                TvSettings.Secure.SETUP_VERSION, 0));
    }

    /**
     * Constructor which takes a specific setup version.
     */
    TutorialController(Context context, int setupVersion) {
        mContext = context;
        mSteps = new ArrayList<TutorialStep>();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mSetupVersion = setupVersion;
        loadTutorialSteps();
    }

    void destroy() {
        mContext = null;
        mSteps.clear();
    }

    /**
     * Loads the tutorial steps.
     * <p>
     * These are loaded from an XML resource.
     */
    private void loadTutorialSteps() {
        mSteps.clear();
        try {
            XmlResourceParser parser = mContext.getResources().getXml(R.xml.tutorial_steps);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if (TAG_STEP.equals(tagName)) {
                        TypedArray a = mContext.obtainStyledAttributes(attrs,
                                R.styleable.TutorialStep);
                        String key = a.getString(R.styleable.TutorialStep_key);
                        TutorialStep step = TutorialStep.create(key);
                        step.setDrawableResourceId(a.getResourceId(
                                R.styleable.TutorialStep_image, 0));
                        step.setTextResourceId(a.getResourceId(
                                R.styleable.TutorialStep_text, 0));
                        step.setTitleResourceId(a.getResourceId(
                                R.styleable.TutorialStep_titleResource, 0));
                        step.setTitleImageResourceId(a.getResourceId(
                                R.styleable.TutorialStep_titleImage, 0));
                        step.setBackgroundResourceId(a.getResourceId(
                                R.styleable.TutorialStep_backgroundImage, 0));
                        step.setModelDependentText(a.getBoolean(
                                R.styleable.TutorialStep_modelDependentText, false));
                        step.setTip(a.getBoolean(R.styleable.TutorialStep_tip, false));
                        int minSetupVersion = a.getInteger(
                                R.styleable.TutorialStep_minSetupVersion, NOT_SET);
                        int maxSetupVersion = a.getInteger(
                                R.styleable.TutorialStep_maxSetupVersion, NOT_SET);

                        boolean addStep = true;

                        if (minSetupVersion != NOT_SET) {
                            addStep = mSetupVersion >= minSetupVersion;
                        }
                        if (maxSetupVersion != NOT_SET) {
                            addStep = mSetupVersion <= maxSetupVersion;
                        }

                        if (addStep) {
                            mSteps.add(step);
                        }
                        a.recycle();
                    }
                }
            }
        } catch (XmlPullParserException e) {
            Log.w(LOG_TAG, "Could not parse tutorial steps", e);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Could not read tutorial steps", e);
        }
    }

    /**
     * Gets the current tutorial step, or {@code null}.
     */
    TutorialStep getCurrentTutorialStep() {
        for (TutorialStep step : mSteps) {
            if (!isTutorialStepComplete(step.getKey())) {
                return step;
            }
        }
        return null;
    }

    /**
     * Advances to the next step of the tutorial and returns {@code true} if
     * there are more steps after.
     */
    boolean advanceStep() {
        TutorialStep step = null;
        boolean hasMore = true;
        for (int index = 0; index < mSteps.size(); ++index) {
            if (!isTutorialStepComplete(mSteps.get(index).getKey())) {
                step = mSteps.get(index);
                hasMore = (index < mSteps.size() - 2);
                break;
            }
        }
        if (step == null) {
            return false;
        }
        setTutorialStepComplete(step.getKey(), true);
        step.onAdvanceStep(mContext);
        return hasMore;
    }

    /**
     * Backs up one step in the tutorial and returns the current step.
     */
    TutorialStep previousStep() {
        TutorialStep step = null;
        for (int index = 0; index < mSteps.size(); ++index) {
            if (!isTutorialStepComplete(mSteps.get(index).getKey())) {
                if (index == 0) {
                    return null;
                }
                step = mSteps.get(index - 1);
                // Unset this as complete.
                setTutorialStepComplete(step.getKey(), false);
                break;
            }
        }
        return step;
    }

    /**
     * Skips the tutorial by setting all the steps completed.
     */
    void skipTutorial() {
        batchEdit(mPreferences, true);
    }

    /**
     * Repeats the tutorial by setting all steps uncompleted.
     */
    void repeatTutorial() {
        batchEdit(mPreferences, false);
    }

    /**
     * Returns {@code true} if the step is the last step.
     */
    boolean isLastStep(TutorialStep step) {
        int index = mSteps.indexOf(step);
        return (index == mSteps.size() - 1);
    }

    /**
     * Gets the current tip number.
     */
    int getTipNumber() {
        int returnNumber = 0;
        for (TutorialStep step : mSteps) {
            if (step.isTip()) {
                returnNumber++;
            }
            if (!isTutorialStepComplete(step.getKey())) {
                return returnNumber;
            }
        }
        return returnNumber;
    }

    int getTotalTips() {
        int returnNumber = 0;
        for (TutorialStep step : mSteps) {
            if (step.isTip()) {
                returnNumber++;
            }
        }
        return returnNumber;
    }

    void setTutorialStepComplete(String key, boolean value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(getTutorialStepPreferenceKey(key), value);
        editor.apply();
    }

    void setTutorialStepsComplete(List<String> keys, boolean value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        for (String key : keys) {
            editor.putBoolean(getTutorialStepPreferenceKey(key), value);
        }
        editor.apply();
    }

    /**
     * Batch edits the tutorial steps.
     */
    private void batchEdit(SharedPreferences preferences, boolean complete) {
        List<String> keys = new ArrayList<String>();
        for (TutorialStep step : mSteps) {
            keys.add(step.getKey());
        }
        setTutorialStepsComplete(keys, complete);
    }

    private boolean isTutorialStepComplete(String key) {
        return mPreferences.getBoolean(getTutorialStepPreferenceKey(key), false);
    }

    private static final String getTutorialStepPreferenceKey(String key) {
        return PREF_KEY_TUTORIAL_PREFIX + key;
    }
}
