# Keep the release configuration explicit as features are added.

# JSch (SSH probe transport): keep whole so R8 cannot strip the SSH implementation.
-keep class com.jcraft.jsch.** { *; }
