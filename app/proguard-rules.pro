# Pleos SDK(Vehicle, NaviHelper, TextToSpeech)는 내부에서 리플렉션과 바인더 인터페이스를 쓸 수 있어 통째로 보존한다.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**
