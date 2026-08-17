param(
    [Parameter(Mandatory = $true)]
    [string]$Source,
    [Parameter(Mandatory = $true)]
    [string]$ResourceRoot
)

Add-Type -AssemblyName System.Drawing
$drawingAssembly = [System.Drawing.Bitmap].Assembly.Location
Add-Type -ReferencedAssemblies $drawingAssembly -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class CalolyIconAlpha {
    public static Bitmap RemoveConnectedCheckerboard(Bitmap source) {
        var bitmap = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb);
        using (var graphics = Graphics.FromImage(bitmap)) {
            graphics.DrawImage(source, 0, 0, source.Width, source.Height);
        }

        var rect = new Rectangle(0, 0, bitmap.Width, bitmap.Height);
        var data = bitmap.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        var bytes = new byte[Math.Abs(data.Stride) * bitmap.Height];
        Marshal.Copy(data.Scan0, bytes, 0, bytes.Length);

        var visited = new bool[bitmap.Width * bitmap.Height];
        var queue = new Queue<int>();
        Action<int, int> seed = (x, y) => {
            var pixel = y * bitmap.Width + x;
            if (!visited[pixel] && IsCheckerboard(bytes, data.Stride, x, y)) {
                visited[pixel] = true;
                queue.Enqueue(pixel);
            }
        };

        for (var x = 0; x < bitmap.Width; x++) { seed(x, 0); seed(x, bitmap.Height - 1); }
        for (var y = 0; y < bitmap.Height; y++) { seed(0, y); seed(bitmap.Width - 1, y); }

        while (queue.Count > 0) {
            var pixel = queue.Dequeue();
            var x = pixel % bitmap.Width;
            var y = pixel / bitmap.Width;
            var offset = y * data.Stride + x * 4;
            bytes[offset + 3] = 0;

            TryVisit(x - 1, y, bitmap.Width, bitmap.Height, bytes, data.Stride, visited, queue);
            TryVisit(x + 1, y, bitmap.Width, bitmap.Height, bytes, data.Stride, visited, queue);
            TryVisit(x, y - 1, bitmap.Width, bitmap.Height, bytes, data.Stride, visited, queue);
            TryVisit(x, y + 1, bitmap.Width, bitmap.Height, bytes, data.Stride, visited, queue);
        }

        Marshal.Copy(bytes, 0, data.Scan0, bytes.Length);
        bitmap.UnlockBits(data);
        return bitmap;
    }

    private static void TryVisit(int x, int y, int width, int height, byte[] bytes, int stride, bool[] visited, Queue<int> queue) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        var pixel = y * width + x;
        if (visited[pixel] || !IsCheckerboard(bytes, stride, x, y)) return;
        visited[pixel] = true;
        queue.Enqueue(pixel);
    }

    private static bool IsCheckerboard(byte[] bytes, int stride, int x, int y) {
        var offset = y * stride + x * 4;
        var b = bytes[offset];
        var g = bytes[offset + 1];
        var r = bytes[offset + 2];
        var neutral = Math.Max(r, Math.Max(g, b)) - Math.Min(r, Math.Min(g, b)) <= 18;
        return neutral && r >= 170 && g >= 170 && b >= 170;
    }
}
'@

$sourceBitmap = [System.Drawing.Bitmap]::new($Source)
$cleanBitmap = [CalolyIconAlpha]::RemoveConnectedCheckerboard($sourceBitmap)
$sourceBitmap.Dispose()

$projectRoot = (Resolve-Path (Join-Path $ResourceRoot '..\..\..\..')).Path
$artworkDirectory = Join-Path $projectRoot 'artwork'
New-Item -ItemType Directory -Force -Path $artworkDirectory | Out-Null
$masterPath = Join-Path $artworkDirectory 'caloly_app_icon.png'
$cleanBitmap.Save($masterPath, [System.Drawing.Imaging.ImageFormat]::Png)

$sizes = [ordered]@{
    'mipmap-mdpi' = 48
    'mipmap-hdpi' = 72
    'mipmap-xhdpi' = 96
    'mipmap-xxhdpi' = 144
    'mipmap-xxxhdpi' = 192
}

foreach ($entry in $sizes.GetEnumerator()) {
    $directory = Join-Path $ResourceRoot $entry.Key
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $resized = [System.Drawing.Bitmap]::new($entry.Value, $entry.Value, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($resized)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.DrawImage($cleanBitmap, 0, 0, $entry.Value, $entry.Value)
    $graphics.Dispose()
    $resized.Save((Join-Path $directory 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $resized.Save((Join-Path $directory 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $resized.Dispose()
}

$cleanBitmap.Dispose()
