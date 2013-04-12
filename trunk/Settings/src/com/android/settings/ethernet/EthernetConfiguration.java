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

package com.android.settings.ethernet;

import android.net.LinkProperties;
import android.net.wifi.WifiConfiguration.IpAssignment;
import android.net.wifi.WifiConfiguration.ProxySettings;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * A class representing the IP configuration of the Ethernet network.
 * This is a mirror of WifiConfiguration and has a couple of
 * cross-class dependencies that should be refactored in a future
 * release.
 */
public class EthernetConfiguration implements Parcelable {
    private static final String TAG = "EthernetConfiguration";

    private ProxySettings mProxySettings;
    private IpAssignment mIpAssignment;
    private LinkProperties mLinkProperties;

    public EthernetConfiguration(ProxySettings proxySettings,
            IpAssignment initialAssignment,
            LinkProperties linkProperties) {
        mProxySettings = proxySettings;
        mIpAssignment = initialAssignment;
        mLinkProperties = linkProperties;
    }

    public static EthernetConfiguration getDefaultConfiguration() {
        return new EthernetConfiguration(
                ProxySettings.NONE, IpAssignment.DHCP, new LinkProperties());
    }

    public ProxySettings getProxySettings() {
        return mProxySettings;
    }

    public LinkProperties getLinkProperties() {
        return mLinkProperties;
    }

    public IpAssignment getIpAssignment() {
        return mIpAssignment;
    }

    public String toString() {
        return mProxySettings + " " + mIpAssignment + " " + mLinkProperties;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mProxySettings.name());
        dest.writeString(mIpAssignment.name());
        dest.writeParcelable(mLinkProperties, flags);
    }

    public static final Creator<EthernetConfiguration> CREATOR =
            new Creator<EthernetConfiguration>() {
        public EthernetConfiguration createFromParcel(Parcel in) {
            EthernetConfiguration config = new EthernetConfiguration(
                    ProxySettings.valueOf(in.readString()),
                    IpAssignment.valueOf(in.readString()),
                    (LinkProperties) in.readParcelable(null));
            return config;
        }

        public EthernetConfiguration[] newArray(int size) {
            return new EthernetConfiguration[size];
        }
    };
}
