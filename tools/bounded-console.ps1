# Only the supported launcher's duplicate console diagnostics. RuneLite's own logs stay separate.
function Assert-PlainDiagnosticPath([string] $Path) {
    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        if (Test-Path -LiteralPath $current) {
            if ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw 'Linked diagnostic path refused.'
            }
        }
        $parent = [IO.Path]::GetDirectoryName($current)
        if ($parent -eq $current) { break }; $current = $parent
    }
}
function Open-BoundedConsole([string] $Directory, [long] $Limit = 8388608, [int] $Archives = 4) {
    $header = [Text.Encoding]::UTF8.GetBytes("Flipping Friend client console v1`n")
    if ($Limit -le $header.Length -or $Archives -lt 1 -or $Archives -gt 4) { throw 'Bounded console policy required.' }
    Assert-PlainDiagnosticPath $Directory
    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
    $file = Join-Path $Directory 'client-console.log'
    foreach ($number in 0..$Archives) {
        $candidate = if ($number -eq 0) { $file } else { "$file.$number" }
        Assert-PlainDiagnosticPath $candidate
        if (Test-Path -LiteralPath $candidate) {
            $item = Get-Item -LiteralPath $candidate -Force
            if ($item.PSIsContainer -or $item.Length -gt $Limit) { throw 'Unexpected diagnostic target; preserve it before launching.' }
            Assert-OwnedConsole $candidate $header
        }
    }
    if (-not (Test-Path -LiteralPath $file)) {
        $created = [IO.File]::Open($file, 'CreateNew', 'Write', 'Read')
        try { $created.Write($header, 0, $header.Length) } finally { $created.Dispose() }
    }
    $stream = [IO.File]::Open($file, 'OpenOrCreate', 'Write', 'Read')
    $stream.Position = $stream.Length
    return @{ File=$file; Limit=$Limit; Archives=$Archives; Stream=$stream; Failed=$false; Header=$header }
}
function Assert-OwnedConsole([string] $File, [byte[]] $Header) {
    $inputStream = [IO.File]::Open($File, 'Open', 'Read', 'ReadWrite')
    try {
        $prefix = New-Object byte[] $Header.Length
        if ($inputStream.Read($prefix, 0, $prefix.Length) -ne $prefix.Length -or
            [Convert]::ToBase64String($prefix) -cne [Convert]::ToBase64String($Header)) {
            throw 'Unrecognized diagnostic file; preserve it before launching.'
        }
    } finally { $inputStream.Dispose() }
}
function Write-BoundedConsole($Log, [string] $Text) {
    if ($Log.Failed) { return }
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Text + [Environment]::NewLine)
        $offset = 0
        while ($offset -lt $bytes.Length) {
            if ($Log.Stream.Length -eq $Log.Limit) {
                foreach ($number in 0..$Log.Archives) {
                    $candidate = if ($number -eq 0) { $Log.File } else { "$($Log.File).$number" }
                    Assert-PlainDiagnosticPath $candidate
                    if ((Test-Path -LiteralPath $candidate) -and (Get-Item -LiteralPath $candidate).PSIsContainer) {
                        throw 'Non-file diagnostic archive refused.'
                    }
                    if (Test-Path -LiteralPath $candidate) { Assert-OwnedConsole $candidate $Log.Header }
                }
                $Log.Stream.Dispose()
                # Exact owned log names only; no filesystem enumeration or recursive deletion.
                $oldest = "$($Log.File).$($Log.Archives)"
                if (Test-Path -LiteralPath $oldest) { Remove-Item -LiteralPath $oldest -Force }
                for ($number = $Log.Archives - 1; $number -ge 1; $number--) {
                    $source = "$($Log.File).$number"
                    if (Test-Path -LiteralPath $source) {
                        Move-Item -LiteralPath $source -Destination "$($Log.File).$($number + 1)" -Force
                    }
                }
                Move-Item -LiteralPath $Log.File -Destination "$($Log.File).1" -Force
                $Log.Stream = [IO.File]::Open($Log.File, 'CreateNew', 'Write', 'Read')
                $Log.Stream.Write($Log.Header, 0, $Log.Header.Length)
            }
            $count = [int][Math]::Min($bytes.Length - $offset, $Log.Limit - $Log.Stream.Length)
            $Log.Stream.Write($bytes, $offset, $count); $offset += $count
        }
        $Log.Stream.Flush()
    } catch {
        $Log.Failed = $true
        if ($Log.Stream) { $Log.Stream.Dispose() }
        Write-Warning 'Client console logging degraded; further console output is discarded. The game process is unchanged.' -WarningAction Continue
    }
}
function Close-BoundedConsole($Log) {
    if ($Log -and $Log.Stream) { $Log.Stream.Dispose(); $Log.Failed = $true }
}
