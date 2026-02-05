param (
    [Parameter(Mandatory = $true)]
    [string]$SourceDir,

    [Parameter(Mandatory = $true)]
    [string]$DestinationDir
)

# Resolve full paths
if (Test-Path $SourceDir) {
    $SourceDir = (Resolve-Path $SourceDir).Path
} else {
    Write-Error "Source directory does not exist: $SourceDir"
    exit 1
}

# Check if destination already exists and abort
if (Test-Path $DestinationDir) {
    Write-Error "Destination directory already exists: $DestinationDir"
    Write-Host "Please remove or choose a different destination directory."
    exit 1
}

# Create destination directory
Write-Host "Creating destination directory: $DestinationDir"
New-Item -ItemType Directory -Path $DestinationDir -Force | Out-Null

# Unity folders we want to keep
$UnityFoldersToCopy = @(
    "Assets",
    "Packages",
    "ProjectSettings",
    "UserSettings"
)

# Unity folders to ignore
$ExcludedDirs = @(
    "Library",
    "Temp",
    "Obj",
    "Build",
    "Builds",
    "Logs",
    ".vs"
)

Write-Host "Copying Unity projects from:`n  $SourceDir`n→ $DestinationDir`n"

# Check if source is itself a Unity project (has Assets folder) or contains Unity projects
$isUnityProject = Test-Path (Join-Path $SourceDir "Assets")

if ($isUnityProject) {
    # Source is a single Unity project - copy it directly to destination
    Write-Host "Detected single Unity project at source"

    # Copy essential Unity folders directly to destination
    foreach ($folder in $UnityFoldersToCopy) {
        $src = Join-Path $SourceDir $folder
        if (Test-Path $src) {
            Write-Host "  Copying $folder"
            Copy-Item -Path $src -Destination $DestinationDir -Recurse -Force
        }
    }

    # Copy solution & project files
    Get-ChildItem -Path $SourceDir -File -Include *.sln, *.csproj, *.asmdef, *.json | ForEach-Object {
        Copy-Item $_.FullName -Destination $DestinationDir -Force
    }
} else {
    # Source contains multiple Unity projects - not supported
    Write-Error "Source directory contains multiple Unity projects. This script only supports single project copying."
    Write-Host "For multiple projects, please copy each project individually."
    Remove-Item -Path $DestinationDir -Force -Recurse
    exit 1
}

Write-Host "`nDone. Unity project copied without cache folders."
