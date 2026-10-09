# ML Kit's code scanner finds its parts by reflection on their no-argument constructors.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
