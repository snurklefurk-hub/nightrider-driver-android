# NightRider Driver — ProGuard rules.
# Release builds keep minification off (minifyEnabled false), so this file is a
# placeholder for when you turn shrinking on later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
