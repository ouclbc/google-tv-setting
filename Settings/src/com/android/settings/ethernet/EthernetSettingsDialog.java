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

import com.android.settings.ProxySelector;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.ethernet.EthernetConfiguration;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.NetworkUtils;
import android.net.ProxyProperties;
import android.net.RouteInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.IpAssignment;
import android.net.wifi.WifiConfiguration.ProxySettings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dialog to set Ethernet setting address.
 *
 * TODO(mmikhailov) refactor to use fragment
 */
public class EthernetSettingsDialog extends AlertDialog {

    public interface OnEthernetConfigurationSetListener {
        void onEthernetConfigurationSet(EthernetConfiguration ethernetConfiguration);
    }

    /* These 2 booleans allow OEMs to enable/disable functionality. */
    private static final boolean ENABLE_IP_CONFIG = true;
    private static final boolean ENABLE_PROXY = true;

    /* These values come from "wifi_proxy_settings" resource array */
    public static final int PROXY_NONE = 0;
    public static final int PROXY_STATIC = 1;

    /* These values come from "network_ip_settings" resource array */
    private static final int DHCP = 0;
    private static final int STATIC_IP = 1;

    // Matches blank input, ips, and domain names
    private static final String HOSTNAME_REGEXP =
            "^$|^[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*(\\.[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*)*$";
    private static final Pattern HOSTNAME_PATTERN;
    private static final String EXCLLIST_REGEXP =
            "$|^(.?[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*(\\.[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*)*)+" +
            "(,(.?[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*(\\.[a-zA-Z0-9]+(\\-[a-zA-Z0-9]+)*)*))*$";
    private static final Pattern EXCLLIST_PATTERN;
    static {
        HOSTNAME_PATTERN = Pattern.compile(HOSTNAME_REGEXP);
        EXCLLIST_PATTERN = Pattern.compile(EXCLLIST_REGEXP);
    }

    private View mView;

    private Spinner mProxySettingsSpinner;
    private TextView mProxyHostView;
    private TextView mProxyPortView;
    private TextView mProxyExclusionListView;

    private Spinner mIpSettingsSpinner;
    private TextView mIpAddressView;
    private TextView mGatewayView;
    private TextView mNetworkPrefixLengthView;
    private TextView mDns1View;
    private TextView mDns2View;

    private final TextWatcher textWatcher = new TextWatcherImpl();

    private final OnEthernetConfigurationSetListener mCallBack;

    // Indicates if we are in the process of setting up values and should not validate them yet.
    private boolean mSettingUpValues;

