# TerminalPlayer release rules.
# Media3 ships its own consumer ProGuard rules, so only app specific entries live here.

-keepattributes *Annotation*, InnerClasses

# The media session service is referenced from the manifest only.
-keep class dev.terminalplayer.playback.PlaybackService { *; }

# Keep MediaSession controller callbacks reachable through reflection-free paths (defensive).
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**
