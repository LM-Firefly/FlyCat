@file:Suppress("ClassName", "PropertyName", "RedundantVisibilityModifier")

package com.github.lmfirefly.flycat.locale

public data class FlyStrings(
    public val About: FlyStrings_About,
    public val AccessControl: FlyStrings_AccessControl,
    public val AppSettings: FlyStrings_AppSettings,
    public val Component: FlyStrings_Component,
    public val Connection: FlyStrings_Connection,
    public val Editor: FlyStrings_Editor,
    public val Feature: FlyStrings_Feature,
    public val Home: FlyStrings_Home,
    public val Log: FlyStrings_Log,
    public val MetaFeature: FlyStrings_MetaFeature,
    public val NetworkSettings: FlyStrings_NetworkSettings,
    public val Onboarding: FlyStrings_Onboarding,
    public val OpenSourceLicenses: FlyStrings_OpenSourceLicenses,
    public val Override: FlyStrings_Override,
    public val ProfilesPage: FlyStrings_ProfilesPage,
    public val ProfilesVM: FlyStrings_ProfilesVM,
    public val Providers: FlyStrings_Providers,
    public val Proxy: FlyStrings_Proxy,
    public val Rules: FlyStrings_Rules,
    public val Service: FlyStrings_Service,
    public val Settings: FlyStrings_Settings,
    public val TrafficStatistics: FlyStrings_TrafficStatistics,
    public val Util: FlyStrings_Util,
)

// ==================== About ====================

public data class FlyStrings_About_App(
    public val VersionLoading: String,
    public val VersionFailed: String,
)

public data class FlyStrings_About_Section(
    public val ProjectLinks: String,
    public val License: String,
)

public data class FlyStrings_About_License(
    public val CheckUpdate: String,
    public val CheckUpdateSummary: String,
    public val Libraries: String,
    public val LibrariesSummary: String,
    public val AgplName: String,
    public val AgplDescription: String,
)

public data class FlyStrings_About(
    public val Title: String,
    public val App: FlyStrings_About_App,
    public val Section: FlyStrings_About_Section,
    public val License: FlyStrings_About_License,
    public val Copyright: String,
)

// ==================== AccessControl ====================

public data class FlyStrings_AccessControl_Search(
    public val Placeholder: String,
    public val Empty: String,
)

public data class FlyStrings_AccessControl_AppList(
    public val Title: String,
    public val Loading: String,
)

public data class FlyStrings_AccessControl_Settings(
    public val Title: String,
    public val ShowSystemApps: String,
    public val SelectedFirst: String,
    public val SortMode: String,
    public val SortModeCurrent: String,
    public val BatchOperation: String,
    public val SelectAll: String,
    public val DeselectAll: String,
    public val Invert: String,
    public val ImportExport: String,
    public val Import: String,
    public val Export: String,
    public val ImportSuccess: String,
    public val ExportSuccess: String,
    public val ImportFailed: String,
    public val RegionQuickSelect: String,
    public val ChinaApps: String,
    public val OverseasApps: String,
    public val RegionSelectResult: String,
)

public data class FlyStrings_AccessControl_SortMode(
    public val PackageName: String,
    public val Label: String,
    public val InstallTime: String,
    public val UpdateTime: String,
)

public data class FlyStrings_AccessControl_Button(
    public val Cancel: String,
    public val Confirm: String,
)

public data class FlyStrings_AccessControl(
    public val Title: String,
    public val Search: FlyStrings_AccessControl_Search,
    public val AppList: FlyStrings_AccessControl_AppList,
    public val Settings: FlyStrings_AccessControl_Settings,
    public val SortMode: FlyStrings_AccessControl_SortMode,
    public val Button: FlyStrings_AccessControl_Button,
)

// ==================== AppSettings ====================

public data class FlyStrings_AppSettings_Section(
    public val Behavior: String,
    public val Interface: String,
    public val Navigation: String,
    public val Privacy: String,
    public val Service: String,
    public val Network: String,
    public val Home: String,
)

public data class FlyStrings_AppSettings_Behavior(
    public val AutoStartTitle: String,
    public val AutoStartSummary: String,
    public val AutoUpdateOnStartTitle: String,
    public val AutoUpdateOnStartSummary: String,
    public val SystemAutoStartGrantToast: String,
)

public data class FlyStrings_AppSettings_Interface(
    public val ThemeModeTitle: String,
    public val ThemeModeSummary: String,
    public val ThemeModeSystem: String,
    public val ThemeModeLight: String,
    public val ThemeModeDark: String,
    public val LanguageTitle: String,
    public val LanguageSummary: String,
    public val LanguageSystem: String,
    public val LanguageChinese: String,
    public val LanguageChineseTraditional: String,
    public val LanguageEnglish: String,
    public val LanguageJapanese: String,
    public val LanguageRussian: String,
    public val ColorThemeTitle: String,
    public val ColorThemePickerTitle: String,
    public val ColorThemeCodeLabel: String,
    public val ColorThemeCustomSummary: String,
    public val ThemeColorPolarityInvertTitle: String,
    public val ThemeColorPolarityInvertSummary: String,
    public val TopBarBlurTitle: String,
    public val TopBarBlurSummary: String,
    public val AutoHideNavbarTitle: String,
    public val AutoHideNavbarSummary: String,
    public val PageScaleTitle: String,
    public val PageScaleDialogSummary: String,
    public val ClassicHomeTitle: String,
    public val HitokotoTitle: String,
    public val HitokotoSummary: String,
    public val SidebarExpandedTitle: String,
    public val HomeQuoteTitle: String,
    public val HomeQuoteDefault: String,
    public val HomeQuoteAuthorTitle: String,
    public val HomeQuoteAuthorDefault: String,
    public val EditHomeQuoteTitle: String,
    public val EditHomeQuoteAuthorTitle: String,
    public val HomeWallpaperImportFailed: String,
    public val HomeWallpaperSourceTitle: String,
    public val HomeWallpaperSourceSummary: String,
    public val HomeWallpaperSourceGallery: String,
    public val HomeWallpaperSourceUrl: String,
    public val HomeWallpaperUrlDialogTitle: String,
    public val WallpaperScrimTitle: String,
)

public data class FlyStrings_AppSettings_Privacy(
    public val HideIconTitle: String,
    public val HideIconSummary: String,
    public val HideFromRecentsTitle: String,
    public val HideFromRecentsSummary: String,
)

