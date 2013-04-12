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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.INetworkManagementEventObserver;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.ProxyProperties;
import android.net.RouteInfo;
import android.net.wifi.WifiConfiguration.IpAssignment;
import android.net.wifi.WifiConfiguration.ProxySettings;
import android.os.Environment;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;

/**
 * A class representing the IP configuration of the Ethernet network.
 * This is a mirror of WifiConfiguration and has a couple of
 * cross-class dependencies that should be refactored in a future
 * release.
 *
 * @hide
 */
public class EthernetManager {
    private static final String TAG = "EthernetManager";
    private static final String IP_CONFIG_FILE = Environment.getDataDirectory() +
            "/misc/ethernet/etherconfig.txt";

    private static final byte FILEVERSION = 2;

    private static final String PROXY_SETTINGS_KEY = "proxySettings";
    private static final String PROXY_HOST_KEY = "proxyHost";
    private static final String PROXY_PORT_KEY = "proxyPort";
    private static final String PROXY_EXCLUSION_LIST_KEY = "proxyExclusionList";

    private static final String IP_ASSIGNMENT_KEY = "ipAssignment";
    private static final String LINK_ADDRESS_KEY = "linkAddress";
    private static final String GATEWAY_KEY = "gateway";
    private static final String DNS_KEY = "dns";
    private static final String EOS = "eos";


    /**
     * Writes configuration to a file.
     */
    public static boolean writeEthernetConfiguration(EthernetConfiguration ethernetConfig) {
        DataOutputStream out = null;
        boolean writeToFile = false;

        if (ethernetConfig == null) {
            ethernetConfig = EthernetConfiguration.getDefaultConfiguration();
        }

        try {
            LinkProperties linkProperties = ethernetConfig.getLinkProperties();
            ProxySettings proxySettings = ethernetConfig.getProxySettings();
            IpAssignment ipAssignment = ethernetConfig.getIpAssignment();

            out = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(IP_CONFIG_FILE)));

            out.writeInt(FILEVERSION);

            try {
                switch (proxySettings) {
                    case NONE:
                        out.writeUTF(PROXY_SETTINGS_KEY);
                        out.writeUTF(proxySettings.toString());
                        writeToFile = true;
                        break;
                    case STATIC:
                        out.writeUTF(PROXY_SETTINGS_KEY);
                        out.writeUTF(proxySettings.toString());

                        final ProxyProperties proxyProperties = linkProperties.getHttpProxy();
                        out.writeUTF(PROXY_HOST_KEY);
                        out.writeUTF(proxyProperties.getHost());
                        out.writeUTF(PROXY_PORT_KEY);
                        out.writeInt(proxyProperties.getPort());
                        out.writeUTF(PROXY_EXCLUSION_LIST_KEY);
                        out.writeUTF(proxyProperties.getExclusionList());
                        writeToFile = true;
                        break;
                    case UNASSIGNED:
                        /* Ignore */
                        break;
                    default:
                        Log.e(TAG, "Ignore invalid proxy settings while writing");
                        break;
                }

                switch (ipAssignment) {
                case STATIC:
                    out.writeUTF(IP_ASSIGNMENT_KEY);
                    out.writeUTF(ipAssignment.toString());

                    for (LinkAddress linkAddr : linkProperties.getLinkAddresses()) {
                        out.writeUTF(LINK_ADDRESS_KEY);
                        out.writeUTF(linkAddr.getAddress().getHostAddress());
                        out.writeInt(linkAddr.getNetworkPrefixLength());
                    }
                    for (RouteInfo route : linkProperties.getRoutes()) {
                        out.writeUTF(GATEWAY_KEY);
                        out.writeUTF(route.getGateway().getHostAddress());
                    }
                    for (InetAddress inetAddr : linkProperties.getDnses()) {
                        out.writeUTF(DNS_KEY);
                        out.writeUTF(inetAddr.getHostAddress());
                    }
                    writeToFile = true;
                    break;
                case DHCP:
                    out.writeUTF(IP_ASSIGNMENT_KEY);
                    out.writeUTF(ipAssignment.toString());
                    writeToFile = true;
                    break;
                case UNASSIGNED:
                    /* Ignore */
                    break;
                default:
                    Log.e(TAG, "Ignore invalid ip assignment while writing");
                    break;
                }
            } catch (NullPointerException e) {
                Log.e(TAG, "Failure in writing " + linkProperties + e);
                writeToFile = false;
            }

