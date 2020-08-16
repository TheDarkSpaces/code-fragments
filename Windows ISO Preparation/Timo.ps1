param (
[ValidateScript({Test-Path $_ -PathType 'Leaf'})][String]$ImagePath = $(
if (Test-Path "..\sources\install.esd" -PathType "Leaf") {
"..\sources\install.esd"
} else {
"..\sources\install.wim"
}
),
[ValidateScript({Test-Path $_ -PathType 'Container'})][String]$MountPath = "..\mount\",
[ValidateRange(1, 9001)][Int]$Index = 1,
[ValidateScript({Test-Path $_ -PathType 'Container'})][String]$FilesPath = ".\",
[String]$ImageOut = "..\install.wim"
)

[System.IO.Directory]::SetCurrentDirectory(((Get-Location -PSProvider FileSystem).ProviderPath))
$ImagePath = $(Resolve-Path $ImagePath)
$MountPath = $(Resolve-Path $MountPath)
$FilesPath = $(Resolve-Path $FilesPath)

function isadministrator {
    $Identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    $Principal = New-Object System.Security.Principal.WindowsPrincipal($Identity)
    $Principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)
}
function isuacenabled {
(Get-ItemProperty HKLM:\Software\Microsoft\Windows\CurrentVersion\Policies\System).EnableLua -ne 0
}
if (!(isadministrator)) {
if (isuacenabled) {
[String[]]$ArgList = @('-NoProfile', '-NoExit', '-Command', "& {cd $pwd; $(Resolve-Path $MyInvocation.MyCommand.Path) -ImagePath $ImagePath -MountPath $MountPath -Index $Index -FilesPath $FilesPath}")
Start-Process PowerShell.exe -Verb RunAs -WorkingDirectory $pwd -ArgumentList $ArgList
return
} else {
throw "You must be Administrator to run this script!"
}
}

$ErrorActionPreference = "Stop"

Expand-WindowsImage -ApplyPath $MountPath -ImagePath $ImagePath -Index $Index


function execute {
param (
[String]$ExeFile,
[String[]]$Parameters = @(),
[Int]$SuccessCode = 0
)

& $ExeFile $Parameters | Out-Host
if ($LastExitCode -ne $SuccessCode) {
Out-Host -InputObject "$ExeFile $Parameters Failed"
throw "$ExeFile failed with exit code $LastExitCode"
}
}

execute -ExeFile DISM -Parameters $("/Image:$MountPath", "/Set-TimeZone:`"W. Europe Standard Time`"")
Add-WindowsPackage -Path $MountPath -PackagePath $([System.IO.Path]::Combine($FilesPath, "rsat.msu"))

Get-AppxProvisionedPackage -Path $MountPath | ForEach-Object {
Remove-AppxProvisionedPackage -Path $MountPath -PackageName $_.PackageName
}
# TODO check
Get-WindowsPackage -Path $MountPath | ForEach-Object {
if (($_.PackageState -eq "Installed") -and ($_.PackageName.Contains("Speech") -or $_.PackageName.Contains("Handwriting"))) {
Remove-WindowsPackage -Path $MountPath -PackageName $_.PackageName
}
}
Get-WindowsCapability -Path $MountPath | ForEach-Object {
if (($_.State -eq "Installed") -and ($_.Name.Contains("Speech") -or $_.Name.Contains("Handwriting"))) {
Remove-WindowsCapability -Path $MountPath -Name $_.Name
}
}
Disable-WindowsOptionalFeature -Path $MountPath -FeatureName "Printing-XPSServices-Features" -Remove
Disable-WindowsOptionalFeature -Path $MountPath -FeatureName "WindowsMediaPlayer" -Remove
Enable-WindowsOptionalFeature -Path $MountPath -FeatureName "Microsoft-Hyper-V" -All

$hives = New-Object System.Collections.Generic.HashSet[String]
try {
@{
"Default" = "Users\Default\NTUSER.DAT";
"LocalSoft" = "Windows\System32\config\SOFTWARE";
"LocalSystem" = "Windows\System32\config\SYSTEM"
}.GetEnumerator() | ForEach-Object {
execute -ExeFile REG -Parameters @('LOAD', "HKLM\$($_.Name)", $([System.IO.Path]::Combine($MountPath, $_.Value)))
$hives.Add($_.Name)
}
execute -ExeFile REG -Parameters @('IMPORT', $([System.IO.Path]::Combine($FilesPath, "all.reg")))
} finally {
$hives | ForEach-Object { "HKLM:\$_" } | Get-Item | ForEach-Object { $_.Name } | Remove-Variable
[GC]::Collect()
[GC]::WaitForPendingFinalizers()
$hives | ForEach-Object {
execute -ExeFile REG -Parameters @('UNLOAD', "HKLM\$_")
}
}

Get-Item -Path "$([System.IO.Path]::Combine($MountPath, `"Users\Default\AppData\Roaming\Microsoft\Windows\SendTo`"))\*" -Include @("*Recipient*.*", "Compressed*.*")
@("Users\Default\AppData\Roaming", "ProgramData") | ForEach-Object { [System.IO.Path]::Combine($MountPath, $_, "Microsoft\Windows\Start Menu\Programs\Accessibility") } | Remove-Item -Recurse -Force



if (Test-Path $ImageOut) {
Remove-Item -Path $ImageOut -Force
}
New-WindowsImage -CapturePath $MountPath -ImagePath $ImageOut -Name "Windows 10 [Modified]" -CompressionType fast -Description "A somewhat customized Windows 10"