public data class FlyStrings_AppSettings_ServiceSection(
    public val TrafficNotificationTitle: String,
    public val TrafficNotificationSummary: String,
    public val LogLevelTitle: String,
    public val LogLevelSummary: String,
    public val SingleNodeTestTitle: String,
    public val SingleNodeTestSummary: String,
    public val ExitUiWhenBackgroundTitle: String,
    public val ExitUiWhenBackgroundSummary: String,
    public val SuperIslandTitle: String,
    public val ShizukuTitle: String,
    public val ShizukuNotRunning: String,
    public val ShizukuPermissionRequired: String,
    public val ShizukuReady: String,
    public val ShizukuStatusUnavailable: String,
    public val ShizukuStatusPending: String,
    public val ShizukuStatusGranted: String,
    public val BatteryOptimizationTitle: String,
    public val BatteryOptimizationSummaryEnabled: String,
    public val BatteryOptimizationSummaryDisabled: String,
)

public data class FlyStrings_AppSettings_Network(
    public val UpdateChannelTitle: String,
    public val UpdateChannelSummary: String,
    public val UpdateChannelStable: String,
    public val UpdateChannelPre: String,
    public val UpdateChannelSmart: String,
    public val AutoCheckAppUpdateTitle: String,
    public val AutoCheckAppUpdateSummary: String,
    public val CustomUserAgentTitle: String,
    public val CustomUserAgentSummaryDefault: String,
)

public data class FlyStrings_AppSettings_PredictiveBack(
    public val Title: String,
    public val RestartSummary: String,
    public val ProgressTitle: String,
)

public data class FlyStrings_AppSettings_WarningDialog(
    public val Title: String,
    public val HideIconMsg1: String,
    public val HideIconMsg2: String,
)

public data class FlyStrings_AppSettings_EditDialog(
    public val UserAgentTitle: String,
)

public data class FlyStrings_AppSettings_Button(
    public val Apply: String,
)

public data class FlyStrings_AppSettings(
    public val Title: String,
    public val Section: FlyStrings_AppSettings_Section,
    public val Behavior: FlyStrings_AppSettings_Behavior,
    public val Interface: FlyStrings_AppSettings_Interface,
    public val Privacy: FlyStrings_AppSettings_Privacy,
    public val ServiceSection: FlyStrings_AppSettings_ServiceSection,
    public val Network: FlyStrings_AppSettings_Network,
    public val PredictiveBack: FlyStrings_AppSettings_PredictiveBack,
    public val WarningDialog: FlyStrings_AppSettings_WarningDialog,
    public val EditDialog: FlyStrings_AppSettings_EditDialog,
    public val Button: FlyStrings_AppSettings_Button,
)

// ==================== Component ====================

public data class FlyStrings_Component_ProfileCard(
    public val Update: String,
    public val Delete: String,
    public val Edit: String,
    public val RemoteSubscription: String,
    public val LocalFile: String,
    public val LocalConfig: String,
    public val ClickToUpdate: String,
    public val Traffic: String,
    public val UsedTraffic: String,
    public val ExpireAt: String,
    public val ExpireToday: String,
    public val Expired: String,
    public val JustNow: String,
    public val MinutesAgo: String,
    public val HoursAgo: String,
    public val DaysAgo: String,
)

public data class FlyStrings_Component_Action(
    public val Export: String,
    public val Delete: String,
    public val Update: String,
    public val Edit: String,
    public val Cancel: String,
    public val Reset: String,
    public val Add: String,
)

public data class FlyStrings_Component_WebView(
    public val InvalidUrl: String,
)

public data class FlyStrings_Component_Selector(
    public val NotModify: String,
    public val Enable: String,
    public val Disable: String,
    public val Replace: String,
    public val Prepend: String,
    public val Append: String,
    public val Merge: String,
)

public data class FlyStrings_Component_Navigation(
    public val Back: String,
)

public data class FlyStrings_Component_Message(
    public val Confirm: String,
    public val Hint: String,
    public val Error: String,
    public val Success: String,
)

public data class FlyStrings_Component_Button(
    public val Cancel: String,
    public val Confirm: String,
    public val Clear: String,
    public val Copy: String,
)

public data class FlyStrings_Component_Flag(
    public val ContentDescription: String,
)

public data class FlyStrings_Component_Loading(
    public val Starting: String,
)

public data class FlyStrings_Component_Update_Action(
    public val DownloadNow: String,
)

public data class FlyStrings_Component_Update_Title(
    public val Available: String,
)

public data class FlyStrings_Component_Update_Message(
    public val Available: String,
    public val SelectPackage: String,
    public val Tag: String,
    public val PackageUniversal: String,
    public val PackageExtension: String,
    public val PackageStandard: String,
    public val CurrentVersion: String,
    public val RemoteVersion: String,
    public val Preparing: String,
    public val DownloadingWithProgress: String,
    public val Downloading: String,
    public val DownloadReady: String,
    public val Error: String,
    public val Checking: String,
    public val NoUpdate: String,
    public val CheckFailed: String,
    public val MissingReleaseMetadata: String,
    public val NoCompatibleAsset: String,
    public val DownloadAlreadyRunning: String,
    public val InstallPromptOpened: String,
    public val InstallFailed: String,
)

public data class FlyStrings_Component_Update(
    public val Action: FlyStrings_Component_Update_Action,
    public val Title: FlyStrings_Component_Update_Title,
    public val Message: FlyStrings_Component_Update_Message,
)

public data class FlyStrings_Component_ConfigInput(
    public val PortLabel: String,
    public val CountItems: String,
    public val Replace: String,
    public val ReplaceSummary: String,
    public val Merge: String,
    public val MergeSummary: String,
)

public data class FlyStrings_Component_BottomBar(
    public val Home: String,
    public val Proxy: String,
    public val Config: String,
    public val Setting: String,
)

public data class FlyStrings_Component_Editor_Action(
    public val Search: String,
)

public data class FlyStrings_Component_Editor_Dialog(
    public val AddTitle: String,
    public val EditTitle: String,
    public val ResetTitle: String,
    public val ResetMessage: String,
)

public data class FlyStrings_Component_Editor_Empty(
    public val Title: String,
    public val Hint: String,
)

public data class FlyStrings_Component_Editor_Error(
    public val KeyEmpty: String,
    public val KeyExists: String,
)

public data class FlyStrings_Component_Editor_Rule(
    public val Type: String,
    public val Target: String,
    public val Content: String,
    public val Src: String,
    public val NoResolve: String,
    public val TargetReject: String,
    public val TargetDirect: String,
    public val TargetMatch: String,
    public val ErrorContentRequired: String,
)

public data class FlyStrings_Component_Editor_ConfigPreview(
    public val Title: String,
    public val Format: String,
    public val Save: String,
    public val SaveFailed: String,
)

public data class FlyStrings_Component_Editor(
    public val CountItems: String,
    public val Action: FlyStrings_Component_Editor_Action,
    public val Dialog: FlyStrings_Component_Editor_Dialog,
    public val Empty: FlyStrings_Component_Editor_Empty,
    public val Error: FlyStrings_Component_Editor_Error,
    public val Rule: FlyStrings_Component_Editor_Rule,
    public val ConfigPreview: FlyStrings_Component_Editor_ConfigPreview,
)

