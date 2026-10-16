# Task-scoped maintenance permissions. No elevation, process control, account files or game input.
function Get-CompanionTaskCaller {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    [pscustomobject]@{ Sid = $identity.User; IsAdministrator = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) }
}
function Get-CompanionTaskMetadataHash([string] $Path) {
    $stream = [IO.File]::OpenRead($Path)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '') }
    finally { $hasher.Dispose(); $stream.Dispose() }
}
function Get-CompanionTaskAccessSnapshot($Task, [string] $Checkout, [string] $RuntimeJar, $Caller) {
    $taskXml = [string]$Task.Xml
    [xml]$xml = $taskXml
    $ns = [Xml.XmlNamespaceManager]::new($xml.NameTable)
    $ns.AddNamespace('t', 'http://schemas.microsoft.com/windows/2004/02/mit/task')
    $principals = @($xml.SelectNodes('/t:Task/t:Principals/t:Principal', $ns))
    $actions = @($xml.SelectNodes('/t:Task/t:Actions/*', $ns))
    if ($principals.Count -ne 1 -or $actions.Count -ne 1 -or $actions[0].LocalName -ne 'Exec') {
        throw 'Task access repair requires one principal and one Java action.'
    }
    $principal = $principals[0]
    # Windows may omit the default RunLevel from exported XML. Require its typed native value;
    # an absent XML element by itself is never proof of a limited task.
    $nativeLevel = $Task.Definition.Principal.RunLevel
    $nativeLogon = $Task.Definition.Principal.LogonType
    if ($null -eq $nativeLevel -or $nativeLevel -ne 0 -or $null -eq $nativeLogon -or $nativeLogon -ne 3 -or
        ($principal.RunLevel -and $principal.RunLevel -ne 'LeastPrivilege') -or $principal.LogonType -ne 'InteractiveToken') {
        throw 'Task access repair requires a non-elevated, interactive user task.'
    }
    $user = [string]$principal.UserId
    $sid = if ($user -match '^S-1-') { [Security.Principal.SecurityIdentifier]::new($user) }
        else { ([Security.Principal.NTAccount]::new($user)).Translate([Security.Principal.SecurityIdentifier]) }
    if ($sid.Value -ne $Caller.Sid.Value) { throw 'Task principal does not match the current Windows user.' }
    $action = $actions[0]
    foreach ($path in @([string]$action.Command, [string]$action.WorkingDirectory, $Checkout, $RuntimeJar)) {
        if (-not $path -or -not [IO.Path]::IsPathRooted($path)) { throw 'Task access repair requires absolute installation paths.' }
    }
    if ([IO.Path]::GetFileName([string]$action.Command) -notmatch '^javaw?\.exe$' -or
        -not (Test-Path -LiteralPath ([string]$action.Command) -PathType Leaf)) { throw 'Task action is not an existing Java executable.' }
    if ([IO.Path]::GetFullPath([string]$action.WorkingDirectory).TrimEnd('\','/') -ne [IO.Path]::GetFullPath($Checkout).TrimEnd('\','/')) {
        throw 'Task belongs to another checkout; permissions were not changed.'
    }
    if ([string]$action.Arguments -notmatch '^\s*(?:-Xmx[1-9][0-9]*[kKmMgG]\s+)?-jar\s+(?:"(?<quoted>[^"]+)"|(?<bare>\S+))\s*$') {
        throw 'Task action has unsupported arguments.'
    }
    $jar = if ($Matches['quoted']) { $Matches['quoted'] } else { $Matches['bare'] }
    if (-not [IO.Path]::IsPathRooted($jar) -or [IO.Path]::GetFullPath($jar) -ne [IO.Path]::GetFullPath($RuntimeJar)) {
        throw 'Task belongs to another runtime JAR; permissions were not changed.'
    }
    $sddl = $Task.GetSecurityDescriptor(7) # owner, group and DACL; never request SACL privileges
    $descriptor = [Security.AccessControl.RawSecurityDescriptor]::new($sddl)
    if ($null -eq $descriptor.DiscretionaryAcl) { throw 'Task has no restrictive DACL; inspect it manually.' }
    $rights = [long]0
    foreach ($ace in $descriptor.DiscretionaryAcl) {
        # Do not reorder, override or try to interpret denies/conditional/object-specific rules.
        if ($ace -isnot [Security.AccessControl.CommonAce] -or $ace.IsCallback -or
            $ace.AceQualifier -ne [Security.AccessControl.AceQualifier]::AccessAllowed) {
            throw 'Task has unsupported or denying access rules; inspect it manually.'
        }
        if ($ace.SecurityIdentifier.Value -ne $sid.Value -or
            ([int]$ace.AceFlags -band [int][Security.AccessControl.AceFlags]::InheritOnly)) { continue }
        $mask = [long]$ace.AccessMask -band [uint32]::MaxValue
        if ($mask -band 0x10000000) { $mask = $mask -bor 0x1f01ff } # generic all
        if ($mask -band 0x80000000L) { $mask = $mask -bor 0x120089 } # generic read
        if ($mask -band 0x40000000) { $mask = $mask -bor 0x120116 } # generic write
        if ($mask -band 0x20000000) { $mask = $mask -bor 0x1200a0 } # generic execute
        $rights = $rights -bor $mask
    }
    if ($Task.Xml -cne $taskXml -or $Task.Definition.Principal.RunLevel -ne $nativeLevel -or
        $Task.Definition.Principal.LogonType -ne $nativeLogon) { throw 'Task changed during inspection; retry its read-only check.' }
    [pscustomobject]@{
        Xml = $taskXml; Sddl = $sddl; Descriptor = $descriptor; Sid = $sid
        Ready = (($rights -band 0x1201bf) -eq 0x1201bf)
        CanRepair = ($Caller.IsAdministrator -or ($descriptor.Owner -and $descriptor.Owner.Value -eq $sid.Value) -or ($rights -band 0x40000))
    }
}
function Set-CompanionTaskUserAccess($Task, [string] $Checkout, [string] $RuntimeJar, [string] $BackupRoot, $Caller) {
    $before = Get-CompanionTaskAccessSnapshot $Task $Checkout $RuntimeJar $Caller
    if ($before.Ready) { Write-Host 'Normal-user companion maintenance is already enabled.'; return }
    if (-not $before.CanRepair) { throw 'Run the one-time task permission repair as administrator. No task or installed file was changed.' }
    $ancestor = [IO.Path]::GetFullPath($BackupRoot)
    while ($ancestor) {
        if ((Test-Path -LiteralPath $ancestor) -and ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Task permission backup path contains a link.'
        }
        $parent = Split-Path -Parent $ancestor
        if ($parent -eq $ancestor) { break }; $ancestor = $parent
    }
    $backup = Join-Path $BackupRoot ('task-access-' + (Get-Date).ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
    New-Item -ItemType Directory -Path $backup -Force | Out-Null
    $encoding = [Text.UTF8Encoding]::new($false)
    $xmlPath = Join-Path $backup 'companion-task.xml'
    $sddlPath = Join-Path $backup 'companion-task.sddl'
    [IO.File]::WriteAllText($xmlPath, $before.Xml, $encoding)
    [IO.File]::WriteAllText($sddlPath, $before.Sddl, $encoding)
    if ([IO.File]::ReadAllText($xmlPath) -cne $before.Xml -or [IO.File]::ReadAllText($sddlPath) -cne $before.Sddl) {
        throw 'Task permission backup verification failed; permissions were not changed.'
    }
    $receiptPath = Join-Path $backup 'task-access-verification.json'
    $receipt = [ordered]@{ schemaVersion=1; status='BACKUP_VERIFIED'; taskName='FlippingFriendCompanion'; checkout=$Checkout
        xmlSha256=(Get-CompanionTaskMetadataHash $xmlPath)
        sddlSha256=(Get-CompanionTaskMetadataHash $sddlPath) }
    $receipt | ConvertTo-Json | Set-Content -LiteralPath $receiptPath -Encoding UTF8
    Write-Host "Task permission backup verified: $backup"
    Get-ChildItem -LiteralPath $backup -File | ForEach-Object { Write-Host "$($_.FullName)  $($_.Length) bytes" }
    if ($Task.Xml -cne $before.Xml -or $Task.GetSecurityDescriptor(7) -cne $before.Sddl) {
        throw 'Task changed after inspection; permissions were not changed.'
    }
    $updated = [Security.AccessControl.RawSecurityDescriptor]::new($before.Sddl)
    $ace = [Security.AccessControl.CommonAce]::new([Security.AccessControl.AceFlags]::None,
        [Security.AccessControl.AceQualifier]::AccessAllowed, 0x1f01ff, $before.Sid, $false, $null)
    $updated.DiscretionaryAcl.InsertAce(0, $ace)
    $oldAccess = $before.Descriptor.GetSddlForm([Security.AccessControl.AccessControlSections]::Access)
    $newAccess = $updated.GetSddlForm([Security.AccessControl.AccessControlSections]::Access)
    $changed = $false
    try {
        $Task.SetSecurityDescriptor($newAccess, 0x10) # do not auto-add or replace another principal ACE
        $changed = $true
        $after = Get-CompanionTaskAccessSnapshot $Task $Checkout $RuntimeJar $Caller
        if (-not $after.Ready -or $after.Xml -cne $before.Xml -or
            $after.Descriptor.Owner.Value -ne $before.Descriptor.Owner.Value -or
            $after.Descriptor.Group.Value -ne $before.Descriptor.Group.Value -or
            $after.Descriptor.DiscretionaryAcl.Count -ne $before.Descriptor.DiscretionaryAcl.Count + 1) {
            throw 'Task permission postcondition failed.'
        }
        $remaining = [Collections.Generic.List[string]]::new()
        foreach ($existing in $after.Descriptor.DiscretionaryAcl) {
            $binary = [byte[]]::new($existing.BinaryLength); $existing.GetBinaryForm($binary,0)
            $remaining.Add([Convert]::ToBase64String($binary))
        }
        foreach ($existing in $before.Descriptor.DiscretionaryAcl) {
            $binary = [byte[]]::new($existing.BinaryLength); $existing.GetBinaryForm($binary,0)
            if (-not $remaining.Remove([Convert]::ToBase64String($binary))) { throw 'An existing task access rule changed.' }
        }
        $receipt.status = 'VERIFIED'; $receipt | ConvertTo-Json | Set-Content -LiteralPath $receiptPath -Encoding UTF8
        Write-Host 'Normal-user companion maintenance enabled. Task remains non-elevated; its action, triggers and state are unchanged.'
    } catch {
        $failure = $_
        if ($changed) {
            $Task.SetSecurityDescriptor($oldAccess, 0x10)
            if ($Task.GetSecurityDescriptor(7) -cne $before.Sddl) { throw 'Task permission repair failed and original ACL restoration could not be verified.' }
        }
        throw $failure
    }
}
