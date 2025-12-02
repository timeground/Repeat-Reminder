
Add-Type -AssemblyName System.Drawing
$path = "app\src\main\res\drawable\ic_logo.png"
if (Test-Path $path) {
    $img = [System.Drawing.Image]::FromFile($path)
    $img.Save("app\src\main\res\drawable\ic_logo_fixed.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $img.Dispose()
    Move-Item -Force "app\src\main\res\drawable\ic_logo_fixed.png" $path
    Write-Host "Converted $path to PNG"
} else {
    Write-Error "File not found: $path"
}
