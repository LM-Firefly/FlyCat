@file:Suppress("RedundantVisibilityModifier", "MemberVisibilityCanBePrivate", "FunctionName", "PropertyName", "ObjectPropertyName", "ClassName", "NonAsciiCharacters", "unused")

package com.github.lmfirefly.flycat.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.lyricist.Lyricist
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings

public val LocalFlyStrings: ProvidableCompositionLocal<FlyStrings> =
    staticCompositionLocalOf { ZhHansStrings }

public val flyStrings: Map<String, FlyStrings> = mapOf(
    "zh-cn" to ZhHansStrings,
    "zh-hans" to ZhHansStrings,
    "zh-sg" to ZhHansStrings,
    "zh" to ZhHansStrings,
    "zh-tw" to ZhStrings,
    "zh-hant" to ZhStrings,
    "zh-hk" to ZhStrings,
    "zh-mo" to ZhStrings,
    "en" to EnStrings,
    "ja" to JaStrings,
    "ru" to RuStrings,
)

public val currentFlyStrings: FlyStrings
    @Composable
    get() = LocalFlyStrings.current

@Composable
public fun rememberFlyStrings(
    defaultLanguageTag: String = "zh-cn",
    currentLanguageTag: String = java.util.Locale.getDefault().toLanguageTag().lowercase(),
): Lyricist<FlyStrings> =
    rememberStrings(flyStrings, defaultLanguageTag, currentLanguageTag)

@Composable
public fun ProvideFlyStrings(
    lyricist: Lyricist<FlyStrings> = rememberFlyStrings(),
    content: @Composable () -> Unit,
) {
    ProvideStrings(lyricist, LocalFlyStrings, content)
}

public object FlyLocaleManager {
    @Volatile
    public var activeStrings: FlyStrings = ZhHansStrings
        internal set

    public fun updateLocale(tag: String) {
        val lower = tag.lowercase()
        val resolved = flyStrings[lower]
            ?: flyStrings[lower.split("-")[0]]
            ?: ZhHansStrings
        activeStrings = resolved
    }
}

