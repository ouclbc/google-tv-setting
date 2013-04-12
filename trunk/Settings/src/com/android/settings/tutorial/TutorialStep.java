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

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

class TutorialStep {

    /**
     * Special case key for the privacy confirmation step.
     */
    static final String KEY_PRIVACY = "privacy_confirm";

    /**
     * Text resource to show as the body of the step.
     */
    private int mTextResId;

    /**
     * Text resource to show as the title of the step.
     */
    private int mTitleResId;

    /**
     * Drawable to use as the title image of the step.
     */
    private int mTitleImageResId;

    /**
     * Drawable to use as the main image of the step.
     */
    private int mDrawableResId;

    /**
     * Drawable to use as the background of the step.
     * <p>
     * If this is not specified, will just use default image.
     */
    private int mBackgroundResId;

    /**
     * Key identifying the step.
     * <p>
     * This is unique.
     */
    private String mKey;

    /**
     * If {@code true}, text string needs to insert the model of the device.
     */
    private boolean mModelDependentText;

    /**
     * If {@code true}, this is a tip. Otherwise, it's a step (such as the
     * intro and final screens.
     */
    private boolean mTip;

    /**
     * Setup version of a version specific tip.
     * <p>
     * If this is not equal to not set, then this tip will only be shown to users who upgraded
     * from that specific version or later.
     */
    private int mMinSetupVersion;

    /**
     * Setup version of a version specific tip.
     * <p>
     * If this is not equal to not set, then this tip will only be shown to users who upgraded
     * from that specific version or earlier.
     */
    private int mMaxSetupVersion;

    public String getKey() {
        return mKey;
    }

    void setTitleResourceId(int titleResId) {
        mTitleResId = titleResId;
    }

    public int getTitleResourceId() {
        return mTitleResId;
    }

    void setTitleImageResourceId(int titleImageResId) {
        mTitleImageResId = titleImageResId;
    }

    public int getTitleImageResourceId() {
        return mTitleImageResId;
    }

    void setTextResourceId(int textResId) {
        mTextResId = textResId;
    }

    public int getTextResourceId() {
        return mTextResId;
    }

    void setDrawableResourceId(int drawableResId) {
        mDrawableResId = drawableResId;
    }

    public int getDrawableResourceId() {
        return mDrawableResId;
    }

    void setBackgroundResourceId(int backgroundResId) {
        mBackgroundResId = backgroundResId;
    }

    public int getBackgroundResourceId() {
        return mBackgroundResId;
    }

    void setModelDependentText(boolean modelDependentText) {
        mModelDependentText = modelDependentText;
    }

    public boolean isModelDependentText() {
        return mModelDependentText;
    }

    void setTip(boolean tip) {
        mTip = tip;
    }

    public boolean isTip() {
        return mTip;
    }

    void setMinSetupVersion(int minSetupVersion) {
        mMinSetupVersion = minSetupVersion;
    }

    public int getMinSetupVersion() {
        return mMinSetupVersion;
    }

    void setMaxSetupVersion(int maxSetupVersion) {
        mMaxSetupVersion = maxSetupVersion;
    }

    public int getMaxSetupVersion() {
        return mMaxSetupVersion;
    }

    /**
     * Returns a view that is embedded in the tutorial, or {@code null}.
     */
    public View getStepView(Context context, ViewGroup parent) {
        return null;
    }

    /**
     * Called when the controller advances past this step.
     */
    public void onAdvanceStep(Context context) {}

    public static TutorialStep create(String key) {
        TutorialStep step;
        if (TutorialStep.KEY_PRIVACY.equals(key)) {
            step = new TutorialPrivacyStep();
        } else {
            step = new TutorialStep();
        }
        step.mKey = key;
        return step;
    }
}
