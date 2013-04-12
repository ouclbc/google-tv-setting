LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

#settings_dir := packages/apps/Settings
#rel_settings_dir := ../../../../../$(settings_dir)

#LOCAL_SRC_FILES := \
#    $(call all-java-files-under,src/) \
#    $(call all-java-files-under,$(rel_settings_dir)/src/)
LOCAL_SRC_FILES := $(call all-java-files-under,src)

#LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res $(settings_dir)/res

LOCAL_JAVA_LIBRARIES := \
    bouncycastle \
    com.google.android.tv.v1 \
    telephony-common

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v4 \
    com.google.tv.ftp.common \
    com.google.tv.preference \
    guava

LOCAL_PACKAGE_NAME := TvSettings
LOCAL_CERTIFICATE := platform
#make Settings not compiled
LOCAL_OVERRIDES_PACKAGES := Settings

#LOCAL_PROGUARD_FLAG_FILES := proguard.flags $(rel_settings_dir)/proguard.flags
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

# Build the tests as well.
include $(call all-makefiles-under, $(LOCAL_PATH))
