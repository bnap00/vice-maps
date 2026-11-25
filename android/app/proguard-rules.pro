# Native bridges are called reflectively from C/Rust; keep them intact.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class uniffi.** { *; }
-keep class org.maplibre.android.** { *; }
-keep class com.stadiamaps.ferrostar.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-dontwarn org.slf4j.**
