# Push models to phone app-private storage: files/models/<subfolder>/
param (
    [Parameter(Mandatory=$true)][string]$Src,
    [Parameter(Mandatory=$true)][string]$Subfolder
)

$Pkg = "com.omnicontrol.app"
$TmpDir = "/data/local/tmp"

if (-not (Test-Path $Src)) {
    Write-Error "Source path $Src does not exist."
    exit 1
}

if ((Get-Item $Src) -is [System.IO.FileInfo]) {
    $FileName = Split-Path $Src -Leaf
    Write-Host "Pushing file $FileName to $TmpDir..."
    adb push $Src "$TmpDir/$FileName"
    Write-Host "Moving to app storage files/models/$Subfolder/..."
    adb shell "run-as $Pkg mkdir -p files/models/$Subfolder"
    adb shell "run-as $Pkg cp $TmpDir/$FileName files/models/$Subfolder/"
    adb shell "rm $TmpDir/$FileName"
    Write-Host "Done! File installed at files/models/$Subfolder/$FileName"
} else {
    $TarName = "temp_models.tar"
    Write-Host "Packing directory $Src into $TarName..."
    tar -cf $TarName -C $Src .
    adb push $TarName "$TmpDir/$TarName"
    Remove-Item $TarName
    Write-Host "Extracting into app storage files/models/$Subfolder/..."
    adb shell "run-as $Pkg mkdir -p files/models/$Subfolder"
    adb shell "run-as $Pkg tar -xf $TmpDir/$TarName -C files/models/$Subfolder"
    adb shell "rm $TmpDir/$TarName"
    Write-Host "Done! Directory installed at files/models/$Subfolder"
}
