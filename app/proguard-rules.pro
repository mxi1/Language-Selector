-dontobfuscate

# The Shizuku / libsu user services are instantiated by class name from another process.
-keep class vegabobo.languageselector.IUserService { *; }
-keep class vegabobo.languageselector.IUserService$* { *; }
-keep class vegabobo.languageselector.service.UserService { *; }
-keep class vegabobo.languageselector.service.RootUserService { *; }
-keep class vegabobo.languageselector.service.ShizukuUserServiceArgs { *; }
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile