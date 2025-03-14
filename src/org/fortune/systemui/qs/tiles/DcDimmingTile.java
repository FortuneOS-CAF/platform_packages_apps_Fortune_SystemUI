/*
 * Copyright (C) 2023 Paranoid Android
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

package org.fortune.systemui.qs.tiles;

import static android.hardware.display.DcDimmingManager.MODE_AUTO_TIME;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.display.DcDimmingManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.animation.Expandable;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import javax.inject.Inject;

/** Quick settings tile: DC Dimming **/
public class DcDimmingTile extends QSTileImpl<QSTile.BooleanState> {

    public static final String TILE_SPEC = "dc_dimming";
    private DcDimmingManager mDcDimmingManager;
    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_dc_dimming_tile);

    @Inject
    public DcDimmingTile(
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
        mDcDimmingManager = (DcDimmingManager) mContext.getSystemService(Context.DC_DIM_SERVICE);
        if (isAvailable()) {
            SettingsObserver settingsObserver = new SettingsObserver(mainHandler);
            settingsObserver.observe();
        }
    }

    @Override
    public boolean isAvailable() {
        return mDcDimmingManager != null && mDcDimmingManager.isAvailable();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        if (getState().state == Tile.STATE_UNAVAILABLE) {
            return;
        }
        mDcDimmingManager.setDcDimming(!mState.value);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int mode = mDcDimmingManager.getAutoMode();
        final boolean dcOn = mDcDimmingManager.isDcDimmingOn();

        state.value = dcOn;
        state.label = mContext.getString(R.string.quick_settings_dc_dimming_label);
        state.icon = mIcon;
        switch (mode) {
            case MODE_AUTO_TIME:
                state.secondaryLabel = mContext.getResources().getString(dcOn
                    ? R.string.quick_settings_dark_mode_secondary_label_until_sunrise
                    : R.string.quick_settings_dark_mode_secondary_label_on_at_sunset);
                break;
            default:
                state.secondaryLabel = null;
                break;
        }
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.contentDescription = TextUtils.isEmpty(state.secondaryLabel)
                ? state.label
                : TextUtils.concat(state.label, ", ", state.secondaryLabel);
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_DC_DIMMING_SETTINGS);
    }

    @Override
    protected void handleSetListening(boolean listening) {
    }

    @Override
    public CharSequence getTileLabel() {
        return getState().label;
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DC_DIMMING_AUTO_MODE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.DC_DIMMING_STATE), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }
    }
}
