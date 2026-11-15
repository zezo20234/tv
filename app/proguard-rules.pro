# ProGuard rules for REALM TV OS
# Keep TV-specific classes
-keep class android.media.tv.** { *; }

# Keep custom views
-keep class com.realm.tvos.* {
    *;
}

# Keep package installer classes
-keep class android.content.pm.PackageInstaller { *; }

# Keep all classes in com.realm.tvos package
-keep class com.realm.tvos.** { *; }
