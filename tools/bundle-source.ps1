# Writes every source file in the tree into one reviewable text file, and prints it.
#
# For handing the whole codebase to something that can only read text -- another model, a reviewer,
# a paste buffer. The output goes to codebase-review.txt in the repository root.
#
# Three things this gets right that `git ls-files | xargs cat` does not:
#
#   * Untracked files are included. `git ls-files` lists only what is *tracked*, and on this tree
#     that silently omits nineteen files -- which at the time of writing were the newest ones,
#     including FillCalibration and six test classes written that day. A review missing exactly the
#     work under review is worse than no review.
#   * Deleted-but-still-tracked files are skipped. They appear in `git ls-files` and are not on disk.
#
# Nothing is redacted. There are no credentials in this tree -- the companion token lives in
# ~/.runelite/osrs-flipping-friend/companion/companion.properties, which is outside it -- but if that
# ever changes, this is the script that would carry them out.

param(
    [string] $Out = 'codebase-review.txt',
    [switch] $Quiet          # write the file without echoing it to the console
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Binary, generated, or bulk data. Anything here is excluded from the bundle.
$skip = '\.(jar|png|jpg|jpeg|gif|svg|ico|gz|zip|onnx|db|db-wal|db-shm|bin|class|exe)$'

$files = git ls-files -co --exclude-standard |
    Where-Object { $_ -notmatch $skip -and (Test-Path -LiteralPath $_) } |
    Sort-Object

$writer = [System.IO.StreamWriter]::new((Join-Path $root $Out), $false, [System.Text.UTF8Encoding]::new($false))
try {
    $header = @(
        "Flipping Friend - full source bundle",
        "generated $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
        "branch $(git rev-parse --abbrev-ref HEAD)  commit $(git rev-parse --short HEAD)",
        "$($files.Count) files",
        ""
    ) -join "`n"
    $writer.WriteLine($header)
    if (-not $Quiet) { Write-Host $header }

    foreach ($file in $files) {
        $bar = "=" * 78
        $head = "$bar`n===== $file`n$bar"
        $writer.WriteLine($head)
        $body = Get-Content -Raw -LiteralPath $file -ErrorAction SilentlyContinue
        if ($null -ne $body) { $writer.WriteLine($body) } else { $writer.WriteLine('(empty)') }
        if (-not $Quiet) {
            Write-Host $head
            Write-Host $body
        }
    }
}
finally { $writer.Dispose() }

$written = Get-Item (Join-Path $root $Out)
Write-Host ''
Write-Host "  $($files.Count) files -> $($written.FullName)" -ForegroundColor Green
Write-Host ("  {0:N0} bytes" -f $written.Length) -ForegroundColor Green
