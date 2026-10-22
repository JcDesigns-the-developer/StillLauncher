# Privacy notes

Still Launcher does not include analytics, advertising SDKs, account sign-in, telemetry, or an INTERNET permission. App discovery and search are local. Launcher preferences are stored with AndroidX DataStore in app-private storage. Wallpaper colors are requested through Android wallpaper metadata APIs.

No calendar, contacts, location, notification-listener, or usage-access permissions are requested. Such features should remain opt-in if added later. Hiding an app would only remove it from this launcher's own lists; it cannot secure or system-hide an application.

Third-party Android widgets are provided by other applications and can independently access the network or request permissions under their own package identity. Still cannot promise their behavior is private.

The current profile export writes user-selected launcher preferences into the app-specific external files directory. Do not put secrets into exported profile files. Third-party widget state is not included.
