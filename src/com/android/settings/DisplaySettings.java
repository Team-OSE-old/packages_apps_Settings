/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.view.RotationPolicy;
import com.android.settings.DreamSettings;
import com.android.settings.ose.DisplayRotation;

import java.util.ArrayList;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_DISPLAY_ROTATION = "display_rotation";
    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_LIGHT_OPTIONS = "category_light_options";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_NOTIFICATION_LIGHT = "notification_light";
    private static final String KEY_BATTERY_LIGHT = "battery_light";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_VOLUME_WAKE = "pref_volume_wake";
    private static final String KEY_WAKEUP_WHEN_PLUGGED_UNPLUGGED = "wakeup_when_plugged_unplugged";
    private static final String KEY_WAKEUP_CATEGORY = "category_wakeup_options";
    private static final String SREC_ENABLE_TOUCHES = "srec_enable_touches";
    private static final String SREC_ENABLE_MIC = "srec_enable_mic";
    private static final String DISABLE_IMMERSIVE_MESSAGE = "disable_immersive_message";
    private static final String KEY_SCREEN_COLOR_SETTINGS = "screencolor_settings";
    private static final String KEY_SCREEN_OFF_GESTURE_SETTINGS = "screen_off_gesture_settings";

    private static final String ROTATION_ANGLE_0 = "0";
    private static final String ROTATION_ANGLE_90 = "90";
    private static final String ROTATION_ANGLE_180 = "180";
    private static final String ROTATION_ANGLE_270 = "270";

    private PreferenceScreen mDisplayRotationPreference;
    private CheckBoxPreference mNotificationPulse;
    private PreferenceCategory mLightOptions;
    private PreferenceScreen mNotificationLight;
    private PreferenceScreen mBatteryPulse;
    private CheckBoxPreference mVolumeWake;
    private CheckBoxPreference mWakeUpWhenPluggedOrUnplugged;
    private PreferenceCategory mWakeUpOptions;
    private CheckBoxPreference mSrecEnableTouches;
    private CheckBoxPreference mSrecEnableMic;
    private CheckBoxPreference mDisableIM;
    private PreferenceScreen mScreenColorSettings;

    private final Configuration mCurConfig = new Configuration();

    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;

    private ContentObserver mAccelerometerRotationObserver =
            new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateDisplayRotationPreferenceDescription();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        PreferenceScreen prefSet = getPreferenceScreen();

        mDisplayRotationPreference = (PreferenceScreen) findPreference(KEY_DISPLAY_ROTATION);
        if (!RotationPolicy.isRotationSupported(getActivity())) {
            getPreferenceScreen().removePreference(mDisplayRotationPreference);
        }

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mLightOptions = (PreferenceCategory) prefSet.findPreference(KEY_LIGHT_OPTIONS);
        mNotificationPulse = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_PULSE);
        mNotificationLight = (PreferenceScreen) findPreference(KEY_NOTIFICATION_LIGHT);
        mBatteryPulse = (PreferenceScreen) findPreference(KEY_BATTERY_LIGHT);
        if (mNotificationPulse != null && mNotificationLight != null && mBatteryPulse != null) {
            if (getResources().getBoolean(
                    com.android.internal.R.bool.config_intrusiveNotificationLed)) {
                 if (getResources().getBoolean(
                         com.android.internal.R.bool.config_multiColorNotificationLed)) {
                     mLightOptions.removePreference(mNotificationPulse);
                     updateLightPulseDescription();
                 } else {
                     mLightOptions.removePreference(mNotificationLight);
                     try {
                         mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                                 Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                     } catch (SettingNotFoundException e) {
                         e.printStackTrace();
                     }
                 }
            } else {
                 mLightOptions.removePreference(mNotificationPulse);
                 mLightOptions.removePreference(mNotificationLight);
            }

            if (!getResources().getBoolean(
                    com.android.internal.R.bool.config_intrusiveBatteryLed)) {
                mLightOptions.removePreference(mBatteryPulse);
            } else {
                updateBatteryPulseDescription();
            }

            //If we're removed everything, get rid of the category
            if (mLightOptions.getPreferenceCount() == 0) {
                prefSet.removePreference(mLightOptions);
            }
        }

        mWakeUpOptions = (PreferenceCategory) prefSet.findPreference(KEY_WAKEUP_CATEGORY);
        int counter = 0;
        mVolumeWake = (CheckBoxPreference) findPreference(KEY_VOLUME_WAKE);
        if (mVolumeWake != null) {
            if (!getResources().getBoolean(R.bool.config_show_volumeRockerWake)) {
                mWakeUpOptions.removePreference(mVolumeWake);
                counter++;
            } else {
                mVolumeWake.setChecked(Settings.System.getInt(resolver,
                        Settings.System.VOLUME_WAKE_SCREEN, 0) == 1);
                mVolumeWake.setOnPreferenceChangeListener(this);
            }
        }

        mScreenColorSettings = (PreferenceScreen) findPreference(KEY_SCREEN_COLOR_SETTINGS);
        if (!isPostProcessingSupported()) {
            getPreferenceScreen().removePreference(mScreenColorSettings);
        }

        mWakeUpWhenPluggedOrUnplugged =
            (CheckBoxPreference) findPreference(KEY_WAKEUP_WHEN_PLUGGED_UNPLUGGED);
        // hide option if device is already set to never wake up
        if(!getResources().getBoolean(
                com.android.internal.R.bool.config_unplugTurnsOnScreen)) {
                mWakeUpOptions.removePreference(mWakeUpWhenPluggedOrUnplugged);
                counter++;
        } else {
            mWakeUpWhenPluggedOrUnplugged.setChecked(Settings.System.getInt(resolver,
                        Settings.System.WAKEUP_WHEN_PLUGGED_UNPLUGGED, 1) == 1);
            mWakeUpWhenPluggedOrUnplugged.setOnPreferenceChangeListener(this);
        }

        if (counter == 2) {
            prefSet.removePreference(mWakeUpOptions);
        }

        mSrecEnableTouches = (CheckBoxPreference) findPreference(SREC_ENABLE_TOUCHES);
        mSrecEnableTouches.setChecked((Settings.System.getInt(resolver,
                Settings.System.SREC_ENABLE_TOUCHES, 0) == 1));
        mSrecEnableTouches.setOnPreferenceChangeListener(this);
        mSrecEnableMic = (CheckBoxPreference) findPreference(SREC_ENABLE_MIC);
        mSrecEnableMic.setChecked((Settings.System.getInt(resolver,
                Settings.System.SREC_ENABLE_MIC, 0) == 1));

        mDisableIM = (CheckBoxPreference) findPreference(DISABLE_IMMERSIVE_MESSAGE);
        mDisableIM.setChecked((Settings.System.getInt(resolver,
                Settings.System.DISABLE_IMMERSIVE_MESSAGE, 0) == 1));

        Utils.updatePreferenceToSpecificActivityFromMetaDataOrRemove(getActivity(),
                getPreferenceScreen(), KEY_SCREEN_OFF_GESTURE_SETTINGS);
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    if (currentTimeout >= timeout) {
                        best = i;
                    }
                }
                summary = preference.getContext().getString(R.string.screen_timeout_summary,
                        entries[best]);
            }
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else if (revisedValues.size() > 0
                    && Long.parseLong(revisedValues.get(revisedValues.size() - 1).toString())
                    == maxTimeout) {
                // If the last one happens to be the same as the max timeout, select that
                screenTimeoutPreference.setValue(String.valueOf(maxTimeout));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true,
                mAccelerometerRotationObserver);

        updateDisplayRotationPreferenceDescription();
        updateState();
        updateLightPulseDescription();
        updateBatteryPulseDescription();
    }

    @Override
    public void onPause() {
        super.onPause();

        getContentResolver().unregisterContentObserver(mAccelerometerRotationObserver);
    }

    private void updateState() {
        updateScreenSaverSummary();
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private void updateLightPulseDescription() {
        if (mNotificationLight == null) {
            return;
        }
        if (Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.NOTIFICATION_LIGHT_PULSE, 0) == 1) {
            mNotificationLight.setSummary(getString(R.string.enabled));
        } else {
            mNotificationLight.setSummary(getString(R.string.disabled));
        }
    }

    private void updateBatteryPulseDescription() {
        if (mBatteryPulse == null) {
            return;
        }
        if (Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.BATTERY_LIGHT_ENABLED, 1) == 1) {
            mBatteryPulse.setSummary(getString(R.string.enabled));
        } else {
            mBatteryPulse.setSummary(getString(R.string.disabled));
        }
     }

    private void updateDisplayRotationPreferenceDescription() {
        if (mDisplayRotationPreference == null) {
            return;
        }
        PreferenceScreen preference = mDisplayRotationPreference;
        StringBuilder summary = new StringBuilder();
        Boolean rotationEnabled = Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) != 0;
        int mode = Settings.System.getInt(getContentResolver(),
            Settings.System.ACCELEROMETER_ROTATION_ANGLES,
            DisplayRotation.ROTATION_0_MODE|DisplayRotation.ROTATION_90_MODE|DisplayRotation.ROTATION_270_MODE);

        if (!rotationEnabled) {
            summary.append(getString(R.string.display_rotation_disabled));
        } else {
            ArrayList<String> rotationList = new ArrayList<String>();
            String delim = "";
            if ((mode & DisplayRotation.ROTATION_0_MODE) != 0) {
                rotationList.add(ROTATION_ANGLE_0);
            }
            if ((mode & DisplayRotation.ROTATION_90_MODE) != 0) {
                rotationList.add(ROTATION_ANGLE_90);
            }
            if ((mode & DisplayRotation.ROTATION_180_MODE) != 0) {
                rotationList.add(ROTATION_ANGLE_180);
            }
            if ((mode & DisplayRotation.ROTATION_270_MODE) != 0) {
                rotationList.add(ROTATION_ANGLE_270);
            }
            for (int i = 0; i < rotationList.size(); i++) {
                summary.append(delim).append(rotationList.get(i));
                if ((rotationList.size() - i) > 2) {
                    delim = ", ";
                } else {
                    delim = " & ";
                }
            }
            summary.append(" " + getString(R.string.display_rotation_unit));
        }
        preference.setSummary(summary);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mNotificationPulse) {
            boolean value = mNotificationPulse.isChecked();
            Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATION_LIGHT_PULSE,
                    value ? 1 : 0);
            return true;
        } else if  (preference == mDisableIM) {
            boolean checked = ((CheckBoxPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DISABLE_IMMERSIVE_MESSAGE, checked ? 1:0);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_VOLUME_WAKE.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.VOLUME_WAKE_SCREEN,
                    (Boolean) objValue ? 1 : 0);
        }
        if (KEY_WAKEUP_WHEN_PLUGGED_UNPLUGGED.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.WAKEUP_WHEN_PLUGGED_UNPLUGGED,
                    (Boolean) objValue ? 1 : 0);
        }
        if (SREC_ENABLE_TOUCHES.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SREC_ENABLE_TOUCHES,
                    (Boolean) objValue ? 1 : 0);
        }
        if (SREC_ENABLE_MIC.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SREC_ENABLE_MIC,
                    (Boolean) objValue ? 1 : 0);
        }

        return true;
    }

    private boolean isPostProcessingSupported() {
        boolean ret = true;
        final PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo("com.qualcomm.display", PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            ret = false;
        }
        return ret;
    }
}