public data class FlyStrings_Component(
    public val ProfileCard: FlyStrings_Component_ProfileCard,
    public val Action: FlyStrings_Component_Action,
    public val WebView: FlyStrings_Component_WebView,
    public val Selector: FlyStrings_Component_Selector,
    public val Navigation: FlyStrings_Component_Navigation,
    public val Message: FlyStrings_Component_Message,
    public val Button: FlyStrings_Component_Button,
    public val Flag: FlyStrings_Component_Flag,
    public val Loading: FlyStrings_Component_Loading,
    public val Update: FlyStrings_Component_Update,
    public val ConfigInput: FlyStrings_Component_ConfigInput,
    public val BottomBar: FlyStrings_Component_BottomBar,
    public val Editor: FlyStrings_Component_Editor,
)

// ==================== Connection ====================

public data class FlyStrings_Connection_Tab(
    public val Active: String,
    public val Closed: String,
)

public data class FlyStrings_Connection_Sort(
    public val Time: String,
    public val Upload: String,
    public val Download: String,
    public val Host: String,
)

public data class FlyStrings_Connection_RelativeTime(
    public val JustNow: String,
    public val MinutesAgo: String,
    public val HoursAgo: String,
    public val DaysAgo: String,
    public val Date: String,
)

public data class FlyStrings_Connection_Detail_Action(
    public val Interrupting: String,
    public val Interrupt: String,
)

public data class FlyStrings_Connection_Detail_Section(
    public val Info: String,
    public val Rule: String,
)

public data class FlyStrings_Connection_Detail_Label(
    public val Protocol: String,
    public val Process: String,
    public val SourceAddress: String,
    public val DestinationAddress: String,
    public val Duration: String,
    public val Upload: String,
    public val Download: String,
    public val Type: String,
    public val Content: String,
)

public data class FlyStrings_Connection_Detail(
    public val Action: FlyStrings_Connection_Detail_Action,
    public val Section: FlyStrings_Connection_Detail_Section,
    public val Label: FlyStrings_Connection_Detail_Label,
)

public data class FlyStrings_Connection_Topology(
    public val EmptyMessage: String,
    public val OpenPage: String,
)

public data class FlyStrings_Connection(
    public val Title: String,
    public val Summary: String,
    public val Tab: FlyStrings_Connection_Tab,
    public val Search: String,
    public val SearchHint: String,
    public val CloseAll: String,
    public val SortBy: String,
    public val Sort: FlyStrings_Connection_Sort,
    public val ChainCount: String,
    public val RelativeTime: FlyStrings_Connection_RelativeTime,
    public val Detail: FlyStrings_Connection_Detail,
    public val Loading: String,
    public val Empty: String,
    public val NoResults: String,
    public val Topology: FlyStrings_Connection_Topology,
)

// ==================== Editor ====================

public data class FlyStrings_Editor_JsonDiagnostic(
    public val FormatError: String,
    public val MustStartWithBraceOrBracket: String,
    public val SyntaxError: String,
    public val UnterminatedStringOrObject: String,
    public val Unknown: String,
    public val Expected: String,
    public val MissingValue: String,
    public val DuplicateKey: String,
)

public data class FlyStrings_Editor(
    public val JsonDiagnostic: FlyStrings_Editor_JsonDiagnostic,
)

// ==================== Feature ====================

public data class FlyStrings_Feature_ServiceStatus(
    public val Section: String,
    public val Running: String,
    public val NeedExtension: String,
    public val NeedSubStore: String,
    public val NotRunning: String,
    public val SwitchStartSubStore: String,
    public val AllowLan: String,
    public val AllowLanSummary: String,
    public val AutoCloseModeSummary: String,
    public val AutoCloseModeAlwaysOn: String,
    public val AutoCloseMode5Min: String,
    public val AutoCloseMode10Min: String,
    public val AutoClosed: String,
    public val AutoCloseModeDisabled: String,
)

public data class FlyStrings_Feature_Panel(
    public val Section: String,
    public val SelectPanel: String,
    public val Protocol: String,
    public val OpenPanel: String,
    public val Unknown: String,
    public val CreateShortcut: String,
    public val CreateShortcutSummary: String,
    public val ShortcutTitle: String,
    public val ShortcutName: String,
    public val ShortcutPickIcon: String,
    public val ShortcutResetIcon: String,
)

public data class FlyStrings_Feature_RemoteController(
    public val Section: String,
    public val ModeTitle: String,
    public val ControlBackend: String,
    public val Protocol: String,
    public val AddBackend: String,
    public val EditBackend: String,
    public val Name: String,
    public val Host: String,
    public val Port: String,
    public val Secret: String,
    public val PortRangeError: String,
    public val Delete: String,
)

public data class FlyStrings_Feature_SubStore(
    public val SectionHint: String,
    public val ExtensionInstalled: String,
    public val ExtensionInstall: String,
    public val JavetAvailable: String,
    public val JavetPending: String,
    public val DownloadHint: String,
    public val DownloadJavet: String,
    public val Downloading: String,
    public val JavetDownloadSuccess: String,
    public val JavetDownloadFailed: String,
    public val DownloadResources: String,
    public val DownloadResourcesSummary: String,
    public val Not32Bit: String,
    public val InstallExtension: String,
    public val DownloadSubStoreFirst: String,
    public val JavetNotReady: String,
    public val FrontendDownloadSuccess: String,
    public val FrontendDownloadFailed: String,
    public val BackendDownloadSuccess: String,
    public val BackendDownloadFailed: String,
    public val DownloadError: String,
    public val Title: String,
    public val PortOccupied: String,
    public val JavetLoadFailed: String,
    public val CaseEngineInitFailed: String,
    public val FrontendExtractFailed: String,
)

public data class FlyStrings_Feature(
    public val Title: String,
    public val ServiceStatus: FlyStrings_Feature_ServiceStatus,
    public val Panel: FlyStrings_Feature_Panel,
    public val RemoteController: FlyStrings_Feature_RemoteController,
    public val SubStore: FlyStrings_Feature_SubStore,
)

// ==================== Home ====================

public data class FlyStrings_Home_Message(
    public val ConfigSwitched: String,
    public val ConfigSwitchFailed: String,
    public val Preparing: String,
    public val StartFailed: String,
    public val StopFailed: String,
)

public data class FlyStrings_Home_Control(
    public val HintAddProfile: String,
    public val HintEnableProfile: String,
    public val Start: String,
    public val Stop: String,
)

public data class FlyStrings_Home_NodeInfo(
    public val Node: String,
    public val Delay: String,
    public val Unknown: String,
    public val DelayValue: String,
)

