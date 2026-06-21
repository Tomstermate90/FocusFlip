package com.alex_lior_tomer.focusflip.utils;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

public class LocaleHelper {

    public static Context onAttach(Context context) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        String lang = preferencesManager.getAppLanguage();
        return setLocale(context, lang);
    }

    public static Context setLocale(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);

        return context.createConfigurationContext(configuration);
    }
}
