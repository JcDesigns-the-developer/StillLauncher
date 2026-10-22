package dev.jcdesigns.still

import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val android.content.Context.dataStore by preferencesDataStore("still_preferences")

enum class HomeStyle(val label: String) {
    PURE_MINIMAL("Pure Minimal"), CLASSIC("Classic"), DASHBOARD("Dashboard"),
    WIDGET_CANVAS("Widget Canvas"), TEXT("Text Launcher"), ICON_FOCUS("Icon Focus"),
    SPLIT("Split Layout"), BLANK("Blank Canvas")
}
enum class Screen { HOME, DRAWER, SETTINGS, LAYOUTS, COLORS, WIDGETS, PRIVACY, BACKUP }

data class LauncherApp(val label: String, val packageName: String, val icon: Drawable)

data class LauncherPrefs(
    val style: HomeStyle = HomeStyle.PURE_MINIMAL,
    val dark: Boolean = false,
    val followWallpaper: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showLabels: Boolean = true,
    val columns: Int = 4,
    val accent: Long = 0xFF343434,
    val hiddenPackages: Set<String> = emptySet(),
    val favoritePackages: Set<String> = emptySet()
)

class MainActivity : ComponentActivity() {
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private var pendingWidgetId: Int = -1

    private val configureWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (pendingWidgetId >= 0) {
            if (result.resultCode == Activity.RESULT_OK) saveWidgetId(pendingWidgetId)
            else { widgetHost.deleteAppWidgetId(pendingWidgetId); pendingWidgetId = -1 }
        }
    }
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        if (id >= 0 && result.resultCode == Activity.RESULT_OK) launchWidgetConfiguration(id)
        else if (id >= 0) { widgetHost.deleteAppWidgetId(id); pendingWidgetId = -1 }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHost = AppWidgetHost(this, 0x535449)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost.startListening()
        setContent { StillApp(this) }
    }
    override fun onDestroy() { if (::widgetHost.isInitialized) widgetHost.stopListening(); super.onDestroy() }

    fun beginAddWidget() {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        val providers = widgetManager.installedProviders
        if (providers.isEmpty()) {
            widgetHost.deleteAppWidgetId(id); pendingWidgetId = -1
            return
        }
        val provider = providers.first()
        if (!widgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            bindWidget.launch(intent)
        } else launchWidgetConfiguration(id)
    }
    private fun launchWidgetConfiguration(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            configureWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        } else saveWidgetId(id)
    }
    private fun saveWidgetId(id: Int) {
        getSharedPreferences("widget_state", MODE_PRIVATE).edit().putInt("widget_id", id).apply()
        pendingWidgetId = -1
    }
    fun createWidgetView(): android.view.View? {
        val id = getSharedPreferences("widget_state", MODE_PRIVATE).getInt("widget_id", -1)
        if (id < 0) return null
        val info = widgetManager.getAppWidgetInfo(id) ?: return null
        return try { widgetHost.createView(this, id, info).apply { setAppWidget(id, info) } } catch (_: Exception) { null }
    }
    fun launchApp(packageName: String) {
        try { packageManager.getLaunchIntentForPackage(packageName)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it) } }
        catch (_: Exception) { }
    }
    fun installedApps(): List<LauncherApp> = try {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.ResolveInfoFlags.of(0))
            .mapNotNull { r -> r.activityInfo?.let { LauncherApp(r.loadLabel(packageManager).toString(), it.packageName, r.loadIcon(packageManager)) } }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }
    } catch (_: Exception) { emptyList() }

    fun chooseDefaultHome() {
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

@Composable
private fun StillApp(activity: MainActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsFlow = remember {
        context.dataStore.data.map { p ->
            LauncherPrefs(
                style = runCatching { HomeStyle.valueOf(p[stringPreferencesKey("style")] ?: "PURE_MINIMAL") }.getOrDefault(HomeStyle.PURE_MINIMAL),
                dark = p[booleanPreferencesKey("dark")] ?: false,
                followWallpaper = p[booleanPreferencesKey("wallpaper")] ?: true,
                showClock = p[booleanPreferencesKey("clock")] ?: true,
                showDate = p[booleanPreferencesKey("date")] ?: true,
                showLabels = p[booleanPreferencesKey("labels")] ?: true,
                columns = (p[intPreferencesKey("columns")] ?: 4).coerceIn(2, 7),
                accent = p[stringPreferencesKey("accent")]?.toLongOrNull() ?: 0xFF343434,
                hiddenPackages = (p[stringPreferencesKey("hidden")] ?: "").split('|').filter { it.isNotBlank() }.toSet(),
                favoritePackages = (p[stringPreferencesKey("favorites")] ?: "").split('|').filter { it.isNotBlank() }.toSet()
            )
        }
    }
    val prefs by prefsFlow.collectAsState(initial = LauncherPrefs())
    var screen by remember { mutableStateOf(Screen.HOME) }
    var query by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<LauncherApp>()) }
    var selectedPalette by remember { mutableStateOf<Color?>(null) }
    val palette = remember(prefs.dark, prefs.followWallpaper, prefs.accent) {
        if (prefs.followWallpaper) {
            runCatching {
                val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                val c = colors?.primaryColor
                if (c != null) Color(c.red(), c.green(), c.blue()) else Color(prefs.accent)
            }.getOrDefault(Color(prefs.accent))
        } else Color(prefs.accent)
    }
    val accent = selectedPalette ?: palette
    val bg = if (prefs.dark) Color(0xFF101112) else Color(0xFFF7F6F2)
    val fg = if (prefs.dark) Color(0xFFF1F0EC) else Color(0xFF222321)
    val muted = if (prefs.dark) Color(0xFFB0B1AC) else Color(0xFF686A65)
    val colorScheme = if (prefs.dark) darkColorScheme(primary = accent, onPrimary = Color.White, background = bg, surface = bg, onBackground = fg, onSurface = fg)
                      else lightColorScheme(primary = accent, onPrimary = Color.White, background = bg, surface = bg, onBackground = fg, onSurface = fg)
    LaunchedEffect(Unit) { apps = activity.installedApps() }
    BackHandler(enabled = screen != Screen.HOME || editMode) {
        if (editMode) editMode = false else screen = Screen.HOME
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    activity, prefs, apps.filter { it.packageName !in prefs.hiddenPackages }, fg, muted, accent,
                    onDrawer = { apps = activity.installedApps(); screen = Screen.DRAWER },
                    onSettings = { screen = Screen.SETTINGS },
                    onEdit = { editMode = !editMode },
                    editMode = editMode,
                    onStyle = { showStylePicker = true },
                    onSearch = { screen = Screen.DRAWER; query = "" }
                )
                Screen.DRAWER -> AppDrawer(apps.filter { it.packageName !in prefs.hiddenPackages }, query, { query = it }, fg, muted, prefs, activity, { screen = Screen.HOME }, { screen = Screen.SETTINGS })
                Screen.SETTINGS -> SettingsScreen(prefs, fg, muted, { screen = Screen.HOME }, { screen = it }, { key, value -> scope.launch { context.dataStore.edit { it[key] = value } } }, activity)
                Screen.LAYOUTS -> LayoutsScreen(prefs, fg, muted, { screen = Screen.SETTINGS }) { style -> scope.launch { context.dataStore.edit { it[stringPreferencesKey("style")] = style.name } } }
                Screen.COLORS -> ColorsScreen(prefs, fg, muted, accent, { screen = Screen.SETTINGS }, { color -> selectedPalette = color; scope.launch { context.dataStore.edit { it[stringPreferencesKey("accent")] = color.toArgb().toLong().toString(); it[booleanPreferencesKey("wallpaper")] = false } } }, { dark -> scope.launch { context.dataStore.edit { it[booleanPreferencesKey("dark")] = dark } } })
                Screen.WIDGETS -> WidgetsScreen(activity, fg, muted, { screen = Screen.SETTINGS })
                Screen.PRIVACY -> PrivacyScreen(fg, muted) { screen = Screen.SETTINGS }
                Screen.BACKUP -> BackupScreen(context, prefs, fg, muted) { screen = Screen.SETTINGS }
            }
            if (showStylePicker) {
                AlertDialog(onDismissRequest = { showStylePicker = false }, title = { Text("Choose a home style") },
                    text = { Column { HomeStyle.entries.forEach { style -> Text(style.label, Modifier.fillMaxWidth().clickable { scope.launch { context.dataStore.edit { it[stringPreferencesKey("style")] = style.name } }; showStylePicker = false }.padding(12.dp)) } } },
                    confirmButton = { TextButton(onClick = { showStylePicker = false }) { Text("Close") } })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    activity: MainActivity, prefs: LauncherPrefs, apps: List<LauncherApp>, fg: Color, muted: Color, accent: Color,
    onDrawer: () -> Unit, onSettings: () -> Unit, onEdit: () -> Unit, editMode: Boolean, onStyle: () -> Unit, onSearch: () -> Unit
) {
    val context = LocalContext.current
    var time by remember { mutableStateOf(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            time = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(15_000)
        }
    }
    val favorites = remember(apps, prefs.favoritePackages) {
        val fav = apps.filter { it.packageName in prefs.favoritePackages }
        if (fav.isNotEmpty()) fav else apps.take(4)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp)
            .pointerInput(Unit) { var drag = 0f; detectVerticalDragGestures(onVerticalDrag = { _, amount -> drag += amount }, onDragEnd = { if (drag < -55) onDrawer(); drag = 0f }) },
        horizontalAlignment = Alignment.Start
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("STILL", fontSize = 12.sp, letterSpacing = 4.sp, color = muted, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search apps", tint = fg) }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Customize", tint = fg) }
            }
        }
        Spacer(Modifier.height(40.dp))
        if (prefs.showClock) Text(time, fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp, color = fg)
        if (prefs.showDate) Text(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()), color = muted, fontSize = 15.sp)
        Spacer(Modifier.height(26.dp))
        if (prefs.style == HomeStyle.DASHBOARD) {
            Text("Your day, at a glance", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardTile("Battery", "Device status", Icons.Rounded.BatteryFull, fg, muted, Modifier.weight(1f))
                DashboardTile("Calendar", "Open calendar", Icons.Rounded.CalendarMonth, fg, muted, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
        }
        if (prefs.style != HomeStyle.BLANK && prefs.style != HomeStyle.WIDGET_CANVAS) {
            Text(if (prefs.style == HomeStyle.CLASSIC || prefs.style == HomeStyle.ICON_FOCUS) "Favourites" else "Your apps", color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            when (prefs.style) {
                HomeStyle.CLASSIC, HomeStyle.ICON_FOCUS -> LazyVerticalGrid(columns = GridCells.Fixed(prefs.columns), modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favorites.take(12), key = { it.packageName }) { app -> AppTile(app, prefs.showLabels && prefs.style != HomeStyle.ICON_FOCUS, fg, activity) }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    favorites.take(if (prefs.style == HomeStyle.PURE_MINIMAL) 4 else 8).forEach { app ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { activity.launchApp(app.packageName) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, color = fg, fontSize = if (prefs.style == HomeStyle.TEXT) 20.sp else 17.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Rounded.ArrowOutward, null, tint = muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        if (editMode) {
            Spacer(Modifier.height(14.dp))
            Text("Edit mode · choose a style or customize settings", color = accent, fontSize = 12.sp)
            OutlinedButton(onClick = onStyle) { Text("Change home style") }
        }
        Spacer(Modifier.weight(1f))
        if (prefs.style != HomeStyle.BLANK) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Swipe up for all apps", color = muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit) { Icon(if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit, if (editMode) "Finish editing" else "Edit home", tint = fg) }
                    IconButton(onClick = onDrawer) { Icon(Icons.Rounded.Apps, "All apps", tint = fg) }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Customize", tint = fg) } }
        }
    }
}

@Composable
private fun DashboardTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, fg: Color, muted: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)).padding(14.dp)) {
        Icon(icon, null, tint = fg)
        Spacer(Modifier.height(12.dp))
        Text(title, color = fg, fontWeight = FontWeight.Medium)
        Text(subtitle, color = muted, fontSize = 11.sp)
    }
}