public data class FlyStrings_Home_Traffic(
    public val UpShort: String,
    public val DownShort: String,
    public val DownloadLong: String,
    public val UploadLong: String,
    public val NoProfile: String,
)

public data class FlyStrings_Home_IpInfo(
    public val ExitIp: String,
)

public data class FlyStrings_Home_Status(
    public val Connecting: String,
    public val Disconnecting: String,
    public val Running: String,
    public val Lost: String,
    public val TapToStart: String,
)

public data class FlyStrings_Home_Greeting(
    public val Morning: String,
    public val Afternoon: String,
    public val Evening: String,
    public val Night: String,
)

public data class FlyStrings_Home_Quote(
    public val AuthorPrefix: String,
)

public data class FlyStrings_Home_RunMode(
    public val Vpn: String,
    public val Tun: String,
    public val Ebpf: String,
)

public data class FlyStrings_Home_TunnelMode(
    public val Rule: String,
    public val Global: String,
    public val Direct: String,
)

public data class FlyStrings_Home_PreviewGuide(
    public val Title: String,
    public val Description: String,
    public val Next: String,
    public val Start: String,
    public val WallpaperTitle: String,
    public val WallpaperDescription: String,
    public val SwipeTitle: String,
    public val SwipeDescription: String,
    public val AddTitle: String,
    public val AddDescription: String,
)

public data class FlyStrings_Home(
    public val Title: String,
    public val Message: FlyStrings_Home_Message,
    public val Control: FlyStrings_Home_Control,
    public val NodeInfo: FlyStrings_Home_NodeInfo,
    public val Traffic: FlyStrings_Home_Traffic,
    public val IpInfo: FlyStrings_Home_IpInfo,
    public val Status: FlyStrings_Home_Status,
    public val Greeting: FlyStrings_Home_Greeting,
    public val Quote: FlyStrings_Home_Quote,
    public val RunMode: FlyStrings_Home_RunMode,
    public val TunnelMode: FlyStrings_Home_TunnelMode,
    public val PreviewGuide: FlyStrings_Home_PreviewGuide,
)

// ==================== Log ====================

public data class FlyStrings_Log_Action(
    public val StopRecording: String,
    public val StartRecording: String,
    public val SaveRecentLogs: String,
    public val ClearLogs: String,
    public val Pause: String,
    public val Save: String,
    public val Delete: String,
)

public data class FlyStrings_Log_Empty(
    public val NoLogs: String,
    public val StartRecordingHint: String,
)

public data class FlyStrings_Log_Message(
    public val SaveFailed: String,
    public val Saved: String,
    public val RecentLogSaved: String,
)

public data class FlyStrings_Log_Status(
    public val Recording: String,
)

public data class FlyStrings_Log_Detail(
    public val RealTimeLog: String,
    public val Loading: String,
    public val WaitingLog: String,
    public val LogEmpty: String,
    public val WillShowWhenGenerated: String,
    public val NoLogContent: String,
)

public data class FlyStrings_Log(
    public val Title: String,
    public val Action: FlyStrings_Log_Action,
    public val Empty: FlyStrings_Log_Empty,
    public val Message: FlyStrings_Log_Message,
    public val Status: FlyStrings_Log_Status,
    public val Detail: FlyStrings_Log_Detail,
)

// ==================== MetaFeature ====================

public data class FlyStrings_MetaFeature_Section(
    public val ConnectionAndTraffic: String,
    public val BackupRestore: String,
    public val Routing: String,
)

public data class FlyStrings_MetaFeature_GeoX(
    public val OnlineUpdateTitle: String,
    public val OnlineUpdateSummary: String,
)

public data class FlyStrings_MetaFeature_CustomRouting(
    public val Title: String,
    public val Summary: String,
    public val EditYaml: String,
    public val GroupTypeTitle: String,
    public val GroupTypeUrlTest: String,
    public val GroupTypeFallback: String,
    public val UrlTestRegionGroupTitle: String,
    public val FallbackRegionGroupTitle: String,
    public val SaveFailed: String,
    public val EditButton: String,
    public val ManualYamlPresetDiscarded: String,
    public val RegionHongKong: String,
    public val RegionTaiwan: String,
    public val RegionJapan: String,
    public val RegionSingapore: String,
    public val RegionUnitedStates: String,
    public val RegionOther: String,
    public val ItemProxy: String,
    public val ItemAds: String,
    public val ItemChina: String,
    public val ItemGlobal: String,
    public val ItemMatch: String,
)

public data class FlyStrings_MetaFeature_RuntimeRules(
    public val Title: String,
    public val Summary: String,
)

public data class FlyStrings_MetaFeature_Download(
    public val DialogTitle: String,
    public val DownloadComplete: String,
)

public data class FlyStrings_MetaFeature_Backup(
    public val BackupTitle: String,
    public val BackupSummary: String,
    public val RestoreTitle: String,
    public val RestoreSummary: String,
    public val BackupDialogTitle: String,
    public val BackupDialogMessage: String,
    public val RestoreDialogTitle: String,
    public val RestoreDialogMessage: String,
    public val RestoreConfirmTitle: String,
    public val RestoreConfirmMessage: String,
    public val BackupSuccess: String,
    public val RestoreSuccess: String,
    public val RestoreSuccessTitle: String,
    public val RestoreSuccessMessage: String,
    public val WebDavRestoreSuccess: String,
    public val RestartNow: String,
    public val RestartLaunchNotFound: String,
    public val BackupFailed: String,
    public val RestoreFailed: String,
)

public data class FlyStrings_MetaFeature_AgeKey(
    public val Section: String,
    public val X25519Title: String,
    public val HybridTitle: String,
    public val SecretKey: String,
    public val PublicKey: String,
    public val Generate: String,
    public val DerivePublicKey: String,
)

public data class FlyStrings_MetaFeature_WebDav(
    public val ConfigTitle: String,
    public val ConfigSummary: String,
    public val TestTitle: String,
    public val TestSummary: String,
    public val TestLoadingTitle: String,
    public val TestLoadingMessage: String,
    public val TestSuccess: String,
    public val TestFailed: String,
    public val ConfigRequired: String,
    public val BackupTitle: String,
    public val BackupSummary: String,
    public val BackupLoadingTitle: String,
    public val BackupLoadingMessage: String,
    public val BackupSuccess: String,
    public val BackupFailed: String,
    public val RestoreTitle: String,
    public val RestoreSummary: String,
    public val RestoreConfirmTitle: String,
    public val RestoreConfirmMessage: String,
    public val RestoreLoadingTitle: String,
    public val RestoreLoadingMessage: String,
    public val RestoreFailed: String,
    public val ConfigUrlExample: String,
    public val ValidationUrlRequired: String,
    public val ValidationAccountRequired: String,
    public val ValidationPasswordRequired: String,
    public val ConfigSaved: String,
    public val LabelUrl: String,
    public val LabelDir: String,
    public val LabelAccount: String,
    public val LabelPassword: String,
)

