param([string]$Revision)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$failures = [System.Collections.Generic.List[string]]::new()

function Read-Source([string]$Path) {
    if ($Revision) {
        $content = & git -C $repo show "$($Revision):$Path" 2>$null
        if ($LASTEXITCODE -ne 0) { return '' }
        return ($content -join [Environment]::NewLine)
    }
    return Get-Content -Raw -LiteralPath (Join-Path $repo $Path)
}

function Assert-Source([bool]$Condition, [string]$Description) {
    if ($Condition) { Write-Output "PASS: $Description" }
    else { $failures.Add($Description); Write-Output "FAIL: $Description" }
}

$base = 'app/src/main/kotlin/com/aistra/hail'
$pager = Read-Source "$base/ui/home/PagerFragment.kt"
$api = Read-Source "$base/ui/api/ApiActivity.kt"
$launcher = Read-Source "$base/utils/HAppLauncher.kt"
$shortcuts = Read-Source "$base/utils/HShortcuts.kt"
$shell = Read-Source "$base/utils/HShell.kt"
$manager = Read-Source "$base/app/AppManager.kt"
$widget = Read-Source "$base/widgets/HailFolderWidgetService.kt"

# Source-level regression guards: these do not replace tests on a real cloned profile.
Assert-Source ($pager.Contains('private fun launchApp(info: AppInfo)')) 'Home launch retains AppInfo identity'
Assert-Source ($pager.Contains('AppManager.setAppFrozen(info.packageName, false, info.userId)')) 'Home unfreeze targets the selected user'
Assert-Source (-not ($pager -match 'AppManager\.isAppFrozen\([^,()]+\)')) 'Home has no current-user-only freeze checks'
Assert-Source (-not ($pager -match 'getIntentForPackage\(HailApi.ACTION_LAUNCH,\s*pkg\)')) 'Pinned launch intents include the user'
Assert-Source ($pager.Contains('HWork.setDeferredFrozen(pkg, !frozen, values[i].toLong(), info.userId)')) 'Deferred actions retain the user'
Assert-Source ($pager.Contains('HailData.removeCheckedApp(info.packageName, saveApps, info.userId)')) 'Removing a clone does not remove the original'
Assert-Source ($api.Contains('getIntExtra(HailData.KEY_USER, HPackages.myUserId)') -and $api.Contains('getQueryParameter(HailData.KEY_USER)')) 'API reads user from intents and deep links'
Assert-Source (-not ($api -match 'AppManager\.isAppFrozen\([^,()]+\)')) 'API and tag actions check the target user'
Assert-Source ($api.Contains('AppManager.setAppFrozen(pkg, false, userId)')) 'API unfreeze retains the user'
Assert-Source ($api.Contains('HAppLauncher.launch(this, pkg, userId)') -and $pager.Contains('HAppLauncher.launch(requireContext(), info.packageName, info.userId)')) 'Both entry points use the shared user-aware launcher'
Assert-Source ($launcher.Contains('HPackages.userHandle(userId)') -and $launcher.Contains('launcher.startMainActivity(activity.componentName, user, null, null)')) 'Clones launch via LauncherApps with their UserHandle'
Assert-Source ($launcher.Contains('if (userId == HPackages.myUserId)') -and $launcher.Contains('HIsland.ensureLaunchIntentExists(packageName)')) 'Current-user and Island launch behavior is retained'
Assert-Source ($shortcuts.Contains('getIntentForPackage(HailApi.ACTION_LAUNCH, packageName, userId)') -and $shortcuts.Contains('dynamic:$packageName#$userId') -and $shortcuts.Contains('"$id#${appInfo.userId}"')) 'Dynamic and pinned shortcuts distinguish original and clone'
Assert-Source ($widget.Contains('getIntentForPackage(HailApi.ACTION_LAUNCH, appInfo.packageName, appInfo.userId)')) 'Folder widget forwards the user'
Assert-Source ($shell.Contains('pm ${if (disabled) "disable" else "enable"} --user $userId $packageName')) 'Root disable/enable commands retain --user'
Assert-Source ($manager.Contains('HShell.setAppDisabled(packageName, frozen, userId)') -and $manager.Contains('setAppFrozen(it.packageName, frozen, it.userId)')) 'Root and batch dispatch preserve the target user'

if ($failures.Count -gt 0) {
    throw "$($failures.Count) multi-user regression checks failed."
}
Write-Output 'All 16 multi-user source regression checks passed.'
