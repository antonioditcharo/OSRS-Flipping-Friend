"""Private, budget-checked pilot deployment. Secrets stay in ignored local storage and stdin."""
import argparse
import base64
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import secrets
import shlex
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / 'local-data/hosted-pilot/private'
STATE = PRIVATE / 'deployment.json'
SSH = Path(os.environ.get('WINDIR', '/usr')) / 'System32/OpenSSH/ssh.exe'
KEYGEN = SSH.with_name('ssh-keygen.exe')
SCP = SSH.with_name('scp.exe')
NAME = 'flipping-friend-private-pilot'


def api(path, data=None, method=None):
    request = urllib.request.Request('https://api.digitalocean.com/v2/' + path,
        data=None if data is None else json.dumps(data).encode(),
        headers={'Authorization': 'Bearer ' + os.environ['FF_DO_TOKEN'],
                 'Content-Type': 'application/json'}, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response) if response.status != 204 else {}
    except urllib.error.HTTPError as error:
        # Response bodies from credential creation must never reach logs.
        try:
            details=json.loads(error.read()).get('message','')
        except Exception:
            details=''
        if len(details)>200 or 'PRIVATE KEY' in details or 'dop_v1_' in details:details='details omitted'
        save(PRIVATE.parent / 'last-api-refusal.json',{'method':request.get_method(),'path':path.split('?')[0],'status':error.code,'message':details})
        raise RuntimeError(f'DigitalOcean {request.get_method()} {path.split("?")[0]} refused: HTTP {error.code}: {details}') from None


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.temporary')
    temporary.write_text(json.dumps(value, indent=2), encoding='utf-8')
    temporary.replace(path)


def quote():
    sizes = api('sizes?per_page=200')['sizes']
    selected = next(s for s in sizes if s['slug'] == 's-2vcpu-4gb')
    if not selected['available'] or 'sfo2' not in selected['regions']:
        raise RuntimeError('Approved San Francisco size is unavailable; no automatic substitution')
    monthly = round(selected['price_monthly'] * 1.3 + 5, 2)
    if monthly > 40:
        raise RuntimeError('Base quote exceeds the allowance reserved for tax and incidental usage')
    value = {'size': selected['slug'], 'region': 'sfo2', 'compute': selected['price_monthly'],
             'dailyBackup': round(selected['price_monthly'] * .3, 2), 'spaces': 5,
             'baseMonthly': monthly, 'ceiling': 50,
             'quotedAt': dt.datetime.now(dt.timezone.utc).isoformat()}
    save(PRIVATE.parent / 'quote.json', value)
    print(json.dumps(value))
    return value


def key(name):
    path = PRIVATE / name
    if not path.exists():
        subprocess.run([str(KEYGEN), '-q', '-t', 'ed25519', '-N', '', '-C', 'flipping-friend-pilot', '-f', str(path)], check=True)
    return path, path.with_suffix('.pub').read_text().strip()


