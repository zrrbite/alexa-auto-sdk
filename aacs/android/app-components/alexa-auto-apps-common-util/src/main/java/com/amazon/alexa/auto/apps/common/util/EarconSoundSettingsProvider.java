package com.amazon.alexa.auto.apps.common.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Service class for interacting with Alexa Earcon Settings
 */
public class EarconSoundSettingsProvider {
    private static final String TAG = EarconSoundSettingsProvider.class.getCanonicalName();

    public static final String EARCON_SETTINGS = "earcon-settings";
    public static final String EARCON_SETTINGS_START = "earcon-settings-start";
    public static final String EARCON_SETTINGS_END = "earcon-settings-end";
    public static final boolean DEFAULT_SOUND_PREFERENCE = true;


    public static void setStartEarconSetting(@NonNull Context context, boolean value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(EARCON_SETTINGS, 0).edit();
        editor.putBoolean(EARCON_SETTINGS_START, value);
        editor.commit();
    }

    public static boolean isStartEarconSettingEnabled(@NonNull Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(EARCON_SETTINGS, 0);
        return sharedPreferences.getBoolean(EARCON_SETTINGS_START, DEFAULT_SOUND_PREFERENCE);
    }

    public static void setEndEarconSetting(@NonNull Context context, boolean value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(EARCON_SETTINGS, 0).edit();
        editor.putBoolean(EARCON_SETTINGS_END, value);
        editor.commit();
    }

    public static boolean isEndEarconSettingEnabled(@NonNull Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(EARCON_SETTINGS, 0);
        return sharedPreferences.getBoolean(EARCON_SETTINGS_END, DEFAULT_SOUND_PREFERENCE);
    }

    public static void resetEarconSettings(@NonNull Context context) {
        setStartEarconSetting(context, DEFAULT_SOUND_PREFERENCE);
        setEndEarconSetting(context, DEFAULT_SOUND_PREFERENCE);
    }
}