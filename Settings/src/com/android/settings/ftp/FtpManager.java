// Copyright 2011 Google Inc. All Rights Reserved.

package com.android.settings.ftp;

import com.android.settings.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.util.Log;
import android.widget.Toast;

import com.google.tv.ftp.common.FtpServiceProxy;

import java.util.Random;

/*
 * FTP service manager
 *
 * It provides a simple UI to control FTP service using a check box.
 * When the check box is set to true, the FTP service tries to start.
 * When it is to false, the FTP service stops.
 */
public class FtpManager implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "FtpManager";
    private static final boolean DEBUG = false;

    private static final String sFtpId = "ftp";
    // TODO(youngsang): In the current implementation, the FTP port is fixed to 2121.
    // But, other apps may already occupy the same port.
    // Therefore, this part need to be changed. The two following ways can be candidates.
    // One is to automatically find an available port number.
    // Another is to make a UI for a user to manually set the port number.
    private static final int sFtpPort = 2121;
    // When max user is only 1, file transfer cannot be performed in FileZilla.
    // So it is set to 2.
    private static final int sFtpMaxLoginUser = 2;
    private static final String sFtpHomeDir =
            Environment.getExternalStorageDirectory().getAbsolutePath();

    private static final int sShowFtpStarted = 1;
    private static final int sShowFtpStarting = 2;
    private static final int sShowFtpStopped = 3;
    private static final int sShowFtpStoping = 4;
    private static final int sShowFtpPreparing = 5;

    // FTP Service
    private FtpServiceProxy mFtpServiceProxy = null;
    private boolean mFtpServiceBound;

    private Context mContext;
    private CheckBoxPreference mFtpCheckBox;

    /**
     * UI Handler
     *
     * It handles incoming messages from FtpService as well.
     */
    class FtpUiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FtpServiceProxy.MSG_FTP_STATE_CHANGE:
                    updateFtpCheckBox();
                    break;
                case FtpServiceProxy.MSG_FTP_SERVICE_DISCONNECTED:
                    start();
                    resume();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    private FtpUiHandler mFtpUiHandler = new FtpUiHandler();

    // The handler is for working with the FTP services, which can be slow.
    private Handler mAsyncFtpHandler;

    public FtpManager(Context context, CheckBoxPreference checkbox) {
        mContext = context;
        mFtpCheckBox = checkbox;

        HandlerThread thread = new HandlerThread("Thread for handling a FTP service");
        thread.start();
        mAsyncFtpHandler = new Handler(thread.getLooper());

        mFtpCheckBox.setOnPreferenceChangeListener(this);
        mFtpServiceProxy = new FtpServiceProxy(mContext, mFtpUiHandler);
    }

    public void start() {
        mFtpServiceProxy.bind();
    }

    public void resume() {
        // When resume runs, FtpService may not be ready.
        // If it is not ready, mFtpCheckBox will be disabled until it is ready.
        if (mFtpServiceProxy.isReady()) {
            updateFtpCheckBox();
        } else {
            switchFtpCheckBox(sShowFtpPreparing);
        }
    }

    public void stop() {
        if (DEBUG) Log.d(TAG, "onStop");

        mFtpServiceProxy.unbind();
    }

    private void updateFtpCheckBox() {
        // Discards intermediate states to implement simply.
        // ex) sShowFtpStarting, sShowFtpStoping, sShowFtpPreparing
        if (mFtpServiceProxy.isServerRunning()) {
            switchFtpCheckBox(sShowFtpStarted);
        } else {
            switchFtpCheckBox(sShowFtpStopped);
        }
    }

    private void switchFtpCheckBox(final int type) {
        mFtpUiHandler.post(new Runnable() {
            @Override
            public void run() {
                switchFtpCheckBoxAsync(type);
            }
        });
    }

    private void switchFtpCheckBoxAsync(int type) {
        if (DEBUG) Log.d(TAG, "switchDisplay " + type);
        switch (type) {
            case sShowFtpStarted:
                mFtpCheckBox.setChecked(true);
                mFtpCheckBox.setEnabled(true);
                mFtpCheckBox.setSummary(
                        "ftp://" + mFtpServiceProxy.getId() +
                        ":" + mFtpServiceProxy.getPassword() +
                        "@" + mFtpServiceProxy.getLocalIpAddress() +
                        ":" + mFtpServiceProxy.getPort());
                break;
            case sShowFtpStarting:
                mFtpCheckBox.setChecked(false);
                mFtpCheckBox.setEnabled(false);
                mFtpCheckBox.setSummary(R.string.ftp_status_starting);
                break;
            case sShowFtpStopped:
                mFtpCheckBox.setChecked(false);
                mFtpCheckBox.setEnabled(true);
                mFtpCheckBox.setSummary(R.string.ftp_status_stopped);
                break;
            case sShowFtpStoping:
                mFtpCheckBox.setChecked(true);
                mFtpCheckBox.setEnabled(false);
                mFtpCheckBox.setSummary(R.string.ftp_status_stopping);
                break;
            case sShowFtpPreparing:
                mFtpCheckBox.setChecked(false);
                mFtpCheckBox.setEnabled(false);
                mFtpCheckBox.setSummary(R.string.ftp_status_preparing);
                break;
        }
    }

    private void switchFtpStatus(final boolean on) {
        // Things to do on the UI thread
        if (on) {
            switchFtpCheckBox(sShowFtpStarting);
        } else {
            switchFtpCheckBox(sShowFtpStoping);
        }

        // Things to do on the FTP thread
        mAsyncFtpHandler.post(new Runnable() {
            @Override
            public void run() {
                if (on) {
                    String ftpId = sFtpId;
                    String ftpPassword = Integer.toString(new Random().nextInt(900000) + 100000);
                    String ftpHomeDir = sFtpHomeDir;
                    int ftpPort = sFtpPort;
                    int ftpMaxLoginUser = sFtpMaxLoginUser;

                    if (DEBUG) ftpPassword = "1";
                    if (DEBUG) Log.d(TAG,"password " + ftpPassword + ".");

                    if (!mFtpServiceProxy.serverStart(ftpId, ftpPassword, ftpPort,
                            ftpHomeDir, ftpMaxLoginUser)) {
                        Toast.makeText(mContext,
                                mContext.getString(R.string.ftp_start_failure),
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    mFtpServiceProxy.serverStop();
                }
                updateFtpCheckBox();
            }
        });
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object ftpEnable) {
        switchFtpStatus((Boolean) ftpEnable);
        return false;  // Don't update UI to opposite state until we're sure.
    }
}