public object FlyTxt {
    public object About {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.About.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.About.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.Title, *args)
        public object App {
            public val VersionLoading: String
                get() = FlyLocaleManager.activeStrings.About.App.VersionLoading
            @androidx.compose.runtime.Composable
            public fun VersionLoading(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.App.VersionLoading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.App.VersionLoading, *args)
            public val VersionFailed: String
                get() = FlyLocaleManager.activeStrings.About.App.VersionFailed
            @androidx.compose.runtime.Composable
            public fun VersionFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.App.VersionFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.App.VersionFailed, *args)
        }
        public object Section {
            public val ProjectLinks: String
                get() = FlyLocaleManager.activeStrings.About.Section.ProjectLinks
            @androidx.compose.runtime.Composable
            public fun ProjectLinks(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.Section.ProjectLinks else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.Section.ProjectLinks, *args)
            public val License: String
                get() = FlyLocaleManager.activeStrings.About.Section.License
            @androidx.compose.runtime.Composable
            public fun License(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.Section.License else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.Section.License, *args)
        }
        public object License {
            public val CheckUpdate: String
                get() = FlyLocaleManager.activeStrings.About.License.CheckUpdate
            @androidx.compose.runtime.Composable
            public fun CheckUpdate(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.License.CheckUpdate else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.License.CheckUpdate, *args)
            public val CheckUpdateSummary: String
                get() = FlyLocaleManager.activeStrings.About.License.CheckUpdateSummary
            @androidx.compose.runtime.Composable
            public fun CheckUpdateSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.License.CheckUpdateSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.License.CheckUpdateSummary, *args)
            public val Libraries: String
                get() = FlyLocaleManager.activeStrings.About.License.Libraries
            @androidx.compose.runtime.Composable
            public fun Libraries(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.License.Libraries else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.License.Libraries, *args)
            public val LibrariesSummary: String
                get() = FlyLocaleManager.activeStrings.About.License.LibrariesSummary
            @androidx.compose.runtime.Composable
            public fun LibrariesSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.License.LibrariesSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.License.LibrariesSummary, *args)
            public val AgplName: String
                get() = FlyLocaleManager.activeStrings.About.License.AgplName
            @androidx.compose.runtime.Composable
            public fun AgplName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.License.AgplName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.License.AgplName, *args)
            public val AgplDescription: String
                get() = FlyLocaleManager.activeStrings.About.License.AgplDescription
            @androidx.compose.runtime.Composable
            public fun AgplDescription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.About.License.AgplDescription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.License.AgplDescription, *args)
        }
        public val Copyright: String
            get() = FlyLocaleManager.activeStrings.About.Copyright
        @androidx.compose.runtime.Composable
        public fun Copyright(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.About.Copyright else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.About.Copyright, *args)
    }
    public object AccessControl {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.AccessControl.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Title, *args)
        public object Search {
            public val Placeholder: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Search.Placeholder
            @androidx.compose.runtime.Composable
            public fun Placeholder(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Search.Placeholder else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Search.Placeholder, *args)
            public val Empty: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Search.Empty
            @androidx.compose.runtime.Composable
            public fun Empty(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Search.Empty else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Search.Empty, *args)
        }
        public object AppList {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.AccessControl.AppList.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.AppList.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.AppList.Title, *args)
            public val Loading: String
                get() = FlyLocaleManager.activeStrings.AccessControl.AppList.Loading
            @androidx.compose.runtime.Composable
            public fun Loading(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.AppList.Loading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.AppList.Loading, *args)
        }
        public object Settings {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.Title, *args)
            public val ShowSystemApps: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.ShowSystemApps
            @androidx.compose.runtime.Composable
            public fun ShowSystemApps(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.ShowSystemApps else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.ShowSystemApps, *args)
            public val SelectedFirst: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.SelectedFirst
            @androidx.compose.runtime.Composable
            public fun SelectedFirst(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.SelectedFirst else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.SelectedFirst, *args)
            public val SortMode: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.SortMode
            @androidx.compose.runtime.Composable
            public fun SortMode(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.SortMode else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.SortMode, *args)
            public val SortModeCurrent: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.SortModeCurrent
            @androidx.compose.runtime.Composable
            public fun SortModeCurrent(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.SortModeCurrent else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.SortModeCurrent, *args)
            public val BatchOperation: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.BatchOperation
            @androidx.compose.runtime.Composable
            public fun BatchOperation(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.BatchOperation else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.BatchOperation, *args)
            public val SelectAll: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.SelectAll
            @androidx.compose.runtime.Composable
            public fun SelectAll(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.SelectAll else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.SelectAll, *args)
            public val DeselectAll: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.DeselectAll
            @androidx.compose.runtime.Composable
            public fun DeselectAll(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.DeselectAll else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.DeselectAll, *args)
            public val Invert: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.Invert
            @androidx.compose.runtime.Composable
            public fun Invert(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.Invert else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.Invert, *args)
            public val ImportExport: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.ImportExport
            @androidx.compose.runtime.Composable
            public fun ImportExport(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.ImportExport else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.ImportExport, *args)
            public val Import: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.Import
            @androidx.compose.runtime.Composable
            public fun Import(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.Import else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.Import, *args)
            public val Export: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.Export
            @androidx.compose.runtime.Composable
            public fun Export(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.Export else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.Export, *args)
            public val ImportSuccess: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.ImportSuccess
            @androidx.compose.runtime.Composable
            public fun ImportSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.ImportSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.ImportSuccess, *args)
            public val ExportSuccess: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.ExportSuccess
            @androidx.compose.runtime.Composable
            public fun ExportSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.ExportSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.ExportSuccess, *args)
            public val ImportFailed: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.ImportFailed
            @androidx.compose.runtime.Composable
            public fun ImportFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.ImportFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.ImportFailed, *args)
            public val RegionQuickSelect: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.RegionQuickSelect
            @androidx.compose.runtime.Composable
            public fun RegionQuickSelect(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.RegionQuickSelect else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.RegionQuickSelect, *args)
            public val ChinaApps: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.ChinaApps
            @androidx.compose.runtime.Composable
            public fun ChinaApps(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.ChinaApps else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.ChinaApps, *args)
            public val OverseasApps: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.OverseasApps
            @androidx.compose.runtime.Composable
            public fun OverseasApps(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.OverseasApps else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.OverseasApps, *args)
            public val RegionSelectResult: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Settings.RegionSelectResult
            @androidx.compose.runtime.Composable
            public fun RegionSelectResult(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Settings.RegionSelectResult else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Settings.RegionSelectResult, *args)
        }
        public object SortMode {
            public val PackageName: String
                get() = FlyLocaleManager.activeStrings.AccessControl.SortMode.PackageName
            @androidx.compose.runtime.Composable
            public fun PackageName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.SortMode.PackageName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.SortMode.PackageName, *args)
            public val Label: String
                get() = FlyLocaleManager.activeStrings.AccessControl.SortMode.Label
            @androidx.compose.runtime.Composable
            public fun Label(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.SortMode.Label else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.SortMode.Label, *args)
            public val InstallTime: String
                get() = FlyLocaleManager.activeStrings.AccessControl.SortMode.InstallTime
            @androidx.compose.runtime.Composable
            public fun InstallTime(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.SortMode.InstallTime else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.SortMode.InstallTime, *args)
            public val UpdateTime: String
                get() = FlyLocaleManager.activeStrings.AccessControl.SortMode.UpdateTime
            @androidx.compose.runtime.Composable
            public fun UpdateTime(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.SortMode.UpdateTime else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.SortMode.UpdateTime, *args)
        }
        public object Button {
            public val Cancel: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Button.Cancel
            @androidx.compose.runtime.Composable
            public fun Cancel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Button.Cancel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Button.Cancel, *args)
            public val Confirm: String
                get() = FlyLocaleManager.activeStrings.AccessControl.Button.Confirm
            @androidx.compose.runtime.Composable
            public fun Confirm(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AccessControl.Button.Confirm else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AccessControl.Button.Confirm, *args)
        }
    }
    public object AppSettings {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.AppSettings.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Title, *args)
        public object Section {
            public val Behavior: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Section.Behavior
            @androidx.compose.runtime.Composable
            public fun Behavior(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Section.Behavior else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Section.Behavior, *args)
            public val Interface: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Section.Interface
            @androidx.compose.runtime.Composable
            public fun Interface(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Section.Interface else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Section.Interface, *args)
            public val Navigation: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Section.Navigation
            @androidx.compose.runtime.Composable
            public fun Navigation(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Section.Navigation else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Section.Navigation, *args)
            public val Privacy: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Section.Privacy
            @androidx.compose.runtime.Composable
            public fun Privacy(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Section.Privacy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Section.Privacy, *args)
            public val Service: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Section.Service
            @androidx.compose.runtime.Composable
            public fun Service(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Section.Service else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Section.Service, *args)
            public val Network: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Section.Network
            @androidx.compose.runtime.Composable
            public fun Network(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Section.Network else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Section.Network, *args)
            public val Home: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Section.Home
            @androidx.compose.runtime.Composable
            public fun Home(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Section.Home else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Section.Home, *args)
        }
        public object Behavior {
            public val AutoStartTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoStartTitle
            @androidx.compose.runtime.Composable
            public fun AutoStartTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoStartTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoStartTitle, *args)
            public val AutoStartSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoStartSummary
            @androidx.compose.runtime.Composable
            public fun AutoStartSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoStartSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoStartSummary, *args)
            public val AutoUpdateOnStartTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoUpdateOnStartTitle
            @androidx.compose.runtime.Composable
            public fun AutoUpdateOnStartTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoUpdateOnStartTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoUpdateOnStartTitle, *args)
            public val AutoUpdateOnStartSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoUpdateOnStartSummary
            @androidx.compose.runtime.Composable
            public fun AutoUpdateOnStartSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoUpdateOnStartSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Behavior.AutoUpdateOnStartSummary, *args)
            public val SystemAutoStartGrantToast: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Behavior.SystemAutoStartGrantToast
            @androidx.compose.runtime.Composable
            public fun SystemAutoStartGrantToast(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Behavior.SystemAutoStartGrantToast else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Behavior.SystemAutoStartGrantToast, *args)
        }
        public object Interface {
            public val ThemeModeTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeTitle
            @androidx.compose.runtime.Composable
            public fun ThemeModeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeTitle, *args)
            public val ThemeModeSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeSummary
            @androidx.compose.runtime.Composable
            public fun ThemeModeSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeSummary, *args)
            public val ThemeModeSystem: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeSystem
            @androidx.compose.runtime.Composable
            public fun ThemeModeSystem(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeSystem else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeSystem, *args)
            public val ThemeModeLight: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeLight
            @androidx.compose.runtime.Composable
            public fun ThemeModeLight(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeLight else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeLight, *args)
            public val ThemeModeDark: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeDark
            @androidx.compose.runtime.Composable
            public fun ThemeModeDark(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeDark else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeModeDark, *args)
            public val LanguageTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageTitle
            @androidx.compose.runtime.Composable
            public fun LanguageTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageTitle, *args)
            public val LanguageSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageSummary
            @androidx.compose.runtime.Composable
            public fun LanguageSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageSummary, *args)
            public val LanguageSystem: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageSystem
            @androidx.compose.runtime.Composable
            public fun LanguageSystem(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageSystem else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageSystem, *args)
            public val LanguageChinese: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageChinese
            @androidx.compose.runtime.Composable
            public fun LanguageChinese(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageChinese else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageChinese, *args)
            public val LanguageChineseTraditional: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageChineseTraditional
            @androidx.compose.runtime.Composable
            public fun LanguageChineseTraditional(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageChineseTraditional else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageChineseTraditional, *args)
            public val LanguageEnglish: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageEnglish
            @androidx.compose.runtime.Composable
            public fun LanguageEnglish(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageEnglish else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageEnglish, *args)
            public val LanguageJapanese: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageJapanese
            @androidx.compose.runtime.Composable
            public fun LanguageJapanese(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageJapanese else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageJapanese, *args)
            public val LanguageRussian: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageRussian
            @androidx.compose.runtime.Composable
            public fun LanguageRussian(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageRussian else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.LanguageRussian, *args)
            public val ColorThemeTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeTitle
            @androidx.compose.runtime.Composable
            public fun ColorThemeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeTitle, *args)
            public val ColorThemePickerTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemePickerTitle
            @androidx.compose.runtime.Composable
            public fun ColorThemePickerTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemePickerTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemePickerTitle, *args)
            public val ColorThemeCodeLabel: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeCodeLabel
            @androidx.compose.runtime.Composable
            public fun ColorThemeCodeLabel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeCodeLabel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeCodeLabel, *args)
            public val ColorThemeCustomSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeCustomSummary
            @androidx.compose.runtime.Composable
            public fun ColorThemeCustomSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeCustomSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ColorThemeCustomSummary, *args)
            public val ThemeColorPolarityInvertTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeColorPolarityInvertTitle
            @androidx.compose.runtime.Composable
            public fun ThemeColorPolarityInvertTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeColorPolarityInvertTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeColorPolarityInvertTitle, *args)
            public val ThemeColorPolarityInvertSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeColorPolarityInvertSummary
            @androidx.compose.runtime.Composable
            public fun ThemeColorPolarityInvertSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeColorPolarityInvertSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ThemeColorPolarityInvertSummary, *args)
            public val TopBarBlurTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.TopBarBlurTitle
            @androidx.compose.runtime.Composable
            public fun TopBarBlurTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.TopBarBlurTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.TopBarBlurTitle, *args)
            public val TopBarBlurSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.TopBarBlurSummary
            @androidx.compose.runtime.Composable
            public fun TopBarBlurSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.TopBarBlurSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.TopBarBlurSummary, *args)
            public val AutoHideNavbarTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.AutoHideNavbarTitle
            @androidx.compose.runtime.Composable
            public fun AutoHideNavbarTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.AutoHideNavbarTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.AutoHideNavbarTitle, *args)
            public val AutoHideNavbarSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.AutoHideNavbarSummary
            @androidx.compose.runtime.Composable
            public fun AutoHideNavbarSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.AutoHideNavbarSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.AutoHideNavbarSummary, *args)
            public val PageScaleTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.PageScaleTitle
            @androidx.compose.runtime.Composable
            public fun PageScaleTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.PageScaleTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.PageScaleTitle, *args)
            public val PageScaleDialogSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.PageScaleDialogSummary
            @androidx.compose.runtime.Composable
            public fun PageScaleDialogSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.PageScaleDialogSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.PageScaleDialogSummary, *args)
            public val ClassicHomeTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.ClassicHomeTitle
            @androidx.compose.runtime.Composable
            public fun ClassicHomeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.ClassicHomeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.ClassicHomeTitle, *args)
            public val HitokotoTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HitokotoTitle
            @androidx.compose.runtime.Composable
            public fun HitokotoTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HitokotoTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HitokotoTitle, *args)
            public val HitokotoSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HitokotoSummary
            @androidx.compose.runtime.Composable
            public fun HitokotoSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HitokotoSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HitokotoSummary, *args)
            public val SidebarExpandedTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.SidebarExpandedTitle
            @androidx.compose.runtime.Composable
            public fun SidebarExpandedTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.SidebarExpandedTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.SidebarExpandedTitle, *args)
            public val HomeQuoteTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteTitle
            @androidx.compose.runtime.Composable
            public fun HomeQuoteTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteTitle, *args)
            public val HomeQuoteDefault: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteDefault
            @androidx.compose.runtime.Composable
            public fun HomeQuoteDefault(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteDefault else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteDefault, *args)
            public val HomeQuoteAuthorTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteAuthorTitle
            @androidx.compose.runtime.Composable
            public fun HomeQuoteAuthorTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteAuthorTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteAuthorTitle, *args)
            public val HomeQuoteAuthorDefault: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteAuthorDefault
            @androidx.compose.runtime.Composable
            public fun HomeQuoteAuthorDefault(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteAuthorDefault else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeQuoteAuthorDefault, *args)
            public val EditHomeQuoteTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.EditHomeQuoteTitle
            @androidx.compose.runtime.Composable
            public fun EditHomeQuoteTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.EditHomeQuoteTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.EditHomeQuoteTitle, *args)
            public val EditHomeQuoteAuthorTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.EditHomeQuoteAuthorTitle
            @androidx.compose.runtime.Composable
            public fun EditHomeQuoteAuthorTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.EditHomeQuoteAuthorTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.EditHomeQuoteAuthorTitle, *args)
            public val HomeWallpaperImportFailed: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperImportFailed
            @androidx.compose.runtime.Composable
            public fun HomeWallpaperImportFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperImportFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperImportFailed, *args)
            public val HomeWallpaperSourceTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceTitle
            @androidx.compose.runtime.Composable
            public fun HomeWallpaperSourceTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceTitle, *args)
            public val HomeWallpaperSourceSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceSummary
            @androidx.compose.runtime.Composable
            public fun HomeWallpaperSourceSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceSummary, *args)
            public val HomeWallpaperSourceGallery: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceGallery
            @androidx.compose.runtime.Composable
            public fun HomeWallpaperSourceGallery(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceGallery else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceGallery, *args)
            public val HomeWallpaperSourceUrl: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceUrl
            @androidx.compose.runtime.Composable
            public fun HomeWallpaperSourceUrl(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceUrl else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperSourceUrl, *args)
            public val HomeWallpaperUrlDialogTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperUrlDialogTitle
            @androidx.compose.runtime.Composable
            public fun HomeWallpaperUrlDialogTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperUrlDialogTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.HomeWallpaperUrlDialogTitle, *args)
            public val WallpaperScrimTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Interface.WallpaperScrimTitle
            @androidx.compose.runtime.Composable
            public fun WallpaperScrimTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Interface.WallpaperScrimTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Interface.WallpaperScrimTitle, *args)
        }
        public object Privacy {
            public val HideIconTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Privacy.HideIconTitle
            @androidx.compose.runtime.Composable
            public fun HideIconTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Privacy.HideIconTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Privacy.HideIconTitle, *args)
            public val HideIconSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Privacy.HideIconSummary
            @androidx.compose.runtime.Composable
            public fun HideIconSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Privacy.HideIconSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Privacy.HideIconSummary, *args)
            public val HideFromRecentsTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Privacy.HideFromRecentsTitle
            @androidx.compose.runtime.Composable
            public fun HideFromRecentsTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Privacy.HideFromRecentsTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Privacy.HideFromRecentsTitle, *args)
            public val HideFromRecentsSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Privacy.HideFromRecentsSummary
            @androidx.compose.runtime.Composable
            public fun HideFromRecentsSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Privacy.HideFromRecentsSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Privacy.HideFromRecentsSummary, *args)
        }
        public object ServiceSection {
            public val TrafficNotificationTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.TrafficNotificationTitle
            @androidx.compose.runtime.Composable
            public fun TrafficNotificationTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.TrafficNotificationTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.TrafficNotificationTitle, *args)
            public val TrafficNotificationSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.TrafficNotificationSummary
            @androidx.compose.runtime.Composable
            public fun TrafficNotificationSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.TrafficNotificationSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.TrafficNotificationSummary, *args)
            public val LogLevelTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.LogLevelTitle
            @androidx.compose.runtime.Composable
            public fun LogLevelTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.LogLevelTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.LogLevelTitle, *args)
            public val LogLevelSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.LogLevelSummary
            @androidx.compose.runtime.Composable
            public fun LogLevelSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.LogLevelSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.LogLevelSummary, *args)
            public val SingleNodeTestTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SingleNodeTestTitle
            @androidx.compose.runtime.Composable
            public fun SingleNodeTestTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SingleNodeTestTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SingleNodeTestTitle, *args)
            public val SingleNodeTestSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SingleNodeTestSummary
            @androidx.compose.runtime.Composable
            public fun SingleNodeTestSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SingleNodeTestSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SingleNodeTestSummary, *args)
            public val ExitUiWhenBackgroundTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ExitUiWhenBackgroundTitle
            @androidx.compose.runtime.Composable
            public fun ExitUiWhenBackgroundTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ExitUiWhenBackgroundTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ExitUiWhenBackgroundTitle, *args)
            public val ExitUiWhenBackgroundSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ExitUiWhenBackgroundSummary
            @androidx.compose.runtime.Composable
            public fun ExitUiWhenBackgroundSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ExitUiWhenBackgroundSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ExitUiWhenBackgroundSummary, *args)
            public val SuperIslandTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SuperIslandTitle
            @androidx.compose.runtime.Composable
            public fun SuperIslandTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SuperIslandTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.SuperIslandTitle, *args)
            public val ShizukuTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuTitle
            @androidx.compose.runtime.Composable
            public fun ShizukuTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuTitle, *args)
            public val ShizukuNotRunning: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuNotRunning
            @androidx.compose.runtime.Composable
            public fun ShizukuNotRunning(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuNotRunning else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuNotRunning, *args)
            public val ShizukuPermissionRequired: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuPermissionRequired
            @androidx.compose.runtime.Composable
            public fun ShizukuPermissionRequired(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuPermissionRequired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuPermissionRequired, *args)
            public val ShizukuReady: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuReady
            @androidx.compose.runtime.Composable
            public fun ShizukuReady(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuReady else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuReady, *args)
            public val ShizukuStatusUnavailable: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusUnavailable
            @androidx.compose.runtime.Composable
            public fun ShizukuStatusUnavailable(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusUnavailable else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusUnavailable, *args)
            public val ShizukuStatusPending: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusPending
            @androidx.compose.runtime.Composable
            public fun ShizukuStatusPending(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusPending else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusPending, *args)
            public val ShizukuStatusGranted: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusGranted
            @androidx.compose.runtime.Composable
            public fun ShizukuStatusGranted(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusGranted else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.ShizukuStatusGranted, *args)
            public val BatteryOptimizationTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationTitle
            @androidx.compose.runtime.Composable
            public fun BatteryOptimizationTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationTitle, *args)
            public val BatteryOptimizationSummaryEnabled: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationSummaryEnabled
            @androidx.compose.runtime.Composable
            public fun BatteryOptimizationSummaryEnabled(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationSummaryEnabled else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationSummaryEnabled, *args)
            public val BatteryOptimizationSummaryDisabled: String
                get() = FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationSummaryDisabled
            @androidx.compose.runtime.Composable
            public fun BatteryOptimizationSummaryDisabled(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationSummaryDisabled else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.ServiceSection.BatteryOptimizationSummaryDisabled, *args)
        }
        public object Network {
            public val UpdateChannelTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelTitle
            @androidx.compose.runtime.Composable
            public fun UpdateChannelTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelTitle, *args)
            public val UpdateChannelSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelSummary
            @androidx.compose.runtime.Composable
            public fun UpdateChannelSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelSummary, *args)
            public val UpdateChannelStable: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelStable
            @androidx.compose.runtime.Composable
            public fun UpdateChannelStable(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelStable else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelStable, *args)
            public val UpdateChannelPre: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelPre
            @androidx.compose.runtime.Composable
            public fun UpdateChannelPre(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelPre else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelPre, *args)
            public val UpdateChannelSmart: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelSmart
            @androidx.compose.runtime.Composable
            public fun UpdateChannelSmart(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelSmart else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.UpdateChannelSmart, *args)
            public val AutoCheckAppUpdateTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.AutoCheckAppUpdateTitle
            @androidx.compose.runtime.Composable
            public fun AutoCheckAppUpdateTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.AutoCheckAppUpdateTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.AutoCheckAppUpdateTitle, *args)
            public val AutoCheckAppUpdateSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.AutoCheckAppUpdateSummary
            @androidx.compose.runtime.Composable
            public fun AutoCheckAppUpdateSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.AutoCheckAppUpdateSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.AutoCheckAppUpdateSummary, *args)
            public val CustomUserAgentTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.CustomUserAgentTitle
            @androidx.compose.runtime.Composable
            public fun CustomUserAgentTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.CustomUserAgentTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.CustomUserAgentTitle, *args)
            public val CustomUserAgentSummaryDefault: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Network.CustomUserAgentSummaryDefault
            @androidx.compose.runtime.Composable
            public fun CustomUserAgentSummaryDefault(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Network.CustomUserAgentSummaryDefault else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Network.CustomUserAgentSummaryDefault, *args)
        }
        public object PredictiveBack {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.Title, *args)
            public val RestartSummary: String
                get() = FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.RestartSummary
            @androidx.compose.runtime.Composable
            public fun RestartSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.RestartSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.RestartSummary, *args)
            public val ProgressTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.ProgressTitle
            @androidx.compose.runtime.Composable
            public fun ProgressTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.ProgressTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.PredictiveBack.ProgressTitle, *args)
        }
        public object WarningDialog {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.AppSettings.WarningDialog.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.WarningDialog.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.WarningDialog.Title, *args)
            public val HideIconMsg1: String
                get() = FlyLocaleManager.activeStrings.AppSettings.WarningDialog.HideIconMsg1
            @androidx.compose.runtime.Composable
            public fun HideIconMsg1(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.WarningDialog.HideIconMsg1 else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.WarningDialog.HideIconMsg1, *args)
            public val HideIconMsg2: String
                get() = FlyLocaleManager.activeStrings.AppSettings.WarningDialog.HideIconMsg2
            @androidx.compose.runtime.Composable
            public fun HideIconMsg2(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.WarningDialog.HideIconMsg2 else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.WarningDialog.HideIconMsg2, *args)
        }
        public object EditDialog {
            public val UserAgentTitle: String
                get() = FlyLocaleManager.activeStrings.AppSettings.EditDialog.UserAgentTitle
            @androidx.compose.runtime.Composable
            public fun UserAgentTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.EditDialog.UserAgentTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.EditDialog.UserAgentTitle, *args)
        }
        public object Button {
            public val Apply: String
                get() = FlyLocaleManager.activeStrings.AppSettings.Button.Apply
            @androidx.compose.runtime.Composable
            public fun Apply(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.AppSettings.Button.Apply else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.AppSettings.Button.Apply, *args)
        }
    }
    public object Component {
        public object ProfileCard {
            public val Update: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.Update
            @androidx.compose.runtime.Composable
            public fun Update(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.Update else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.Update, *args)
            public val Delete: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.Delete
            @androidx.compose.runtime.Composable
            public fun Delete(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.Delete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.Delete, *args)
            public val Edit: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.Edit
            @androidx.compose.runtime.Composable
            public fun Edit(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.Edit else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.Edit, *args)
            public val RemoteSubscription: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.RemoteSubscription
            @androidx.compose.runtime.Composable
            public fun RemoteSubscription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.RemoteSubscription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.RemoteSubscription, *args)
            public val LocalFile: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.LocalFile
            @androidx.compose.runtime.Composable
            public fun LocalFile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.LocalFile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.LocalFile, *args)
            public val LocalConfig: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.LocalConfig
            @androidx.compose.runtime.Composable
            public fun LocalConfig(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.LocalConfig else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.LocalConfig, *args)
            public val ClickToUpdate: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.ClickToUpdate
            @androidx.compose.runtime.Composable
            public fun ClickToUpdate(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.ClickToUpdate else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.ClickToUpdate, *args)
            public val Traffic: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.Traffic
            @androidx.compose.runtime.Composable
            public fun Traffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.Traffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.Traffic, *args)
            public val UsedTraffic: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.UsedTraffic
            @androidx.compose.runtime.Composable
            public fun UsedTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.UsedTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.UsedTraffic, *args)
            public val ExpireAt: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.ExpireAt
            @androidx.compose.runtime.Composable
            public fun ExpireAt(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.ExpireAt else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.ExpireAt, *args)
            public val ExpireToday: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.ExpireToday
            @androidx.compose.runtime.Composable
            public fun ExpireToday(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.ExpireToday else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.ExpireToday, *args)
            public val Expired: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.Expired
            @androidx.compose.runtime.Composable
            public fun Expired(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.Expired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.Expired, *args)
            public val JustNow: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.JustNow
            @androidx.compose.runtime.Composable
            public fun JustNow(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.JustNow else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.JustNow, *args)
            public val MinutesAgo: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.MinutesAgo
            @androidx.compose.runtime.Composable
            public fun MinutesAgo(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.MinutesAgo else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.MinutesAgo, *args)
            public val HoursAgo: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.HoursAgo
            @androidx.compose.runtime.Composable
            public fun HoursAgo(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.HoursAgo else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.HoursAgo, *args)
            public val DaysAgo: String
                get() = FlyLocaleManager.activeStrings.Component.ProfileCard.DaysAgo
            @androidx.compose.runtime.Composable
            public fun DaysAgo(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ProfileCard.DaysAgo else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ProfileCard.DaysAgo, *args)
        }
        public object Action {
            public val Export: String
                get() = FlyLocaleManager.activeStrings.Component.Action.Export
            @androidx.compose.runtime.Composable
            public fun Export(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Action.Export else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Action.Export, *args)
            public val Delete: String
                get() = FlyLocaleManager.activeStrings.Component.Action.Delete
            @androidx.compose.runtime.Composable
            public fun Delete(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Action.Delete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Action.Delete, *args)
            public val Update: String
                get() = FlyLocaleManager.activeStrings.Component.Action.Update
            @androidx.compose.runtime.Composable
            public fun Update(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Action.Update else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Action.Update, *args)
            public val Edit: String
                get() = FlyLocaleManager.activeStrings.Component.Action.Edit
            @androidx.compose.runtime.Composable
            public fun Edit(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Action.Edit else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Action.Edit, *args)
            public val Cancel: String
                get() = FlyLocaleManager.activeStrings.Component.Action.Cancel
            @androidx.compose.runtime.Composable
            public fun Cancel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Action.Cancel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Action.Cancel, *args)
            public val Reset: String
                get() = FlyLocaleManager.activeStrings.Component.Action.Reset
            @androidx.compose.runtime.Composable
            public fun Reset(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Action.Reset else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Action.Reset, *args)
            public val Add: String
                get() = FlyLocaleManager.activeStrings.Component.Action.Add
            @androidx.compose.runtime.Composable
            public fun Add(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Action.Add else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Action.Add, *args)
        }
        public object WebView {
            public val InvalidUrl: String
                get() = FlyLocaleManager.activeStrings.Component.WebView.InvalidUrl
            @androidx.compose.runtime.Composable
            public fun InvalidUrl(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.WebView.InvalidUrl else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.WebView.InvalidUrl, *args)
        }
        public object Selector {
            public val NotModify: String
                get() = FlyLocaleManager.activeStrings.Component.Selector.NotModify
            @androidx.compose.runtime.Composable
            public fun NotModify(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Selector.NotModify else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Selector.NotModify, *args)
            public val Enable: String
                get() = FlyLocaleManager.activeStrings.Component.Selector.Enable
            @androidx.compose.runtime.Composable
            public fun Enable(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Selector.Enable else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Selector.Enable, *args)
            public val Disable: String
                get() = FlyLocaleManager.activeStrings.Component.Selector.Disable
            @androidx.compose.runtime.Composable
            public fun Disable(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Selector.Disable else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Selector.Disable, *args)
            public val Replace: String
                get() = FlyLocaleManager.activeStrings.Component.Selector.Replace
            @androidx.compose.runtime.Composable
            public fun Replace(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Selector.Replace else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Selector.Replace, *args)
            public val Prepend: String
                get() = FlyLocaleManager.activeStrings.Component.Selector.Prepend
            @androidx.compose.runtime.Composable
            public fun Prepend(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Selector.Prepend else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Selector.Prepend, *args)
            public val Append: String
                get() = FlyLocaleManager.activeStrings.Component.Selector.Append
            @androidx.compose.runtime.Composable
            public fun Append(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Selector.Append else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Selector.Append, *args)
            public val Merge: String
                get() = FlyLocaleManager.activeStrings.Component.Selector.Merge
            @androidx.compose.runtime.Composable
            public fun Merge(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Selector.Merge else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Selector.Merge, *args)
        }
        public object Navigation {
            public val Back: String
                get() = FlyLocaleManager.activeStrings.Component.Navigation.Back
            @androidx.compose.runtime.Composable
            public fun Back(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Navigation.Back else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Navigation.Back, *args)
        }
        public object Message {
            public val Confirm: String
                get() = FlyLocaleManager.activeStrings.Component.Message.Confirm
            @androidx.compose.runtime.Composable
            public fun Confirm(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Message.Confirm else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Message.Confirm, *args)
            public val Hint: String
                get() = FlyLocaleManager.activeStrings.Component.Message.Hint
            @androidx.compose.runtime.Composable
            public fun Hint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Message.Hint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Message.Hint, *args)
            public val Error: String
                get() = FlyLocaleManager.activeStrings.Component.Message.Error
            @androidx.compose.runtime.Composable
            public fun Error(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Message.Error else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Message.Error, *args)
            public val Success: String
                get() = FlyLocaleManager.activeStrings.Component.Message.Success
            @androidx.compose.runtime.Composable
            public fun Success(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Message.Success else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Message.Success, *args)
        }
        public object Button {
            public val Cancel: String
                get() = FlyLocaleManager.activeStrings.Component.Button.Cancel
            @androidx.compose.runtime.Composable
            public fun Cancel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Button.Cancel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Button.Cancel, *args)
            public val Confirm: String
                get() = FlyLocaleManager.activeStrings.Component.Button.Confirm
            @androidx.compose.runtime.Composable
            public fun Confirm(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Button.Confirm else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Button.Confirm, *args)
            public val Clear: String
                get() = FlyLocaleManager.activeStrings.Component.Button.Clear
            @androidx.compose.runtime.Composable
            public fun Clear(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Button.Clear else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Button.Clear, *args)
            public val Copy: String
                get() = FlyLocaleManager.activeStrings.Component.Button.Copy
            @androidx.compose.runtime.Composable
            public fun Copy(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Button.Copy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Button.Copy, *args)
        }
        public object Flag {
            public val ContentDescription: String
                get() = FlyLocaleManager.activeStrings.Component.Flag.ContentDescription
            @androidx.compose.runtime.Composable
            public fun ContentDescription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Flag.ContentDescription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Flag.ContentDescription, *args)
        }
        public object Loading {
            public val Starting: String
                get() = FlyLocaleManager.activeStrings.Component.Loading.Starting
            @androidx.compose.runtime.Composable
            public fun Starting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Loading.Starting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Loading.Starting, *args)
        }
        public object Update {
            public object Action {
                public val DownloadNow: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Action.DownloadNow
                @androidx.compose.runtime.Composable
                public fun DownloadNow(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Action.DownloadNow else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Action.DownloadNow, *args)
            }
            public object Title {
                public val Available: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Title.Available
                @androidx.compose.runtime.Composable
                public fun Available(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Title.Available else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Title.Available, *args)
            }
            public object Message {
                public val Available: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.Available
                @androidx.compose.runtime.Composable
                public fun Available(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.Available else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.Available, *args)
                public val SelectPackage: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.SelectPackage
                @androidx.compose.runtime.Composable
                public fun SelectPackage(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.SelectPackage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.SelectPackage, *args)
                public val Tag: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.Tag
                @androidx.compose.runtime.Composable
                public fun Tag(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.Tag else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.Tag, *args)
                public val PackageUniversal: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.PackageUniversal
                @androidx.compose.runtime.Composable
                public fun PackageUniversal(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.PackageUniversal else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.PackageUniversal, *args)
                public val PackageExtension: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.PackageExtension
                @androidx.compose.runtime.Composable
                public fun PackageExtension(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.PackageExtension else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.PackageExtension, *args)
                public val PackageStandard: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.PackageStandard
                @androidx.compose.runtime.Composable
                public fun PackageStandard(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.PackageStandard else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.PackageStandard, *args)
                public val CurrentVersion: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.CurrentVersion
                @androidx.compose.runtime.Composable
                public fun CurrentVersion(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.CurrentVersion else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.CurrentVersion, *args)
                public val RemoteVersion: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.RemoteVersion
                @androidx.compose.runtime.Composable
                public fun RemoteVersion(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.RemoteVersion else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.RemoteVersion, *args)
                public val Preparing: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.Preparing
                @androidx.compose.runtime.Composable
                public fun Preparing(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.Preparing else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.Preparing, *args)
                public val DownloadingWithProgress: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.DownloadingWithProgress
                @androidx.compose.runtime.Composable
                public fun DownloadingWithProgress(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.DownloadingWithProgress else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.DownloadingWithProgress, *args)
                public val Downloading: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.Downloading
                @androidx.compose.runtime.Composable
                public fun Downloading(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.Downloading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.Downloading, *args)
                public val DownloadReady: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.DownloadReady
                @androidx.compose.runtime.Composable
                public fun DownloadReady(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.DownloadReady else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.DownloadReady, *args)
                public val Error: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.Error
                @androidx.compose.runtime.Composable
                public fun Error(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.Error else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.Error, *args)
                public val Checking: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.Checking
                @androidx.compose.runtime.Composable
                public fun Checking(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.Checking else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.Checking, *args)
                public val NoUpdate: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.NoUpdate
                @androidx.compose.runtime.Composable
                public fun NoUpdate(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.NoUpdate else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.NoUpdate, *args)
                public val CheckFailed: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.CheckFailed
                @androidx.compose.runtime.Composable
                public fun CheckFailed(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.CheckFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.CheckFailed, *args)
                public val MissingReleaseMetadata: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.MissingReleaseMetadata
                @androidx.compose.runtime.Composable
                public fun MissingReleaseMetadata(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.MissingReleaseMetadata else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.MissingReleaseMetadata, *args)
                public val NoCompatibleAsset: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.NoCompatibleAsset
                @androidx.compose.runtime.Composable
                public fun NoCompatibleAsset(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.NoCompatibleAsset else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.NoCompatibleAsset, *args)
                public val DownloadAlreadyRunning: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.DownloadAlreadyRunning
                @androidx.compose.runtime.Composable
                public fun DownloadAlreadyRunning(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.DownloadAlreadyRunning else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.DownloadAlreadyRunning, *args)
                public val InstallPromptOpened: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.InstallPromptOpened
                @androidx.compose.runtime.Composable
                public fun InstallPromptOpened(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.InstallPromptOpened else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.InstallPromptOpened, *args)
                public val InstallFailed: String
                    get() = FlyLocaleManager.activeStrings.Component.Update.Message.InstallFailed
                @androidx.compose.runtime.Composable
                public fun InstallFailed(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Update.Message.InstallFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Update.Message.InstallFailed, *args)
            }
        }
        public object ConfigInput {
            public val PortLabel: String
                get() = FlyLocaleManager.activeStrings.Component.ConfigInput.PortLabel
            @androidx.compose.runtime.Composable
            public fun PortLabel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ConfigInput.PortLabel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ConfigInput.PortLabel, *args)
            public val CountItems: String
                get() = FlyLocaleManager.activeStrings.Component.ConfigInput.CountItems
            @androidx.compose.runtime.Composable
            public fun CountItems(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ConfigInput.CountItems else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ConfigInput.CountItems, *args)
            public val Replace: String
                get() = FlyLocaleManager.activeStrings.Component.ConfigInput.Replace
            @androidx.compose.runtime.Composable
            public fun Replace(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ConfigInput.Replace else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ConfigInput.Replace, *args)
            public val ReplaceSummary: String
                get() = FlyLocaleManager.activeStrings.Component.ConfigInput.ReplaceSummary
            @androidx.compose.runtime.Composable
            public fun ReplaceSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ConfigInput.ReplaceSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ConfigInput.ReplaceSummary, *args)
            public val Merge: String
                get() = FlyLocaleManager.activeStrings.Component.ConfigInput.Merge
            @androidx.compose.runtime.Composable
            public fun Merge(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ConfigInput.Merge else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ConfigInput.Merge, *args)
            public val MergeSummary: String
                get() = FlyLocaleManager.activeStrings.Component.ConfigInput.MergeSummary
            @androidx.compose.runtime.Composable
            public fun MergeSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.ConfigInput.MergeSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.ConfigInput.MergeSummary, *args)
        }
        public object BottomBar {
            public val Home: String
                get() = FlyLocaleManager.activeStrings.Component.BottomBar.Home
            @androidx.compose.runtime.Composable
            public fun Home(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.BottomBar.Home else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.BottomBar.Home, *args)
            public val Proxy: String
                get() = FlyLocaleManager.activeStrings.Component.BottomBar.Proxy
            @androidx.compose.runtime.Composable
            public fun Proxy(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.BottomBar.Proxy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.BottomBar.Proxy, *args)
            public val Config: String
                get() = FlyLocaleManager.activeStrings.Component.BottomBar.Config
            @androidx.compose.runtime.Composable
            public fun Config(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.BottomBar.Config else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.BottomBar.Config, *args)
            public val Setting: String
                get() = FlyLocaleManager.activeStrings.Component.BottomBar.Setting
            @androidx.compose.runtime.Composable
            public fun Setting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.BottomBar.Setting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.BottomBar.Setting, *args)
        }
        public object Editor {
            public val CountItems: String
                get() = FlyLocaleManager.activeStrings.Component.Editor.CountItems
            @androidx.compose.runtime.Composable
            public fun CountItems(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.CountItems else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.CountItems, *args)
            public object Action {
                public val Search: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Action.Search
                @androidx.compose.runtime.Composable
                public fun Search(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Action.Search else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Action.Search, *args)
            }
            public object Dialog {
                public val AddTitle: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Dialog.AddTitle
                @androidx.compose.runtime.Composable
                public fun AddTitle(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Dialog.AddTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Dialog.AddTitle, *args)
                public val EditTitle: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Dialog.EditTitle
                @androidx.compose.runtime.Composable
                public fun EditTitle(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Dialog.EditTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Dialog.EditTitle, *args)
                public val ResetTitle: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Dialog.ResetTitle
                @androidx.compose.runtime.Composable
                public fun ResetTitle(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Dialog.ResetTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Dialog.ResetTitle, *args)
                public val ResetMessage: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Dialog.ResetMessage
                @androidx.compose.runtime.Composable
                public fun ResetMessage(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Dialog.ResetMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Dialog.ResetMessage, *args)
            }
            public object Empty {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Empty.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Empty.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Empty.Title, *args)
                public val Hint: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Empty.Hint
                @androidx.compose.runtime.Composable
                public fun Hint(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Empty.Hint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Empty.Hint, *args)
            }
            public object Error {
                public val KeyEmpty: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Error.KeyEmpty
                @androidx.compose.runtime.Composable
                public fun KeyEmpty(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Error.KeyEmpty else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Error.KeyEmpty, *args)
                public val KeyExists: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Error.KeyExists
                @androidx.compose.runtime.Composable
                public fun KeyExists(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Error.KeyExists else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Error.KeyExists, *args)
            }
            public object Rule {
                public val Type: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.Type
                @androidx.compose.runtime.Composable
                public fun Type(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.Type else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.Type, *args)
                public val Target: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.Target
                @androidx.compose.runtime.Composable
                public fun Target(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.Target else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.Target, *args)
                public val Content: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.Content
                @androidx.compose.runtime.Composable
                public fun Content(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.Content else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.Content, *args)
                public val Src: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.Src
                @androidx.compose.runtime.Composable
                public fun Src(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.Src else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.Src, *args)
                public val NoResolve: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.NoResolve
                @androidx.compose.runtime.Composable
                public fun NoResolve(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.NoResolve else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.NoResolve, *args)
                public val TargetReject: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetReject
                @androidx.compose.runtime.Composable
                public fun TargetReject(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetReject else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetReject, *args)
                public val TargetDirect: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetDirect
                @androidx.compose.runtime.Composable
                public fun TargetDirect(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetDirect else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetDirect, *args)
                public val TargetMatch: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetMatch
                @androidx.compose.runtime.Composable
                public fun TargetMatch(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetMatch else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.TargetMatch, *args)
                public val ErrorContentRequired: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.Rule.ErrorContentRequired
                @androidx.compose.runtime.Composable
                public fun ErrorContentRequired(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.Rule.ErrorContentRequired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.Rule.ErrorContentRequired, *args)
            }
            public object ConfigPreview {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Title, *args)
                public val Format: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Format
                @androidx.compose.runtime.Composable
                public fun Format(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Format else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Format, *args)
                public val Save: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Save
                @androidx.compose.runtime.Composable
                public fun Save(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Save else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.Save, *args)
                public val SaveFailed: String
                    get() = FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.SaveFailed
                @androidx.compose.runtime.Composable
                public fun SaveFailed(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.SaveFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Component.Editor.ConfigPreview.SaveFailed, *args)
            }
        }
    }
    public object Connection {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Connection.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Title, *args)
        public val Summary: String
            get() = FlyLocaleManager.activeStrings.Connection.Summary
        @androidx.compose.runtime.Composable
        public fun Summary(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Summary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Summary, *args)
        public object Tab {
            public val Active: String
                get() = FlyLocaleManager.activeStrings.Connection.Tab.Active
            @androidx.compose.runtime.Composable
            public fun Active(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Tab.Active else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Tab.Active, *args)
            public val Closed: String
                get() = FlyLocaleManager.activeStrings.Connection.Tab.Closed
            @androidx.compose.runtime.Composable
            public fun Closed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Tab.Closed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Tab.Closed, *args)
        }
        public val Search: String
            get() = FlyLocaleManager.activeStrings.Connection.Search
        @androidx.compose.runtime.Composable
        public fun Search(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Search else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Search, *args)
        public val SearchHint: String
            get() = FlyLocaleManager.activeStrings.Connection.SearchHint
        @androidx.compose.runtime.Composable
        public fun SearchHint(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.SearchHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.SearchHint, *args)
        public val CloseAll: String
            get() = FlyLocaleManager.activeStrings.Connection.CloseAll
        @androidx.compose.runtime.Composable
        public fun CloseAll(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.CloseAll else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.CloseAll, *args)
        public val SortBy: String
            get() = FlyLocaleManager.activeStrings.Connection.SortBy
        @androidx.compose.runtime.Composable
        public fun SortBy(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.SortBy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.SortBy, *args)
        public object Sort {
            public val Time: String
                get() = FlyLocaleManager.activeStrings.Connection.Sort.Time
            @androidx.compose.runtime.Composable
            public fun Time(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Sort.Time else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Sort.Time, *args)
            public val Upload: String
                get() = FlyLocaleManager.activeStrings.Connection.Sort.Upload
            @androidx.compose.runtime.Composable
            public fun Upload(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Sort.Upload else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Sort.Upload, *args)
            public val Download: String
                get() = FlyLocaleManager.activeStrings.Connection.Sort.Download
            @androidx.compose.runtime.Composable
            public fun Download(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Sort.Download else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Sort.Download, *args)
            public val Host: String
                get() = FlyLocaleManager.activeStrings.Connection.Sort.Host
            @androidx.compose.runtime.Composable
            public fun Host(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Sort.Host else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Sort.Host, *args)
        }
        public val ChainCount: String
            get() = FlyLocaleManager.activeStrings.Connection.ChainCount
        @androidx.compose.runtime.Composable
        public fun ChainCount(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.ChainCount else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.ChainCount, *args)
        public object RelativeTime {
            public val JustNow: String
                get() = FlyLocaleManager.activeStrings.Connection.RelativeTime.JustNow
            @androidx.compose.runtime.Composable
            public fun JustNow(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.RelativeTime.JustNow else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.RelativeTime.JustNow, *args)
            public val MinutesAgo: String
                get() = FlyLocaleManager.activeStrings.Connection.RelativeTime.MinutesAgo
            @androidx.compose.runtime.Composable
            public fun MinutesAgo(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.RelativeTime.MinutesAgo else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.RelativeTime.MinutesAgo, *args)
            public val HoursAgo: String
                get() = FlyLocaleManager.activeStrings.Connection.RelativeTime.HoursAgo
            @androidx.compose.runtime.Composable
            public fun HoursAgo(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.RelativeTime.HoursAgo else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.RelativeTime.HoursAgo, *args)
            public val DaysAgo: String
                get() = FlyLocaleManager.activeStrings.Connection.RelativeTime.DaysAgo
            @androidx.compose.runtime.Composable
            public fun DaysAgo(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.RelativeTime.DaysAgo else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.RelativeTime.DaysAgo, *args)
            public val Date: String
                get() = FlyLocaleManager.activeStrings.Connection.RelativeTime.Date
            @androidx.compose.runtime.Composable
            public fun Date(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.RelativeTime.Date else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.RelativeTime.Date, *args)
        }
        public object Detail {
            public object Action {
                public val Interrupting: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Action.Interrupting
                @androidx.compose.runtime.Composable
                public fun Interrupting(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Action.Interrupting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Action.Interrupting, *args)
                public val Interrupt: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Action.Interrupt
                @androidx.compose.runtime.Composable
                public fun Interrupt(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Action.Interrupt else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Action.Interrupt, *args)
            }
            public object Section {
                public val Info: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Section.Info
                @androidx.compose.runtime.Composable
                public fun Info(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Section.Info else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Section.Info, *args)
                public val Rule: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Section.Rule
                @androidx.compose.runtime.Composable
                public fun Rule(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Section.Rule else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Section.Rule, *args)
            }
            public object Label {
                public val Protocol: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.Protocol
                @androidx.compose.runtime.Composable
                public fun Protocol(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.Protocol else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.Protocol, *args)
                public val Process: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.Process
                @androidx.compose.runtime.Composable
                public fun Process(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.Process else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.Process, *args)
                public val SourceAddress: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.SourceAddress
                @androidx.compose.runtime.Composable
                public fun SourceAddress(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.SourceAddress else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.SourceAddress, *args)
                public val DestinationAddress: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.DestinationAddress
                @androidx.compose.runtime.Composable
                public fun DestinationAddress(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.DestinationAddress else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.DestinationAddress, *args)
                public val Duration: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.Duration
                @androidx.compose.runtime.Composable
                public fun Duration(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.Duration else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.Duration, *args)
                public val Upload: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.Upload
                @androidx.compose.runtime.Composable
                public fun Upload(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.Upload else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.Upload, *args)
                public val Download: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.Download
                @androidx.compose.runtime.Composable
                public fun Download(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.Download else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.Download, *args)
                public val Type: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.Type
                @androidx.compose.runtime.Composable
                public fun Type(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.Type else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.Type, *args)
                public val Content: String
                    get() = FlyLocaleManager.activeStrings.Connection.Detail.Label.Content
                @androidx.compose.runtime.Composable
                public fun Content(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Detail.Label.Content else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Detail.Label.Content, *args)
            }
        }
        public val Loading: String
            get() = FlyLocaleManager.activeStrings.Connection.Loading
        @androidx.compose.runtime.Composable
        public fun Loading(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Loading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Loading, *args)
        public val Empty: String
            get() = FlyLocaleManager.activeStrings.Connection.Empty
        @androidx.compose.runtime.Composable
        public fun Empty(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Empty else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Empty, *args)
        public val NoResults: String
            get() = FlyLocaleManager.activeStrings.Connection.NoResults
        @androidx.compose.runtime.Composable
        public fun NoResults(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.NoResults else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.NoResults, *args)
        public object Topology {
            public val EmptyMessage: String
                get() = FlyLocaleManager.activeStrings.Connection.Topology.EmptyMessage
            @androidx.compose.runtime.Composable
            public fun EmptyMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Topology.EmptyMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Topology.EmptyMessage, *args)
            public val OpenPage: String
                get() = FlyLocaleManager.activeStrings.Connection.Topology.OpenPage
            @androidx.compose.runtime.Composable
            public fun OpenPage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Connection.Topology.OpenPage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Connection.Topology.OpenPage, *args)
        }
    }
    public object Editor {
        public object JsonDiagnostic {
            public val FormatError: String
                get() = FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.FormatError
            @androidx.compose.runtime.Composable
            public fun FormatError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.FormatError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.FormatError, *args)
            public val MustStartWithBraceOrBracket: String
                get() = FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.MustStartWithBraceOrBracket
            @androidx.compose.runtime.Composable
            public fun MustStartWithBraceOrBracket(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.MustStartWithBraceOrBracket else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.MustStartWithBraceOrBracket, *args)
            public val SyntaxError: String
                get() = FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.SyntaxError
            @androidx.compose.runtime.Composable
            public fun SyntaxError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.SyntaxError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.SyntaxError, *args)
            public val UnterminatedStringOrObject: String
                get() = FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.UnterminatedStringOrObject
            @androidx.compose.runtime.Composable
            public fun UnterminatedStringOrObject(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.UnterminatedStringOrObject else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.UnterminatedStringOrObject, *args)
            public val Unknown: String
                get() = FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.Unknown
            @androidx.compose.runtime.Composable
            public fun Unknown(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.Unknown else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.Unknown, *args)
            public val Expected: String
                get() = FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.Expected
            @androidx.compose.runtime.Composable
            public fun Expected(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.Expected else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.Expected, *args)
            public val MissingValue: String
                get() = FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.MissingValue
            @androidx.compose.runtime.Composable
            public fun MissingValue(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.MissingValue else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.MissingValue, *args)
            public val DuplicateKey: String
                get() = FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.DuplicateKey
            @androidx.compose.runtime.Composable
            public fun DuplicateKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.DuplicateKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Editor.JsonDiagnostic.DuplicateKey, *args)
        }
    }
    public object Feature {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Feature.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Title, *args)
        public object ServiceStatus {
            public val Section: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.Section
            @androidx.compose.runtime.Composable
            public fun Section(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.Section else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.Section, *args)
            public val Running: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.Running
            @androidx.compose.runtime.Composable
            public fun Running(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.Running else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.Running, *args)
            public val NeedExtension: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.NeedExtension
            @androidx.compose.runtime.Composable
            public fun NeedExtension(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.NeedExtension else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.NeedExtension, *args)
            public val NeedSubStore: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.NeedSubStore
            @androidx.compose.runtime.Composable
            public fun NeedSubStore(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.NeedSubStore else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.NeedSubStore, *args)
            public val NotRunning: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.NotRunning
            @androidx.compose.runtime.Composable
            public fun NotRunning(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.NotRunning else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.NotRunning, *args)
            public val SwitchStartSubStore: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.SwitchStartSubStore
            @androidx.compose.runtime.Composable
            public fun SwitchStartSubStore(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.SwitchStartSubStore else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.SwitchStartSubStore, *args)
            public val AllowLan: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.AllowLan
            @androidx.compose.runtime.Composable
            public fun AllowLan(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.AllowLan else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.AllowLan, *args)
            public val AllowLanSummary: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.AllowLanSummary
            @androidx.compose.runtime.Composable
            public fun AllowLanSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.AllowLanSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.AllowLanSummary, *args)
            public val AutoCloseModeSummary: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeSummary
            @androidx.compose.runtime.Composable
            public fun AutoCloseModeSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeSummary, *args)
            public val AutoCloseModeAlwaysOn: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeAlwaysOn
            @androidx.compose.runtime.Composable
            public fun AutoCloseModeAlwaysOn(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeAlwaysOn else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeAlwaysOn, *args)
            public val AutoCloseMode5Min: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseMode5Min
            @androidx.compose.runtime.Composable
            public fun AutoCloseMode5Min(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseMode5Min else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseMode5Min, *args)
            public val AutoCloseMode10Min: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseMode10Min
            @androidx.compose.runtime.Composable
            public fun AutoCloseMode10Min(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseMode10Min else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseMode10Min, *args)
            public val AutoClosed: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoClosed
            @androidx.compose.runtime.Composable
            public fun AutoClosed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoClosed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoClosed, *args)
            public val AutoCloseModeDisabled: String
                get() = FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeDisabled
            @androidx.compose.runtime.Composable
            public fun AutoCloseModeDisabled(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeDisabled else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.ServiceStatus.AutoCloseModeDisabled, *args)
        }
        public object Panel {
            public val Section: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.Section
            @androidx.compose.runtime.Composable
            public fun Section(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.Section else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.Section, *args)
            public val SelectPanel: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.SelectPanel
            @androidx.compose.runtime.Composable
            public fun SelectPanel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.SelectPanel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.SelectPanel, *args)
            public val Protocol: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.Protocol
            @androidx.compose.runtime.Composable
            public fun Protocol(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.Protocol else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.Protocol, *args)
            public val OpenPanel: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.OpenPanel
            @androidx.compose.runtime.Composable
            public fun OpenPanel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.OpenPanel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.OpenPanel, *args)
            public val Unknown: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.Unknown
            @androidx.compose.runtime.Composable
            public fun Unknown(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.Unknown else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.Unknown, *args)
            public val CreateShortcut: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.CreateShortcut
            @androidx.compose.runtime.Composable
            public fun CreateShortcut(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.CreateShortcut else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.CreateShortcut, *args)
            public val CreateShortcutSummary: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.CreateShortcutSummary
            @androidx.compose.runtime.Composable
            public fun CreateShortcutSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.CreateShortcutSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.CreateShortcutSummary, *args)
            public val ShortcutTitle: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.ShortcutTitle
            @androidx.compose.runtime.Composable
            public fun ShortcutTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.ShortcutTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.ShortcutTitle, *args)
            public val ShortcutName: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.ShortcutName
            @androidx.compose.runtime.Composable
            public fun ShortcutName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.ShortcutName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.ShortcutName, *args)
            public val ShortcutPickIcon: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.ShortcutPickIcon
            @androidx.compose.runtime.Composable
            public fun ShortcutPickIcon(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.ShortcutPickIcon else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.ShortcutPickIcon, *args)
            public val ShortcutResetIcon: String
                get() = FlyLocaleManager.activeStrings.Feature.Panel.ShortcutResetIcon
            @androidx.compose.runtime.Composable
            public fun ShortcutResetIcon(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.Panel.ShortcutResetIcon else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.Panel.ShortcutResetIcon, *args)
        }
        public object RemoteController {
            public val Section: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.Section
            @androidx.compose.runtime.Composable
            public fun Section(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.Section else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.Section, *args)
            public val ModeTitle: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.ModeTitle
            @androidx.compose.runtime.Composable
            public fun ModeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.ModeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.ModeTitle, *args)
            public val ControlBackend: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.ControlBackend
            @androidx.compose.runtime.Composable
            public fun ControlBackend(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.ControlBackend else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.ControlBackend, *args)
            public val Protocol: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.Protocol
            @androidx.compose.runtime.Composable
            public fun Protocol(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.Protocol else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.Protocol, *args)
            public val AddBackend: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.AddBackend
            @androidx.compose.runtime.Composable
            public fun AddBackend(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.AddBackend else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.AddBackend, *args)
            public val EditBackend: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.EditBackend
            @androidx.compose.runtime.Composable
            public fun EditBackend(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.EditBackend else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.EditBackend, *args)
            public val Name: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.Name
            @androidx.compose.runtime.Composable
            public fun Name(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.Name else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.Name, *args)
            public val Host: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.Host
            @androidx.compose.runtime.Composable
            public fun Host(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.Host else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.Host, *args)
            public val Port: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.Port
            @androidx.compose.runtime.Composable
            public fun Port(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.Port else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.Port, *args)
            public val Secret: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.Secret
            @androidx.compose.runtime.Composable
            public fun Secret(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.Secret else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.Secret, *args)
            public val PortRangeError: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.PortRangeError
            @androidx.compose.runtime.Composable
            public fun PortRangeError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.PortRangeError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.PortRangeError, *args)
            public val Delete: String
                get() = FlyLocaleManager.activeStrings.Feature.RemoteController.Delete
            @androidx.compose.runtime.Composable
            public fun Delete(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.RemoteController.Delete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.RemoteController.Delete, *args)
        }
        public object SubStore {
            public val SectionHint: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.SectionHint
            @androidx.compose.runtime.Composable
            public fun SectionHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.SectionHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.SectionHint, *args)
            public val ExtensionInstalled: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.ExtensionInstalled
            @androidx.compose.runtime.Composable
            public fun ExtensionInstalled(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.ExtensionInstalled else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.ExtensionInstalled, *args)
            public val ExtensionInstall: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.ExtensionInstall
            @androidx.compose.runtime.Composable
            public fun ExtensionInstall(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.ExtensionInstall else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.ExtensionInstall, *args)
            public val JavetAvailable: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.JavetAvailable
            @androidx.compose.runtime.Composable
            public fun JavetAvailable(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.JavetAvailable else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.JavetAvailable, *args)
            public val JavetPending: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.JavetPending
            @androidx.compose.runtime.Composable
            public fun JavetPending(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.JavetPending else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.JavetPending, *args)
            public val DownloadHint: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.DownloadHint
            @androidx.compose.runtime.Composable
            public fun DownloadHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.DownloadHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.DownloadHint, *args)
            public val DownloadJavet: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.DownloadJavet
            @androidx.compose.runtime.Composable
            public fun DownloadJavet(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.DownloadJavet else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.DownloadJavet, *args)
            public val Downloading: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.Downloading
            @androidx.compose.runtime.Composable
            public fun Downloading(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.Downloading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.Downloading, *args)
            public val JavetDownloadSuccess: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.JavetDownloadSuccess
            @androidx.compose.runtime.Composable
            public fun JavetDownloadSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.JavetDownloadSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.JavetDownloadSuccess, *args)
            public val JavetDownloadFailed: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.JavetDownloadFailed
            @androidx.compose.runtime.Composable
            public fun JavetDownloadFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.JavetDownloadFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.JavetDownloadFailed, *args)
            public val DownloadResources: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.DownloadResources
            @androidx.compose.runtime.Composable
            public fun DownloadResources(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.DownloadResources else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.DownloadResources, *args)
            public val DownloadResourcesSummary: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.DownloadResourcesSummary
            @androidx.compose.runtime.Composable
            public fun DownloadResourcesSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.DownloadResourcesSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.DownloadResourcesSummary, *args)
            public val Not32Bit: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.Not32Bit
            @androidx.compose.runtime.Composable
            public fun Not32Bit(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.Not32Bit else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.Not32Bit, *args)
            public val InstallExtension: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.InstallExtension
            @androidx.compose.runtime.Composable
            public fun InstallExtension(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.InstallExtension else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.InstallExtension, *args)
            public val DownloadSubStoreFirst: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.DownloadSubStoreFirst
            @androidx.compose.runtime.Composable
            public fun DownloadSubStoreFirst(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.DownloadSubStoreFirst else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.DownloadSubStoreFirst, *args)
            public val JavetNotReady: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.JavetNotReady
            @androidx.compose.runtime.Composable
            public fun JavetNotReady(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.JavetNotReady else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.JavetNotReady, *args)
            public val FrontendDownloadSuccess: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.FrontendDownloadSuccess
            @androidx.compose.runtime.Composable
            public fun FrontendDownloadSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.FrontendDownloadSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.FrontendDownloadSuccess, *args)
            public val FrontendDownloadFailed: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.FrontendDownloadFailed
            @androidx.compose.runtime.Composable
            public fun FrontendDownloadFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.FrontendDownloadFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.FrontendDownloadFailed, *args)
            public val BackendDownloadSuccess: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.BackendDownloadSuccess
            @androidx.compose.runtime.Composable
            public fun BackendDownloadSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.BackendDownloadSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.BackendDownloadSuccess, *args)
            public val BackendDownloadFailed: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.BackendDownloadFailed
            @androidx.compose.runtime.Composable
            public fun BackendDownloadFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.BackendDownloadFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.BackendDownloadFailed, *args)
            public val DownloadError: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.DownloadError
            @androidx.compose.runtime.Composable
            public fun DownloadError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.DownloadError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.DownloadError, *args)
            public val Title: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.Title, *args)
            public val PortOccupied: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.PortOccupied
            @androidx.compose.runtime.Composable
            public fun PortOccupied(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.PortOccupied else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.PortOccupied, *args)
            public val JavetLoadFailed: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.JavetLoadFailed
            @androidx.compose.runtime.Composable
            public fun JavetLoadFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.JavetLoadFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.JavetLoadFailed, *args)
            public val CaseEngineInitFailed: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.CaseEngineInitFailed
            @androidx.compose.runtime.Composable
            public fun CaseEngineInitFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.CaseEngineInitFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.CaseEngineInitFailed, *args)
            public val FrontendExtractFailed: String
                get() = FlyLocaleManager.activeStrings.Feature.SubStore.FrontendExtractFailed
            @androidx.compose.runtime.Composable
            public fun FrontendExtractFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Feature.SubStore.FrontendExtractFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Feature.SubStore.FrontendExtractFailed, *args)
        }
    }
    public object Home {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Home.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Title, *args)
        public object Message {
            public val ConfigSwitched: String
                get() = FlyLocaleManager.activeStrings.Home.Message.ConfigSwitched
            @androidx.compose.runtime.Composable
            public fun ConfigSwitched(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Message.ConfigSwitched else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Message.ConfigSwitched, *args)
            public val ConfigSwitchFailed: String
                get() = FlyLocaleManager.activeStrings.Home.Message.ConfigSwitchFailed
            @androidx.compose.runtime.Composable
            public fun ConfigSwitchFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Message.ConfigSwitchFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Message.ConfigSwitchFailed, *args)
            public val Preparing: String
                get() = FlyLocaleManager.activeStrings.Home.Message.Preparing
            @androidx.compose.runtime.Composable
            public fun Preparing(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Message.Preparing else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Message.Preparing, *args)
            public val StartFailed: String
                get() = FlyLocaleManager.activeStrings.Home.Message.StartFailed
            @androidx.compose.runtime.Composable
            public fun StartFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Message.StartFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Message.StartFailed, *args)
            public val StopFailed: String
                get() = FlyLocaleManager.activeStrings.Home.Message.StopFailed
            @androidx.compose.runtime.Composable
            public fun StopFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Message.StopFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Message.StopFailed, *args)
        }
        public object Control {
            public val HintAddProfile: String
                get() = FlyLocaleManager.activeStrings.Home.Control.HintAddProfile
            @androidx.compose.runtime.Composable
            public fun HintAddProfile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Control.HintAddProfile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Control.HintAddProfile, *args)
            public val HintEnableProfile: String
                get() = FlyLocaleManager.activeStrings.Home.Control.HintEnableProfile
            @androidx.compose.runtime.Composable
            public fun HintEnableProfile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Control.HintEnableProfile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Control.HintEnableProfile, *args)
            public val Start: String
                get() = FlyLocaleManager.activeStrings.Home.Control.Start
            @androidx.compose.runtime.Composable
            public fun Start(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Control.Start else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Control.Start, *args)
            public val Stop: String
                get() = FlyLocaleManager.activeStrings.Home.Control.Stop
            @androidx.compose.runtime.Composable
            public fun Stop(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Control.Stop else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Control.Stop, *args)
        }
        public object NodeInfo {
            public val Node: String
                get() = FlyLocaleManager.activeStrings.Home.NodeInfo.Node
            @androidx.compose.runtime.Composable
            public fun Node(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.NodeInfo.Node else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.NodeInfo.Node, *args)
            public val Delay: String
                get() = FlyLocaleManager.activeStrings.Home.NodeInfo.Delay
            @androidx.compose.runtime.Composable
            public fun Delay(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.NodeInfo.Delay else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.NodeInfo.Delay, *args)
            public val Unknown: String
                get() = FlyLocaleManager.activeStrings.Home.NodeInfo.Unknown
            @androidx.compose.runtime.Composable
            public fun Unknown(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.NodeInfo.Unknown else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.NodeInfo.Unknown, *args)
            public val DelayValue: String
                get() = FlyLocaleManager.activeStrings.Home.NodeInfo.DelayValue
            @androidx.compose.runtime.Composable
            public fun DelayValue(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.NodeInfo.DelayValue else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.NodeInfo.DelayValue, *args)
        }
        public object Traffic {
            public val UpShort: String
                get() = FlyLocaleManager.activeStrings.Home.Traffic.UpShort
            @androidx.compose.runtime.Composable
            public fun UpShort(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Traffic.UpShort else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Traffic.UpShort, *args)
            public val DownShort: String
                get() = FlyLocaleManager.activeStrings.Home.Traffic.DownShort
            @androidx.compose.runtime.Composable
            public fun DownShort(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Traffic.DownShort else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Traffic.DownShort, *args)
            public val DownloadLong: String
                get() = FlyLocaleManager.activeStrings.Home.Traffic.DownloadLong
            @androidx.compose.runtime.Composable
            public fun DownloadLong(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Traffic.DownloadLong else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Traffic.DownloadLong, *args)
            public val UploadLong: String
                get() = FlyLocaleManager.activeStrings.Home.Traffic.UploadLong
            @androidx.compose.runtime.Composable
            public fun UploadLong(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Traffic.UploadLong else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Traffic.UploadLong, *args)
            public val NoProfile: String
                get() = FlyLocaleManager.activeStrings.Home.Traffic.NoProfile
            @androidx.compose.runtime.Composable
            public fun NoProfile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Traffic.NoProfile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Traffic.NoProfile, *args)
        }
        public object IpInfo {
            public val ExitIp: String
                get() = FlyLocaleManager.activeStrings.Home.IpInfo.ExitIp
            @androidx.compose.runtime.Composable
            public fun ExitIp(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.IpInfo.ExitIp else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.IpInfo.ExitIp, *args)
        }
        public object Status {
            public val Connecting: String
                get() = FlyLocaleManager.activeStrings.Home.Status.Connecting
            @androidx.compose.runtime.Composable
            public fun Connecting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Status.Connecting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Status.Connecting, *args)
            public val Disconnecting: String
                get() = FlyLocaleManager.activeStrings.Home.Status.Disconnecting
            @androidx.compose.runtime.Composable
            public fun Disconnecting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Status.Disconnecting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Status.Disconnecting, *args)
            public val Running: String
                get() = FlyLocaleManager.activeStrings.Home.Status.Running
            @androidx.compose.runtime.Composable
            public fun Running(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Status.Running else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Status.Running, *args)
            public val Lost: String
                get() = FlyLocaleManager.activeStrings.Home.Status.Lost
            @androidx.compose.runtime.Composable
            public fun Lost(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Status.Lost else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Status.Lost, *args)
            public val TapToStart: String
                get() = FlyLocaleManager.activeStrings.Home.Status.TapToStart
            @androidx.compose.runtime.Composable
            public fun TapToStart(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Status.TapToStart else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Status.TapToStart, *args)
        }
        public object Greeting {
            public val Morning: String
                get() = FlyLocaleManager.activeStrings.Home.Greeting.Morning
            @androidx.compose.runtime.Composable
            public fun Morning(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Greeting.Morning else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Greeting.Morning, *args)
            public val Afternoon: String
                get() = FlyLocaleManager.activeStrings.Home.Greeting.Afternoon
            @androidx.compose.runtime.Composable
            public fun Afternoon(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Greeting.Afternoon else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Greeting.Afternoon, *args)
            public val Evening: String
                get() = FlyLocaleManager.activeStrings.Home.Greeting.Evening
            @androidx.compose.runtime.Composable
            public fun Evening(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Greeting.Evening else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Greeting.Evening, *args)
            public val Night: String
                get() = FlyLocaleManager.activeStrings.Home.Greeting.Night
            @androidx.compose.runtime.Composable
            public fun Night(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Greeting.Night else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Greeting.Night, *args)
        }
        public object Quote {
            public val AuthorPrefix: String
                get() = FlyLocaleManager.activeStrings.Home.Quote.AuthorPrefix
            @androidx.compose.runtime.Composable
            public fun AuthorPrefix(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.Quote.AuthorPrefix else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.Quote.AuthorPrefix, *args)
        }
        public object RunMode {
            public val Vpn: String
                get() = FlyLocaleManager.activeStrings.Home.RunMode.Vpn
            @androidx.compose.runtime.Composable
            public fun Vpn(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.RunMode.Vpn else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.RunMode.Vpn, *args)
            public val Tun: String
                get() = FlyLocaleManager.activeStrings.Home.RunMode.Tun
            @androidx.compose.runtime.Composable
            public fun Tun(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.RunMode.Tun else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.RunMode.Tun, *args)
            public val Ebpf: String
                get() = FlyLocaleManager.activeStrings.Home.RunMode.Ebpf
            @androidx.compose.runtime.Composable
            public fun Ebpf(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.RunMode.Ebpf else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.RunMode.Ebpf, *args)
        }
        public object TunnelMode {
            public val Rule: String
                get() = FlyLocaleManager.activeStrings.Home.TunnelMode.Rule
            @androidx.compose.runtime.Composable
            public fun Rule(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.TunnelMode.Rule else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.TunnelMode.Rule, *args)
            public val Global: String
                get() = FlyLocaleManager.activeStrings.Home.TunnelMode.Global
            @androidx.compose.runtime.Composable
            public fun Global(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.TunnelMode.Global else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.TunnelMode.Global, *args)
            public val Direct: String
                get() = FlyLocaleManager.activeStrings.Home.TunnelMode.Direct
            @androidx.compose.runtime.Composable
            public fun Direct(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.TunnelMode.Direct else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.TunnelMode.Direct, *args)
        }
        public object PreviewGuide {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.Title, *args)
            public val Description: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.Description
            @androidx.compose.runtime.Composable
            public fun Description(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.Description else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.Description, *args)
            public val Next: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.Next
            @androidx.compose.runtime.Composable
            public fun Next(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.Next else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.Next, *args)
            public val Start: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.Start
            @androidx.compose.runtime.Composable
            public fun Start(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.Start else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.Start, *args)
            public val WallpaperTitle: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.WallpaperTitle
            @androidx.compose.runtime.Composable
            public fun WallpaperTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.WallpaperTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.WallpaperTitle, *args)
            public val WallpaperDescription: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.WallpaperDescription
            @androidx.compose.runtime.Composable
            public fun WallpaperDescription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.WallpaperDescription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.WallpaperDescription, *args)
            public val SwipeTitle: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.SwipeTitle
            @androidx.compose.runtime.Composable
            public fun SwipeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.SwipeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.SwipeTitle, *args)
            public val SwipeDescription: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.SwipeDescription
            @androidx.compose.runtime.Composable
            public fun SwipeDescription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.SwipeDescription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.SwipeDescription, *args)
            public val AddTitle: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.AddTitle
            @androidx.compose.runtime.Composable
            public fun AddTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.AddTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.AddTitle, *args)
            public val AddDescription: String
                get() = FlyLocaleManager.activeStrings.Home.PreviewGuide.AddDescription
            @androidx.compose.runtime.Composable
            public fun AddDescription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Home.PreviewGuide.AddDescription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Home.PreviewGuide.AddDescription, *args)
        }
    }
    public object Log {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Log.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Title, *args)
        public object Action {
            public val StopRecording: String
                get() = FlyLocaleManager.activeStrings.Log.Action.StopRecording
            @androidx.compose.runtime.Composable
            public fun StopRecording(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Action.StopRecording else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Action.StopRecording, *args)
            public val StartRecording: String
                get() = FlyLocaleManager.activeStrings.Log.Action.StartRecording
            @androidx.compose.runtime.Composable
            public fun StartRecording(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Action.StartRecording else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Action.StartRecording, *args)
            public val SaveRecentLogs: String
                get() = FlyLocaleManager.activeStrings.Log.Action.SaveRecentLogs
            @androidx.compose.runtime.Composable
            public fun SaveRecentLogs(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Action.SaveRecentLogs else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Action.SaveRecentLogs, *args)
            public val ClearLogs: String
                get() = FlyLocaleManager.activeStrings.Log.Action.ClearLogs
            @androidx.compose.runtime.Composable
            public fun ClearLogs(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Action.ClearLogs else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Action.ClearLogs, *args)
            public val Pause: String
                get() = FlyLocaleManager.activeStrings.Log.Action.Pause
            @androidx.compose.runtime.Composable
            public fun Pause(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Action.Pause else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Action.Pause, *args)
            public val Save: String
                get() = FlyLocaleManager.activeStrings.Log.Action.Save
            @androidx.compose.runtime.Composable
            public fun Save(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Action.Save else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Action.Save, *args)
            public val Delete: String
                get() = FlyLocaleManager.activeStrings.Log.Action.Delete
            @androidx.compose.runtime.Composable
            public fun Delete(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Action.Delete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Action.Delete, *args)
        }
        public object Empty {
            public val NoLogs: String
                get() = FlyLocaleManager.activeStrings.Log.Empty.NoLogs
            @androidx.compose.runtime.Composable
            public fun NoLogs(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Empty.NoLogs else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Empty.NoLogs, *args)
            public val StartRecordingHint: String
                get() = FlyLocaleManager.activeStrings.Log.Empty.StartRecordingHint
            @androidx.compose.runtime.Composable
            public fun StartRecordingHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Empty.StartRecordingHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Empty.StartRecordingHint, *args)
        }
        public object Message {
            public val SaveFailed: String
                get() = FlyLocaleManager.activeStrings.Log.Message.SaveFailed
            @androidx.compose.runtime.Composable
            public fun SaveFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Message.SaveFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Message.SaveFailed, *args)
            public val Saved: String
                get() = FlyLocaleManager.activeStrings.Log.Message.Saved
            @androidx.compose.runtime.Composable
            public fun Saved(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Message.Saved else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Message.Saved, *args)
            public val RecentLogSaved: String
                get() = FlyLocaleManager.activeStrings.Log.Message.RecentLogSaved
            @androidx.compose.runtime.Composable
            public fun RecentLogSaved(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Message.RecentLogSaved else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Message.RecentLogSaved, *args)
        }
        public object Status {
            public val Recording: String
                get() = FlyLocaleManager.activeStrings.Log.Status.Recording
            @androidx.compose.runtime.Composable
            public fun Recording(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Status.Recording else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Status.Recording, *args)
        }
        public object Detail {
            public val RealTimeLog: String
                get() = FlyLocaleManager.activeStrings.Log.Detail.RealTimeLog
            @androidx.compose.runtime.Composable
            public fun RealTimeLog(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Detail.RealTimeLog else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Detail.RealTimeLog, *args)
            public val Loading: String
                get() = FlyLocaleManager.activeStrings.Log.Detail.Loading
            @androidx.compose.runtime.Composable
            public fun Loading(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Detail.Loading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Detail.Loading, *args)
            public val WaitingLog: String
                get() = FlyLocaleManager.activeStrings.Log.Detail.WaitingLog
            @androidx.compose.runtime.Composable
            public fun WaitingLog(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Detail.WaitingLog else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Detail.WaitingLog, *args)
            public val LogEmpty: String
                get() = FlyLocaleManager.activeStrings.Log.Detail.LogEmpty
            @androidx.compose.runtime.Composable
            public fun LogEmpty(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Detail.LogEmpty else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Detail.LogEmpty, *args)
            public val WillShowWhenGenerated: String
                get() = FlyLocaleManager.activeStrings.Log.Detail.WillShowWhenGenerated
            @androidx.compose.runtime.Composable
            public fun WillShowWhenGenerated(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Detail.WillShowWhenGenerated else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Detail.WillShowWhenGenerated, *args)
            public val NoLogContent: String
                get() = FlyLocaleManager.activeStrings.Log.Detail.NoLogContent
            @androidx.compose.runtime.Composable
            public fun NoLogContent(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Log.Detail.NoLogContent else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Log.Detail.NoLogContent, *args)
        }
    }
    public object MetaFeature {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.MetaFeature.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Title, *args)
        public object Section {
            public val ConnectionAndTraffic: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Section.ConnectionAndTraffic
            @androidx.compose.runtime.Composable
            public fun ConnectionAndTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Section.ConnectionAndTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Section.ConnectionAndTraffic, *args)
            public val BackupRestore: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Section.BackupRestore
            @androidx.compose.runtime.Composable
            public fun BackupRestore(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Section.BackupRestore else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Section.BackupRestore, *args)
            public val Routing: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Section.Routing
            @androidx.compose.runtime.Composable
            public fun Routing(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Section.Routing else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Section.Routing, *args)
        }
        public object GeoX {
            public val OnlineUpdateTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.GeoX.OnlineUpdateTitle
            @androidx.compose.runtime.Composable
            public fun OnlineUpdateTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.GeoX.OnlineUpdateTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.GeoX.OnlineUpdateTitle, *args)
            public val OnlineUpdateSummary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.GeoX.OnlineUpdateSummary
            @androidx.compose.runtime.Composable
            public fun OnlineUpdateSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.GeoX.OnlineUpdateSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.GeoX.OnlineUpdateSummary, *args)
        }
        public object CustomRouting {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.Title, *args)
            public val Summary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.Summary
            @androidx.compose.runtime.Composable
            public fun Summary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.Summary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.Summary, *args)
            public val EditYaml: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.EditYaml
            @androidx.compose.runtime.Composable
            public fun EditYaml(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.EditYaml else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.EditYaml, *args)
            public val GroupTypeTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeTitle
            @androidx.compose.runtime.Composable
            public fun GroupTypeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeTitle, *args)
            public val GroupTypeUrlTest: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeUrlTest
            @androidx.compose.runtime.Composable
            public fun GroupTypeUrlTest(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeUrlTest else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeUrlTest, *args)
            public val GroupTypeFallback: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeFallback
            @androidx.compose.runtime.Composable
            public fun GroupTypeFallback(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeFallback else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.GroupTypeFallback, *args)
            public val UrlTestRegionGroupTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.UrlTestRegionGroupTitle
            @androidx.compose.runtime.Composable
            public fun UrlTestRegionGroupTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.UrlTestRegionGroupTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.UrlTestRegionGroupTitle, *args)
            public val FallbackRegionGroupTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.FallbackRegionGroupTitle
            @androidx.compose.runtime.Composable
            public fun FallbackRegionGroupTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.FallbackRegionGroupTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.FallbackRegionGroupTitle, *args)
            public val SaveFailed: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.SaveFailed
            @androidx.compose.runtime.Composable
            public fun SaveFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.SaveFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.SaveFailed, *args)
            public val EditButton: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.EditButton
            @androidx.compose.runtime.Composable
            public fun EditButton(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.EditButton else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.EditButton, *args)
            public val ManualYamlPresetDiscarded: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ManualYamlPresetDiscarded
            @androidx.compose.runtime.Composable
            public fun ManualYamlPresetDiscarded(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ManualYamlPresetDiscarded else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ManualYamlPresetDiscarded, *args)
            public val RegionHongKong: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionHongKong
            @androidx.compose.runtime.Composable
            public fun RegionHongKong(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionHongKong else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionHongKong, *args)
            public val RegionTaiwan: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionTaiwan
            @androidx.compose.runtime.Composable
            public fun RegionTaiwan(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionTaiwan else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionTaiwan, *args)
            public val RegionJapan: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionJapan
            @androidx.compose.runtime.Composable
            public fun RegionJapan(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionJapan else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionJapan, *args)
            public val RegionSingapore: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionSingapore
            @androidx.compose.runtime.Composable
            public fun RegionSingapore(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionSingapore else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionSingapore, *args)
            public val RegionUnitedStates: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionUnitedStates
            @androidx.compose.runtime.Composable
            public fun RegionUnitedStates(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionUnitedStates else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionUnitedStates, *args)
            public val RegionOther: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionOther
            @androidx.compose.runtime.Composable
            public fun RegionOther(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionOther else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.RegionOther, *args)
            public val ItemProxy: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemProxy
            @androidx.compose.runtime.Composable
            public fun ItemProxy(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemProxy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemProxy, *args)
            public val ItemAds: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemAds
            @androidx.compose.runtime.Composable
            public fun ItemAds(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemAds else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemAds, *args)
            public val ItemChina: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemChina
            @androidx.compose.runtime.Composable
            public fun ItemChina(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemChina else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemChina, *args)
            public val ItemGlobal: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemGlobal
            @androidx.compose.runtime.Composable
            public fun ItemGlobal(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemGlobal else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemGlobal, *args)
            public val ItemMatch: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemMatch
            @androidx.compose.runtime.Composable
            public fun ItemMatch(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemMatch else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.CustomRouting.ItemMatch, *args)
        }
        public object RuntimeRules {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.RuntimeRules.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.RuntimeRules.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.RuntimeRules.Title, *args)
            public val Summary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.RuntimeRules.Summary
            @androidx.compose.runtime.Composable
            public fun Summary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.RuntimeRules.Summary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.RuntimeRules.Summary, *args)
        }
        public object Download {
            public val DialogTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Download.DialogTitle
            @androidx.compose.runtime.Composable
            public fun DialogTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Download.DialogTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Download.DialogTitle, *args)
            public val DownloadComplete: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Download.DownloadComplete
            @androidx.compose.runtime.Composable
            public fun DownloadComplete(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Download.DownloadComplete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Download.DownloadComplete, *args)
        }
        public object Backup {
            public val BackupTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupTitle
            @androidx.compose.runtime.Composable
            public fun BackupTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupTitle, *args)
            public val BackupSummary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupSummary
            @androidx.compose.runtime.Composable
            public fun BackupSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupSummary, *args)
            public val RestoreTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreTitle
            @androidx.compose.runtime.Composable
            public fun RestoreTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreTitle, *args)
            public val RestoreSummary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSummary
            @androidx.compose.runtime.Composable
            public fun RestoreSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSummary, *args)
            public val BackupDialogTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupDialogTitle
            @androidx.compose.runtime.Composable
            public fun BackupDialogTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupDialogTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupDialogTitle, *args)
            public val BackupDialogMessage: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupDialogMessage
            @androidx.compose.runtime.Composable
            public fun BackupDialogMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupDialogMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupDialogMessage, *args)
            public val RestoreDialogTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreDialogTitle
            @androidx.compose.runtime.Composable
            public fun RestoreDialogTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreDialogTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreDialogTitle, *args)
            public val RestoreDialogMessage: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreDialogMessage
            @androidx.compose.runtime.Composable
            public fun RestoreDialogMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreDialogMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreDialogMessage, *args)
            public val RestoreConfirmTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreConfirmTitle
            @androidx.compose.runtime.Composable
            public fun RestoreConfirmTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreConfirmTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreConfirmTitle, *args)
            public val RestoreConfirmMessage: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreConfirmMessage
            @androidx.compose.runtime.Composable
            public fun RestoreConfirmMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreConfirmMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreConfirmMessage, *args)
            public val BackupSuccess: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupSuccess
            @androidx.compose.runtime.Composable
            public fun BackupSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupSuccess, *args)
            public val RestoreSuccess: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccess
            @androidx.compose.runtime.Composable
            public fun RestoreSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccess, *args)
            public val RestoreSuccessTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccessTitle
            @androidx.compose.runtime.Composable
            public fun RestoreSuccessTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccessTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccessTitle, *args)
            public val RestoreSuccessMessage: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccessMessage
            @androidx.compose.runtime.Composable
            public fun RestoreSuccessMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccessMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreSuccessMessage, *args)
            public val WebDavRestoreSuccess: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.WebDavRestoreSuccess
            @androidx.compose.runtime.Composable
            public fun WebDavRestoreSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.WebDavRestoreSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.WebDavRestoreSuccess, *args)
            public val RestartNow: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestartNow
            @androidx.compose.runtime.Composable
            public fun RestartNow(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestartNow else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestartNow, *args)
            public val RestartLaunchNotFound: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestartLaunchNotFound
            @androidx.compose.runtime.Composable
            public fun RestartLaunchNotFound(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestartLaunchNotFound else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestartLaunchNotFound, *args)
            public val BackupFailed: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupFailed
            @androidx.compose.runtime.Composable
            public fun BackupFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.BackupFailed, *args)
            public val RestoreFailed: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreFailed
            @androidx.compose.runtime.Composable
            public fun RestoreFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.Backup.RestoreFailed, *args)
        }
        public object AgeKey {
            public val Section: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.AgeKey.Section
            @androidx.compose.runtime.Composable
            public fun Section(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.AgeKey.Section else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.AgeKey.Section, *args)
            public val X25519Title: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.AgeKey.X25519Title
            @androidx.compose.runtime.Composable
            public fun X25519Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.AgeKey.X25519Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.AgeKey.X25519Title, *args)
            public val HybridTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.AgeKey.HybridTitle
            @androidx.compose.runtime.Composable
            public fun HybridTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.AgeKey.HybridTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.AgeKey.HybridTitle, *args)
            public val SecretKey: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.AgeKey.SecretKey
            @androidx.compose.runtime.Composable
            public fun SecretKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.AgeKey.SecretKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.AgeKey.SecretKey, *args)
            public val PublicKey: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.AgeKey.PublicKey
            @androidx.compose.runtime.Composable
            public fun PublicKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.AgeKey.PublicKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.AgeKey.PublicKey, *args)
            public val Generate: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.AgeKey.Generate
            @androidx.compose.runtime.Composable
            public fun Generate(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.AgeKey.Generate else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.AgeKey.Generate, *args)
            public val DerivePublicKey: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.AgeKey.DerivePublicKey
            @androidx.compose.runtime.Composable
            public fun DerivePublicKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.AgeKey.DerivePublicKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.AgeKey.DerivePublicKey, *args)
        }
        public object WebDav {
            public val ConfigTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigTitle
            @androidx.compose.runtime.Composable
            public fun ConfigTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigTitle, *args)
            public val ConfigSummary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigSummary
            @androidx.compose.runtime.Composable
            public fun ConfigSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigSummary, *args)
            public val TestTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestTitle
            @androidx.compose.runtime.Composable
            public fun TestTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestTitle, *args)
            public val TestSummary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestSummary
            @androidx.compose.runtime.Composable
            public fun TestSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestSummary, *args)
            public val TestLoadingTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestLoadingTitle
            @androidx.compose.runtime.Composable
            public fun TestLoadingTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestLoadingTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestLoadingTitle, *args)
            public val TestLoadingMessage: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestLoadingMessage
            @androidx.compose.runtime.Composable
            public fun TestLoadingMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestLoadingMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestLoadingMessage, *args)
            public val TestSuccess: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestSuccess
            @androidx.compose.runtime.Composable
            public fun TestSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestSuccess, *args)
            public val TestFailed: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestFailed
            @androidx.compose.runtime.Composable
            public fun TestFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.TestFailed, *args)
            public val ConfigRequired: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigRequired
            @androidx.compose.runtime.Composable
            public fun ConfigRequired(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigRequired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigRequired, *args)
            public val BackupTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupTitle
            @androidx.compose.runtime.Composable
            public fun BackupTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupTitle, *args)
            public val BackupSummary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupSummary
            @androidx.compose.runtime.Composable
            public fun BackupSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupSummary, *args)
            public val BackupLoadingTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupLoadingTitle
            @androidx.compose.runtime.Composable
            public fun BackupLoadingTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupLoadingTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupLoadingTitle, *args)
            public val BackupLoadingMessage: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupLoadingMessage
            @androidx.compose.runtime.Composable
            public fun BackupLoadingMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupLoadingMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupLoadingMessage, *args)
            public val BackupSuccess: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupSuccess
            @androidx.compose.runtime.Composable
            public fun BackupSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupSuccess, *args)
            public val BackupFailed: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupFailed
            @androidx.compose.runtime.Composable
            public fun BackupFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.BackupFailed, *args)
            public val RestoreTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreTitle
            @androidx.compose.runtime.Composable
            public fun RestoreTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreTitle, *args)
            public val RestoreSummary: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreSummary
            @androidx.compose.runtime.Composable
            public fun RestoreSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreSummary, *args)
            public val RestoreConfirmTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreConfirmTitle
            @androidx.compose.runtime.Composable
            public fun RestoreConfirmTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreConfirmTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreConfirmTitle, *args)
            public val RestoreConfirmMessage: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreConfirmMessage
            @androidx.compose.runtime.Composable
            public fun RestoreConfirmMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreConfirmMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreConfirmMessage, *args)
            public val RestoreLoadingTitle: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreLoadingTitle
            @androidx.compose.runtime.Composable
            public fun RestoreLoadingTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreLoadingTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreLoadingTitle, *args)
            public val RestoreLoadingMessage: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreLoadingMessage
            @androidx.compose.runtime.Composable
            public fun RestoreLoadingMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreLoadingMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreLoadingMessage, *args)
            public val RestoreFailed: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreFailed
            @androidx.compose.runtime.Composable
            public fun RestoreFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.RestoreFailed, *args)
            public val ConfigUrlExample: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigUrlExample
            @androidx.compose.runtime.Composable
            public fun ConfigUrlExample(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigUrlExample else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigUrlExample, *args)
            public val ValidationUrlRequired: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationUrlRequired
            @androidx.compose.runtime.Composable
            public fun ValidationUrlRequired(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationUrlRequired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationUrlRequired, *args)
            public val ValidationAccountRequired: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationAccountRequired
            @androidx.compose.runtime.Composable
            public fun ValidationAccountRequired(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationAccountRequired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationAccountRequired, *args)
            public val ValidationPasswordRequired: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationPasswordRequired
            @androidx.compose.runtime.Composable
            public fun ValidationPasswordRequired(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationPasswordRequired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.ValidationPasswordRequired, *args)
            public val ConfigSaved: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigSaved
            @androidx.compose.runtime.Composable
            public fun ConfigSaved(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigSaved else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.ConfigSaved, *args)
            public val LabelUrl: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelUrl
            @androidx.compose.runtime.Composable
            public fun LabelUrl(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelUrl else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelUrl, *args)
            public val LabelDir: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelDir
            @androidx.compose.runtime.Composable
            public fun LabelDir(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelDir else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelDir, *args)
            public val LabelAccount: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelAccount
            @androidx.compose.runtime.Composable
            public fun LabelAccount(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelAccount else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelAccount, *args)
            public val LabelPassword: String
                get() = FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelPassword
            @androidx.compose.runtime.Composable
            public fun LabelPassword(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelPassword else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.MetaFeature.WebDav.LabelPassword, *args)
        }
    }
    public object NetworkSettings {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.NetworkSettings.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Title, *args)
        public object Section {
            public val VpnOptions: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Section.VpnOptions
            @androidx.compose.runtime.Composable
            public fun VpnOptions(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Section.VpnOptions else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Section.VpnOptions, *args)
            public val Advanced: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Section.Advanced
            @androidx.compose.runtime.Composable
            public fun Advanced(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Section.Advanced else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Section.Advanced, *args)
            public val ProxyOptions: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Section.ProxyOptions
            @androidx.compose.runtime.Composable
            public fun ProxyOptions(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Section.ProxyOptions else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Section.ProxyOptions, *args)
            public val Kernel: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Section.Kernel
            @androidx.compose.runtime.Composable
            public fun Kernel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Section.Kernel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Section.Kernel, *args)
        }
        public object Advanced {
            public val DisableOverrideTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Advanced.DisableOverrideTitle
            @androidx.compose.runtime.Composable
            public fun DisableOverrideTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Advanced.DisableOverrideTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Advanced.DisableOverrideTitle, *args)
        }
        public object Kernel {
            public val ActiveTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.ActiveTitle
            @androidx.compose.runtime.Composable
            public fun ActiveTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.ActiveTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.ActiveTitle, *args)
            public val RefreshTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RefreshTitle
            @androidx.compose.runtime.Composable
            public fun RefreshTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RefreshTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RefreshTitle, *args)
            public val CustomTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.CustomTitle
            @androidx.compose.runtime.Composable
            public fun CustomTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.CustomTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.CustomTitle, *args)
            public val BundledAlpha: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.BundledAlpha
            @androidx.compose.runtime.Composable
            public fun BundledAlpha(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.BundledAlpha else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.BundledAlpha, *args)
            public val RestartHint: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartHint
            @androidx.compose.runtime.Composable
            public fun RestartHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartHint, *args)
            public val RestartDialogTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartDialogTitle
            @androidx.compose.runtime.Composable
            public fun RestartDialogTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartDialogTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartDialogTitle, *args)
            public val RestartDialogMessage: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartDialogMessage
            @androidx.compose.runtime.Composable
            public fun RestartDialogMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartDialogMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.RestartDialogMessage, *args)
            public val ActiveTag: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.ActiveTag
            @androidx.compose.runtime.Composable
            public fun ActiveTag(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.ActiveTag else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.ActiveTag, *args)
            public val DownloadingTag: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.DownloadingTag
            @androidx.compose.runtime.Composable
            public fun DownloadingTag(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.DownloadingTag else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.DownloadingTag, *args)
            public val InstalledTag: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.InstalledTag
            @androidx.compose.runtime.Composable
            public fun InstalledTag(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.InstalledTag else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.InstalledTag, *args)
            public val PluginFormatTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.PluginFormatTitle
            @androidx.compose.runtime.Composable
            public fun PluginFormatTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.PluginFormatTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.PluginFormatTitle, *args)
            public val PluginFormatBody: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Kernel.PluginFormatBody
            @androidx.compose.runtime.Composable
            public fun PluginFormatBody(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Kernel.PluginFormatBody else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Kernel.PluginFormatBody, *args)
        }
        public object RunMode {
            public val SectionTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RunMode.SectionTitle
            @androidx.compose.runtime.Composable
            public fun SectionTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RunMode.SectionTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RunMode.SectionTitle, *args)
            public val VpnServiceTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RunMode.VpnServiceTitle
            @androidx.compose.runtime.Composable
            public fun VpnServiceTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RunMode.VpnServiceTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RunMode.VpnServiceTitle, *args)
            public val VpnServiceSummary: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RunMode.VpnServiceSummary
            @androidx.compose.runtime.Composable
            public fun VpnServiceSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RunMode.VpnServiceSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RunMode.VpnServiceSummary, *args)
            public val TunTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RunMode.TunTitle
            @androidx.compose.runtime.Composable
            public fun TunTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RunMode.TunTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RunMode.TunTitle, *args)
            public val TunSummary: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RunMode.TunSummary
            @androidx.compose.runtime.Composable
            public fun TunSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RunMode.TunSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RunMode.TunSummary, *args)
            public val EbpfTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RunMode.EbpfTitle
            @androidx.compose.runtime.Composable
            public fun EbpfTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RunMode.EbpfTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RunMode.EbpfTitle, *args)
            public val EbpfSummary: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RunMode.EbpfSummary
            @androidx.compose.runtime.Composable
            public fun EbpfSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RunMode.EbpfSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RunMode.EbpfSummary, *args)
        }
        public object EbpfOptions {
            public val BypassCnTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.EbpfOptions.BypassCnTitle
            @androidx.compose.runtime.Composable
            public fun BypassCnTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.EbpfOptions.BypassCnTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.EbpfOptions.BypassCnTitle, *args)
            public val BypassCnSummary: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.EbpfOptions.BypassCnSummary
            @androidx.compose.runtime.Composable
            public fun BypassCnSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.EbpfOptions.BypassCnSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.EbpfOptions.BypassCnSummary, *args)
        }
        public object VpnOptions {
            public val BypassPrivateTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.BypassPrivateTitle
            @androidx.compose.runtime.Composable
            public fun BypassPrivateTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.BypassPrivateTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.BypassPrivateTitle, *args)
            public val DnsHijackTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.DnsHijackTitle
            @androidx.compose.runtime.Composable
            public fun DnsHijackTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.DnsHijackTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.DnsHijackTitle, *args)
            public val AllowBypassTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.AllowBypassTitle
            @androidx.compose.runtime.Composable
            public fun AllowBypassTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.AllowBypassTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.AllowBypassTitle, *args)
            public val EnableIpv6Title: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.EnableIpv6Title
            @androidx.compose.runtime.Composable
            public fun EnableIpv6Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.EnableIpv6Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.EnableIpv6Title, *args)
            public val SystemProxyTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.SystemProxyTitle
            @androidx.compose.runtime.Composable
            public fun SystemProxyTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.SystemProxyTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.SystemProxyTitle, *args)
            public val TunStackTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackTitle
            @androidx.compose.runtime.Composable
            public fun TunStackTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackTitle, *args)
            public val TunStackSystem: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackSystem
            @androidx.compose.runtime.Composable
            public fun TunStackSystem(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackSystem else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackSystem, *args)
            public val TunStackGVisor: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackGVisor
            @androidx.compose.runtime.Composable
            public fun TunStackGVisor(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackGVisor else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackGVisor, *args)
            public val TunStackMixed: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackMixed
            @androidx.compose.runtime.Composable
            public fun TunStackMixed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackMixed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackMixed, *args)
            public val TunStackMips: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackMips
            @androidx.compose.runtime.Composable
            public fun TunStackMips(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackMips else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.VpnOptions.TunStackMips, *args)
        }
        public object ProxyOptions {
            public val TunStackTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.TunStackTitle
            @androidx.compose.runtime.Composable
            public fun TunStackTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.TunStackTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.TunStackTitle, *args)
            public val AccessControlModeTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AccessControlModeTitle
            @androidx.compose.runtime.Composable
            public fun AccessControlModeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AccessControlModeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AccessControlModeTitle, *args)
            public val AllowAll: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AllowAll
            @androidx.compose.runtime.Composable
            public fun AllowAll(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AllowAll else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AllowAll, *args)
            public val AllowSelected: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AllowSelected
            @androidx.compose.runtime.Composable
            public fun AllowSelected(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AllowSelected else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.AllowSelected, *args)
            public val RejectSelected: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.RejectSelected
            @androidx.compose.runtime.Composable
            public fun RejectSelected(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.RejectSelected else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.RejectSelected, *args)
            public val ManageAccessControlTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.ManageAccessControlTitle
            @androidx.compose.runtime.Composable
            public fun ManageAccessControlTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.ManageAccessControlTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.ProxyOptions.ManageAccessControlTitle, *args)
        }
        public object RootTun {
            public val IfNameTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.IfNameTitle
            @androidx.compose.runtime.Composable
            public fun IfNameTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.IfNameTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.IfNameTitle, *args)
            public val IfNameSummary: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.IfNameSummary
            @androidx.compose.runtime.Composable
            public fun IfNameSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.IfNameSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.IfNameSummary, *args)
            public val MtuTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.MtuTitle
            @androidx.compose.runtime.Composable
            public fun MtuTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.MtuTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.MtuTitle, *args)
            public val MtuSummary: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.MtuSummary
            @androidx.compose.runtime.Composable
            public fun MtuSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.MtuSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.MtuSummary, *args)
            public val AutoRouteTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.AutoRouteTitle
            @androidx.compose.runtime.Composable
            public fun AutoRouteTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.AutoRouteTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.AutoRouteTitle, *args)
            public val StrictRouteTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.StrictRouteTitle
            @androidx.compose.runtime.Composable
            public fun StrictRouteTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.StrictRouteTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.StrictRouteTitle, *args)
            public val AutoRedirectTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.AutoRedirectTitle
            @androidx.compose.runtime.Composable
            public fun AutoRedirectTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.AutoRedirectTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.AutoRedirectTitle, *args)
            public val DnsModeTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeTitle
            @androidx.compose.runtime.Composable
            public fun DnsModeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeTitle, *args)
            public val DnsModeRedirHost: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeRedirHost
            @androidx.compose.runtime.Composable
            public fun DnsModeRedirHost(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeRedirHost else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeRedirHost, *args)
            public val DnsModeFakeIp: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeFakeIp
            @androidx.compose.runtime.Composable
            public fun DnsModeFakeIp(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeFakeIp else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.DnsModeFakeIp, *args)
            public val FakeIpRangeTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRangeTitle
            @androidx.compose.runtime.Composable
            public fun FakeIpRangeTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRangeTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRangeTitle, *args)
            public val FakeIpRangeSummary: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRangeSummary
            @androidx.compose.runtime.Composable
            public fun FakeIpRangeSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRangeSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRangeSummary, *args)
            public val FakeIpRange6Title: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRange6Title
            @androidx.compose.runtime.Composable
            public fun FakeIpRange6Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRange6Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRange6Title, *args)
            public val FakeIpRange6Summary: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRange6Summary
            @androidx.compose.runtime.Composable
            public fun FakeIpRange6Summary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRange6Summary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.RootTun.FakeIpRange6Summary, *args)
        }
        public object Error {
            public val VpnDenied: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Error.VpnDenied
            @androidx.compose.runtime.Composable
            public fun VpnDenied(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Error.VpnDenied else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Error.VpnDenied, *args)
            public val RootRequired: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.Error.RootRequired
            @androidx.compose.runtime.Composable
            public fun RootRequired(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.Error.RootRequired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.Error.RootRequired, *args)
        }
        public object WifiAutomation {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Title, *args)
            public val EnabledTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EnabledTitle
            @androidx.compose.runtime.Composable
            public fun EnabledTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EnabledTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EnabledTitle, *args)
            public val WifiNameHeading: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.WifiNameHeading
            @androidx.compose.runtime.Composable
            public fun WifiNameHeading(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.WifiNameHeading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.WifiNameHeading, *args)
            public val NetworkChangeSection: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NetworkChangeSection
            @androidx.compose.runtime.Composable
            public fun NetworkChangeSection(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NetworkChangeSection else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NetworkChangeSection, *args)
            public val OtherWifiTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.OtherWifiTitle
            @androidx.compose.runtime.Composable
            public fun OtherWifiTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.OtherWifiTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.OtherWifiTitle, *args)
            public val NoWifiTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoWifiTitle
            @androidx.compose.runtime.Composable
            public fun NoWifiTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoWifiTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoWifiTitle, *args)
            public val AddCurrent: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.AddCurrent
            @androidx.compose.runtime.Composable
            public fun AddCurrent(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.AddCurrent else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.AddCurrent, *args)
            public val AddManual: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.AddManual
            @androidx.compose.runtime.Composable
            public fun AddManual(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.AddManual else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.AddManual, *args)
            public val Scan: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Scan
            @androidx.compose.runtime.Composable
            public fun Scan(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Scan else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Scan, *args)
            public val Scanning: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Scanning
            @androidx.compose.runtime.Composable
            public fun Scanning(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Scanning else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Scanning, *args)
            public val ScanEmpty: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ScanEmpty
            @androidx.compose.runtime.Composable
            public fun ScanEmpty(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ScanEmpty else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ScanEmpty, *args)
            public val ScanUnavailable: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ScanUnavailable
            @androidx.compose.runtime.Composable
            public fun ScanUnavailable(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ScanUnavailable else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ScanUnavailable, *args)
            public val ManualDialogTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ManualDialogTitle
            @androidx.compose.runtime.Composable
            public fun ManualDialogTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ManualDialogTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ManualDialogTitle, *args)
            public val EditDialogTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EditDialogTitle
            @androidx.compose.runtime.Composable
            public fun EditDialogTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EditDialogTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EditDialogTitle, *args)
            public val ManualDialogLabel: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ManualDialogLabel
            @androidx.compose.runtime.Composable
            public fun ManualDialogLabel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ManualDialogLabel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ManualDialogLabel, *args)
            public val StartAction: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.StartAction
            @androidx.compose.runtime.Composable
            public fun StartAction(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.StartAction else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.StartAction, *args)
            public val StopAction: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.StopAction
            @androidx.compose.runtime.Composable
            public fun StopAction(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.StopAction else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.StopAction, *args)
            public val KeepAction: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.KeepAction
            @androidx.compose.runtime.Composable
            public fun KeepAction(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.KeepAction else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.KeepAction, *args)
            public val ActionTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ActionTitle
            @androidx.compose.runtime.Composable
            public fun ActionTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ActionTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ActionTitle, *args)
            public val ProfileAction: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ProfileAction
            @androidx.compose.runtime.Composable
            public fun ProfileAction(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ProfileAction else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ProfileAction, *args)
            public val NoSwitchAction: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoSwitchAction
            @androidx.compose.runtime.Composable
            public fun NoSwitchAction(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoSwitchAction else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoSwitchAction, *args)
            public val EmptyRules: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EmptyRules
            @androidx.compose.runtime.Composable
            public fun EmptyRules(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EmptyRules else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.EmptyRules, *args)
            public val VpnOnly: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.VpnOnly
            @androidx.compose.runtime.Composable
            public fun VpnOnly(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.VpnOnly else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.VpnOnly, *args)
            public val PermissionTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.PermissionTitle
            @androidx.compose.runtime.Composable
            public fun PermissionTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.PermissionTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.PermissionTitle, *args)
            public val PermissionMessage: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.PermissionMessage
            @androidx.compose.runtime.Composable
            public fun PermissionMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.PermissionMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.PermissionMessage, *args)
            public val ApproximateTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ApproximateTitle
            @androidx.compose.runtime.Composable
            public fun ApproximateTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ApproximateTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ApproximateTitle, *args)
            public val ApproximateMessage: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ApproximateMessage
            @androidx.compose.runtime.Composable
            public fun ApproximateMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ApproximateMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.ApproximateMessage, *args)
            public val DeniedTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.DeniedTitle
            @androidx.compose.runtime.Composable
            public fun DeniedTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.DeniedTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.DeniedTitle, *args)
            public val DeniedMessage: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.DeniedMessage
            @androidx.compose.runtime.Composable
            public fun DeniedMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.DeniedMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.DeniedMessage, *args)
            public val SettingsTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.SettingsTitle
            @androidx.compose.runtime.Composable
            public fun SettingsTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.SettingsTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.SettingsTitle, *args)
            public val SettingsMessage: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.SettingsMessage
            @androidx.compose.runtime.Composable
            public fun SettingsMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.SettingsMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.SettingsMessage, *args)
            public val LocationTitle: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.LocationTitle
            @androidx.compose.runtime.Composable
            public fun LocationTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.LocationTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.LocationTitle, *args)
            public val LocationMessage: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.LocationMessage
            @androidx.compose.runtime.Composable
            public fun LocationMessage(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.LocationMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.LocationMessage, *args)
            public val Grant: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Grant
            @androidx.compose.runtime.Composable
            public fun Grant(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Grant else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Grant, *args)
            public val OpenSettings: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.OpenSettings
            @androidx.compose.runtime.Composable
            public fun OpenSettings(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.OpenSettings else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.OpenSettings, *args)
            public val TurnOnLocation: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.TurnOnLocation
            @androidx.compose.runtime.Composable
            public fun TurnOnLocation(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.TurnOnLocation else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.TurnOnLocation, *args)
            public val Added: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Added
            @androidx.compose.runtime.Composable
            public fun Added(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Added else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Added, *args)
            public val Duplicate: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Duplicate
            @androidx.compose.runtime.Composable
            public fun Duplicate(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Duplicate else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Duplicate, *args)
            public val NoWifi: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoWifi
            @androidx.compose.runtime.Composable
            public fun NoWifi(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoWifi else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.NoWifi, *args)
            public val Unavailable: String
                get() = FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Unavailable
            @androidx.compose.runtime.Composable
            public fun Unavailable(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Unavailable else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.NetworkSettings.WifiAutomation.Unavailable, *args)
        }
    }
    public object Onboarding {
        public object Navigation {
            public val Back: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Navigation.Back
            @androidx.compose.runtime.Composable
            public fun Back(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Navigation.Back else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Navigation.Back, *args)
            public val Next: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Navigation.Next
            @androidx.compose.runtime.Composable
            public fun Next(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Navigation.Next else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Navigation.Next, *args)
            public val Enter: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Navigation.Enter
            @androidx.compose.runtime.Composable
            public fun Enter(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Navigation.Enter else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Navigation.Enter, *args)
            public val Start: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Navigation.Start
            @androidx.compose.runtime.Composable
            public fun Start(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Navigation.Start else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Navigation.Start, *args)
        }
        public object Welcome {
            public val Tagline: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Welcome.Tagline
            @androidx.compose.runtime.Composable
            public fun Tagline(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Welcome.Tagline else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Welcome.Tagline, *args)
        }
        public object Permission {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Permission.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Permission.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Permission.Title, *args)
            public val Subtitle: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Permission.Subtitle
            @androidx.compose.runtime.Composable
            public fun Subtitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Permission.Subtitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Permission.Subtitle, *args)
            public object Common {
                public val Granted: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Permission.Common.Granted
                @androidx.compose.runtime.Composable
                public fun Granted(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Permission.Common.Granted else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Permission.Common.Granted, *args)
            }
            public object Notification {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.Title, *args)
                public val SummaryNeed: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.SummaryNeed
                @androidx.compose.runtime.Composable
                public fun SummaryNeed(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.SummaryNeed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.SummaryNeed, *args)
                public val SummaryNotRequired: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.SummaryNotRequired
                @androidx.compose.runtime.Composable
                public fun SummaryNotRequired(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.SummaryNotRequired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Permission.Notification.SummaryNotRequired, *args)
            }
            public object AppList {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Permission.AppList.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Permission.AppList.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Permission.AppList.Title, *args)
                public val SummaryNeed: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Permission.AppList.SummaryNeed
                @androidx.compose.runtime.Composable
                public fun SummaryNeed(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Permission.AppList.SummaryNeed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Permission.AppList.SummaryNeed, *args)
            }
        }
        public object Privacy {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.Title, *args)
            public val Subtitle: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.Subtitle
            @androidx.compose.runtime.Composable
            public fun Subtitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.Subtitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.Subtitle, *args)
            public val RichTextLead: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextLead
            @androidx.compose.runtime.Composable
            public fun RichTextLead(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextLead else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextLead, *args)
            public val RichTextPrefix: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextPrefix
            @androidx.compose.runtime.Composable
            public fun RichTextPrefix(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextPrefix else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextPrefix, *args)
            public val RichTextConnector: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextConnector
            @androidx.compose.runtime.Composable
            public fun RichTextConnector(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextConnector else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextConnector, *args)
            public val RichTextSuffix: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextSuffix
            @androidx.compose.runtime.Composable
            public fun RichTextSuffix(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextSuffix else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.RichTextSuffix, *args)
            public val TermsLink: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.TermsLink
            @androidx.compose.runtime.Composable
            public fun TermsLink(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.TermsLink else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.TermsLink, *args)
            public val PolicyLink: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.PolicyLink
            @androidx.compose.runtime.Composable
            public fun PolicyLink(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.PolicyLink else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.PolicyLink, *args)
            public object Accept {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Privacy.Accept.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Privacy.Accept.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Privacy.Accept.Title, *args)
            }
        }
        public object Personalize {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Personalize.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Personalize.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Personalize.Title, *args)
            public val Subtitle: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Personalize.Subtitle
            @androidx.compose.runtime.Composable
            public fun Subtitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Personalize.Subtitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Personalize.Subtitle, *args)
        }
        public object Finish {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Finish.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Finish.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Finish.Title, *args)
            public val Subtitle: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Finish.Subtitle
            @androidx.compose.runtime.Composable
            public fun Subtitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Finish.Subtitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Finish.Subtitle, *args)
        }
        public object Project {
            public object Github {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Project.Github.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Project.Github.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Project.Github.Title, *args)
                public val Summary: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Project.Github.Summary
                @androidx.compose.runtime.Composable
                public fun Summary(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Project.Github.Summary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Project.Github.Summary, *args)
            }
            public object Community {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Project.Community.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Project.Community.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Project.Community.Title, *args)
                public val Summary: String
                    get() = FlyLocaleManager.activeStrings.Onboarding.Project.Community.Summary
                @androidx.compose.runtime.Composable
                public fun Summary(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Project.Community.Summary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Project.Community.Summary, *args)
            }
        }
        public object Sheet {
            public val PrivacyPolicyTitle: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Sheet.PrivacyPolicyTitle
            @androidx.compose.runtime.Composable
            public fun PrivacyPolicyTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Sheet.PrivacyPolicyTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Sheet.PrivacyPolicyTitle, *args)
            public val LoadFailed: String
                get() = FlyLocaleManager.activeStrings.Onboarding.Sheet.LoadFailed
            @androidx.compose.runtime.Composable
            public fun LoadFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Onboarding.Sheet.LoadFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Onboarding.Sheet.LoadFailed, *args)
        }
    }
    public object OpenSourceLicenses {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.OpenSourceLicenses.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.OpenSourceLicenses.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.OpenSourceLicenses.Title, *args)
        public object LicenseSheet {
            public val NoContent: String
                get() = FlyLocaleManager.activeStrings.OpenSourceLicenses.LicenseSheet.NoContent
            @androidx.compose.runtime.Composable
            public fun NoContent(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.OpenSourceLicenses.LicenseSheet.NoContent else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.OpenSourceLicenses.LicenseSheet.NoContent, *args)
        }
    }
    public object Override {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Override.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Title, *args)
        public object Action {
            public val Create: String
                get() = FlyLocaleManager.activeStrings.Override.Action.Create
            @androidx.compose.runtime.Composable
            public fun Create(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Action.Create else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Action.Create, *args)
            public val New: String
                get() = FlyLocaleManager.activeStrings.Override.Action.New
            @androidx.compose.runtime.Composable
            public fun New(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Action.New else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Action.New, *args)
            public val NetworkImport: String
                get() = FlyLocaleManager.activeStrings.Override.Action.NetworkImport
            @androidx.compose.runtime.Composable
            public fun NetworkImport(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Action.NetworkImport else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Action.NetworkImport, *args)
        }
        public object BuiltIn {
            public val PreventDnsLeak: String
                get() = FlyLocaleManager.activeStrings.Override.BuiltIn.PreventDnsLeak
            @androidx.compose.runtime.Composable
            public fun PreventDnsLeak(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.BuiltIn.PreventDnsLeak else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.BuiltIn.PreventDnsLeak, *args)
            public val AddDirectRules: String
                get() = FlyLocaleManager.activeStrings.Override.BuiltIn.AddDirectRules
            @androidx.compose.runtime.Composable
            public fun AddDirectRules(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.BuiltIn.AddDirectRules else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.BuiltIn.AddDirectRules, *args)
            public val PuddingDog: String
                get() = FlyLocaleManager.activeStrings.Override.BuiltIn.PuddingDog
            @androidx.compose.runtime.Composable
            public fun PuddingDog(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.BuiltIn.PuddingDog else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.BuiltIn.PuddingDog, *args)
            public val Acl4ssrOnlineFull: String
                get() = FlyLocaleManager.activeStrings.Override.BuiltIn.Acl4ssrOnlineFull
            @androidx.compose.runtime.Composable
            public fun Acl4ssrOnlineFull(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.BuiltIn.Acl4ssrOnlineFull else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.BuiltIn.Acl4ssrOnlineFull, *args)
            public val CopyName: String
                get() = FlyLocaleManager.activeStrings.Override.BuiltIn.CopyName
            @androidx.compose.runtime.Composable
            public fun CopyName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.BuiltIn.CopyName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.BuiltIn.CopyName, *args)
        }
        public object Empty {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.Override.Empty.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Empty.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Empty.Title, *args)
            public val Hint: String
                get() = FlyLocaleManager.activeStrings.Override.Empty.Hint
            @androidx.compose.runtime.Composable
            public fun Hint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Empty.Hint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Empty.Hint, *args)
            public val UserTitle: String
                get() = FlyLocaleManager.activeStrings.Override.Empty.UserTitle
            @androidx.compose.runtime.Composable
            public fun UserTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Empty.UserTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Empty.UserTitle, *args)
            public val UserHint: String
                get() = FlyLocaleManager.activeStrings.Override.Empty.UserHint
            @androidx.compose.runtime.Composable
            public fun UserHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Empty.UserHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Empty.UserHint, *args)
        }
        public object Section {
            public val BuiltIn: String
                get() = FlyLocaleManager.activeStrings.Override.Section.BuiltIn
            @androidx.compose.runtime.Composable
            public fun BuiltIn(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Section.BuiltIn else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Section.BuiltIn, *args)
            public val User: String
                get() = FlyLocaleManager.activeStrings.Override.Section.User
            @androidx.compose.runtime.Composable
            public fun User(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Section.User else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Section.User, *args)
        }
        public object Status {
            public val InUse: String
                get() = FlyLocaleManager.activeStrings.Override.Status.InUse
            @androidx.compose.runtime.Composable
            public fun InUse(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Status.InUse else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Status.InUse, *args)
            public val NotInUse: String
                get() = FlyLocaleManager.activeStrings.Override.Status.NotInUse
            @androidx.compose.runtime.Composable
            public fun NotInUse(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Status.NotInUse else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Status.NotInUse, *args)
            public val BuiltIn: String
                get() = FlyLocaleManager.activeStrings.Override.Status.BuiltIn
            @androidx.compose.runtime.Composable
            public fun BuiltIn(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Status.BuiltIn else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Status.BuiltIn, *args)
        }
        public object Card {
            public val Copy: String
                get() = FlyLocaleManager.activeStrings.Override.Card.Copy
            @androidx.compose.runtime.Composable
            public fun Copy(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Card.Copy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Card.Copy, *args)
            public val Export: String
                get() = FlyLocaleManager.activeStrings.Override.Card.Export
            @androidx.compose.runtime.Composable
            public fun Export(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Card.Export else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Card.Export, *args)
            public val Edit: String
                get() = FlyLocaleManager.activeStrings.Override.Card.Edit
            @androidx.compose.runtime.Composable
            public fun Edit(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Card.Edit else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Card.Edit, *args)
            public val Delete: String
                get() = FlyLocaleManager.activeStrings.Override.Card.Delete
            @androidx.compose.runtime.Composable
            public fun Delete(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Card.Delete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Card.Delete, *args)
            public val EditButton: String
                get() = FlyLocaleManager.activeStrings.Override.Card.EditButton
            @androidx.compose.runtime.Composable
            public fun EditButton(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Card.EditButton else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Card.EditButton, *args)
            public val DeleteButton: String
                get() = FlyLocaleManager.activeStrings.Override.Card.DeleteButton
            @androidx.compose.runtime.Composable
            public fun DeleteButton(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Card.DeleteButton else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Card.DeleteButton, *args)
            public val Apply: String
                get() = FlyLocaleManager.activeStrings.Override.Card.Apply
            @androidx.compose.runtime.Composable
            public fun Apply(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Card.Apply else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Card.Apply, *args)
            public val ApplyButton: String
                get() = FlyLocaleManager.activeStrings.Override.Card.ApplyButton
            @androidx.compose.runtime.Composable
            public fun ApplyButton(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Card.ApplyButton else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Card.ApplyButton, *args)
        }
        public object ApplySheet {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.Override.ApplySheet.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.ApplySheet.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.ApplySheet.Title, *args)
            public val Empty: String
                get() = FlyLocaleManager.activeStrings.Override.ApplySheet.Empty
            @androidx.compose.runtime.Composable
            public fun Empty(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.ApplySheet.Empty else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.ApplySheet.Empty, *args)
            public val Success: String
                get() = FlyLocaleManager.activeStrings.Override.ApplySheet.Success
            @androidx.compose.runtime.Composable
            public fun Success(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.ApplySheet.Success else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.ApplySheet.Success, *args)
            public val Failed: String
                get() = FlyLocaleManager.activeStrings.Override.ApplySheet.Failed
            @androidx.compose.runtime.Composable
            public fun Failed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.ApplySheet.Failed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.ApplySheet.Failed, *args)
            public object Button {
                public val Cancel: String
                    get() = FlyLocaleManager.activeStrings.Override.ApplySheet.Button.Cancel
                @androidx.compose.runtime.Composable
                public fun Cancel(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.ApplySheet.Button.Cancel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.ApplySheet.Button.Cancel, *args)
                public val Confirm: String
                    get() = FlyLocaleManager.activeStrings.Override.ApplySheet.Button.Confirm
                @androidx.compose.runtime.Composable
                public fun Confirm(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.ApplySheet.Button.Confirm else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.ApplySheet.Button.Confirm, *args)
            }
        }
        public object Import {
            public val ReadError: String
                get() = FlyLocaleManager.activeStrings.Override.Import.ReadError
            @androidx.compose.runtime.Composable
            public fun ReadError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Import.ReadError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Import.ReadError, *args)
            public val Failed: String
                get() = FlyLocaleManager.activeStrings.Override.Import.Failed
            @androidx.compose.runtime.Composable
            public fun Failed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Import.Failed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Import.Failed, *args)
            public val FileError: String
                get() = FlyLocaleManager.activeStrings.Override.Import.FileError
            @androidx.compose.runtime.Composable
            public fun FileError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Import.FileError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Import.FileError, *args)
            public val NetworkError: String
                get() = FlyLocaleManager.activeStrings.Override.Import.NetworkError
            @androidx.compose.runtime.Composable
            public fun NetworkError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Import.NetworkError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Import.NetworkError, *args)
            public val InvalidUrl: String
                get() = FlyLocaleManager.activeStrings.Override.Import.InvalidUrl
            @androidx.compose.runtime.Composable
            public fun InvalidUrl(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Import.InvalidUrl else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Import.InvalidUrl, *args)
            public val HttpError: String
                get() = FlyLocaleManager.activeStrings.Override.Import.HttpError
            @androidx.compose.runtime.Composable
            public fun HttpError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Import.HttpError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Import.HttpError, *args)
            public val UnsupportedType: String
                get() = FlyLocaleManager.activeStrings.Override.Import.UnsupportedType
            @androidx.compose.runtime.Composable
            public fun UnsupportedType(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Import.UnsupportedType else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Import.UnsupportedType, *args)
            public val EmptyJavaScript: String
                get() = FlyLocaleManager.activeStrings.Override.Import.EmptyJavaScript
            @androidx.compose.runtime.Composable
            public fun EmptyJavaScript(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Import.EmptyJavaScript else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Import.EmptyJavaScript, *args)
        }
        public object Error {
            public val SaveFailed: String
                get() = FlyLocaleManager.activeStrings.Override.Error.SaveFailed
            @androidx.compose.runtime.Composable
            public fun SaveFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Error.SaveFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Error.SaveFailed, *args)
        }
        public object Export {
            public val Failed: String
                get() = FlyLocaleManager.activeStrings.Override.Export.Failed
            @androidx.compose.runtime.Composable
            public fun Failed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Export.Failed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Export.Failed, *args)
            public val Success: String
                get() = FlyLocaleManager.activeStrings.Override.Export.Success
            @androidx.compose.runtime.Composable
            public fun Success(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Export.Success else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Export.Success, *args)
        }
        public object Dialog {
            public object Create {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Create.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Create.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Create.Title, *args)
                public val Name: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Create.Name
                @androidx.compose.runtime.Composable
                public fun Name(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Create.Name else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Create.Name, *args)
                public val Url: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Create.Url
                @androidx.compose.runtime.Composable
                public fun Url(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Create.Url else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Create.Url, *args)
                public val Type: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Create.Type
                @androidx.compose.runtime.Composable
                public fun Type(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Create.Type else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Create.Type, *args)
            }
            public object Delete {
                public val Title: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Delete.Title
                @androidx.compose.runtime.Composable
                public fun Title(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Delete.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Delete.Title, *args)
                public val InUseMessage: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Delete.InUseMessage
                @androidx.compose.runtime.Composable
                public fun InUseMessage(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Delete.InUseMessage else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Delete.InUseMessage, *args)
                public val Message: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Delete.Message
                @androidx.compose.runtime.Composable
                public fun Message(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Delete.Message else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Delete.Message, *args)
            }
            public object Button {
                public val Cancel: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Button.Cancel
                @androidx.compose.runtime.Composable
                public fun Cancel(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Button.Cancel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Button.Cancel, *args)
                public val Delete: String
                    get() = FlyLocaleManager.activeStrings.Override.Dialog.Button.Delete
                @androidx.compose.runtime.Composable
                public fun Delete(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Dialog.Button.Delete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Dialog.Button.Delete, *args)
            }
        }
        public object Draft {
            public val BasicRouting: String
                get() = FlyLocaleManager.activeStrings.Override.Draft.BasicRouting
            @androidx.compose.runtime.Composable
            public fun BasicRouting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Draft.BasicRouting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Draft.BasicRouting, *args)
            public val ServiceRouting: String
                get() = FlyLocaleManager.activeStrings.Override.Draft.ServiceRouting
            @androidx.compose.runtime.Composable
            public fun ServiceRouting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Draft.ServiceRouting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Draft.ServiceRouting, *args)
        }
        public object Save {
            public val ImportDefaultName: String
                get() = FlyLocaleManager.activeStrings.Override.Save.ImportDefaultName
            @androidx.compose.runtime.Composable
            public fun ImportDefaultName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Save.ImportDefaultName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Save.ImportDefaultName, *args)
        }
        public object Label {
            public val RulesReplace: String
                get() = FlyLocaleManager.activeStrings.Override.Label.RulesReplace
            @androidx.compose.runtime.Composable
            public fun RulesReplace(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Override.Label.RulesReplace else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Override.Label.RulesReplace, *args)
        }
    }
    public object ProfilesPage {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.ProfilesPage.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Title, *args)
        public object Action {
            public val UpdateAll: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Action.UpdateAll
            @androidx.compose.runtime.Composable
            public fun UpdateAll(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Action.UpdateAll else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Action.UpdateAll, *args)
            public val AddProfile: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Action.AddProfile
            @androidx.compose.runtime.Composable
            public fun AddProfile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Action.AddProfile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Action.AddProfile, *args)
        }
        public object Empty {
            public val NoProfiles: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Empty.NoProfiles
            @androidx.compose.runtime.Composable
            public fun NoProfiles(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Empty.NoProfiles else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Empty.NoProfiles, *args)
            public val Hint: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Empty.Hint
            @androidx.compose.runtime.Composable
            public fun Hint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Empty.Hint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Empty.Hint, *args)
        }
        public object Sheet {
            public val AddTitle: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Sheet.AddTitle
            @androidx.compose.runtime.Composable
            public fun AddTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Sheet.AddTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Sheet.AddTitle, *args)
            public val EditTitle: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Sheet.EditTitle
            @androidx.compose.runtime.Composable
            public fun EditTitle(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Sheet.EditTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Sheet.EditTitle, *args)
        }
        public object Type {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Type.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Type.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Type.Title, *args)
            public val Subscription: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Type.Subscription
            @androidx.compose.runtime.Composable
            public fun Subscription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Type.Subscription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Type.Subscription, *args)
            public val LocalFile: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Type.LocalFile
            @androidx.compose.runtime.Composable
            public fun LocalFile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Type.LocalFile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Type.LocalFile, *args)
            public val QrScan: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Type.QrScan
            @androidx.compose.runtime.Composable
            public fun QrScan(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Type.QrScan else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Type.QrScan, *args)
        }
        public object Input {
            public val ProfileName: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Input.ProfileName
            @androidx.compose.runtime.Composable
            public fun ProfileName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Input.ProfileName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Input.ProfileName, *args)
            public val SubscriptionUrl: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Input.SubscriptionUrl
            @androidx.compose.runtime.Composable
            public fun SubscriptionUrl(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Input.SubscriptionUrl else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Input.SubscriptionUrl, *args)
            public val SelectFile: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Input.SelectFile
            @androidx.compose.runtime.Composable
            public fun SelectFile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Input.SelectFile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Input.SelectFile, *args)
            public val NewProfile: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Input.NewProfile
            @androidx.compose.runtime.Composable
            public fun NewProfile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Input.NewProfile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Input.NewProfile, *args)
            public val AgeSecretKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Input.AgeSecretKey
            @androidx.compose.runtime.Composable
            public fun AgeSecretKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Input.AgeSecretKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Input.AgeSecretKey, *args)
            public val AutoUpdateInterval: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Input.AutoUpdateInterval
            @androidx.compose.runtime.Composable
            public fun AutoUpdateInterval(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Input.AutoUpdateInterval else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Input.AutoUpdateInterval, *args)
            public val AutoUpdateIntervalHint: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Input.AutoUpdateIntervalHint
            @androidx.compose.runtime.Composable
            public fun AutoUpdateIntervalHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Input.AutoUpdateIntervalHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Input.AutoUpdateIntervalHint, *args)
        }
        public object QrScanner {
            public val NeedPermission: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.NeedPermission
            @androidx.compose.runtime.Composable
            public fun NeedPermission(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.NeedPermission else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.NeedPermission, *args)
            public val NeedCamera: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.NeedCamera
            @androidx.compose.runtime.Composable
            public fun NeedCamera(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.NeedCamera else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.NeedCamera, *args)
            public val SelectFromAlbum: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.SelectFromAlbum
            @androidx.compose.runtime.Composable
            public fun SelectFromAlbum(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.SelectFromAlbum else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.SelectFromAlbum, *args)
            public val RecognizeSuccess: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeSuccess
            @androidx.compose.runtime.Composable
            public fun RecognizeSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeSuccess, *args)
            public val RecognizeFailed: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeFailed
            @androidx.compose.runtime.Composable
            public fun RecognizeFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeFailed, *args)
            public val RecognizeError: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeError
            @androidx.compose.runtime.Composable
            public fun RecognizeError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.QrScanner.RecognizeError, *args)
        }
        public object Message {
            public val UnknownFile: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Message.UnknownFile
            @androidx.compose.runtime.Composable
            public fun UnknownFile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Message.UnknownFile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Message.UnknownFile, *args)
        }
        public object Validation {
            public val EnterUrl: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Validation.EnterUrl
            @androidx.compose.runtime.Composable
            public fun EnterUrl(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Validation.EnterUrl else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Validation.EnterUrl, *args)
            public val SelectFile: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Validation.SelectFile
            @androidx.compose.runtime.Composable
            public fun SelectFile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Validation.SelectFile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Validation.SelectFile, *args)
            public val YamlOnly: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Validation.YamlOnly
            @androidx.compose.runtime.Composable
            public fun YamlOnly(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Validation.YamlOnly else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Validation.YamlOnly, *args)
        }
        public object Progress {
            public val Downloading: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Progress.Downloading
            @androidx.compose.runtime.Composable
            public fun Downloading(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Progress.Downloading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Progress.Downloading, *args)
        }
        public object Button {
            public val Cancel: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Button.Cancel
            @androidx.compose.runtime.Composable
            public fun Cancel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Button.Cancel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Button.Cancel, *args)
            public val Confirm: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.Button.Confirm
            @androidx.compose.runtime.Composable
            public fun Confirm(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.Button.Confirm else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.Button.Confirm, *args)
        }
        public object DeleteDialog {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Title, *args)
            public val Message: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Message
            @androidx.compose.runtime.Composable
            public fun Message(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Message else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Message, *args)
            public val Confirm: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Confirm
            @androidx.compose.runtime.Composable
            public fun Confirm(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Confirm else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.DeleteDialog.Confirm, *args)
        }
        public object EditDialog {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.EditDialog.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.EditDialog.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.EditDialog.Title, *args)
        }
        public object LinkSettings {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Title, *args)
            public val OpenMode: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenMode
            @androidx.compose.runtime.Composable
            public fun OpenMode(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenMode else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenMode, *args)
            public val OpenModeInApp: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenModeInApp
            @androidx.compose.runtime.Composable
            public fun OpenModeInApp(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenModeInApp else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenModeInApp, *args)
            public val OpenModeExternal: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenModeExternal
            @androidx.compose.runtime.Composable
            public fun OpenModeExternal(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenModeExternal else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.OpenModeExternal, *args)
            public val DefaultLink: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.DefaultLink
            @androidx.compose.runtime.Composable
            public fun DefaultLink(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.DefaultLink else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.DefaultLink, *args)
            public val DefaultLinkSummary: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.DefaultLinkSummary
            @androidx.compose.runtime.Composable
            public fun DefaultLinkSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.DefaultLinkSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.DefaultLinkSummary, *args)
            public val AddLink: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.AddLink
            @androidx.compose.runtime.Composable
            public fun AddLink(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.AddLink else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.AddLink, *args)
            public val EditLink: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.EditLink
            @androidx.compose.runtime.Composable
            public fun EditLink(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.EditLink else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.EditLink, *args)
            public val Name: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Name
            @androidx.compose.runtime.Composable
            public fun Name(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Name else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Name, *args)
            public val Url: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Url
            @androidx.compose.runtime.Composable
            public fun Url(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Url else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Url, *args)
            public val Close: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Close
            @androidx.compose.runtime.Composable
            public fun Close(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Close else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Close, *args)
            public object Validation {
                public val EnterName: String
                    get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.EnterName
                @androidx.compose.runtime.Composable
                public fun EnterName(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.EnterName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.EnterName, *args)
                public val EnterUrl: String
                    get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.EnterUrl
                @androidx.compose.runtime.Composable
                public fun EnterUrl(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.EnterUrl else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.EnterUrl, *args)
                public val InvalidUrl: String
                    get() = FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.InvalidUrl
                @androidx.compose.runtime.Composable
                public fun InvalidUrl(vararg args: Any?): String =
                    if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.InvalidUrl else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.LinkSettings.Validation.InvalidUrl, *args)
            }
        }
        public object ShareDialog {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.Title, *args)
            public val ShareFile: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ShareFile
            @androidx.compose.runtime.Composable
            public fun ShareFile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ShareFile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ShareFile, *args)
            public val ShareLink: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ShareLink
            @androidx.compose.runtime.Composable
            public fun ShareLink(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ShareLink else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ShareLink, *args)
            public val NoLink: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.NoLink
            @androidx.compose.runtime.Composable
            public fun NoLink(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.NoLink else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.NoLink, *args)
            public val ImportedConfigMissing: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ImportedConfigMissing
            @androidx.compose.runtime.Composable
            public fun ImportedConfigMissing(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ImportedConfigMissing else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.ShareDialog.ImportedConfigMissing, *args)
        }
        public object SettingsDialog {
            public val Title: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.Title
            @androidx.compose.runtime.Composable
            public fun Title(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.Title, *args)
            public val SectionType: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionType
            @androidx.compose.runtime.Composable
            public fun SectionType(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionType else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionType, *args)
            public val SectionSubscription: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionSubscription
            @androidx.compose.runtime.Composable
            public fun SectionSubscription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionSubscription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionSubscription, *args)
            public val SectionOverride: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionOverride
            @androidx.compose.runtime.Composable
            public fun SectionOverride(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionOverride else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SectionOverride, *args)
            public val ChangeLink: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.ChangeLink
            @androidx.compose.runtime.Composable
            public fun ChangeLink(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.ChangeLink else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.ChangeLink, *args)
            public val CustomRouting: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.CustomRouting
            @androidx.compose.runtime.Composable
            public fun CustomRouting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.CustomRouting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.CustomRouting, *args)
            public val CustomRoutingSummary: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.CustomRoutingSummary
            @androidx.compose.runtime.Composable
            public fun CustomRoutingSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.CustomRoutingSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.CustomRoutingSummary, *args)
            public val NoDescription: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.NoDescription
            @androidx.compose.runtime.Composable
            public fun NoDescription(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.NoDescription else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.NoDescription, *args)
            public val EditProfile: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.EditProfile
            @androidx.compose.runtime.Composable
            public fun EditProfile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.EditProfile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.EditProfile, *args)
            public val OpenConfig: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.OpenConfig
            @androidx.compose.runtime.Composable
            public fun OpenConfig(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.OpenConfig else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.OpenConfig, *args)
            public val EditSettings: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.EditSettings
            @androidx.compose.runtime.Composable
            public fun EditSettings(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.EditSettings else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.EditSettings, *args)
            public val SaveFailed: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SaveFailed
            @androidx.compose.runtime.Composable
            public fun SaveFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SaveFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.SaveFailed, *args)
            public val ConfigMissing: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.ConfigMissing
            @androidx.compose.runtime.Composable
            public fun ConfigMissing(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.ConfigMissing else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.ConfigMissing, *args)
            public val AgeSecretKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.AgeSecretKey
            @androidx.compose.runtime.Composable
            public fun AgeSecretKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.AgeSecretKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.SettingsDialog.AgeSecretKey, *args)
        }
        public object AgeKey {
            public val PublicKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.PublicKey
            @androidx.compose.runtime.Composable
            public fun PublicKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.PublicKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.PublicKey, *args)
            public val SecretKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.SecretKey
            @androidx.compose.runtime.Composable
            public fun SecretKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.SecretKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.SecretKey, *args)
            public val PublicKeyPlaceholder: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.PublicKeyPlaceholder
            @androidx.compose.runtime.Composable
            public fun PublicKeyPlaceholder(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.PublicKeyPlaceholder else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.PublicKeyPlaceholder, *args)
            public val DerivePublicKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.DerivePublicKey
            @androidx.compose.runtime.Composable
            public fun DerivePublicKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.DerivePublicKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.DerivePublicKey, *args)
            public val CopyPublicKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopyPublicKey
            @androidx.compose.runtime.Composable
            public fun CopyPublicKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopyPublicKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopyPublicKey, *args)
            public val GenerateSecretKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GenerateSecretKey
            @androidx.compose.runtime.Composable
            public fun GenerateSecretKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GenerateSecretKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GenerateSecretKey, *args)
            public val CopySecretKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopySecretKey
            @androidx.compose.runtime.Composable
            public fun CopySecretKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopySecretKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopySecretKey, *args)
            public val HideKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.HideKey
            @androidx.compose.runtime.Composable
            public fun HideKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.HideKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.HideKey, *args)
            public val ShowKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.ShowKey
            @androidx.compose.runtime.Composable
            public fun ShowKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.ShowKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.ShowKey, *args)
            public val GeneratedPublicKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GeneratedPublicKey
            @androidx.compose.runtime.Composable
            public fun GeneratedPublicKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GeneratedPublicKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GeneratedPublicKey, *args)
            public val CopiedPublicKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopiedPublicKey
            @androidx.compose.runtime.Composable
            public fun CopiedPublicKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopiedPublicKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopiedPublicKey, *args)
            public val GeneratedSecretKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GeneratedSecretKey
            @androidx.compose.runtime.Composable
            public fun GeneratedSecretKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GeneratedSecretKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GeneratedSecretKey, *args)
            public val CopiedSecretKey: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopiedSecretKey
            @androidx.compose.runtime.Composable
            public fun CopiedSecretKey(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopiedSecretKey else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.CopiedSecretKey, *args)
            public val GenerateFailed: String
                get() = FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GenerateFailed
            @androidx.compose.runtime.Composable
            public fun GenerateFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GenerateFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesPage.AgeKey.GenerateFailed, *args)
        }
    }
    public object ProfilesVM {
        public object Message {
            public val ProfileAdded: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileAdded
            @androidx.compose.runtime.Composable
            public fun ProfileAdded(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileAdded else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileAdded, *args)
            public val AddFailed: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Message.AddFailed
            @androidx.compose.runtime.Composable
            public fun AddFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Message.AddFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Message.AddFailed, *args)
            public val ProfileDeleted: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileDeleted
            @androidx.compose.runtime.Composable
            public fun ProfileDeleted(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileDeleted else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileDeleted, *args)
            public val DeleteFailed: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Message.DeleteFailed
            @androidx.compose.runtime.Composable
            public fun DeleteFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Message.DeleteFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Message.DeleteFailed, *args)
            public val ProfileUpdated: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileUpdated
            @androidx.compose.runtime.Composable
            public fun ProfileUpdated(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileUpdated else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Message.ProfileUpdated, *args)
            public val UpdateFailed: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Message.UpdateFailed
            @androidx.compose.runtime.Composable
            public fun UpdateFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Message.UpdateFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Message.UpdateFailed, *args)
            public val ToggleFailed: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Message.ToggleFailed
            @androidx.compose.runtime.Composable
            public fun ToggleFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Message.ToggleFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Message.ToggleFailed, *args)
            public val CannotDeleteActiveProfile: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Message.CannotDeleteActiveProfile
            @androidx.compose.runtime.Composable
            public fun CannotDeleteActiveProfile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Message.CannotDeleteActiveProfile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Message.CannotDeleteActiveProfile, *args)
        }
        public object Progress {
            public val Preparing: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Progress.Preparing
            @androidx.compose.runtime.Composable
            public fun Preparing(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Progress.Preparing else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Progress.Preparing, *args)
            public val Verifying: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Progress.Verifying
            @androidx.compose.runtime.Composable
            public fun Verifying(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Progress.Verifying else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Progress.Verifying, *args)
            public val ImportComplete: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Progress.ImportComplete
            @androidx.compose.runtime.Composable
            public fun ImportComplete(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Progress.ImportComplete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Progress.ImportComplete, *args)
        }
        public object Error {
            public val ProfileNotExist: String
                get() = FlyLocaleManager.activeStrings.ProfilesVM.Error.ProfileNotExist
            @androidx.compose.runtime.Composable
            public fun ProfileNotExist(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.ProfilesVM.Error.ProfileNotExist else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.ProfilesVM.Error.ProfileNotExist, *args)
        }
    }
    public object Providers {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Providers.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Title, *args)
        public object Action {
            public val UpdateAll: String
                get() = FlyLocaleManager.activeStrings.Providers.Action.UpdateAll
            @androidx.compose.runtime.Composable
            public fun UpdateAll(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Action.UpdateAll else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Action.UpdateAll, *args)
            public val Update: String
                get() = FlyLocaleManager.activeStrings.Providers.Action.Update
            @androidx.compose.runtime.Composable
            public fun Update(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Action.Update else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Action.Update, *args)
            public val Upload: String
                get() = FlyLocaleManager.activeStrings.Providers.Action.Upload
            @androidx.compose.runtime.Composable
            public fun Upload(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Action.Upload else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Action.Upload, *args)
            public val View: String
                get() = FlyLocaleManager.activeStrings.Providers.Action.View
            @androidx.compose.runtime.Composable
            public fun View(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Action.View else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Action.View, *args)
            public val Operation: String
                get() = FlyLocaleManager.activeStrings.Providers.Action.Operation
            @androidx.compose.runtime.Composable
            public fun Operation(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Action.Operation else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Action.Operation, *args)
            public val OpenInFiles: String
                get() = FlyLocaleManager.activeStrings.Providers.Action.OpenInFiles
            @androidx.compose.runtime.Composable
            public fun OpenInFiles(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Action.OpenInFiles else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Action.OpenInFiles, *args)
        }
        public object Empty {
            public val NotRunning: String
                get() = FlyLocaleManager.activeStrings.Providers.Empty.NotRunning
            @androidx.compose.runtime.Composable
            public fun NotRunning(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Empty.NotRunning else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Empty.NotRunning, *args)
            public val NotRunningHint: String
                get() = FlyLocaleManager.activeStrings.Providers.Empty.NotRunningHint
            @androidx.compose.runtime.Composable
            public fun NotRunningHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Empty.NotRunningHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Empty.NotRunningHint, *args)
            public val NoProviders: String
                get() = FlyLocaleManager.activeStrings.Providers.Empty.NoProviders
            @androidx.compose.runtime.Composable
            public fun NoProviders(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Empty.NoProviders else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Empty.NoProviders, *args)
            public val NoProvidersHint: String
                get() = FlyLocaleManager.activeStrings.Providers.Empty.NoProvidersHint
            @androidx.compose.runtime.Composable
            public fun NoProvidersHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Empty.NoProvidersHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Empty.NoProvidersHint, *args)
        }
        public object Type {
            public val ProxyProviders: String
                get() = FlyLocaleManager.activeStrings.Providers.Type.ProxyProviders
            @androidx.compose.runtime.Composable
            public fun ProxyProviders(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Type.ProxyProviders else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Type.ProxyProviders, *args)
            public val RuleProviders: String
                get() = FlyLocaleManager.activeStrings.Providers.Type.RuleProviders
            @androidx.compose.runtime.Composable
            public fun RuleProviders(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Type.RuleProviders else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Type.RuleProviders, *args)
        }
        public object Info {
            public val ExpireDays: String
                get() = FlyLocaleManager.activeStrings.Providers.Info.ExpireDays
            @androidx.compose.runtime.Composable
            public fun ExpireDays(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Info.ExpireDays else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Info.ExpireDays, *args)
            public val Expired: String
                get() = FlyLocaleManager.activeStrings.Providers.Info.Expired
            @androidx.compose.runtime.Composable
            public fun Expired(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Info.Expired else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Info.Expired, *args)
            public val RemainingTraffic: String
                get() = FlyLocaleManager.activeStrings.Providers.Info.RemainingTraffic
            @androidx.compose.runtime.Composable
            public fun RemainingTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Info.RemainingTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Info.RemainingTraffic, *args)
            public val UsedTraffic: String
                get() = FlyLocaleManager.activeStrings.Providers.Info.UsedTraffic
            @androidx.compose.runtime.Composable
            public fun UsedTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Info.UsedTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Info.UsedTraffic, *args)
        }
        public object VehicleType {
            public val Http: String
                get() = FlyLocaleManager.activeStrings.Providers.VehicleType.Http
            @androidx.compose.runtime.Composable
            public fun Http(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.VehicleType.Http else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.VehicleType.Http, *args)
            public val File: String
                get() = FlyLocaleManager.activeStrings.Providers.VehicleType.File
            @androidx.compose.runtime.Composable
            public fun File(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.VehicleType.File else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.VehicleType.File, *args)
            public val Inline: String
                get() = FlyLocaleManager.activeStrings.Providers.VehicleType.Inline
            @androidx.compose.runtime.Composable
            public fun Inline(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.VehicleType.Inline else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.VehicleType.Inline, *args)
            public val Compatible: String
                get() = FlyLocaleManager.activeStrings.Providers.VehicleType.Compatible
            @androidx.compose.runtime.Composable
            public fun Compatible(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.VehicleType.Compatible else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.VehicleType.Compatible, *args)
        }
        public object Message {
            public val FetchFailed: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.FetchFailed
            @androidx.compose.runtime.Composable
            public fun FetchFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.FetchFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.FetchFailed, *args)
            public val UpdateSuccess: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.UpdateSuccess
            @androidx.compose.runtime.Composable
            public fun UpdateSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.UpdateSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.UpdateSuccess, *args)
            public val UpdateFailed: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.UpdateFailed
            @androidx.compose.runtime.Composable
            public fun UpdateFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.UpdateFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.UpdateFailed, *args)
            public val AllUpdated: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.AllUpdated
            @androidx.compose.runtime.Composable
            public fun AllUpdated(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.AllUpdated else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.AllUpdated, *args)
            public val UploadSuccess: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.UploadSuccess
            @androidx.compose.runtime.Composable
            public fun UploadSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.UploadSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.UploadSuccess, *args)
            public val UploadFailed: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.UploadFailed
            @androidx.compose.runtime.Composable
            public fun UploadFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.UploadFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.UploadFailed, *args)
            public val OpenFileFailed: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.OpenFileFailed
            @androidx.compose.runtime.Composable
            public fun OpenFileFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.OpenFileFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.OpenFileFailed, *args)
            public val ConvertMrsFailed: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.ConvertMrsFailed
            @androidx.compose.runtime.Composable
            public fun ConvertMrsFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.ConvertMrsFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.ConvertMrsFailed, *args)
            public val CannotReadFile: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.CannotReadFile
            @androidx.compose.runtime.Composable
            public fun CannotReadFile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.CannotReadFile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.CannotReadFile, *args)
            public val FileExceedsLimit: String
                get() = FlyLocaleManager.activeStrings.Providers.Message.FileExceedsLimit
            @androidx.compose.runtime.Composable
            public fun FileExceedsLimit(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.Message.FileExceedsLimit else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.Message.FileExceedsLimit, *args)
        }
        public val InfoTitle: String
            get() = FlyLocaleManager.activeStrings.Providers.InfoTitle
        @androidx.compose.runtime.Composable
        public fun InfoTitle(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.InfoTitle else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.InfoTitle, *args)
        public val InfoSummary: String
            get() = FlyLocaleManager.activeStrings.Providers.InfoSummary
        @androidx.compose.runtime.Composable
        public fun InfoSummary(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.InfoSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.InfoSummary, *args)
        public val ProviderPath: String
            get() = FlyLocaleManager.activeStrings.Providers.ProviderPath
        @androidx.compose.runtime.Composable
        public fun ProviderPath(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Providers.ProviderPath else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Providers.ProviderPath, *args)
    }
    public object Proxy {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Proxy.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Title, *args)
        public object Mode {
            public val Direct: String
                get() = FlyLocaleManager.activeStrings.Proxy.Mode.Direct
            @androidx.compose.runtime.Composable
            public fun Direct(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Mode.Direct else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Mode.Direct, *args)
        }
        public object Action {
            public val Panel: String
                get() = FlyLocaleManager.activeStrings.Proxy.Action.Panel
            @androidx.compose.runtime.Composable
            public fun Panel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Action.Panel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Action.Panel, *args)
            public val Test: String
                get() = FlyLocaleManager.activeStrings.Proxy.Action.Test
            @androidx.compose.runtime.Composable
            public fun Test(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Action.Test else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Action.Test, *args)
            public val Sort: String
                get() = FlyLocaleManager.activeStrings.Proxy.Action.Sort
            @androidx.compose.runtime.Composable
            public fun Sort(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Action.Sort else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Action.Sort, *args)
        }
        public object Node {
            public val Timeout: String
                get() = FlyLocaleManager.activeStrings.Proxy.Node.Timeout
            @androidx.compose.runtime.Composable
            public fun Timeout(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Node.Timeout else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Node.Timeout, *args)
            public val Count: String
                get() = FlyLocaleManager.activeStrings.Proxy.Node.Count
            @androidx.compose.runtime.Composable
            public fun Count(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Node.Count else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Node.Count, *args)
        }
        public object Empty {
            public val NoNodes: String
                get() = FlyLocaleManager.activeStrings.Proxy.Empty.NoNodes
            @androidx.compose.runtime.Composable
            public fun NoNodes(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Empty.NoNodes else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Empty.NoNodes, *args)
            public val Hint: String
                get() = FlyLocaleManager.activeStrings.Proxy.Empty.Hint
            @androidx.compose.runtime.Composable
            public fun Hint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Empty.Hint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Empty.Hint, *args)
        }
        public object Testing {
            public val Group: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.Group
            @androidx.compose.runtime.Composable
            public fun Group(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.Group else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.Group, *args)
            public val All: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.All
            @androidx.compose.runtime.Composable
            public fun All(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.All else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.All, *args)
            public val RequestSent: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.RequestSent
            @androidx.compose.runtime.Composable
            public fun RequestSent(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.RequestSent else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.RequestSent, *args)
            public val Failed: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.Failed
            @androidx.compose.runtime.Composable
            public fun Failed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.Failed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.Failed, *args)
            public val InProgress: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.InProgress
            @androidx.compose.runtime.Composable
            public fun InProgress(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.InProgress else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.InProgress, *args)
            public val Progress: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.Progress
            @androidx.compose.runtime.Composable
            public fun Progress(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.Progress else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.Progress, *args)
            public val Pull: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.Pull
            @androidx.compose.runtime.Composable
            public fun Pull(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.Pull else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.Pull, *args)
            public val Release: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.Release
            @androidx.compose.runtime.Composable
            public fun Release(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.Release else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.Release, *args)
            public val Complete: String
                get() = FlyLocaleManager.activeStrings.Proxy.Testing.Complete
            @androidx.compose.runtime.Composable
            public fun Complete(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Testing.Complete else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Testing.Complete, *args)
        }
        public object Selection {
            public val Switched: String
                get() = FlyLocaleManager.activeStrings.Proxy.Selection.Switched
            @androidx.compose.runtime.Composable
            public fun Switched(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Selection.Switched else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Selection.Switched, *args)
            public val Failed: String
                get() = FlyLocaleManager.activeStrings.Proxy.Selection.Failed
            @androidx.compose.runtime.Composable
            public fun Failed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Selection.Failed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Selection.Failed, *args)
            public val Error: String
                get() = FlyLocaleManager.activeStrings.Proxy.Selection.Error
            @androidx.compose.runtime.Composable
            public fun Error(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Selection.Error else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Selection.Error, *args)
            public val Timeout: String
                get() = FlyLocaleManager.activeStrings.Proxy.Selection.Timeout
            @androidx.compose.runtime.Composable
            public fun Timeout(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Selection.Timeout else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Selection.Timeout, *args)
            public val RequireRunning: String
                get() = FlyLocaleManager.activeStrings.Proxy.Selection.RequireRunning
            @androidx.compose.runtime.Composable
            public fun RequireRunning(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.Selection.RequireRunning else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.Selection.RequireRunning, *args)
        }
        public object SortMode {
            public val Default: String
                get() = FlyLocaleManager.activeStrings.Proxy.SortMode.Default
            @androidx.compose.runtime.Composable
            public fun Default(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.SortMode.Default else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.SortMode.Default, *args)
            public val ByName: String
                get() = FlyLocaleManager.activeStrings.Proxy.SortMode.ByName
            @androidx.compose.runtime.Composable
            public fun ByName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.SortMode.ByName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.SortMode.ByName, *args)
            public val ByLatency: String
                get() = FlyLocaleManager.activeStrings.Proxy.SortMode.ByLatency
            @androidx.compose.runtime.Composable
            public fun ByLatency(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.SortMode.ByLatency else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.SortMode.ByLatency, *args)
        }
        public object DisplayMode {
            public val SingleDetailed: String
                get() = FlyLocaleManager.activeStrings.Proxy.DisplayMode.SingleDetailed
            @androidx.compose.runtime.Composable
            public fun SingleDetailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.DisplayMode.SingleDetailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.DisplayMode.SingleDetailed, *args)
            public val SingleSimple: String
                get() = FlyLocaleManager.activeStrings.Proxy.DisplayMode.SingleSimple
            @androidx.compose.runtime.Composable
            public fun SingleSimple(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.DisplayMode.SingleSimple else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.DisplayMode.SingleSimple, *args)
            public val DoubleDetailed: String
                get() = FlyLocaleManager.activeStrings.Proxy.DisplayMode.DoubleDetailed
            @androidx.compose.runtime.Composable
            public fun DoubleDetailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.DisplayMode.DoubleDetailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.DisplayMode.DoubleDetailed, *args)
            public val DoubleSimple: String
                get() = FlyLocaleManager.activeStrings.Proxy.DisplayMode.DoubleSimple
            @androidx.compose.runtime.Composable
            public fun DoubleSimple(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Proxy.DisplayMode.DoubleSimple else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Proxy.DisplayMode.DoubleSimple, *args)
        }
    }
    public object Rules {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Rules.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Title, *args)
        public val Summary: String
            get() = FlyLocaleManager.activeStrings.Rules.Summary
        @androidx.compose.runtime.Composable
        public fun Summary(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Summary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Summary, *args)
        public val SearchHint: String
            get() = FlyLocaleManager.activeStrings.Rules.SearchHint
        @androidx.compose.runtime.Composable
        public fun SearchHint(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.SearchHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.SearchHint, *args)
        public object Label {
            public val Proxy: String
                get() = FlyLocaleManager.activeStrings.Rules.Label.Proxy
            @androidx.compose.runtime.Composable
            public fun Proxy(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Label.Proxy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Label.Proxy, *args)
            public val HitRate: String
                get() = FlyLocaleManager.activeStrings.Rules.Label.HitRate
            @androidx.compose.runtime.Composable
            public fun HitRate(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Label.HitRate else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Label.HitRate, *args)
        }
        public object Message {
            public val ToggleFailed: String
                get() = FlyLocaleManager.activeStrings.Rules.Message.ToggleFailed
            @androidx.compose.runtime.Composable
            public fun ToggleFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Message.ToggleFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Message.ToggleFailed, *args)
        }
        public object Empty {
            public val Loading: String
                get() = FlyLocaleManager.activeStrings.Rules.Empty.Loading
            @androidx.compose.runtime.Composable
            public fun Loading(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Empty.Loading else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Empty.Loading, *args)
            public val LoadingHint: String
                get() = FlyLocaleManager.activeStrings.Rules.Empty.LoadingHint
            @androidx.compose.runtime.Composable
            public fun LoadingHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Empty.LoadingHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Empty.LoadingHint, *args)
            public val NotRunning: String
                get() = FlyLocaleManager.activeStrings.Rules.Empty.NotRunning
            @androidx.compose.runtime.Composable
            public fun NotRunning(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Empty.NotRunning else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Empty.NotRunning, *args)
            public val NotRunningHint: String
                get() = FlyLocaleManager.activeStrings.Rules.Empty.NotRunningHint
            @androidx.compose.runtime.Composable
            public fun NotRunningHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Empty.NotRunningHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Empty.NotRunningHint, *args)
            public val NoRules: String
                get() = FlyLocaleManager.activeStrings.Rules.Empty.NoRules
            @androidx.compose.runtime.Composable
            public fun NoRules(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Empty.NoRules else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Empty.NoRules, *args)
            public val NoRulesHint: String
                get() = FlyLocaleManager.activeStrings.Rules.Empty.NoRulesHint
            @androidx.compose.runtime.Composable
            public fun NoRulesHint(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Empty.NoRulesHint else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Empty.NoRulesHint, *args)
            public val NoResults: String
                get() = FlyLocaleManager.activeStrings.Rules.Empty.NoResults
            @androidx.compose.runtime.Composable
            public fun NoResults(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Rules.Empty.NoResults else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Rules.Empty.NoResults, *args)
        }
    }
    public object Service {
        public object Notification {
            public val Running: String
                get() = FlyLocaleManager.activeStrings.Service.Notification.Running
            @androidx.compose.runtime.Composable
            public fun Running(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Notification.Running else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Notification.Running, *args)
            public val UnknownProfile: String
                get() = FlyLocaleManager.activeStrings.Service.Notification.UnknownProfile
            @androidx.compose.runtime.Composable
            public fun UnknownProfile(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Notification.UnknownProfile else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Notification.UnknownProfile, *args)
            public val NoNode: String
                get() = FlyLocaleManager.activeStrings.Service.Notification.NoNode
            @androidx.compose.runtime.Composable
            public fun NoNode(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Notification.NoNode else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Notification.NoNode, *args)
            public val CurrentNode: String
                get() = FlyLocaleManager.activeStrings.Service.Notification.CurrentNode
            @androidx.compose.runtime.Composable
            public fun CurrentNode(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Notification.CurrentNode else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Notification.CurrentNode, *args)
            public val CurrentNodeLabel: String
                get() = FlyLocaleManager.activeStrings.Service.Notification.CurrentNodeLabel
            @androidx.compose.runtime.Composable
            public fun CurrentNodeLabel(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Notification.CurrentNodeLabel else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Notification.CurrentNodeLabel, *args)
            public val RealtimeTraffic: String
                get() = FlyLocaleManager.activeStrings.Service.Notification.RealtimeTraffic
            @androidx.compose.runtime.Composable
            public fun RealtimeTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Notification.RealtimeTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Notification.RealtimeTraffic, *args)
            public val SpeedLine: String
                get() = FlyLocaleManager.activeStrings.Service.Notification.SpeedLine
            @androidx.compose.runtime.Composable
            public fun SpeedLine(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Notification.SpeedLine else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Notification.SpeedLine, *args)
            public val TotalTraffic: String
                get() = FlyLocaleManager.activeStrings.Service.Notification.TotalTraffic
            @androidx.compose.runtime.Composable
            public fun TotalTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Notification.TotalTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Notification.TotalTraffic, *args)
        }
        public object Tile {
            public val ClickToOpen: String
                get() = FlyLocaleManager.activeStrings.Service.Tile.ClickToOpen
            @androidx.compose.runtime.Composable
            public fun ClickToOpen(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Tile.ClickToOpen else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Tile.ClickToOpen, *args)
            public val ClickToStartProxy: String
                get() = FlyLocaleManager.activeStrings.Service.Tile.ClickToStartProxy
            @androidx.compose.runtime.Composable
            public fun ClickToStartProxy(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Tile.ClickToStartProxy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Tile.ClickToStartProxy, *args)
            public val ClickToStopProxy: String
                get() = FlyLocaleManager.activeStrings.Service.Tile.ClickToStopProxy
            @androidx.compose.runtime.Composable
            public fun ClickToStopProxy(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Tile.ClickToStopProxy else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Tile.ClickToStopProxy, *args)
            public val Connecting: String
                get() = FlyLocaleManager.activeStrings.Service.Tile.Connecting
            @androidx.compose.runtime.Composable
            public fun Connecting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Tile.Connecting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Tile.Connecting, *args)
            public val Disconnecting: String
                get() = FlyLocaleManager.activeStrings.Service.Tile.Disconnecting
            @androidx.compose.runtime.Composable
            public fun Disconnecting(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Service.Tile.Disconnecting else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Service.Tile.Disconnecting, *args)
        }
    }
    public object Settings {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.Settings.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.Title, *args)
        public object Section {
            public val UiSettings: String
                get() = FlyLocaleManager.activeStrings.Settings.Section.UiSettings
            @androidx.compose.runtime.Composable
            public fun UiSettings(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.Section.UiSettings else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.Section.UiSettings, *args)
            public val More: String
                get() = FlyLocaleManager.activeStrings.Settings.Section.More
            @androidx.compose.runtime.Composable
            public fun More(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.Section.More else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.Section.More, *args)
        }
        public object UiSettings {
            public val App: String
                get() = FlyLocaleManager.activeStrings.Settings.UiSettings.App
            @androidx.compose.runtime.Composable
            public fun App(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.UiSettings.App else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.UiSettings.App, *args)
            public val AppSummary: String
                get() = FlyLocaleManager.activeStrings.Settings.UiSettings.AppSummary
            @androidx.compose.runtime.Composable
            public fun AppSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.UiSettings.AppSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.UiSettings.AppSummary, *args)
            public val Network: String
                get() = FlyLocaleManager.activeStrings.Settings.UiSettings.Network
            @androidx.compose.runtime.Composable
            public fun Network(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.UiSettings.Network else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.UiSettings.Network, *args)
            public val NetworkSummary: String
                get() = FlyLocaleManager.activeStrings.Settings.UiSettings.NetworkSummary
            @androidx.compose.runtime.Composable
            public fun NetworkSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.UiSettings.NetworkSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.UiSettings.NetworkSummary, *args)
            public val Override: String
                get() = FlyLocaleManager.activeStrings.Settings.UiSettings.Override
            @androidx.compose.runtime.Composable
            public fun Override(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.UiSettings.Override else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.UiSettings.Override, *args)
            public val OverrideSummary: String
                get() = FlyLocaleManager.activeStrings.Settings.UiSettings.OverrideSummary
            @androidx.compose.runtime.Composable
            public fun OverrideSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.UiSettings.OverrideSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.UiSettings.OverrideSummary, *args)
            public val MetaFeatures: String
                get() = FlyLocaleManager.activeStrings.Settings.UiSettings.MetaFeatures
            @androidx.compose.runtime.Composable
            public fun MetaFeatures(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.UiSettings.MetaFeatures else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.UiSettings.MetaFeatures, *args)
            public val MetaFeaturesSummary: String
                get() = FlyLocaleManager.activeStrings.Settings.UiSettings.MetaFeaturesSummary
            @androidx.compose.runtime.Composable
            public fun MetaFeaturesSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.UiSettings.MetaFeaturesSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.UiSettings.MetaFeaturesSummary, *args)
        }
        public object More {
            public val Lab: String
                get() = FlyLocaleManager.activeStrings.Settings.More.Lab
            @androidx.compose.runtime.Composable
            public fun Lab(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.More.Lab else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.More.Lab, *args)
            public val LabSummary: String
                get() = FlyLocaleManager.activeStrings.Settings.More.LabSummary
            @androidx.compose.runtime.Composable
            public fun LabSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.More.LabSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.More.LabSummary, *args)
            public val Logs: String
                get() = FlyLocaleManager.activeStrings.Settings.More.Logs
            @androidx.compose.runtime.Composable
            public fun Logs(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.More.Logs else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.More.Logs, *args)
            public val LogsSummary: String
                get() = FlyLocaleManager.activeStrings.Settings.More.LogsSummary
            @androidx.compose.runtime.Composable
            public fun LogsSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.More.LogsSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.More.LogsSummary, *args)
            public val Onboarding: String
                get() = FlyLocaleManager.activeStrings.Settings.More.Onboarding
            @androidx.compose.runtime.Composable
            public fun Onboarding(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.More.Onboarding else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.More.Onboarding, *args)
            public val OnboardingSummary: String
                get() = FlyLocaleManager.activeStrings.Settings.More.OnboardingSummary
            @androidx.compose.runtime.Composable
            public fun OnboardingSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.More.OnboardingSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.More.OnboardingSummary, *args)
            public val About: String
                get() = FlyLocaleManager.activeStrings.Settings.More.About
            @androidx.compose.runtime.Composable
            public fun About(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.More.About else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.More.About, *args)
            public val AboutSummary: String
                get() = FlyLocaleManager.activeStrings.Settings.More.AboutSummary
            @androidx.compose.runtime.Composable
            public fun AboutSummary(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.More.AboutSummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.More.AboutSummary, *args)
        }
        public object Error {
            public val WebviewFailed: String
                get() = FlyLocaleManager.activeStrings.Settings.Error.WebviewFailed
            @androidx.compose.runtime.Composable
            public fun WebviewFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.Error.WebviewFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.Error.WebviewFailed, *args)
        }
        public object RemoteController {
            public val ConnectionSuccess: String
                get() = FlyLocaleManager.activeStrings.Settings.RemoteController.ConnectionSuccess
            @androidx.compose.runtime.Composable
            public fun ConnectionSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.RemoteController.ConnectionSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.RemoteController.ConnectionSuccess, *args)
            public val ConnectionFailed: String
                get() = FlyLocaleManager.activeStrings.Settings.RemoteController.ConnectionFailed
            @androidx.compose.runtime.Composable
            public fun ConnectionFailed(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Settings.RemoteController.ConnectionFailed else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Settings.RemoteController.ConnectionFailed, *args)
        }
    }
    public object TrafficStatistics {
        public val Title: String
            get() = FlyLocaleManager.activeStrings.TrafficStatistics.Title
        @androidx.compose.runtime.Composable
        public fun Title(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Title else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Title, *args)
        public val EntrySummary: String
            get() = FlyLocaleManager.activeStrings.TrafficStatistics.EntrySummary
        @androidx.compose.runtime.Composable
        public fun EntrySummary(vararg args: Any?): String =
            if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.EntrySummary else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.EntrySummary, *args)
        public object TimeRange {
            public val Today: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.TimeRange.Today
            @androidx.compose.runtime.Composable
            public fun Today(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.TimeRange.Today else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.TimeRange.Today, *args)
            public val Week: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.TimeRange.Week
            @androidx.compose.runtime.Composable
            public fun Week(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.TimeRange.Week else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.TimeRange.Week, *args)
        }
        public object Summary {
            public val TodayTraffic: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Summary.TodayTraffic
            @androidx.compose.runtime.Composable
            public fun TodayTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Summary.TodayTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Summary.TodayTraffic, *args)
            public val WeekTraffic: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Summary.WeekTraffic
            @androidx.compose.runtime.Composable
            public fun WeekTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Summary.WeekTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Summary.WeekTraffic, *args)
        }
        public object Donut {
            public val Other: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Donut.Other
            @androidx.compose.runtime.Composable
            public fun Other(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Donut.Other else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Donut.Other, *args)
        }
        public object Sort {
            public val ByName: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByName
            @androidx.compose.runtime.Composable
            public fun ByName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByName, *args)
            public val ByUpload: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByUpload
            @androidx.compose.runtime.Composable
            public fun ByUpload(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByUpload else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByUpload, *args)
            public val ByDownload: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByDownload
            @androidx.compose.runtime.Composable
            public fun ByDownload(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByDownload else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByDownload, *args)
            public val ByTotal: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByTotal
            @androidx.compose.runtime.Composable
            public fun ByTotal(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByTotal else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Sort.ByTotal, *args)
        }
        public object Action {
            public val Clear: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Action.Clear
            @androidx.compose.runtime.Composable
            public fun Clear(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Action.Clear else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Action.Clear, *args)
            public val ClearSuccess: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Action.ClearSuccess
            @androidx.compose.runtime.Composable
            public fun ClearSuccess(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Action.ClearSuccess else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Action.ClearSuccess, *args)
        }
        public object Section {
            public val Traffic: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Section.Traffic
            @androidx.compose.runtime.Composable
            public fun Traffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Section.Traffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Section.Traffic, *args)
            public val PerAppTraffic: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Section.PerAppTraffic
            @androidx.compose.runtime.Composable
            public fun PerAppTraffic(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Section.PerAppTraffic else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Section.PerAppTraffic, *args)
            public val EmptyApps: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Section.EmptyApps
            @androidx.compose.runtime.Composable
            public fun EmptyApps(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Section.EmptyApps else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Section.EmptyApps, *args)
        }
        public object Metric {
            public val Download: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Download
            @androidx.compose.runtime.Composable
            public fun Download(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Download else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Download, *args)
            public val Upload: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Upload
            @androidx.compose.runtime.Composable
            public fun Upload(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Upload else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Upload, *args)
            public val Total: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Total
            @androidx.compose.runtime.Composable
            public fun Total(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Total else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Metric.Total, *args)
            public val UsageLine: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Metric.UsageLine
            @androidx.compose.runtime.Composable
            public fun UsageLine(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Metric.UsageLine else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Metric.UsageLine, *args)
        }
        public object Buckets {
            public val UnknownAppName: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnknownAppName
            @androidx.compose.runtime.Composable
            public fun UnknownAppName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnknownAppName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnknownAppName, *args)
            public val UnattributedTrafficName: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnattributedTrafficName
            @androidx.compose.runtime.Composable
            public fun UnattributedTrafficName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnattributedTrafficName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnattributedTrafficName, *args)
            public val UnattributedRouteName: String
                get() = FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnattributedRouteName
            @androidx.compose.runtime.Composable
            public fun UnattributedRouteName(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnattributedRouteName else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.TrafficStatistics.Buckets.UnattributedRouteName, *args)
        }
    }
    public object Util {
        public object Error {
            public val UnknownError: String
                get() = FlyLocaleManager.activeStrings.Util.Error.UnknownError
            @androidx.compose.runtime.Composable
            public fun UnknownError(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Util.Error.UnknownError else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Util.Error.UnknownError, *args)
            public val CannotCreateDir: String
                get() = FlyLocaleManager.activeStrings.Util.Error.CannotCreateDir
            @androidx.compose.runtime.Composable
            public fun CannotCreateDir(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Util.Error.CannotCreateDir else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Util.Error.CannotCreateDir, *args)
            public val TargetNotDir: String
                get() = FlyLocaleManager.activeStrings.Util.Error.TargetNotDir
            @androidx.compose.runtime.Composable
            public fun TargetNotDir(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Util.Error.TargetNotDir else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Util.Error.TargetNotDir, *args)
            public val PathTraversalDetected: String
                get() = FlyLocaleManager.activeStrings.Util.Error.PathTraversalDetected
            @androidx.compose.runtime.Composable
            public fun PathTraversalDetected(vararg args: Any?): String =
                if (args.isEmpty()) FlyLocaleManager.activeStrings.Util.Error.PathTraversalDetected else String.format(java.util.Locale.getDefault(), FlyLocaleManager.activeStrings.Util.Error.PathTraversalDetected, *args)
        }
    }
}
