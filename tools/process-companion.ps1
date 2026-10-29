# Normal-user lifecycle. Called by apply-update.ps1; never edits a scheduled task or database.
function Get-NativeProcessCommandLine([int] $ProcessId) {
    # CIM can omit an owned JVM's CommandLine despite allowing limited native process queries.
    # Read the actual argument string; a task definition, port or executable name is not identity.
    if (-not ('FlippingFriend.ProcessIdentityQuery' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace FlippingFriend {
    public static class ProcessIdentityQuery {
        [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,uint pid);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetProcessTimes(IntPtr process,out long created,out long exited,out long kernel,out long user);
        [DllImport("kernel32.dll",SetLastError=true)] static extern bool TerminateProcess(IntPtr process,uint code);
        [DllImport("ntdll.dll")] static extern int NtQueryInformationProcess(IntPtr process,int informationClass,IntPtr information,uint length,out uint needed);
        public static string Read(int pid) {
            if(pid<=0) throw new ArgumentException("A positive process identity is required.");
            IntPtr process=OpenProcess(0x1000,false,(uint)pid); // PROCESS_QUERY_LIMITED_INFORMATION only.
            if(process==IntPtr.Zero) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
            try { return ReadHandle(process); }
            finally { CloseHandle(process); }
        }
        public static void StopVerified(int pid,long expectedCreation,string expectedCommandLine) {
            if(pid<=0 || expectedCreation<=0 || String.IsNullOrEmpty(expectedCommandLine)) throw new ArgumentException("Verified process identity required.");
            IntPtr process=OpenProcess(0x1001,false,(uint)pid); // Query limited information + terminate only.
            if(process==IntPtr.Zero) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
            try {
                long created,exited,kernel,user;
                if(!GetProcessTimes(process,out created,out exited,out kernel,out user)) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
                // CIM preserves microseconds; native FILETIME has one extra decimal place.
                if(created/10!=expectedCreation/10 || !String.Equals(ReadHandle(process),expectedCommandLine,StringComparison.Ordinal))
                    throw new InvalidOperationException("Companion handle identity changed; no process was stopped.");
                if(!TerminateProcess(process,0)) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
            } finally { CloseHandle(process); }
        }
        private static string ReadHandle(IntPtr process) {
                uint needed; int status=NtQueryInformationProcess(process,60,IntPtr.Zero,0,out needed);
                int header=IntPtr.Size==8?16:8;
                if(needed<header || needed>1048576) throw new InvalidOperationException("Native command-line query unavailable.");
                uint capacity=needed; IntPtr buffer=Marshal.AllocHGlobal((int)capacity);
                try {
                    status=NtQueryInformationProcess(process,60,buffer,capacity,out needed);
                    if(status!=0) throw new InvalidOperationException("Native command-line query refused.");
                    int length=(ushort)Marshal.ReadInt16(buffer);
                    int maximum=(ushort)Marshal.ReadInt16(buffer,2);
                    IntPtr text=Marshal.ReadIntPtr(buffer,IntPtr.Size==8?8:4);
                    long offset=text.ToInt64()-buffer.ToInt64();
                    if(length<=0 || length%2!=0 || maximum<length || offset<header || offset>capacity || length>capacity-offset)
                        throw new InvalidOperationException("Native command-line result is inconsistent.");
                    return Marshal.PtrToStringUni(text,length/2);
                } finally { Marshal.FreeHGlobal(buffer); }
        }
    }
}
'@
    }
    return [FlippingFriend.ProcessIdentityQuery]::Read($ProcessId)
}
function Stop-NativeVerifiedCompanion($Process) {
    if (-not ('FlippingFriend.ProcessIdentityQuery' -as [type])) { $null=Get-NativeProcessCommandLine $Process.ProcessId }
    $creation=([DateTime]$Process.CreationDate).ToUniversalTime().ToFileTimeUtc()
    [FlippingFriend.ProcessIdentityQuery]::StopVerified([int]$Process.ProcessId,$creation,[string]$Process.VerifiedCommandLine)
}
function Assert-CompanionLifecycleGuard([string] $Jar) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($Jar)
        try {
            $entry = $archive.GetEntry('META-INF/MANIFEST.MF')
            if (-not $entry -or -not $archive.GetEntry('com/flippingfriend/companion/CompanionLifecycle.class')) { throw 'Missing startup guard.' }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if ($metadata -notmatch '(?m)^FF-Lifecycle-Guard: 1\r?$') { throw 'Missing startup guard version.' }
        } finally { $archive.Dispose() }
    } catch { throw "Companion lifecycle guard is missing or unsupported: $($_.Exception.Message)" }
}
function Get-OwnedCompanionProcesses([string] $ExpectedSid) {
    $matching = @()
    foreach ($process in @(Get-CimInstance Win32_Process -ErrorAction Stop)) {
        if ($process.Name -notin @('java.exe','javaw.exe')) { continue }
        $commandLine = $process.CommandLine
        if (-not $commandLine) {
            $nativeOwner = Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid -ErrorAction Stop
            if ($nativeOwner.ReturnValue -ne 0 -or $nativeOwner.Sid -ne $ExpectedSid) { continue }
            try { $commandLine = Get-NativeProcessCommandLine $process.ProcessId }
            catch { continue } # An unidentified listener still refuses the entire update below.
        }
        if ($commandLine -notmatch '(?:^|\s)-jar\s+(?:"(?<quoted>[^"]+)"|(?<bare>\S+))\s*$') { continue }
        $jar = if ($Matches['quoted']) { $Matches['quoted'] } else { $Matches['bare'] }
        if (-not [IO.Path]::IsPathRooted($jar) -or (Normalize-AbsolutePath $jar) -ne (Normalize-AbsolutePath $companionInstalled)) { continue }
        $owner = Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid -ErrorAction Stop
        if ($owner.ReturnValue -ne 0 -or $owner.Sid -ne $ExpectedSid) { throw 'Companion process owner cannot be verified as this Windows user.' }
        $matching += [pscustomobject]@{ ProcessId=$process.ProcessId; CreationDate=$process.CreationDate; VerifiedCommandLine=$commandLine }
    }
    $listener = Get-CompanionPid
    if ($listener -ne 0 -and $listener -notin @($matching | ForEach-Object { [int]$_.ProcessId })) {
        throw 'Port 37777 belongs to an unidentified process; it was not stopped.'
    }
    return $matching
}
function Stop-OwnedCompanionProcesses([string] $ExpectedSid) {
    $deadline = (Get-Date).AddSeconds(20)
    do {
        $matching = @(Get-OwnedCompanionProcesses $ExpectedSid)
        if ($matching.Count -eq 0) { Wait-PortFree; return }
        foreach ($process in $matching) {
            # Re-read immediately before stopping to reject a changed/reused PID or foreign owner.
            $current = @(Get-OwnedCompanionProcesses $ExpectedSid | Where-Object { $_.ProcessId -eq $process.ProcessId })
            if ($current.Count -ne 1) { continue }
            if ($current[0].CreationDate -ne $process.CreationDate) { throw 'Companion process identity changed; no process was stopped.' }
            try { Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop }
            catch {
                $verified = @(Get-OwnedCompanionProcesses $ExpectedSid | Where-Object { $_.ProcessId -eq $process.ProcessId })
                if ($verified.Count -eq 0) { continue }
                if ($verified.Count -ne 1 -or $verified[0].CreationDate -ne $process.CreationDate) {
                    throw 'Companion process identity changed; no native stop was attempted.'
                }
                try { Stop-NativeVerifiedCompanion $verified[0] }
                catch {
                    $nativeFailure = $_
                    # Termination can finish between verification and opening the native handle.
                    # A fresh absence is a drained process, not permission to ignore access denial
                    # for a live/reused PID. The next loop still verifies every listener/owner.
                    $remaining = @(Get-CimInstance Win32_Process -Filter ("ProcessId=" + [int]$process.ProcessId) -ErrorAction Stop)
                    if ($remaining.Count -ne 0) { throw $nativeFailure }
                }
            }
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    # A verified termination may consume the drain deadline. Confirm the final state once;
    # never report a live writer merely because the last empty observation arrived late.
    if (@(Get-OwnedCompanionProcesses $ExpectedSid).Count -eq 0) { Wait-PortFree; return }
    throw 'Companion did not stop; no account backup was started.'
}
function Copy-AtomicRuntimeArtifact([string] $Source, [string] $Destination) {
    $temporary = $Destination + '.stage-' + [Guid]::NewGuid().ToString('N')
    try {
        Copy-Item -LiteralPath $Source -Destination $temporary -Force
        if ((Get-ArtifactSha256 $Source) -ne (Get-ArtifactSha256 $temporary)) { throw 'Staged runtime artifact verification failed.' }
        if (Test-Path -LiteralPath $Destination -PathType Leaf) { [IO.File]::Replace($temporary, $Destination, [NullString]::Value) }
        else { [IO.File]::Move($temporary, $Destination) }
    } finally {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) { Remove-Item -LiteralPath $temporary -Force }
    }
}
function Invoke-ProcessManagedUpdate {
    if (-not $Unattended -or -not $task -or $ConfigureCompanionTask -or $ResumeBackup) {
        throw 'UserProcess requires an unattended update of the existing enabled installation, without task reconfiguration or legacy ResumeBackup.'
    }
    $access = & "$PSScriptRoot\enable-user-updates.ps1" -CheckOnly -PassThru
    $expectedSid = $access.Sid.Value
    if (-not $expectedSid) { throw 'Current-user limited task scope was not established.' }
    $null = Get-OwnedCompanionProcesses $expectedSid
    if (-not $SkipBuild) {
        $jdk = @($env:JAVA_HOME, (Split-Path -Parent (Split-Path -Parent $java)), 'C:\Program Files\Java\jdk-23') |
            Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe') -PathType Leaf) } | Select-Object -First 1
        if (-not $jdk) { throw 'Building requires an installed JDK.' }
        $env:JAVA_HOME = $jdk
        & "$PSScriptRoot\build.ps1"
        if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE; installation was not changed." }
    }
    Assert-CompanionLifecycleGuard $companionBuilt
    if (-not (Test-Path -LiteralPath $built -PathType Leaf)) { throw 'Plugin artifact is absent.' }
    $lock = [IO.File]::Open((Join-Path $runtime 'installation.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $changed = $false; $committed = $false
    try {
        Assert-InstalledPair
        $task = Get-CompanionTask; Assert-Task $task
        $before = & "$PSScriptRoot\enable-user-updates.ps1" -CheckOnly -PassThru
        if ($before.Sid.Value -ne $expectedSid -or $before.Xml -cne $access.Xml -or $before.Sddl -cne $access.Sddl) {
            throw 'Companion task scope changed before the update.'
        }
        $null = Get-OwnedCompanionProcesses $expectedSid
        $backup = Join-Path $dataRoot ('backups\' + (Get-Date).ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
        New-Item -ItemType Directory -Path $backup | Out-Null
        $savedTaskDefinition = Export-ScheduledTask -TaskName $taskName -TaskPath '\'
        $savedTaskDefinition | Set-Content -LiteralPath (Join-Path $backup 'companion-task.xml') -Encoding UTF8
        $before.Sddl | Set-Content -LiteralPath (Join-Path $backup 'companion-task.sddl') -Encoding UTF8
        $artifacts = @(@($installed,'osrs-flipping-friend.jar'), @($companionInstalled,'flipping-friend-companion.jar'), @($manifestPath,'installation.json'))
        $hashes = @{}
        foreach ($artifact in $artifacts) {
            $hashes[$artifact[1]] = Get-ArtifactSha256 $artifact[0]
            $saved = Join-Path $backup $artifact[1]
            Copy-Item -LiteralPath $artifact[0] -Destination $saved -Force
            if ((Get-ArtifactSha256 $saved) -ne $hashes[$artifact[1]]) { throw 'Original runtime backup verification failed.' }
        }
        # First cutover: old code did not honor installation.lock. Replace it atomically, then
        # drain every old process again before copying any account files. Future scheduled starts
        # now hit the startup gate before tokens, stores or housekeeping can be touched.
        Stop-OwnedCompanionProcesses $expectedSid
        Copy-AtomicRuntimeArtifact $companionBuilt $companionInstalled
        $changed = $true
        Stop-OwnedCompanionProcesses $expectedSid
        Copy-AccountTrees $backup
        Assert-UnattendedBackup $backup $companionInstalled $savedTaskDefinition $hashes['flipping-friend-companion.jar']
        $after = & "$PSScriptRoot\enable-user-updates.ps1" -CheckOnly -PassThru
        if ($after.Xml -cne $before.Xml -or $after.Sddl -cne $before.Sddl) { throw 'Companion task changed during backup.' }
        Copy-AtomicRuntimeArtifact $built $installed
        @{ schemaVersion=1; checkout=$root; backupDirectory=$backup; installedAtUtc=[DateTime]::UtcNow.ToString('o')
            pluginSha256=(Get-ArtifactSha256 $installed); companionSha256=(Get-ArtifactSha256 $companionInstalled)
            processLifecycleVersion=1
        } | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding UTF8
        Assert-InstalledPair
        $committed = $true
        Write-Host "Backup verified: $backup"
        Write-Host 'Protected startup task and permissions unchanged; companion maintenance uses this user process.'
    } catch {
        $failure = $_
        if ($changed -and -not $committed) {
            try {
                Stop-OwnedCompanionProcesses $expectedSid
                # Restore executable artifacts only, never account files or databases.
                foreach ($artifact in $artifacts) {
                    Copy-AtomicRuntimeArtifact (Join-Path $backup $artifact[1]) $artifact[0]
                    if ((Get-ArtifactSha256 $artifact[0]) -ne $hashes[$artifact[1]]) { throw 'Runtime artifact rollback mismatch.' }
                }
                Write-Host 'Original runtime pair and manifest restored; account data was not restored or changed by the installer.'
            } catch { throw "Update failed; runtime artifact rollback also failed: $($_.Exception.Message). Preserve $backup and inspect status." }
        }
        throw $failure
    } finally { $lock.Dispose() }
    Start-Companion (Get-CompanionTask) $java -Direct
    Write-Host 'Update applied. Both installed JARs verified. Normal-user process lifecycle enabled.'
}
