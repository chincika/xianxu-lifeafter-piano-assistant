param(
    [switch]$WithSeparation
)

$ErrorActionPreference = 'Stop'
$runtimeRoot = Join-Path $env:LOCALAPPDATA 'GamePianoAutoplay\audio-runtime'
$downloadRoot = Join-Path $env:TEMP 'GamePianoAutoplay-audio-setup'
$pythonZip = Join-Path $downloadRoot 'python-3.10.11-embed-amd64.zip'
$getPip = Join-Path $downloadRoot 'get-pip.py'
$pythonExe = Join-Path $runtimeRoot 'python.exe'
$ffmpegRoot = Join-Path $runtimeRoot 'ffmpeg'
$ffmpegZip = Join-Path $downloadRoot 'ffmpeg-8.1-win64-gpl-shared.zip'

New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null

if (-not (Test-Path -LiteralPath $pythonExe)) {
    Write-Host 'Downloading isolated Python 3.10 runtime...'
    Invoke-WebRequest -UseBasicParsing `
        -Uri 'https://www.python.org/ftp/python/3.10.11/python-3.10.11-embed-amd64.zip' `
        -OutFile $pythonZip
    New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
    Expand-Archive -LiteralPath $pythonZip -DestinationPath $runtimeRoot -Force
    $pthFile = Join-Path $runtimeRoot 'python310._pth'
    $pth = Get-Content -LiteralPath $pthFile
    $pth -replace '^#import site$', 'import site' | Set-Content -LiteralPath $pthFile -Encoding ascii
}

Write-Host 'Installing pip and the local audio-to-MIDI engine...'
Invoke-WebRequest -UseBasicParsing -Uri 'https://bootstrap.pypa.io/get-pip.py' -OutFile $getPip
& $pythonExe $getPip --disable-pip-version-check
if ($LASTEXITCODE -ne 0) { throw "get-pip failed with exit code $LASTEXITCODE" }

& $pythonExe -m pip install --disable-pip-version-check --upgrade 'pip<27' 'setuptools<81'
if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed with exit code $LASTEXITCODE" }

& $pythonExe -m pip install --disable-pip-version-check 'basic-pitch==0.4.0'
if ($LASTEXITCODE -ne 0) { throw "Basic Pitch installation failed with exit code $LASTEXITCODE" }

if ($WithSeparation) {
    if (-not (Get-ChildItem -LiteralPath $ffmpegRoot -Filter 'avcodec-62.dll' -Recurse -ErrorAction SilentlyContinue)) {
        Write-Host 'Downloading the FFmpeg shared runtime required by TorchCodec...'
        Invoke-WebRequest -UseBasicParsing `
            -Uri 'https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.1-latest-win64-gpl-shared-8.1.zip' `
            -OutFile $ffmpegZip
        New-Item -ItemType Directory -Force -Path $ffmpegRoot | Out-Null
        Expand-Archive -LiteralPath $ffmpegZip -DestinationPath $ffmpegRoot -Force
    }
    Write-Host 'Installing optional Demucs vocal-separation engine...'
    & $pythonExe -m pip install --disable-pip-version-check `
        'demucs==4.0.1' 'imageio-ffmpeg>=0.5.1' 'torchcodec>=0.8.0'
    if ($LASTEXITCODE -ne 0) { throw "Demucs installation failed with exit code $LASTEXITCODE" }
}

Write-Host "Audio engine ready: $pythonExe"
& $pythonExe -c "import importlib.util; from basic_pitch.inference import predict; print('basic_pitch=ok demucs=' + ('ok' if importlib.util.find_spec('demucs') else 'missing'))"