public data class FlyStrings_MetaFeature(
    public val Title: String,
    public val Section: FlyStrings_MetaFeature_Section,
    public val GeoX: FlyStrings_MetaFeature_GeoX,
    public val CustomRouting: FlyStrings_MetaFeature_CustomRouting,
    public val RuntimeRules: FlyStrings_MetaFeature_RuntimeRules,
    public val Download: FlyStrings_MetaFeature_Download,
    public val Backup: FlyStrings_MetaFeature_Backup,
    public val AgeKey: FlyStrings_MetaFeature_AgeKey,
    public val WebDav: FlyStrings_MetaFeature_WebDav,
)

// ==================== NetworkSettings ====================

public data class FlyStrings_NetworkSettings_Section(
    public val VpnOptions: String,
    public val Advanced: String,
    public val ProxyOptions: String,
    public val Kernel: String,
)

public data class FlyStrings_NetworkSettings_Advanced(
    public val DisableOverrideTitle: String,
)

public data class FlyStrings_NetworkSettings_Kernel(
    public val ActiveTitle: String,
    public val RefreshTitle: String,
    public val CustomTitle: String,
    public val BundledAlpha: String,
    public val RestartHint: String,
    public val RestartDialogTitle: String,
    public val RestartDialogMessage: String,
    public val ActiveTag: String,
    public val DownloadingTag: String,
    public val InstalledTag: String,
    public val PluginFormatTitle: String,
    public val PluginFormatBody: String,
)

public data class FlyStrings_NetworkSettings_RunMode(
    public val SectionTitle: String,
    public val VpnServiceTitle: String,
    public val VpnServiceSummary: String,
    public val TunTitle: String,
    public val TunSummary: String,
    public val EbpfTitle: String,
    public val EbpfSummary: String,
)

public data class FlyStrings_NetworkSettings_EbpfOptions(
    public val BypassCnTitle: String,
    public val BypassCnSummary: String,
)

public data class FlyStrings_NetworkSettings_VpnOptions(
    public val BypassPrivateTitle: String,
    public val DnsHijackTitle: String,
    public val AllowBypassTitle: String,
    public val EnableIpv6Title: String,
    public val SystemProxyTitle: String,
    public val TunStackTitle: String,
    public val TunStackSystem: String,
    public val TunStackGVisor: String,
    public val TunStackMixed: String,
    public val TunStackMips: String,
)

public data class FlyStrings_NetworkSettings_ProxyOptions(
    public val TunStackTitle: String,
    public val AccessControlModeTitle: String,
    public val AllowAll: String,
    public val AllowSelected: String,
    public val RejectSelected: String,
    public val ManageAccessControlTitle: String,
)

public data class FlyStrings_NetworkSettings_RootTun(
    public val IfNameTitle: String,
    public val IfNameSummary: String,
    public val MtuTitle: String,
    public val MtuSummary: String,
    public val AutoRouteTitle: String,
    public val StrictRouteTitle: String,
    public val AutoRedirectTitle: String,
    public val DnsModeTitle: String,
    public val DnsModeRedirHost: String,
    public val DnsModeFakeIp: String,
    public val FakeIpRangeTitle: String,
    public val FakeIpRangeSummary: String,
    public val FakeIpRange6Title: String,
    public val FakeIpRange6Summary: String,
)

public data class FlyStrings_NetworkSettings_Error(
    public val VpnDenied: String,
    public val RootRequired: String,
)

public data class FlyStrings_NetworkSettings_WifiAutomation(
    public val Title: String,
    public val EnabledTitle: String,
    public val WifiNameHeading: String,
    public val NetworkChangeSection: String,
    public val OtherWifiTitle: String,
    public val NoWifiTitle: String,
    public val AddCurrent: String,
    public val AddManual: String,
    public val Scan: String,
    public val Scanning: String,
    public val ScanEmpty: String,
    public val ScanUnavailable: String,
    public val ManualDialogTitle: String,
    public val EditDialogTitle: String,
    public val ManualDialogLabel: String,
    public val StartAction: String,
    public val StopAction: String,
    public val KeepAction: String,
    public val ActionTitle: String,
    public val ProfileAction: String,
    public val NoSwitchAction: String,
    public val EmptyRules: String,
    public val VpnOnly: String,
    public val PermissionTitle: String,
    public val PermissionMessage: String,
    public val ApproximateTitle: String,
    public val ApproximateMessage: String,
    public val DeniedTitle: String,
    public val DeniedMessage: String,
    public val SettingsTitle: String,
    public val SettingsMessage: String,
    public val LocationTitle: String,
    public val LocationMessage: String,
    public val Grant: String,
    public val OpenSettings: String,
    public val TurnOnLocation: String,
    public val Added: String,
    public val Duplicate: String,
    public val NoWifi: String,
    public val Unavailable: String,
)

public data class FlyStrings_NetworkSettings(
    public val Title: String,
    public val Section: FlyStrings_NetworkSettings_Section,
    public val Advanced: FlyStrings_NetworkSettings_Advanced,
    public val Kernel: FlyStrings_NetworkSettings_Kernel,
    public val RunMode: FlyStrings_NetworkSettings_RunMode,
    public val EbpfOptions: FlyStrings_NetworkSettings_EbpfOptions,
    public val VpnOptions: FlyStrings_NetworkSettings_VpnOptions,
    public val ProxyOptions: FlyStrings_NetworkSettings_ProxyOptions,
    public val RootTun: FlyStrings_NetworkSettings_RootTun,
    public val Error: FlyStrings_NetworkSettings_Error,
    public val WifiAutomation: FlyStrings_NetworkSettings_WifiAutomation,
)

// ==================== Onboarding ====================

public data class FlyStrings_Onboarding_Navigation(
    public val Back: String,
    public val Next: String,
    public val Enter: String,
    public val Start: String,
)

public data class FlyStrings_Onboarding_Welcome(
    public val Tagline: String,
)

public data class FlyStrings_Onboarding_Permission_Common(
    public val Granted: String,
)

public data class FlyStrings_Onboarding_Permission_Notification(
    public val Title: String,
    public val SummaryNeed: String,
    public val SummaryNotRequired: String,
)

public data class FlyStrings_Onboarding_Permission_AppList(
    public val Title: String,
    public val SummaryNeed: String,
)

public data class FlyStrings_Onboarding_Permission(
    public val Title: String,
    public val Subtitle: String,
    public val Common: FlyStrings_Onboarding_Permission_Common,
    public val Notification: FlyStrings_Onboarding_Permission_Notification,
    public val AppList: FlyStrings_Onboarding_Permission_AppList,
)