def provision():
    quoted = quote()
    resume=json.loads(STATE.read_text()) if STATE.exists() else None
    if resume and 'dropletId' in resume:
        print('Existing pilot receipt retained; use status/deploy. No duplicate server created.')
        return
    if any(d['name'] == NAME for d in api('droplets?per_page=200')['droplets']):
        raise RuntimeError('A same-name server exists without this receipt; refusing to infer ownership')
    PRIVATE.mkdir(parents=True, exist_ok=True)
    client_key, client_pub = key('pilot-client-ed25519')
    host_key, host_pub = key('pilot-host-ed25519')
    backup_key, backup_pub = key('backup-ed25519')
    installation = resume["installationId"] if resume else secrets.token_hex(16)
    device_token = json.loads((PRIVATE/"client-config.json").read_text())["token"] if resume else secrets.token_urlsafe(48)
    account_root = Path.home() / '.runelite/osrs-flipping-friend/companion/accounts'
    import re
    accounts = [p.name for p in account_root.iterdir() if re.fullmatch(r'rs-[0-9a-f]{64}', p.name)
                and (p / 'flipping-friend.db').is_file()]
    if not accounts:
        raise RuntimeError('No explicitly scoped local account database exists')
    config = {'port': 38888, 'dataDirectory': '/var/lib/flipping-friend',
        'devices': [{'userId': 'owner', 'installationId': installation,
                     'tokenSha256': hashlib.sha256(device_token.encode()).hexdigest(), 'accounts': accounts}]}
    save(PRIVATE / 'server-config.json', config)
    save(PRIVATE / 'client-config.json', {'enabled': True, 'shadow': True, 'recommendations': False,
        'coordination': False, 'automaticRecovery': True, 'endpoint': 'http://127.0.0.1:38888',
        'token': device_token, 'installationId': installation, 'generation': 1})
    with urllib.request.urlopen('https://api.ipify.org', timeout=15) as response:
        client_ip = response.read(64).decode().strip()
    import ipaddress
    ipaddress.IPv4Address(client_ip)
    firewall = {'id':resume['firewallId']} if resume else api('firewalls', {'name': NAME, 'inbound_rules': [
        {'protocol': 'tcp', 'ports': '22', 'sources': {'addresses': [client_ip + '/32']}}],
        'outbound_rules': [{'protocol': 'tcp', 'ports': '1-65535', 'destinations': {'addresses': ['0.0.0.0/0', '::/0']}},
            {'protocol': 'udp', 'ports': '1-65535', 'destinations': {'addresses': ['0.0.0.0/0', '::/0']}}]})['firewall']
    def file(path, contents, mode='0600'):
        return {'path': path, 'encoding': 'b64', 'content': base64.b64encode(contents.encode()).decode(), 'permissions': mode}
    cloud = {'package_update': True, 'packages': ['openjdk-17-jre-headless', 'python3-boto3', 'age', 'ca-certificates'],
        'disable_root': False, 'ssh_pwauth': False, 'ssh_authorized_keys': [client_pub],
        'ssh_keys': {'ed25519_private': host_key.read_text(), 'ed25519_public': host_pub},
        'write_files': [file('/etc/flipping-friend/server-config.json', json.dumps(config)),
            file('/etc/flipping-friend/backup-recipient.pub', backup_pub + '\n')],
        'runcmd': [['useradd', '--system', '--home', '/var/lib/flipping-friend', '--shell', '/usr/sbin/nologin', 'flipping-friend'],
            ['install', '-d', '-o', 'flipping-friend', '-g', 'flipping-friend', '/var/lib/flipping-friend'],
            ['install', '-d', '/opt/flipping-friend/releases'],
            ['chown', 'flipping-friend:flipping-friend', '/etc/flipping-friend/server-config.json']]}
    known_keys=api('account/keys')['ssh_keys']
    ssh_key=next((k for k in known_keys if k['public_key'].split()[:2]==client_pub.split()[:2]),None)
    if ssh_key is None:ssh_key=api('account/keys',{'name':NAME,'public_key':client_pub})['ssh_key']
    # Store recovery metadata BEFORE the paid mutation. A failed response must never trigger a duplicate purchase.
    receipt = {'name': NAME, 'firewallId': firewall['id'], 'installationId': installation,
               'quote': quoted, 'hostPublicKey': host_pub, 'clientAddress': client_ip,
               'status': 'PROVISIONING', 'bucket': 'ff-pilot-' + installation}
    save(STATE, receipt)
    droplet = api('droplets', {'name': NAME, 'region': 'sfo2', 'size': quoted['size'],
        'image': 'ubuntu-24-04-x64', 'ssh_keys':[ssh_key['id']], 'backups': True, 'backup_policy': {'plan': 'daily', 'hour': 0},
        'user_data': '#cloud-config\n' + json.dumps(cloud)})['droplet']
    receipt['dropletId'] = droplet['id'];receipt['status'] = 'CREATED';save(STATE, receipt)
    api(f"firewalls/{firewall['id']}/droplets", {'droplet_ids': [droplet['id']]})
    print('Private pilot server created with daily backups. Runtime is not activated.')


def state():
    result = json.loads(STATE.read_text())
    if 'dropletId' not in result:
        # Recover only an exact private name created by this recorded attempt, never purchase twice.
        matching = [d for d in api('droplets?per_page=200')['droplets'] if d['name'] == result['name']]
        if len(matching) != 1:
            raise RuntimeError('Provisioning receipt requires inspection; no duplicate creation attempted')
        result['dropletId'] = matching[0]['id'];save(STATE, result)
    droplet = api(f"droplets/{result['dropletId']}")['droplet']
    if droplet['name'] != result['name'] or droplet['size_slug'] != result['quote']['size']:
        raise RuntimeError('Server identity or budget changed')
    public = [n['ip_address'] for n in droplet['networks']['v4'] if n['type'] == 'public']
    if len(public) != 1:
        raise RuntimeError('Server public address is not ready')
    result['address'] = public[0];save(STATE, result)
    (PRIVATE / 'known_hosts').write_text(public[0] + ' ' + result['hostPublicKey'] + '\n')
    return result


def ssh_arguments(receipt):
    return ['-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10',
            '-o', 'UserKnownHostsFile="' + (PRIVATE / 'known_hosts').as_posix() + '"', '-i', str(PRIVATE / 'pilot-client-ed25519'),
            'root@' + receipt['address']]


def remote(receipt, command, data=None):
    return subprocess.run([str(SSH), *ssh_arguments(receipt), command], input=data, text=True, check=True, capture_output=True).stdout


