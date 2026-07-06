# BouncyCastle lightweight API is used reflectively in places; keep it intact.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
