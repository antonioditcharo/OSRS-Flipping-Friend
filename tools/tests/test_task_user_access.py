"""Native Windows ACL parsing with disposable files and a fake registered task only."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

HARNESS = r'''
$ErrorActionPreference='Stop'
$fixture=$env:FF_TASK_ACCESS_TEST_ROOT
$scenario=Get-Content -Raw (Join-Path $fixture 'scenario.json') | ConvertFrom-Json
. (Join-Path $fixture 'task-user-access.ps1')
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
if($scenario.builtinAdminSid){$sid=[Security.Principal.SecurityIdentifier]::new($sid.AccountDomainSid.Value+'-500')}
$caller=[pscustomobject]@{Sid=$sid;IsAdministrator=[bool]$scenario.admin}
$owner=if($scenario.ownerIsUser){$sid.Value}else{'BA'}
$global:originalSddl="O:$($owner)G:SYD:(A;;FA;;;SY)(A;;FA;;;BA)(A;;FR;;;$($sid.Value))"
if($scenario.alreadyReady){$global:originalSddl="O:$($owner)G:SYD:(A;;FA;;;SY)(A;;FA;;;BA)(A;;FA;;;$($sid.Value))"}
if($scenario.deny){$global:originalSddl="O:$($owner)G:SYD:(D;;FW;;;$($sid.Value))(A;;FA;;;SY)(A;;FA;;;BA)(A;;FR;;;$($sid.Value))"}
# Native task reads already render SID aliases (for example RID 500 as LA). The fake must
# return that same representation before and after SetSecurityDescriptor, including rollback.
$global:originalSddl=([Security.AccessControl.RawSecurityDescriptor]::new($global:originalSddl)).GetSddlForm([Security.AccessControl.AccessControlSections]::All)
$global:currentSddl=$global:originalSddl
$global:setCalls=0;$global:readCalls=0
$java=[Security.SecurityElement]::Escape((Join-Path $fixture 'java.exe'))
$checkout=Join-Path $fixture 'checkout'
$runtime=Join-Path $fixture 'runtime/flipping-friend-companion.jar'
$working=if($scenario.foreignCheckout){Join-Path $fixture 'foreign'}else{$checkout}
$jar=if($scenario.foreignJar){Join-Path $fixture 'other.jar'}else{$runtime}
$user=if($scenario.foreignUser){'S-1-5-21-1-2-3-9999'}else{$sid.Value}
$level=if($scenario.elevated){'HighestAvailable'}else{'LeastPrivilege'}
$logon=if($scenario.passwordLogon){'Password'}else{'InteractiveToken'}
$arguments='-Xmx768m -jar &quot;'+[Security.SecurityElement]::Escape($jar)+'&quot;'
if($scenario.extraArguments){$arguments+=' --unexpected'}
$global:task=[pscustomobject]@{Xml="<Task xmlns=`"http://schemas.microsoft.com/windows/2004/02/mit/task`"><Principals><Principal><UserId>$user</UserId><LogonType>$logon</LogonType><RunLevel>$level</RunLevel></Principal></Principals><Settings><Enabled>false</Enabled></Settings><Actions><Exec><Command>$java</Command><Arguments>$arguments</Arguments><WorkingDirectory>$([Security.SecurityElement]::Escape($working))</WorkingDirectory></Exec></Actions></Task>"}
if($scenario.omitRunLevel){$task.Xml=$task.Xml -replace '<RunLevel>[^<]+</RunLevel>',''}
$task | Add-Member NoteProperty Definition ([pscustomobject]@{Principal=[pscustomobject]@{
    RunLevel=$(if($scenario.elevated -or $scenario.nativeElevated){1}else{0})
    LogonType=$(if($scenario.passwordLogon){1}else{3})}})
$originalXml=$task.Xml
$task | Add-Member ScriptMethod GetSecurityDescriptor {
    param($flags)
    if($flags -ne 7){throw 'Unexpected security descriptor request'}
    $global:readCalls++
    if($scenario.raceDefinitionDuringRead -and $global:readCalls -eq 1){
        $global:task.Xml=$global:task.Xml -replace '<WorkingDirectory>[^<]+</WorkingDirectory>','<WorkingDirectory>C:\foreign</WorkingDirectory>'
    }
    if($scenario.race -and $global:readCalls -eq 2){$global:currentSddl+='(A;;FR;;;BU)'}
    return $global:currentSddl
}
$task | Add-Member ScriptMethod SetSecurityDescriptor {
    param($access,$flags)
    $global:setCalls++
    if($flags -ne 16 -or $access -notmatch '^D:'){throw 'Unexpected permission scope'}
    if($scenario.setDenied){throw 'Synthetic task permission denied'}
    if($scenario.ignoreFirstSet -and $global:setCalls -eq 1){return}
    $old=[Security.AccessControl.RawSecurityDescriptor]::new($global:currentSddl)
    $new=[Security.AccessControl.RawSecurityDescriptor]::new($access)
    $old.DiscretionaryAcl=$new.DiscretionaryAcl
    $global:currentSddl=$old.GetSddlForm([Security.AccessControl.AccessControlSections]::All)
}
$errorText=$null;$ready=$null;$canRepair=$null
try {
    if($scenario.inspect){
        $snapshot=Get-CompanionTaskAccessSnapshot $task $checkout $runtime $caller
        $ready=$snapshot.Ready;$canRepair=$snapshot.CanRepair
    }else{
        Set-CompanionTaskUserAccess $task $checkout $runtime (Join-Path $fixture 'backups') $caller
        $ready=(Get-CompanionTaskAccessSnapshot $task $checkout $runtime $caller).Ready
    }
}catch{$errorText=$_.Exception.Message}
Write-Output ('RESULT='+(@{error=$errorText;ready=$ready;canRepair=$canRepair;setCalls=$global:setCalls;
    originalAclRestored=($global:currentSddl -ceq $global:originalSddl);xmlUnchanged=($task.Xml -ceq $originalXml)}|ConvertTo-Json -Compress))
'''


@unittest.skipUnless(os.name == 'nt', 'Task Scheduler ACLs use Windows security APIs')
class TaskUserAccessTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='friend task access ')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        source = Path(__file__).resolve().parents[1] / 'task-user-access.ps1'
        shutil.copyfile(source, self.root / source.name)
        (self.root / 'java.exe').write_bytes(b'fixture, never executed')
        (self.root / 'harness.ps1').write_text(HARNESS, encoding='utf-8')
        self.shell = os.environ.get('FF_UPDATE_TEST_SHELL') or shutil.which('powershell') or shutil.which('pwsh')

    def run_case(self, **scenario):
        (self.root / 'scenario.json').write_text(json.dumps(scenario), encoding='utf-8')
        result = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-File', str(self.root / 'harness.ps1')],
            env=dict(os.environ, FF_TASK_ACCESS_TEST_ROOT=str(self.root)), capture_output=True, text=True, timeout=20)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.output = result.stdout + result.stderr
        lines = [line for line in result.stdout.splitlines() if line.startswith('RESULT=')]
        self.assertEqual(1, len(lines), self.output)
        return json.loads(lines[0][7:])

    def test_protected_task_is_inspected_without_modification(self):
        value = self.run_case(inspect=True)
        self.assertIsNone(value['error'], self.output)
        self.assertFalse(value['ready']); self.assertFalse(value['canRepair'])
        self.assertEqual(0, value['setCalls']); self.assertFalse((self.root / 'backups').exists())

    def test_read_only_caller_needs_one_approval_without_starting_a_repair(self):
        value = self.run_case()
        self.assertIn('one-time', value['error'], self.output)
        self.assertEqual(0, value['setCalls']); self.assertFalse((self.root / 'backups').exists())

    def assert_successful_repair(self, **scenario):
        value = self.run_case(**scenario)
        self.assertIsNone(value['error'], self.output)
        self.assertTrue(value['ready']); self.assertTrue(value['xmlUnchanged'])
        self.assertEqual(1, value['setCalls'])
        backups = list((self.root / 'backups').iterdir())
        self.assertEqual(1, len(backups))
        receipt = json.loads((backups[0] / 'task-access-verification.json').read_text(encoding='utf-8-sig'))
        self.assertEqual('VERIFIED', receipt['status'])
        self.assertEqual({'companion-task.xml', 'companion-task.sddl', 'task-access-verification.json'},
            {path.name for path in backups[0].iterdir()})
        import hashlib
        for name, key in [('companion-task.xml', 'xmlSha256'), ('companion-task.sddl', 'sddlSha256')]:
            self.assertEqual(receipt[key].lower(), hashlib.sha256((backups[0] / name).read_bytes()).hexdigest())

    def test_task_owner_can_enable_own_maintenance_without_admin(self):
        self.assert_successful_repair(ownerIsUser=True)

    def test_one_admin_repair_preserves_task_definition_and_other_rules(self):
        self.assert_successful_repair(admin=True)

    def test_windows_omitted_xml_run_level_needs_native_least_privilege_proof(self):
        self.assert_successful_repair(admin=True, omitRunLevel=True)

    def test_contradictory_native_run_level_cannot_get_a_permission_grant(self):
        value = self.run_case(admin=True, nativeElevated=True)
        self.assertIsNotNone(value['error'], self.output)
        self.assertEqual(0, value['setCalls']); self.assertFalse((self.root / 'backups').exists())

    def test_already_ready_task_does_not_change_acl_or_create_backup(self):
        value = self.run_case(alreadyReady=True)
        self.assertIsNone(value['error'], self.output)
        self.assertTrue(value['ready']); self.assertEqual(0, value['setCalls'])
        self.assertFalse((self.root / 'backups').exists())

    def test_unrelated_elevated_or_credentialed_tasks_cannot_be_modified(self):
        for flag in ['foreignUser', 'foreignCheckout', 'foreignJar', 'extraArguments', 'elevated', 'passwordLogon', 'deny']:
            with self.subTest(flag=flag):
                value = self.run_case(admin=True, **{flag: True})
                self.assertIsNotNone(value['error'], self.output)
                self.assertEqual(0, value['setCalls']); self.assertFalse((self.root / 'backups').exists())

    def test_task_change_after_backup_prevents_permission_write(self):
        value = self.run_case(admin=True, race=True)
        self.assertIn('changed after inspection', value['error'], self.output)
        self.assertEqual(0, value['setCalls'])

    def test_definition_change_during_inspection_cannot_become_the_approved_snapshot(self):
        value = self.run_case(inspect=True, admin=True, raceDefinitionDuringRead=True)
        self.assertIsNotNone(value['error'], self.output)
        self.assertIn('changed during inspection', value['error'], self.output)
        self.assertEqual(0, value['setCalls']); self.assertFalse((self.root / 'backups').exists())

    def test_permission_write_failure_keeps_original_task_and_verified_backup(self):
        value = self.run_case(admin=True, setDenied=True)
        self.assertIn('Synthetic task permission denied', value['error'], self.output)
        self.assertEqual(1, value['setCalls']); self.assertTrue(value['originalAclRestored'])
        self.assertTrue(value['xmlUnchanged'])

    def test_failed_postcondition_restores_original_acl(self):
        value = self.run_case(admin=True, ignoreFirstSet=True)
        self.assertIn('postcondition failed', value['error'], self.output)
        self.assertEqual(2, value['setCalls']); self.assertTrue(value['originalAclRestored'])
        self.assertTrue(value['xmlUnchanged'])

    def test_builtin_admin_sid_alias_preserves_exact_native_rollback_snapshot(self):
        value = self.run_case(admin=True, builtinAdminSid=True, ignoreFirstSet=True)
        self.assertIn('postcondition failed', value['error'], self.output)
        self.assertEqual(2, value['setCalls']); self.assertTrue(value['originalAclRestored'])
        self.assertTrue(value['xmlUnchanged'])
