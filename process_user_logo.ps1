
Add-Type -AssemblyName System.Drawing
$source = "C:\Users\athul\.gemini\antigravity\brain\9779a1e3-a4dd-43bf-b082-8730d4d70b3c\uploaded_image_1764678578906.jpg"
$dest = "app\src\main\res\drawable\ic_logo.png"

Write-Host "Processing $source..."

if (Test-Path $source) {
    try {
        $img = [System.Drawing.Image]::FromFile($source)
        # Create a new bitmap to ensure clean metadata and format
        $bmp = New-Object System.Drawing.Bitmap($img.Width, $img.Height)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.DrawImage($img, 0, 0, $img.Width, $img.Height)
        
        # Save as PNG
        $bmp.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
        
        $g.Dispose()
        $bmp.Dispose()
        $img.Dispose()
        
        Write-Host "Successfully saved valid PNG to $dest"
    }
    catch {
        Write-Error "Failed to convert image: $_"
        exit 1
    }
}
else {
    Write-Error "Source file not found!"
    exit 1
}