public data class FlyStrings_Onboarding_Privacy_Accept(
    public val Title: String,
)

public data class FlyStrings_Onboarding_Privacy(
    public val Title: String,
    public val Subtitle: String,
    public val RichTextLead: String,
    public val RichTextPrefix: String,
    public val RichTextConnector: String,
    public val RichTextSuffix: String,
    public val TermsLink: String,
    public val PolicyLink: String,
    public val Accept: FlyStrings_Onboarding_Privacy_Accept,
)

public data class FlyStrings_Onboarding_Personalize(
    public val Title: String,
    public val Subtitle: String,
)

public data class FlyStrings_Onboarding_Finish(
    public val Title: String,
    public val Subtitle: String,
)

public data class FlyStrings_Onboarding_Project_Github(
    public val Title: String,
    public val Summary: String,
)

public data class FlyStrings_Onboarding_Project_Community(
    public val Title: String,
    public val Summary: String,
)

public data class FlyStrings_Onboarding_Project(
    public val Github: FlyStrings_Onboarding_Project_Github,
    public val Community: FlyStrings_Onboarding_Project_Community,
)

public data class FlyStrings_Onboarding_Sheet(
    public val PrivacyPolicyTitle: String,
    public val LoadFailed: String,
)

public data class FlyStrings_Onboarding(
    public val Navigation: FlyStrings_Onboarding_Navigation,
    public val Welcome: FlyStrings_Onboarding_Welcome,
    public val Permission: FlyStrings_Onboarding_Permission,
    public val Privacy: FlyStrings_Onboarding_Privacy,
    public val Personalize: FlyStrings_Onboarding_Personalize,
    public val Finish: FlyStrings_Onboarding_Finish,
    public val Project: FlyStrings_Onboarding_Project,
    public val Sheet: FlyStrings_Onboarding_Sheet,
)

// ==================== OpenSourceLicenses ====================

public data class FlyStrings_OpenSourceLicenses_LicenseSheet(
    public val NoContent: String,
)

public data class FlyStrings_OpenSourceLicenses(
    public val Title: String,
    public val LicenseSheet: FlyStrings_OpenSourceLicenses_LicenseSheet,
)

// ==================== Override ====================

public data class FlyStrings_Override_Action(
    public val Create: String,
    public val New: String,
    public val NetworkImport: String,
)

public data class FlyStrings_Override_BuiltIn(
    public val PreventDnsLeak: String,
    public val AddDirectRules: String,
    public val PuddingDog: String,
    public val Acl4ssrOnlineFull: String,
    public val CopyName: String,
)

public data class FlyStrings_Override_Empty(
    public val Title: String,
    public val Hint: String,
    public val UserTitle: String,
    public val UserHint: String,
)

public data class FlyStrings_Override_Section(
    public val BuiltIn: String,
    public val User: String,
)

public data class FlyStrings_Override_Status(
    public val InUse: String,
    public val NotInUse: String,
    public val BuiltIn: String,
)

public data class FlyStrings_Override_Card(
    public val Copy: String,
    public val Export: String,
    public val Edit: String,
    public val Delete: String,
    public val EditButton: String,
    public val DeleteButton: String,
    public val Apply: String,
    public val ApplyButton: String,
)

public data class FlyStrings_Override_ApplySheet_Button(
    public val Cancel: String,
    public val Confirm: String,
)

public data class FlyStrings_Override_ApplySheet(
    public val Title: String,
    public val Empty: String,
    public val Success: String,
    public val Failed: String,
    public val Button: FlyStrings_Override_ApplySheet_Button,
)

public data class FlyStrings_Override_Import(
    public val ReadError: String,
    public val Failed: String,
    public val FileError: String,
    public val NetworkError: String,
    public val InvalidUrl: String,
    public val HttpError: String,
    public val UnsupportedType: String,
    public val EmptyJavaScript: String,
)

public data class FlyStrings_Override_Error(
    public val SaveFailed: String,
)

public data class FlyStrings_Override_Export(
    public val Failed: String,
    public val Success: String,
)

public data class FlyStrings_Override_Dialog_Create(
    public val Title: String,
    public val Name: String,
    public val Url: String,
    public val Type: String,
)

public data class FlyStrings_Override_Dialog_Delete(
    public val Title: String,
    public val InUseMessage: String,
    public val Message: String,
)

public data class FlyStrings_Override_Dialog_Button(
    public val Cancel: String,
    public val Delete: String,
)

public data class FlyStrings_Override_Dialog(
    public val Create: FlyStrings_Override_Dialog_Create,
    public val Delete: FlyStrings_Override_Dialog_Delete,
    public val Button: FlyStrings_Override_Dialog_Button,
)

public data class FlyStrings_Override_Draft(
    public val BasicRouting: String,
    public val ServiceRouting: String,
)

public data class FlyStrings_Override_Save(
    public val ImportDefaultName: String,
)

public data class FlyStrings_Override_Label(
    public val RulesReplace: String,
)

public data class FlyStrings_Override(
    public val Title: String,
    public val Action: FlyStrings_Override_Action,
    public val BuiltIn: FlyStrings_Override_BuiltIn,
    public val Empty: FlyStrings_Override_Empty,
    public val Section: FlyStrings_Override_Section,
    public val Status: FlyStrings_Override_Status,
    public val Card: FlyStrings_Override_Card,
    public val ApplySheet: FlyStrings_Override_ApplySheet,
    public val Import: FlyStrings_Override_Import,
    public val Error: FlyStrings_Override_Error,
    public val Export: FlyStrings_Override_Export,
    public val Dialog: FlyStrings_Override_Dialog,
    public val Draft: FlyStrings_Override_Draft,
    public val Save: FlyStrings_Override_Save,
    public val Label: FlyStrings_Override_Label,
)

// ==================== ProfilesPage ====================

public data class FlyStrings_ProfilesPage_Action(
    public val UpdateAll: String,
    public val AddProfile: String,
)

public data class FlyStrings_ProfilesPage_Empty(
    public val NoProfiles: String,
    public val Hint: String,
)

public data class FlyStrings_ProfilesPage_Sheet(
    public val AddTitle: String,
    public val EditTitle: String,
)

public data class FlyStrings_ProfilesPage_Type(
    public val Title: String,
    public val Subscription: String,
    public val LocalFile: String,
    public val QrScan: String,
)

public data class FlyStrings_ProfilesPage_Input(
    public val ProfileName: String,
    public val SubscriptionUrl: String,
    public val SelectFile: String,
    public val NewProfile: String,
    public val AgeSecretKey: String,
    public val AutoUpdateInterval: String,
    public val AutoUpdateIntervalHint: String,
)