@Composable
private fun AppTile(app: LauncherApp, showLabel: Boolean, fg: Color, activity: MainActivity) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { activity.launchApp(app.packageName) }.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(factory = { android.widget.ImageView(it).apply { setImageDrawable(app.icon); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } }, modifier = Modifier.size(42.dp))
        if (showLabel) { Spacer(Modifier.height(5.dp)); Text(app.label, color = fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun AppDrawer(apps: List<LauncherApp>, query: String, onQuery: (String) -> Unit, fg: Color, muted: Color, prefs: LauncherPrefs, activity: MainActivity, onBack: () -> Unit, onSettings: () -> Unit) {
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("Applications", fontSize = 24.sp, color = fg, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Settings") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = muted)
            Spacer(Modifier.width(10.dp))
            BasicTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = fg, fontSize = 16.sp), decorationBox = { inner -> if (query.isEmpty()) Text("Search apps", color = muted); inner() })
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable { activity.launchApp(app.packageName) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AndroidView(factory = { android.widget.ImageView(it).apply { setImageDrawable(app.icon) } }, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, color = fg, modifier = Modifier.weight(1f), fontSize = 15.sp)
                    Icon(Icons.Rounded.ArrowOutward, null, tint = muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(p: LauncherPrefs, fg: Color, muted: Color, back: () -> Unit, navigate: (Screen) -> Unit, save: suspend (androidx.datastore.preferences.core.Preferences.Key<Boolean>, Boolean) -> Unit, activity: MainActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Header("Customize", back, fg)
        Text("Make it yours. Changes are saved on this device.", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { SettingRow("Home screen", "Choose one of eight flexible starting layouts", Icons.Rounded.Dashboard) { navigate(Screen.LAYOUTS) } }
            item { SettingRow("Wallpaper & colors", "Palette, light/dark mode and accent", Icons.Rounded.Palette) { navigate(Screen.COLORS) } }
            item { SettingRow("Widgets", "Add an Android home-screen widget", Icons.Rounded.Widgets) { navigate(Screen.WIDGETS) } }
            item { SettingRow("App drawer", "Search and launch installed apps", Icons.Rounded.Apps) { navigate(Screen.DRAWER) } }
            item { SettingRow("Gestures", "Swipe up opens the app drawer; long-press edits", Icons.Rounded.Gesture) { navigate(Screen.PRIVACY) } }
            item { SettingRow("Privacy", "On-device behavior and permission notes", Icons.Rounded.Shield) { navigate(Screen.PRIVACY) } }
            item { SettingRow("Backup & restore", "Export or import launcher preferences", Icons.Rounded.SaveAlt) { navigate(Screen.BACKUP) } }
            item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
            item { SettingToggle("Show clock", p.showClock, fg, muted) { scope.launch { context.dataStore.edit { it[booleanPreferencesKey("clock")] = !p.showClock } } } }
            item { SettingToggle("Show date", p.showDate, fg, muted) { scope.launch { context.dataStore.edit { it[booleanPreferencesKey("date")] = !p.showDate } } } }
            item { SettingToggle("Show app labels", p.showLabels, fg, muted) { scope.launch { context.dataStore.edit { it[booleanPreferencesKey("labels")] = !p.showLabels } } } }
            item { Text("Grid columns: ${p.columns}", color = fg, modifier = Modifier.padding(top = 18.dp)) }
            item { Slider(value = p.columns.toFloat(), onValueChange = { n -> scope.launch { context.dataStore.edit { it[intPreferencesKey("columns")] = n.toInt().coerceIn(2,7) } } }, valueRange = 2f..7f, steps = 4) }
            item { OutlinedButton(onClick = { activity.chooseDefaultHome() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Home, null); Spacer(Modifier.width(8.dp)); Text("Set as default home") } }
            item { TextButton(onClick = { scope.launch { context.dataStore.edit { it.clear() } } }) { Text("Reset all settings") } }
        }
    }
}

@Composable
private fun LayoutsScreen(p: LauncherPrefs, fg: Color, muted: Color, back: () -> Unit, select: (HomeStyle) -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Header("Home styles", back, fg)
        Text("Presets are starting points; your other settings stay available.", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(HomeStyle.entries) { style ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (style == p.style) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .28f)).clickable { select(style) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(style.label, color = fg, fontWeight = FontWeight.Medium); Text(styleDescription(style), color = muted, fontSize = 12.sp) }
                    if (style == p.style) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
private fun styleDescription(style: HomeStyle) = when (style) {
    HomeStyle.PURE_MINIMAL -> "Clock, date and a short list of shortcuts"
    HomeStyle.CLASSIC -> "App grid and labels"
    HomeStyle.DASHBOARD -> "A compact information-first home"
    HomeStyle.WIDGET_CANVAS -> "A clear starting point for widgets"
    HomeStyle.TEXT -> "Large, readable app names"
    HomeStyle.ICON_FOCUS -> "Icons with minimal labels"
    HomeStyle.SPLIT -> "Clock-first split composition"
    HomeStyle.BLANK -> "Nearly empty; just your wallpaper"
}

@Composable
private fun ColorsScreen(p: LauncherPrefs, fg: Color, muted: Color, current: Color, back: () -> Unit, setAccent: (Color) -> Unit, setDark: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Header("Wallpaper & colors", back, fg)
        Text("Wallpaper colors are read on-device. Nothing is uploaded.", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Color(0xFF343434), Color(0xFF526B5D), Color(0xFF65769C), Color(0xFF9A6D79), Color(0xFFB07A42), Color(0xFF77689A)).forEach { c ->
                Box(Modifier.size(42.dp).clip(CircleShape).background(c).clickable { setAccent(c) }, contentAlignment = Alignment.Center) { if (c == current) Icon(Icons.Rounded.Check, null, tint = Color.White) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Dark mode", color = fg); Text("Use a darker background", color = muted, fontSize = 12.sp) }; Switch(checked = p.dark, onCheckedChange = setDark) }
        Spacer(Modifier.height(18.dp))
        Text("Preview", color = muted, fontSize = 12.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).padding(20.dp)) {
            Text("9:41", color = fg, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Text("Wednesday, October 22", color = muted)
            Spacer(Modifier.height(12.dp))
            Text("A quiet home screen.", color = fg)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(70.dp).height(4.dp).clip(CircleShape).background(current))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { setAccent(current); }, modifier = Modifier.fillMaxWidth()) { Text("Apply preview") }
    }
}

@Composable
private fun WidgetsScreen(activity: MainActivity, fg: Color, muted: Color, back: () -> Unit) {
    var widgetView by remember { mutableStateOf<android.view.View?>(null) }
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Header("Widgets", back, fg)
        Text("Third-party widgets are rendered by their own providers; their internal styling may not be customizable.", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { activity.beginAddWidget() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Android widget") }
        Spacer(Modifier.height(14.dp))
        Text("Saved widget preview", color = fg, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        val view = remember { activity.createWidgetView() }
        if (view != null) AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 320.dp))
        else Text("No saved widget yet. Choose Add Android widget to begin.", color = muted, fontSize = 13.sp)
    }
}

@Composable
private fun PrivacyScreen(fg: Color, muted: Color, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Header("Privacy", back, fg)
        Text("Private by design", color = fg, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        listOf(
            "No analytics, advertising SDKs, accounts or telemetry are included.",
            "The manifest does not request INTERNET permission. Core launcher functions work offline.",
            "App discovery and filtering happen locally on your device.",
            "Wallpaper palette extraction uses Android wallpaper color metadata.",
            "Hidden apps are hidden only from this launcher; they are not locked or concealed from Android.",
            "Calendar, location, contacts and usage access are not requested by this starter build.",
            "Android widgets may independently request network or other permissions through their own providers."
        ).forEach { item -> Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp)); Spacer(Modifier.width(10.dp)); Text(item, color = muted, fontSize = 14.sp) } }
        Spacer(Modifier.height(12.dp))
        Text("Open-source licenses", color = fg, fontWeight = FontWeight.Medium)
        Text("This project is an independent implementation. See LICENSE and THIRD_PARTY_NOTICES.md.", color = muted, fontSize = 13.sp)
    }
}

@Composable
private fun BackupScreen(context: android.content.Context, prefs: LauncherPrefs, fg: Color, muted: Color, back: () -> Unit) {
    var message by remember { mutableStateOf("Exports contain launcher preferences only, not third-party widget state.") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Header("Backup & restore", back, fg)
        Text(message, color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
            val obj = JSONObject().put("format", "still-launcher-profile").put("version", 1)
                .put("style", prefs.style.name).put("dark", prefs.dark).put("wallpaper", prefs.followWallpaper)
                .put("clock", prefs.showClock).put("date", prefs.showDate).put("labels", prefs.showLabels)
                .put("columns", prefs.columns).put("accent", prefs.accent.toString())
            val file = File(context.getExternalFilesDir(null), "still-profile.json")
            runCatching { file.writeText(obj.toString(2)); message = "Exported: ${file.absolutePath}" }
                .onFailure { message = "Export failed: ${it.localizedMessage}" }
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Export profile to app storage") }
        Text("For security, imported files should be validated before applying. Import UI is not yet included in this starter release.", color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun Header(title: String, back: () -> Unit, fg: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, "Back", tint = fg) }
        Text(title, color = fg, fontSize = 25.sp, fontWeight = FontWeight.Light)
    }
    Spacer(Modifier.height(8.dp))
}
@Composable
private fun SettingRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = click).padding(vertical = 14.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun SettingToggle(title: String, checked: Boolean, fg: Color, muted: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = fg); Text("Show this on the home screen", color = muted, fontSize = 12.sp) }
        Switch(checked = checked, onCheckedChange = { onClick() })
    }
}
