-keep class com.questlog.app.MainActivity$JsBridge { *; }
-keepclassmembers class com.questlog.app.MainActivity$JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
