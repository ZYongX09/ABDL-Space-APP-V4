# Prevent R8 class merging/inlining across the API33 boundary. No broad miuix keep rule.
-keep,allowobfuscation class top.abdl_space.app.core.designsystem.glass.V4GlassApi33 { *; }
-keep,allowobfuscation class top.abdl_space.app.core.designsystem.glass.Api33Renderer { *; }