def deploy(receipt):
    jar = ROOT / 'companion/build/libs/flipping-friend-companion.jar'
    if not jar.is_file():
        raise RuntimeError('Build and verify the companion artifact first')
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    print(remote(receipt, 'cloud-init status --wait').strip())
    archive = PRIVATE / 'deployment.tar'
    import tarfile
    with tarfile.open(archive, 'w') as bundle:
        bundle.add(jar, arcname='companion.jar')
        for name in ('flipping-friend-pilot.service', 'pilot-backup.py', 'flipping-friend-backup.service', 'flipping-friend-backup.timer'):
            bundle.add(Path(__file__).parent / name, arcname=name)
    subprocess.run([str(SCP), *ssh_arguments(receipt)[:-1], str(archive), 'root@' + receipt['address'] + ':/root/ff-deployment.tar'], check=True, capture_output=True)
    command = ('install -d /root/ff-deployment && tar -xf /root/ff-deployment.tar -C /root/ff-deployment && '
        'test "$(sha256sum /root/ff-deployment/companion.jar | cut -d " " -f 1)" = ' + shlex.quote(digest) + ' && '
        'install -m 0644 /root/ff-deployment/companion.jar /opt/flipping-friend/releases/' + digest + '.jar && '
        'if test -L /opt/flipping-friend/current.jar; then cp -P /opt/flipping-friend/current.jar /opt/flipping-friend/previous.jar; fi && '
        'ln -sfn releases/' + digest + '.jar /opt/flipping-friend/current.jar && '
        'install -m 0644 /root/ff-deployment/flipping-friend-*.service /root/ff-deployment/flipping-friend-*.timer /etc/systemd/system/ && '
        'install -m 0755 /root/ff-deployment/pilot-backup.py /opt/flipping-friend/pilot-backup.py && '
        'systemctl daemon-reload && systemctl enable --now flipping-friend-pilot && systemctl restart flipping-friend-pilot')
    remote(receipt, command)
    receipt['artifactSha256'] = digest;receipt['status'] = 'DEPLOYED';save(STATE, receipt)
    print('Hosted artifact deployed; financial authority remains local.')


def spaces(receipt):
    sys.path.insert(0,str(PRIVATE.parent/'pythondeps'))
    import boto3
    from botocore.config import Config
    bootstrap=PRIVATE/'spaces-bootstrap.json'
    if not bootstrap.exists():
        save(bootstrap,api('spaces/keys',{'name':NAME+'-bootstrap','grants':[{'bucket':'','permission':'fullaccess'}]})['key'])
    credentials=json.loads(bootstrap.read_text())
    client=boto3.client('s3',endpoint_url='https://sfo2.digitaloceanspaces.com',region_name='sfo2',
        aws_access_key_id=credentials['access_key'],aws_secret_access_key=credentials['secret_key'],config=Config(s3={'addressing_style':'virtual'}))
    names={b['Name'] for b in client.list_buckets()['Buckets']}
    if receipt['bucket'] not in names:client.create_bucket(Bucket=receipt['bucket'],ACL='private')
    client.put_bucket_lifecycle_configuration(Bucket=receipt['bucket'],LifecycleConfiguration={'Rules':[
        {'ID':'legacy-recovery-bundles','Status':'Enabled','Filter':{'Prefix':'evidence/'},'Expiration':{'Days':30}},
        {'ID':'hourly-recovery-bundles','Status':'Enabled','Filter':{'Prefix':'hourly/'},'Expiration':{'Days':2}},
        {'ID':'daily-recovery-bundles','Status':'Enabled','Filter':{'Prefix':'daily/'},'Expiration':{'Days':30}}]})
    limited=PRIVATE/'spaces-server.json'
    if not limited.exists():
        scoped=api('spaces/keys',{'name':NAME+'-backup','grants':[{'bucket':receipt['bucket'],'permission':'readwrite'}]})['key']
        scoped['bucket']=receipt['bucket'];save(limited,scoped)
    # Server receives only the key restricted to this backup bucket; the bootstrap key stays local.
    command="python3 -c "+shlex.quote("import sys,os;from pathlib import Path;p=Path('/etc/flipping-friend/spaces.json');p.write_text(sys.stdin.read());os.chmod(p,0o600)")
    remote(receipt,command,limited.read_text())
    receipt['spacesConfigured']=True;save(STATE,receipt)
    print('Private backup bucket and bucket-scoped server credential configured')


def main():
    mode = argparse.ArgumentParser();mode.add_argument('mode', choices=['quote', 'provision', 'deploy', 'status', 'tunnel', 'backup', 'spaces'])
    selected = mode.parse_args().mode
    if selected == 'quote':quote();return
    if selected == 'provision':provision();return
    receipt = state()
    if selected == 'status':
        print(json.dumps({'status': receipt['status'], 'dropletId': receipt['dropletId'], 'baseMonthly': receipt['quote']['baseMonthly']}))
    elif selected == 'deploy':deploy(receipt)
    elif selected == 'spaces':spaces(receipt)
    elif selected == 'tunnel':
        (PRIVATE/'tunnel.enabled').touch()
        command = [sys.executable,str(Path(__file__).with_name('pilot-tunnel.py'))]
        child_env=os.environ.copy();child_env.pop('FF_DO_TOKEN',None)
        with (PRIVATE / 'tunnel.log').open('ab') as log:
            process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                env=child_env,creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        save(PRIVATE / 'tunnel.json', {'pid': process.pid, 'address': receipt['address']})
        print('Private encrypted tunnel started; local trading does not depend on it.')
    else:
        print(remote(receipt, 'systemctl start flipping-friend-backup.service && systemctl is-active flipping-friend-pilot').strip())


if __name__ == '__main__':
    try:main()
    except Exception as error:
        print(str(error), file=sys.stderr);sys.exit(1)