    public EthernetSettingsDialog(Context context, OnEthernetConfigurationSetListener callBack) {
        super(context);

        mCallBack = callBack;

        setTitle(context.getText(R.string.ethernet_settings_dialog_title));

        LayoutInflater factory = LayoutInflater.from(context);
        mView = factory.inflate(R.layout.ethernet_settings_view, null);
        setView(mView);

        setupViewControls();

        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.set),
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (DialogInterface.BUTTON_POSITIVE == which) {
                            if (mCallBack != null && !Utils.isMonkeyRunning()) {
                                mCallBack.onEthernetConfigurationSet(
                                        getEthernetConfigurationFromEdits());
                            }
                        }
                    }
                });
        setButton(DialogInterface.BUTTON_NEGATIVE,
                context.getText(R.string.cancel), (OnClickListener) null);
        setIcon(0);
    }

    private void setupViewControls() {
        setupProxyViewControls();
        setupStaticIpViewControls();
    }

    private void setupProxyViewControls() {
        if (!ENABLE_PROXY) {
            return;
        }

        mView.findViewById(R.id.proxy_settings_fields).setVisibility(View.VISIBLE);

        mProxySettingsSpinner = (Spinner) mView.findViewById(R.id.proxy_settings);
        mProxySettingsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showProxyFields();
                enableSubmitIfAppropriate();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mProxySettingsSpinner.setVisibility(View.VISIBLE);

        mProxyHostView = (TextView) mView.findViewById(R.id.proxy_hostname);
        mProxyHostView.addTextChangedListener(textWatcher);

        mProxyPortView = (TextView) mView.findViewById(R.id.proxy_port);
        mProxyPortView.addTextChangedListener(textWatcher);

        mProxyExclusionListView = (TextView) mView.findViewById(R.id.proxy_exclusionlist);
        mProxyExclusionListView.addTextChangedListener(textWatcher);
    }

    private void setupStaticIpViewControls() {
        if (!ENABLE_IP_CONFIG) {
            return;
        }

        mView.findViewById(R.id.ip_fields).setVisibility(View.VISIBLE);

        // IP settings DHCP/Static
        mIpSettingsSpinner = (Spinner) mView.findViewById(R.id.ip_settings);
        mIpSettingsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showIpConfigFields();
                enableSubmitIfAppropriate();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // IP address
        mIpAddressView = (TextView) mView.findViewById(R.id.ipaddress);
        mIpAddressView.addTextChangedListener(textWatcher);

        // Gateway
        mGatewayView = (TextView) mView.findViewById(R.id.gateway);
        mGatewayView.addTextChangedListener(textWatcher);

        // Network prefix length
        mNetworkPrefixLengthView = (TextView) mView.findViewById(
                R.id.network_prefix_length);
        mNetworkPrefixLengthView.addTextChangedListener(textWatcher);

        // DNS 1
        mDns1View = (TextView) mView.findViewById(R.id.dns1);
        mDns1View.addTextChangedListener(textWatcher);

        // DNS 2
        mDns2View = (TextView) mView.findViewById(R.id.dns2);
        mDns2View.addTextChangedListener(textWatcher);
    }

    private void showIpConfigFields() {
        if (ENABLE_IP_CONFIG && mIpSettingsSpinner.getSelectedItemPosition() == STATIC_IP) {
            mView.findViewById(R.id.staticip).setVisibility(View.VISIBLE);
        } else {
            mView.findViewById(R.id.staticip).setVisibility(View.GONE);
        }
    }

    private void showProxyFields() {
        if (ENABLE_PROXY && mProxySettingsSpinner.getSelectedItemPosition() == PROXY_STATIC) {
            mView.findViewById(R.id.proxy_fields).setVisibility(View.VISIBLE);
        } else {
            mView.findViewById(R.id.proxy_fields).setVisibility(View.GONE);
        }
    }

    public void setEthernetConfiguration(EthernetConfiguration ethernetConfiguration) {
        mSettingUpValues = true;

        if (ENABLE_PROXY) {
            mProxySettingsSpinner.setSelection(ethernetConfiguration != null &&
                    ethernetConfiguration.getProxySettings() == ProxySettings.STATIC
                    ? PROXY_STATIC : PROXY_NONE);
            mProxyHostView.setText(null);
            mProxyPortView.setText(null);
            mProxyExclusionListView.setText(null);
        }

        if (ENABLE_IP_CONFIG) {
            mIpSettingsSpinner.setSelection(ethernetConfiguration != null &&
                    ethernetConfiguration.getIpAssignment() == IpAssignment.STATIC
                    ? STATIC_IP : DHCP);
            mIpAddressView.setText(null);
            mGatewayView.setText(null);
            mNetworkPrefixLengthView.setText(null);
            mDns1View.setText(null);
            mDns2View.setText(null);
        }

        if (ethernetConfiguration != null) {
            LinkProperties linkProperties = ethernetConfiguration.getLinkProperties();

            if (ENABLE_PROXY) {
                final ProxyProperties proxyProperties = linkProperties.getHttpProxy();
                if (proxyProperties != null) {
                    mProxyHostView.setText(proxyProperties.getHost());
                    mProxyPortView.setText(String.valueOf(proxyProperties.getPort()));
                    mProxyExclusionListView.setText(proxyProperties.getExclusionList());
                }
            }

            if (ENABLE_IP_CONFIG) {
                for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                    mIpAddressView.setText(linkAddress.getAddress().getHostAddress());
                    mNetworkPrefixLengthView.setText(Integer.toString(linkAddress
                            .getNetworkPrefixLength()));
                    break;
                }
                for (RouteInfo gateway : linkProperties.getRoutes()) {
                    mGatewayView.setText(gateway.getGateway().getHostAddress());
                    break;
                }
                boolean first = true;
                for (InetAddress dns : linkProperties.getDnses()) {
                    if (first) {
                        mDns1View.setText(dns.getHostAddress());
                        first = false;
                    } else {
                        mDns2View.setText(dns.getHostAddress());
                        break;
                    }
                }
            }
        }

        mSettingUpValues = false;
        showIpConfigFields();
        showProxyFields();
        enableSubmitIfAppropriate();
    }

    public static boolean isValidIpAddress(String ipAddress, boolean allowEmptyValue) {
        if (ipAddress == null || ipAddress.length() == 0) {
            return allowEmptyValue;
        }

        try {
            InetAddress.getByName(ipAddress);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Validates string with proxy exclusion list.
     *
     * @param exclList string to validate.
     * @return resource id of error message string or 0 if valid.
     */
    public static int validateProxyExclusionList(String exclList) {
        Matcher listMatch = EXCLLIST_PATTERN.matcher(exclList);
        return !listMatch.matches() ? R.string.proxy_error_invalid_exclusion_list : 0;
    }

    private boolean validateProxyFields() {
        if (!ENABLE_PROXY) {
            return true;
        }

        final Context context = getContext();
        boolean errors = false;

        if (isValidIpAddress(mProxyHostView.getText().toString(), false)) {
            mProxyHostView.setError(null);
        } else {
            mProxyHostView.setError(
                    context.getString(R.string.wifi_ip_settings_invalid_ip_address));
            errors = true;
        }

        int port = -1;
        try {
            port = Integer.parseInt(mProxyPortView.getText().toString());
            mProxyPortView.setError(null);
        } catch (NumberFormatException e) {
            // Intentionally left blank
        }
        if (port < 0) {
            mProxyPortView.setError(context.getString(R.string.proxy_error_invalid_port));
            errors = true;
        }

        final String exclusionList = mProxyExclusionListView.getText().toString();
        final int listResult = validateProxyExclusionList(exclusionList);
        if (listResult == 0) {
            mProxyExclusionListView.setError(null);
        } else {
            mProxyExclusionListView.setError(context.getString(listResult));
            errors = true;
        }

        return !errors;
    }

    private boolean validateIpConfigFields() {
        if (!ENABLE_IP_CONFIG) {
            return true;
        }

        final Context context = getContext();
        boolean errors = false;

        if (isValidIpAddress(mIpAddressView.getText().toString(), false)) {
            mIpAddressView.setError(null);
        } else {
            mIpAddressView.setError(
                    context.getString(R.string.wifi_ip_settings_invalid_ip_address));
            errors = true;
        }

        int networkPrefixLength = -1;
        try {
            networkPrefixLength = Integer.parseInt(mNetworkPrefixLengthView.getText().toString());
            mNetworkPrefixLengthView.setError(null);
        } catch (NumberFormatException e) { }
        if (networkPrefixLength < 0 || networkPrefixLength > 32) {
            mNetworkPrefixLengthView.setError(
                    context.getString(R.string.wifi_ip_settings_invalid_network_prefix_length));
            errors = true;
        }

        if (isValidIpAddress(mGatewayView.getText().toString(), false)) {
            mGatewayView.setError(null);
        } else {
            mGatewayView.setError(context.getString(R.string.wifi_ip_settings_invalid_gateway));
            errors = true;
        }

        if (isValidIpAddress(mDns1View.getText().toString(), false)) {
            mDns1View.setError(null);
        } else {
            mDns1View.setError(context.getString(R.string.wifi_ip_settings_invalid_dns));
            errors = true;
        }

        if (isValidIpAddress(mDns2View.getText().toString(), true)) {
            mDns2View.setError(null);
        } else {
            mDns2View.setError(context.getString(R.string.wifi_ip_settings_invalid_dns));
            errors = true;
        }

        return !errors;
    }

    private EthernetConfiguration getEthernetConfigurationFromEdits() {
        final LinkProperties linkProperties = new LinkProperties();

        final ProxySettings proxySettings =
                ENABLE_PROXY && mProxySettingsSpinner.getSelectedItemPosition() == PROXY_STATIC
                        ? ProxySettings.STATIC : ProxySettings.NONE;

        if (proxySettings == ProxySettings.STATIC) {
            try {
                linkProperties.setHttpProxy(new ProxyProperties(
                        mProxyHostView.getText().toString(),
                        Integer.parseInt(mProxyPortView.getText().toString()),
                        mProxyExclusionListView.getText().toString()));
            } catch (IllegalArgumentException e) {
                // Should not happen if validations are done right
                throw new RuntimeException(e);
            }
        }

        final IpAssignment ipAssignment =
                ENABLE_IP_CONFIG && mIpSettingsSpinner.getSelectedItemPosition() == STATIC_IP
                        ? IpAssignment.STATIC : IpAssignment.DHCP;

        if (ipAssignment == IpAssignment.STATIC) {
            try {
                linkProperties.addLinkAddress(new LinkAddress(
                        NetworkUtils.numericToInetAddress(mIpAddressView.getText().toString()),
                        Integer.parseInt(mNetworkPrefixLengthView.getText().toString())));
                linkProperties.addRoute(new RouteInfo(
                        NetworkUtils.numericToInetAddress(mGatewayView.getText().toString())));
                linkProperties.addDns(
                        NetworkUtils.numericToInetAddress(mDns1View.getText().toString()));
                if (mDns2View.getText().length() > 0) {
                    linkProperties.addDns(
                            NetworkUtils.numericToInetAddress(mDns2View.getText().toString()));
                }
            } catch (IllegalArgumentException e) {
                // Should not happen if validations are done right
                throw new RuntimeException(e);
            }
        }

        return new EthernetConfiguration(proxySettings, ipAssignment, linkProperties);
    }

    private boolean isProxyFieldsValid() {
        if (ENABLE_PROXY && mProxySettingsSpinner.getSelectedItemPosition() == PROXY_STATIC) {
            return validateProxyFields();
        }
        return true;
    }

    private boolean isIpFieldsValid() {
        if (ENABLE_IP_CONFIG && mIpSettingsSpinner.getSelectedItemPosition() == STATIC_IP) {
            return validateIpConfigFields();
        }
        return true;
    }

    private void enableSubmitIfAppropriate() {
        setPositiveButtonEnabled(isProxyFieldsValid() && isIpFieldsValid());
    }

    private void setPositiveButtonEnabled(boolean enabled) {
        getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(enabled);
    }

    private class TextWatcherImpl implements TextWatcher {
        @Override
        public void afterTextChanged(Editable s) {
            // Do not validate fields while values are being setted up.
            if (!mSettingUpValues) {
                enableSubmitIfAppropriate();
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