public data class FlyStrings_ProfilesPage_QrScanner(
    public val NeedPermission: String,
    public val NeedCamera: String,
    public val SelectFromAlbum: String,
    public val RecognizeSuccess: String,
    public val RecognizeFailed: String,
    public val RecognizeError: String,
)

public data class FlyStrings_ProfilesPage_Message(
    public val UnknownFile: String,
)

public data class FlyStrings_ProfilesPage_Validation(
    public val EnterUrl: String,
    public val SelectFile: String,
    public val YamlOnly: String,
)

public data class FlyStrings_ProfilesPage_Progress(
    public val Downloading: String,
)

public data class FlyStrings_ProfilesPage_Button(
    public val Cancel: String,
    public val Confirm: String,
)

public data class FlyStrings_ProfilesPage_DeleteDialog(
    public val Title: String,
    public val Message: String,
    public val Confirm: String,
)

public data class FlyStrings_ProfilesPage_EditDialog(
    public val Title: String,
)

public data class FlyStrings_ProfilesPage_LinkSettings_Validation(
    public val EnterName: String,
    public val EnterUrl: String,
    public val InvalidUrl: String,
)

public data class FlyStrings_ProfilesPage_LinkSettings(
    public val Title: String,
    public val OpenMode: String,
    public val OpenModeInApp: String,
    public val OpenModeExternal: String,
    public val DefaultLink: String,
    public val DefaultLinkSummary: String,
    public val AddLink: String,
    public val EditLink: String,
    public val Name: String,
    public val Url: String,
    public val Close: String,
    public val Validation: FlyStrings_ProfilesPage_LinkSettings_Validation,
)

public data class FlyStrings_ProfilesPage_ShareDialog(
    public val Title: String,
    public val ShareFile: String,
    public val ShareLink: String,
    public val NoLink: String,
    public val ImportedConfigMissing: String,
)

public data class FlyStrings_ProfilesPage_SettingsDialog(
    public val Title: String,
    public val SectionType: String,
    public val SectionSubscription: String,
    public val SectionOverride: String,
    public val ChangeLink: String,
    public val CustomRouting: String,
    public val CustomRoutingSummary: String,
    public val NoDescription: String,
    public val EditProfile: String,
    public val OpenConfig: String,
    public val EditSettings: String,
    public val SaveFailed: String,
    public val ConfigMissing: String,
    public val AgeSecretKey: String,
)

public data class FlyStrings_ProfilesPage_AgeKey(
    public val PublicKey: String,
    public val SecretKey: String,
    public val PublicKeyPlaceholder: String,
    public val DerivePublicKey: String,
    public val CopyPublicKey: String,
    public val GenerateSecretKey: String,
    public val CopySecretKey: String,
    public val HideKey: String,
    public val ShowKey: String,
    public val GeneratedPublicKey: String,
    public val CopiedPublicKey: String,
    public val GeneratedSecretKey: String,
    public val CopiedSecretKey: String,
    public val GenerateFailed: String,
)

public data class FlyStrings_ProfilesPage(
    public val Title: String,
    public val Action: FlyStrings_ProfilesPage_Action,
    public val Empty: FlyStrings_ProfilesPage_Empty,
    public val Sheet: FlyStrings_ProfilesPage_Sheet,
    public val Type: FlyStrings_ProfilesPage_Type,
    public val Input: FlyStrings_ProfilesPage_Input,
    public val QrScanner: FlyStrings_ProfilesPage_QrScanner,
    public val Message: FlyStrings_ProfilesPage_Message,
    public val Validation: FlyStrings_ProfilesPage_Validation,
    public val Progress: FlyStrings_ProfilesPage_Progress,
    public val Button: FlyStrings_ProfilesPage_Button,
    public val DeleteDialog: FlyStrings_ProfilesPage_DeleteDialog,
    public val EditDialog: FlyStrings_ProfilesPage_EditDialog,
    public val LinkSettings: FlyStrings_ProfilesPage_LinkSettings,
    public val ShareDialog: FlyStrings_ProfilesPage_ShareDialog,
    public val SettingsDialog: FlyStrings_ProfilesPage_SettingsDialog,
    public val AgeKey: FlyStrings_ProfilesPage_AgeKey,
)

// ==================== ProfilesVM ====================

public data class FlyStrings_ProfilesVM_Message(
    public val ProfileAdded: String,
    public val AddFailed: String,
    public val ProfileDeleted: String,
    public val DeleteFailed: String,
    public val ProfileUpdated: String,
    public val UpdateFailed: String,
    public val ToggleFailed: String,
    public val CannotDeleteActiveProfile: String,
)

public data class FlyStrings_ProfilesVM_Progress(
    public val Preparing: String,
    public val Verifying: String,
    public val ImportComplete: String,
)

public data class FlyStrings_ProfilesVM_Error(
    public val ProfileNotExist: String,
)

public data class FlyStrings_ProfilesVM(
    public val Message: FlyStrings_ProfilesVM_Message,
    public val Progress: FlyStrings_ProfilesVM_Progress,
    public val Error: FlyStrings_ProfilesVM_Error,
)

// ==================== Providers ====================

public data class FlyStrings_Providers_Action(
    public val UpdateAll: String,
    public val Update: String,
    public val Upload: String,
    public val View: String,
    public val Operation: String,
    public val OpenInFiles: String,
)

public data class FlyStrings_Providers_Empty(
    public val NotRunning: String,
    public val NotRunningHint: String,
    public val NoProviders: String,
    public val NoProvidersHint: String,
)

public data class FlyStrings_Providers_Type(
    public val ProxyProviders: String,
    public val RuleProviders: String,
)

public data class FlyStrings_Providers_Info(
    public val ExpireDays: String,
    public val Expired: String,
    public val RemainingTraffic: String,
    public val UsedTraffic: String,
)

public data class FlyStrings_Providers_VehicleType(
    public val Http: String,
    public val File: String,
    public val Inline: String,
    public val Compatible: String,
)

public data class FlyStrings_Providers_Message(
    public val FetchFailed: String,
    public val UpdateSuccess: String,
    public val UpdateFailed: String,
    public val AllUpdated: String,
    public val UploadSuccess: String,
    public val UploadFailed: String,
    public val OpenFileFailed: String,
    public val ConvertMrsFailed: String,
    public val CannotReadFile: String,
    public val FileExceedsLimit: String,
)

public data class FlyStrings_Providers(
    public val Title: String,
    public val Action: FlyStrings_Providers_Action,
    public val Empty: FlyStrings_Providers_Empty,
    public val Type: FlyStrings_Providers_Type,
    public val Info: FlyStrings_Providers_Info,
    public val VehicleType: FlyStrings_Providers_VehicleType,
    public val Message: FlyStrings_Providers_Message,
    public val InfoTitle: String,
    public val InfoSummary: String,
    public val ProviderPath: String,
)