            out.writeUTF(EOS);
        } catch (IOException e) {
            Log.e(TAG, "Error writing data file");
            writeToFile = false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {}
            }
        }

        return writeToFile;
    }

    public static EthernetConfiguration getEthernetConfiguration(ConnectivityManager manager) {
        EthernetConfiguration ethernetConfig = readEthernetConfiguration();

        if (ethernetConfig.getIpAssignment() == IpAssignment.DHCP) {
            LinkProperties linkProperties = manager.getLinkProperties(
                    ConnectivityManager.TYPE_ETHERNET);

            ethernetConfig = new EthernetConfiguration(
                    ProxySettings.NONE, IpAssignment.DHCP, linkProperties);
        }

        return ethernetConfig;
    }

    public static NetworkInfo getEthernetStatus(ConnectivityManager manager) {
        return manager.getActiveNetworkInfoForUid(ConnectivityManager.TYPE_ETHERNET);
    }

    /**
     * Reads configuration from file.
     */
    public static EthernetConfiguration readEthernetConfiguration() {
        DataInputStream in = null;
        EthernetConfiguration result = null;

        try {
            ProxySettings proxySettings = ProxySettings.UNASSIGNED;
            String proxyHost = null;
            int proxyPort = 0;
            String proxyExclusionList = null;

            IpAssignment ipAssignment = IpAssignment.UNASSIGNED;
            LinkProperties linkProperties = new LinkProperties();

            in = new DataInputStream(new BufferedInputStream(
                    new FileInputStream(IP_CONFIG_FILE)));

            if (in.readInt() != FILEVERSION) {
                Log.e(TAG, "Bad version on IP configuration file, ignore read");
                return null;
            }

            while (true) {
                int id = -1;
                String key = in.readUTF();

                try {
                    if (key.equals(PROXY_SETTINGS_KEY)) {
                        proxySettings = ProxySettings.valueOf(in.readUTF());
                    } else if (key.equals(PROXY_HOST_KEY)) {
                        proxyHost = in.readUTF();
                    } else if (key.equals(PROXY_PORT_KEY)) {
                        proxyPort = in.readInt();
                    } else if (key.equals(PROXY_EXCLUSION_LIST_KEY)) {
                        proxyExclusionList = in.readUTF();
                    } else if (key.equals(IP_ASSIGNMENT_KEY)) {
                        ipAssignment = IpAssignment.valueOf(in.readUTF());
                    } else if (key.equals(LINK_ADDRESS_KEY)) {
                        LinkAddress linkAddr = new LinkAddress(
                                NetworkUtils.numericToInetAddress(in.readUTF()),
                                in.readInt());
                        linkProperties.addLinkAddress(linkAddr);
                    } else if (key.equals(GATEWAY_KEY)) {
                        linkProperties.addRoute(new RouteInfo(
                                NetworkUtils.numericToInetAddress(in.readUTF())));
                    } else if (key.equals(DNS_KEY)) {
                        linkProperties.addDns(
                                NetworkUtils.numericToInetAddress(in.readUTF()));
                    } else if (key.equals(EOS)) {
                        break;
                    } else {
                        Log.e(TAG, "Ignore unknown key " + key
                              + "while reading");
                    }
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Ignore invalid address while reading" + e);
                }
            }

            // simple verification of inputs: if there is no configuration,
            // or an illegal value is present, return null (which will cause
            // DHCP to be used).
            if (proxySettings == ProxySettings.STATIC || ipAssignment == IpAssignment.STATIC) {
                if (proxySettings == ProxySettings.STATIC) {
                    linkProperties.setHttpProxy(
                            new ProxyProperties(proxyHost, proxyPort, proxyExclusionList));
                }
                result = new EthernetConfiguration(proxySettings, ipAssignment, linkProperties);
            }
        } catch (EOFException ignore) {
        } catch (IOException e) {
            Log.e(TAG, "Error parsing configuration" + e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {}
            }
        }

        if (result == null) {
            result = EthernetConfiguration.getDefaultConfiguration();
        }
        return result;
    }


    public static boolean setEthernetConfiguration(EthernetConfiguration ethernetConfig) {
        boolean result = writeEthernetConfiguration(ethernetConfig);

        if (result) {
            try {
                final String interfaceName = SystemProperties.get("ethernet.interface", "eth0");

                IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
                INetworkManagementService service =
                        INetworkManagementService.Stub.asInterface(b);

                service.setInterfaceDown(interfaceName);
                service.setInterfaceUp(interfaceName);
            } catch (RemoteException ex) {
                Log.w(TAG, "Exception getting ethernet config: " + ex);
            }
        }

        return result;
    }

    public static String getEthernetMacAddress() {
        String macAddr = "00:00:00:00:00:00";

        try {
            final String interfaceName = SystemProperties.get("ethernet.interface", "eth0");

            IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
            INetworkManagementService service =
                    INetworkManagementService.Stub.asInterface(b);
            macAddr = service.getInterfaceConfig(interfaceName).getHardwareAddress();
        } catch (RemoteException ex) {
            Log.w(TAG, "Exception getting ethernet config: " + ex);
        }

        return macAddr;
    }

    public static void registerObserver(INetworkManagementEventObserver observer) {
        try {
            IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
            INetworkManagementService service =
                    INetworkManagementService.Stub.asInterface(b);
            service.registerObserver(observer);

        } catch (RemoteException ex) {
            Log.w(TAG, "Could not register INetworkManagementEventObserver " + ex);
        }
    }

    public static void unregisterObserver(INetworkManagementEventObserver observer) {
        try {
            IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
            INetworkManagementService service =
                    INetworkManagementService.Stub.asInterface(b);
            service.unregisterObserver(observer);

        } catch (RemoteException ex) {
            Log.w(TAG, "Could not unregister INetworkManagementEventObserver " + ex);
        }
    }
}
