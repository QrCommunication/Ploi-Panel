# Keep the release configuration explicit as features are added.

# JSch (SSH host-key probe): its algorithm providers are loaded by class name at runtime.
-keep class com.jcraft.jsch.** { *; }

# Optional upstream JSch integrations absent on Android: Windows Pageant/JNA, desktop
# GSS and Unix sockets, logging adapters, and Bouncy Castle alternative providers.
# The probe uses Android/JCA algorithms; these are not required runtime dependencies.
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**