// ==================== Proxy ====================

public data class FlyStrings_Proxy_Mode(
    public val Direct: String,
)

public data class FlyStrings_Proxy_Action(
    public val Panel: String,
    public val Test: String,
    public val Sort: String,
)

public data class FlyStrings_Proxy_Node(
    public val Timeout: String,
    public val Count: String,
)

public data class FlyStrings_Proxy_Empty(
    public val NoNodes: String,
    public val Hint: String,
)

public data class FlyStrings_Proxy_Testing(
    public val Group: String,
    public val All: String,
    public val RequestSent: String,
    public val Failed: String,
    public val InProgress: String,
    public val Progress: String,
    public val Pull: String,
    public val Release: String,
    public val Complete: String,
)

public data class FlyStrings_Proxy_Selection(
    public val Switched: String,
    public val Failed: String,
    public val Error: String,
    public val Timeout: String,
    public val RequireRunning: String,
)

public data class FlyStrings_Proxy_SortMode(
    public val Default: String,
    public val ByName: String,
    public val ByLatency: String,
)

public data class FlyStrings_Proxy_DisplayMode(
    public val SingleDetailed: String,
    public val SingleSimple: String,
    public val DoubleDetailed: String,
    public val DoubleSimple: String,
)

public data class FlyStrings_Proxy(
    public val Title: String,
    public val Mode: FlyStrings_Proxy_Mode,
    public val Action: FlyStrings_Proxy_Action,
    public val Node: FlyStrings_Proxy_Node,
    public val Empty: FlyStrings_Proxy_Empty,
    public val Testing: FlyStrings_Proxy_Testing,
    public val Selection: FlyStrings_Proxy_Selection,
    public val SortMode: FlyStrings_Proxy_SortMode,
    public val DisplayMode: FlyStrings_Proxy_DisplayMode,
)

// ==================== Rules ====================

public data class FlyStrings_Rules_Label(
    public val Proxy: String,
    public val HitRate: String,
)

public data class FlyStrings_Rules_Message(
    public val ToggleFailed: String,
)

public data class FlyStrings_Rules_Empty(
    public val Loading: String,
    public val LoadingHint: String,
    public val NotRunning: String,
    public val NotRunningHint: String,
    public val NoRules: String,
    public val NoRulesHint: String,
    public val NoResults: String,
)

public data class FlyStrings_Rules(
    public val Title: String,
    public val Summary: String,
    public val SearchHint: String,
    public val Label: FlyStrings_Rules_Label,
    public val Message: FlyStrings_Rules_Message,
    public val Empty: FlyStrings_Rules_Empty,
)

// ==================== Service ====================

public data class FlyStrings_Service_Notification(
    public val Running: String,
    public val UnknownProfile: String,
    public val NoNode: String,
    public val CurrentNode: String,
    public val CurrentNodeLabel: String,
    public val RealtimeTraffic: String,
    public val SpeedLine: String,
    public val TotalTraffic: String,
)

public data class FlyStrings_Service_Tile(
    public val ClickToOpen: String,
    public val ClickToStartProxy: String,
    public val ClickToStopProxy: String,
    public val Connecting: String,
    public val Disconnecting: String,
)

public data class FlyStrings_Service(
    public val Notification: FlyStrings_Service_Notification,
    public val Tile: FlyStrings_Service_Tile,
)

// ==================== Settings ====================

public data class FlyStrings_Settings_Section(
    public val UiSettings: String,
    public val More: String,
)

public data class FlyStrings_Settings_UiSettings(
    public val App: String,
    public val AppSummary: String,
    public val Network: String,
    public val NetworkSummary: String,
    public val Override: String,
    public val OverrideSummary: String,
    public val MetaFeatures: String,
    public val MetaFeaturesSummary: String,
)

public data class FlyStrings_Settings_More(
    public val Lab: String,
    public val LabSummary: String,
    public val Logs: String,
    public val LogsSummary: String,
    public val Onboarding: String,
    public val OnboardingSummary: String,
    public val About: String,
    public val AboutSummary: String,
)

public data class FlyStrings_Settings_Error(
    public val WebviewFailed: String,
)

public data class FlyStrings_Settings_RemoteController(
    public val ConnectionSuccess: String,
    public val ConnectionFailed: String,
)

public data class FlyStrings_Settings(
    public val Title: String,
    public val Section: FlyStrings_Settings_Section,
    public val UiSettings: FlyStrings_Settings_UiSettings,
    public val More: FlyStrings_Settings_More,
    public val Error: FlyStrings_Settings_Error,
    public val RemoteController: FlyStrings_Settings_RemoteController,
)

// ==================== TrafficStatistics ====================

public data class FlyStrings_TrafficStatistics_TimeRange(
    public val Today: String,
    public val Week: String,
)

public data class FlyStrings_TrafficStatistics_Summary(
    public val TodayTraffic: String,
    public val WeekTraffic: String,
)

public data class FlyStrings_TrafficStatistics_Donut(
    public val Other: String,
)

public data class FlyStrings_TrafficStatistics_Sort(
    public val ByName: String,
    public val ByUpload: String,
    public val ByDownload: String,
    public val ByTotal: String,
)

public data class FlyStrings_TrafficStatistics_Action(
    public val Clear: String,
    public val ClearSuccess: String,
)

public data class FlyStrings_TrafficStatistics_Section(
    public val Traffic: String,
    public val PerAppTraffic: String,
    public val EmptyApps: String,
)

public data class FlyStrings_TrafficStatistics_Metric(
    public val Download: String,
    public val Upload: String,
    public val Total: String,
    public val UsageLine: String,
)

public data class FlyStrings_TrafficStatistics_Buckets(
    public val UnknownAppName: String,
    public val UnattributedTrafficName: String,
    public val UnattributedRouteName: String,
)

public data class FlyStrings_TrafficStatistics(
    public val Title: String,
    public val EntrySummary: String,
    public val TimeRange: FlyStrings_TrafficStatistics_TimeRange,
    public val Summary: FlyStrings_TrafficStatistics_Summary,
    public val Donut: FlyStrings_TrafficStatistics_Donut,
    public val Sort: FlyStrings_TrafficStatistics_Sort,
    public val Action: FlyStrings_TrafficStatistics_Action,
    public val Section: FlyStrings_TrafficStatistics_Section,
    public val Metric: FlyStrings_TrafficStatistics_Metric,
    public val Buckets: FlyStrings_TrafficStatistics_Buckets,
)

// ==================== Util ====================

public data class FlyStrings_Util_Error(
    public val UnknownError: String,
    public val CannotCreateDir: String,
    public val TargetNotDir: String,
    public val PathTraversalDetected: String,
)

public data class FlyStrings_Util(
    public val Error: FlyStrings_Util_Error,
)
