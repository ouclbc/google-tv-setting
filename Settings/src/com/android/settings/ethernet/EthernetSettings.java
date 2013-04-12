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

import com.android.settings.ethernet.EthernetConfiguration;
import com.android.settings.ethernet.EthernetSettingsDialog;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.INetworkManagementEventObserver;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;

public class EthernetSettings extends SettingsPreferenceFragment {
    private static final String TAG = "EthernetSettings";
    private static final int DIALOG_ETHERNET_SETTINGS = 0;
    private static final String KEY_PROXY_IP = "proxy_ip_settings";
    private static final String KEY_IP_ADDRESS = "ip_address";
    private static final String KEY_ETHERNET_MAC = "ethernet_mac_address";
    private static final String KEY_EMULATOR_IGNORED_SETTINGS[] = {
        KEY_PROXY_IP,
        KEY_IP_ADDRESS,
        KEY_ETHERNET_MAC,
    };

    private BroadcastReceiver mNetworkStatusChangeReceiver;
    private Context mContext;
    private Handler mRefreshHandler;
    private INetworkManagementEventObserver mNMEventObserver;

    private static final int CABLE_UNKNOWN = 0;
    private static final int CABLE_DISCONNECTED = 1;
    private static final int CABLE_CONNECTED = 2;
    private int mCableState = CABLE_UNKNOWN;

    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceActivity's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (KEY_PROXY_IP.equals(preference.getKey())) {
            showDialog(DIALOG_ETHERNET_SETTINGS);
            return true;
        }
        // Let the intents be launched by the Preference manager
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ethernet_settings);

        // Disable settings that are irrelevant / not applicable to emulator
        if (isEmulatorClient()) {
            for (String preferenceName : KEY_EMULATOR_IGNORED_SETTINGS) {
                Preference preferenceSettings = findPreference(preferenceName);
                preferenceSettings.setEnabled(false);
            }
        }

        mContext = findPreference(KEY_PROXY_IP).getContext();
        refreshEthernetSummary();

        mRefreshHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                 refreshEthernetSummary();
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();

        registerEventCallbacks();
    }

    @Override
    public void onPause() {
        super.onPause();

        removeDialog(DIALOG_ETHERNET_SETTINGS);
        unregisterEventCallbacks();
    }

    private void registerEventCallbacks() {
        IntentFilter networkStatusFilter = new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION);
        mNetworkStatusChangeReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                // TODO(jaewan): Use ConnectivityManager.EXTRA_NETWORK_TYPE once we move to jb-mr1
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(
                        ConnectivityManager.EXTRA_NETWORK_INFO);

                if (networkInfo != null &&
                        networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    refreshEthernetSummary();
                }
            }
        };
        mContext.registerReceiver(mNetworkStatusChangeReceiver, networkStatusFilter);

        mNMEventObserver = new INetworkManagementEventObserver.Stub() {
            @Override
            public void interfaceLinkStateChanged(String iface, boolean up) {
                if (SystemProperties.get("ethernet.interface", "eth0").equals(iface)) {
                    mCableState = up ? CABLE_CONNECTED : CABLE_DISCONNECTED;
                    mRefreshHandler.sendEmptyMessage(0);
                }
            }
            public void interfaceStatusChanged(String iface, boolean up) {}
            public void interfaceAdded(String iface) {}
            public void interfaceRemoved(String iface) {}
            public void limitReached(String limitName, String iface) {}
            public void interfaceClassDataActivityChanged(String label, boolean active) {}
        };
        EthernetManager.registerObserver(mNMEventObserver);
    }

    private void unregisterEventCallbacks() {
        mContext.unregisterReceiver(mNetworkStatusChangeReceiver);
        EthernetManager.unregisterObserver(mNMEventObserver);
    }

    private void refreshEthernetSummary() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo status = EthernetManager.getEthernetStatus(manager);
        String ipAddress = null;

        if (status != null && status.isConnected()) {
            String ipV4Address = null;
            String ipV6Address = null;
            try {
                for (NetworkInterface nif : Collections.list(
                        NetworkInterface.getNetworkInterfaces())) {
                    if (!nif.getName().startsWith("eth")) continue;
                    for (InetAddress inetAddress : Collections.list(nif.getInetAddresses())) {
                        if (inetAddress.isLoopbackAddress()) continue;
                        if (inetAddress instanceof Inet4Address) {
                            ipV4Address = inetAddress.getHostAddress().toString();
                        } else if (inetAddress instanceof Inet6Address) {
                            // There can be multiple IPv6 addresses.
                            if (ipV6Address == null) {
                                ipV6Address = inetAddress.getHostAddress().toString();
                            } else {
                                ipV6Address = ipV6Address + ", " +
                                        inetAddress.getHostAddress().toString();
                            }
                        }
                    }
                }
            } catch (SocketException ex) {
                Log.e("Error", ex.toString());
            }

            if (ipV4Address != null) {
                ipAddress = ipV4Address;
                if (ipV6Address != null) {
                    ipAddress = ipAddress + ", " + ipV6Address;
                }
            } else if (ipV6Address != null) {
                ipAddress = ipV6Address;
            }

            if (mCableState == CABLE_UNKNOWN) {
                mCableState = CABLE_CONNECTED;
            }
        } else if (mCableState == CABLE_CONNECTED) {
            ipAddress = getString(R.string.network_ethernet_status_connecting);
        }

        if (ipAddress == null) {
            ipAddress = getString(R.string.network_test_message_failed);
        }
        findPreference(KEY_IP_ADDRESS).setSummary(ipAddress);

        String ethernetMacAddress = EthernetManager.getEthernetMacAddress();
        findPreference(KEY_ETHERNET_MAC).setSummary(
                !TextUtils.isEmpty(ethernetMacAddress) ? ethernetMacAddress
                        : getString(R.string.status_unavailable));
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case DIALOG_ETHERNET_SETTINGS:
                return new EthernetSettingsDialog(mContext,
                        new EthernetSettingsDialog.OnEthernetConfigurationSetListener() {
                            @Override
                            public void onEthernetConfigurationSet(
                                    EthernetConfiguration ethernetConfiguration) {
                                try {
                                    EthernetManager.setEthernetConfiguration(
                                            ethernetConfiguration);
                                } catch (Exception e) {
                                    Toast.makeText(mContext,
                                            R.string.ethernet_settings_save_error,
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                });
        }
        return null;
    }

    public static boolean isEmulatorClient() {
        return SystemProperties.get("ro.kernel.qemu").equals("1");
    }
}
