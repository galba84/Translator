param([switch]$RefreshLock, [switch]$ManifestOnly)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path $PSScriptRoot -Parent
$cache = Join-Path $root '.work/downloads'
$assets = Join-Path $root 'app/src/main/assets'
$models = Join-Path $assets 'models'
$lockPath = Join-Path $PSScriptRoot 'resources.lock.json'
$utf8 = New-Object System.Text.UTF8Encoding($false)
New-Item -ItemType Directory -Force $cache,$models,(Join-Path $root 'app/libs') | Out-Null
$previous = if (Test-Path $lockPath) { Get-Content $lockPath -Raw | ConvertFrom-Json } else { $null }
$records = [System.Collections.Generic.List[object]]::new()
$supportedPairs = @('en-es', 'es-en')
function Fetch([string]$url, [string]$destination, [string]$expected = '') {
    $relative = $destination.Substring($root.Length + 1).Replace('\','/')
    $locked = @($previous | Where-Object { $_.path -eq $relative })
    if (!$expected -and $locked.Count -gt 0 -and !$RefreshLock) { $expected = $locked[0].sha256 }
    if (!(Test-Path $destination) -or ($expected -and (Get-FileHash $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected)) {
        New-Item -ItemType Directory -Force (Split-Path $destination -Parent) | Out-Null
        Write-Output "Downloading $relative"
        & curl.exe --fail --location --retry 4 --silent --show-error $url --output "$destination.partial"
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $url" }
        if ($expected -and (Get-FileHash "$destination.partial" -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw "Checksum mismatch: $url" }
        Move-Item -LiteralPath "$destination.partial" -Destination $destination -Force
    }
    $hash = (Get-FileHash $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    $records.Add([ordered]@{ path=$relative; url=$url; sha256=$hash })
}
if (!$ManifestOnly) {
$revisions = [ordered]@{
    'en-es'='4b002a4c7edd54a7ced58877258b87f7efd3f892'
    'es-en'='eadfd7c658a9d8929ac3b8e996b68a68e2c7d480'
}
foreach ($pair in $revisions.Keys) {
    $revision = $revisions[$pair]
    $metadata = Invoke-RestMethod "https://huggingface.co/api/models/Xenova/opus-mt-$pair/revision/${revision}?blobs=true"
    foreach ($file in @('config.json','tokenizer.json','vocab.json','source.spm','target.spm','README.md','onnx/encoder_model_quantized.onnx','onnx/decoder_model_quantized.onnx','onnx/decoder_with_past_model_quantized.onnx')) {
        $info = $metadata.siblings | Where-Object { $_.rfilename -eq $file }
        if (!$info) { throw "Required artifact absent: $pair/$file" }
        $expected = if ($info.lfs) { $info.lfs.sha256 } else { '' }
        $targetName = $file.Replace('onnx/','')
        Fetch "https://huggingface.co/Xenova/opus-mt-$pair/resolve/$revision/$file" (Join-Path $models "translation/$pair/$targetName") $expected
    }
}
Fetch 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.20/sherpa-onnx-static-link-onnxruntime-1.12.20.aar' (Join-Path $root 'app/libs/sherpa-onnx-static-link-onnxruntime-1.12.20.aar')
foreach ($entry in @(
    @('asr-models','sherpa-onnx-whisper-base'),
    @('tts-models','vits-piper-en_US-lessac-medium'),
    @('tts-models','vits-piper-es_ES-sharvard-medium')
)) {
    $name = $entry[1]
    $archive = Join-Path $cache "$name.tar.bz2"
    Fetch "https://github.com/k2-fsa/sherpa-onnx/releases/download/$($entry[0])/$name.tar.bz2" $archive
    if (!(Test-Path (Join-Path $models "$name/.extracted"))) {
        Write-Output "Extracting $name"
        & tar.exe -xjf $archive -C $models
        if ($LASTEXITCODE -ne 0) { throw "Extraction failed: $name" }
        [System.IO.File]::WriteAllText((Join-Path $models "$name/.extracted"), 'ok', $utf8)
    }
}
$records | ConvertTo-Json -Depth 5 | ForEach-Object { [System.IO.File]::WriteAllText($lockPath, $_, $utf8) }
}
$manifest = [System.Collections.Generic.List[object]]::new()
$translationRoot = [System.IO.Path]::GetFullPath((Join-Path $models 'translation'))
Get-ChildItem -LiteralPath $translationRoot -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -notin $supportedPairs } | ForEach-Object {
    $obsoletePath = [System.IO.Path]::GetFullPath($_.FullName)
    if (!$obsoletePath.StartsWith($translationRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a path outside the generated translation directory: $obsoletePath"
    }
    Remove-Item -LiteralPath $obsoletePath -Recurse -Force
}
# These two bundled voices only require English/Spanish phonemizer language data.
Get-ChildItem -LiteralPath $models -Directory -Filter 'vits-piper-*' | ForEach-Object {
    $phonemizer = Join-Path $_.FullName 'espeak-ng-data'
    Get-ChildItem -LiteralPath $phonemizer -File -Filter '*_dict' | Where-Object { $_.Name -notin @('en_dict', 'es_dict') } | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Force
    }
    Get-ChildItem -LiteralPath (Join-Path $phonemizer 'lang') -File -Recurse | Where-Object { $_.Name -notmatch '^(en|es)(-|$)' } | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Force
    }
}
if ($ManifestOnly -and $previous) {
    $supportedPattern = '/translation/(' + ($supportedPairs -join '|') + ')/'
    $filteredLock = @($previous | Where-Object { $_.path -notmatch '/translation/' -or $_.path -match $supportedPattern })
    [System.IO.File]::WriteAllText($lockPath, ($filteredLock | ConvertTo-Json -Depth 5), $utf8)
}
foreach ($unused in @('base-encoder.onnx', 'base-decoder.onnx')) {
    $unusedPath = Join-Path $models "sherpa-onnx-whisper-base/$unused"
    if (Test-Path $unusedPath) { Remove-Item -LiteralPath $unusedPath -Force }
}
Get-ChildItem $models -File -Recurse | Where-Object {
    $_.Name -ne '.extracted' -and $_.Name -notin @('base-encoder.onnx','base-decoder.onnx') -and $_.FullName -notmatch '[\\/]test_wavs[\\/]'
} | Sort-Object FullName | ForEach-Object {
    $manifest.Add([ordered]@{ path=$_.FullName.Substring($assets.Length+1).Replace('\','/'); bytes=$_.Length; sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() })
}
[System.IO.File]::WriteAllText((Join-Path $assets 'resources.json'), ($manifest | ConvertTo-Json -Depth 4), $utf8)
Write-Output "Prepared $($manifest.Count) bundled files."
