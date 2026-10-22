# Feature status

## Implemented in source
- Native Kotlin + Jetpack Compose Android application project.
- HOME intent filter and default-home settings shortcut.
- Local launchable-app discovery, alphabetical app drawer and live local filtering.
- Eight shared layout presets: Pure Minimal, Classic, Dashboard, Widget Canvas, Text Launcher, Icon Focus, Split Layout and Blank Canvas.
- Persisted home style, light/dark mode, clock/date/labels visibility, grid columns and accent preferences via DataStore.
- Wallpaper primary-color metadata as a local palette input, with a manual accent palette.
- Local-only privacy information screen.
- AppWidgetHost/AppWidgetManager integration path with system binding and provider configuration flows.
- Versioned JSON preference export to app-specific storage.
- Basic unit tests for stable preset IDs, profile header format, and grid bounds.
- No analytics, ad SDK, account, telemetry or INTERNET permission in the manifest.

## Partial
- Layout presets are starting compositions, not yet a drag-and-drop shared element canvas.
- Widget support has real platform binding and hosting, but the UI does not yet provide a full provider picker, previews, multi-widget placement, resize controls or persisted widget pages.
- Wallpaper color uses Android's primary wallpaper color metadata; no robust image extraction fallback or full palette variants yet.
- Settings include practical working controls but not every requested setting category or per-element override.
- Backup exports selected preferences; import, duplicate/rename profiles, conflict confirmation and restore UI are not yet implemented.
- Favorite app storage is represented in the preference model, but a full favorite management UI is not yet implemented.
- Gestures: vertical swipe-up detection and explicit app drawer/edit actions; gesture remapping, multi-page navigation and robust nested-widget gesture arbitration are not implemented.
- App hide preferences are modeled but hide-management UI is not yet implemented.
- Dashboard tiles are visual information entry points, not full calendar/weather/battery providers.
- The widget host starts with the first provider rather than a selection flow. This should be upgraded before production use.

## Not implemented yet
- Built-in calendar, weather, battery, music, notes, tasks, screen-time and storage widgets.
- Drag/drop, resize handles, multiple pages, undo/cancel edit transaction, layout profile import and full backup/restore.
- Icon pack support and per-app icon/label overrides.
- Advanced app folders/categories, recently used sorting, uninstall/app-info contextual actions.
- Gesture configuration page, accessibility/reduced-motion audit and extensive UI/instrumentation tests.
- Full wallpaper-derived palette modes (muted/vibrant/pastel/monochrome/high contrast) and contrast evaluation for all overrides.
- Physical device and emulator verification.

## Build verification
Not run in the authoring environment: Java 21 is available, but Gradle and Android SDK are not installed/configured. Do not treat this source archive as a verified APK.
