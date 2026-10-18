package vegabobo.languageselector;

interface IUserService {
    void exit() = 1;
    void destroy() = 16777114;
    int getUid() = 1000;

    // ILocaleManager
    void setApplicationLocales(String packageName, in LocaleList locales) = 2000;
    LocaleList getApplicationLocales(String packageName) = 2001;
    LocaleList getSystemLocales() = 2002;

    /**
     * Batched variant of getApplicationLocales. For every package the element at the
     * same index is the app's language tag list joined with ";". An empty string means
     * the app uses the system default locales.
     */
    String[] getApplicationLocalesTags(in String[] packageNames) = 2003;

    // IActivityManager
    void forceStopPackage(String packageName) = 3000;

    // IActivityTaskManager
    String getFirstRunningTaskPackage() = 4000;
}
