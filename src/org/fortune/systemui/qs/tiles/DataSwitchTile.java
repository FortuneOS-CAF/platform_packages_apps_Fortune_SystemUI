package org.fortune.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.SysUIToast;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import com.android.systemui.res.R;
import java.util.List;

import javax.inject.Inject;

public class DataSwitchTile extends QSTileImpl<BooleanState> {
    public static final String TILE_SPEC = "dataswitch";
    private static final String SETTING_USER_PREF_DATA_SUB = "user_preferred_data_sub";
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyManager mTelephonyManager;

    BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "mSimReceiver:onReceive");
            refreshState();
        }
    };

    private boolean mCanSwitch = true;

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            mCanSwitch = mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE;
            refreshState();
        }
    };

    private boolean mRegistered = false;
    private int mSimCount = 0;

    @Inject
    public DataSwitchTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mSubscriptionManager = SubscriptionManager.from(host.getContext());
        mTelephonyManager = TelephonyManager.from(host.getContext());
    }

    @Override
    public boolean isAvailable() {
        int count = TelephonyManager.getDefault().getPhoneCount();
        Log.d(TAG, "phoneCount: " + count);
        return count >= 2;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            if (!mRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
                mContext.registerReceiver(mSimReceiver, filter);
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                mRegistered = true;
            }
            refreshState();
        } else if (mRegistered) {
            mContext.unregisterReceiver(mSimReceiver);
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            mRegistered = false;
        }
    }

    private void updateSimCount() {
        String simState = SystemProperties.get("gsm.sim.state");
        Log.d(TAG, "DataSwitchTile:updateSimCount:simState=" + simState);
        mSimCount = 0;
        try {
            String[] sims = TextUtils.split(simState, ",");
            for (String sim : sims) {
                if (!sim.isEmpty() && !sim.equalsIgnoreCase(
                        IccCardConstants.INTENT_VALUE_ICC_ABSENT) && !sim.equalsIgnoreCase(
                        IccCardConstants.INTENT_VALUE_ICC_NOT_READY)) {
                    mSimCount++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error to parse sim state");
        }
        Log.d(TAG, "DataSwitchTile:updateSimCount:mSimCount=" + mSimCount);
    }

    @Override
    public void handleClick(@Nullable Expandable expandable) {
        if (!mCanSwitch) {
            Log.d(TAG, "Call state=" + mTelephonyManager.getCallState());
        } else if (mSimCount == 0) {
            Log.d(TAG, "handleClick:no sim card");
            SysUIToast.makeText(mContext, mContext.getString(R.string.qs_data_switch_toast_0),
                    Toast.LENGTH_LONG).show();
        } else if (mSimCount == 1) {
            Log.d(TAG, "handleClick:only one sim card");
            SysUIToast.makeText(mContext, mContext.getString(R.string.qs_data_switch_toast_1),
                    Toast.LENGTH_LONG).show();
        } else {
            AsyncTask.execute(() -> {
                toggleMobileDataEnabled();
                refreshState();
            });
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.qs_data_switch_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        boolean activeSIMZero;
        if (arg == null) {
            int defaultPhoneId = SubscriptionManager.getPhoneId(
                    SubscriptionManager.getDefaultDataSubscriptionId());
            Log.d(TAG, "default data phone id=" + defaultPhoneId);
            activeSIMZero = defaultPhoneId == 0;
        } else {
            activeSIMZero = (Boolean) arg;
        }
        updateSimCount();
        switch (mSimCount) {
            case 1:
                state.icon = ResourceIcon.get(activeSIMZero ? R.drawable.ic_qs_data_switch_1
                        : R.drawable.ic_qs_data_switch_2);
                state.secondaryLabel = mContext.getString(
                        activeSIMZero ? R.string.qs_data_switch_text_1
                                : R.string.qs_data_switch_text_2);
                state.value = false;
                break;
            case 2:
                state.icon = ResourceIcon.get(activeSIMZero ? R.drawable.ic_qs_data_switch_1
                        : R.drawable.ic_qs_data_switch_2);
                state.secondaryLabel = mContext.getString(
                        activeSIMZero ? R.string.qs_data_switch_text_1
                                : R.string.qs_data_switch_text_2);
                state.value = true;
                break;
            default:
                state.icon = ResourceIcon.get(R.drawable.ic_qs_data_switch_1);
                state.secondaryLabel = mContext.getString(R.string.qs_data_switch_text_1);
                state.value = false;
                break;
        }

        if (mSimCount < 2) {
            state.state = 0;
        } else if (!mCanSwitch) {
            state.state = 0;
            Log.d(TAG, "call state isn't idle, set to unavailable.");
        } else {
            state.state = state.value ? 2 : 1;
        }

        state.label = mContext.getString(R.string.qs_data_switch_label);
        state.contentDescription = mContext.getString(
                activeSIMZero ? R.string.qs_data_switch_changed_1
                        : R.string.qs_data_switch_changed_2);
    }

    /**
     * Set whether to enable data for {@code subId}, also whether to disable data for other
     * subscription
     */
    private void toggleMobileDataEnabled() {
        // Get opposite slot 2 ^ 3 = 1, 1 ^ 3 = 2
        int subId = SubscriptionManager.getDefaultDataSubscriptionId() ^ 3;
        final TelephonyManager telephonyManager =
                mTelephonyManager.createForSubscriptionId(subId);
        telephonyManager.setDataEnabled(true);
        mSubscriptionManager.setDefaultDataSubId(subId);
        Settings.Global.putInt(mContext.getContentResolver(), SETTING_USER_PREF_DATA_SUB, subId);
        Log.d(TAG, "Enabled subID: " + subId);

        List<SubscriptionInfo> subInfoList = mSubscriptionManager.getActiveSubscriptionInfoList(
                true);
        if (subInfoList != null) {
            for (SubscriptionInfo subInfo : subInfoList) {
                // We never disable mobile data for opportunistic subscriptions.
                if (subInfo.getSubscriptionId() != subId && !subInfo.isOpportunistic()) {
                    mTelephonyManager.createForSubscriptionId(
                            subInfo.getSubscriptionId()).setDataEnabled(false);
                    Log.d(TAG, "Disabled subID: " + subInfo.getSubscriptionId());
                }
            }
        }
    }
}
