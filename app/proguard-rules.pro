# Die App hat keine JavaScript-Bridge (kein addJavascriptInterface),
# daher sind keine @JavascriptInterface-Keep-Regeln noetig.

# Zeilennummern fuer lesbare Stacktraces behalten, Quelldateinamen verschleiern
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AndroidX Webkit Boundary-Interfaces (Reflection-basiert)
-keep class org.chromium.support_lib_boundary.** { *; }
