#!/usr/bin/python3
"""Snapshot SQLite through its backup API, verify a restore, encrypt, then upload privately."""
from contextlib import closing
import datetime
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess
import tarfile
import tempfile


def snapshot(source, destination):
    with closing(sqlite3.connect(source.resolve().as_uri() + '?mode=ro', uri=True)) as read:
        with closing(sqlite3.connect(destination)) as target:
            read.backup(target)
            if target.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                raise RuntimeError('Snapshot integrity refused')


def main():
    import boto3
    configuration = json.loads(Path('/etc/flipping-friend/spaces.json').read_text())
    root = Path('/var/lib/flipping-friend')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    with tempfile.TemporaryDirectory(prefix='ff-backup-') as temporary:
        temporary = Path(temporary)
        snapshot(root / 'pilot-evidence.db', temporary / 'pilot-evidence.db')
        shutil.copy2(temporary / 'pilot-evidence.db', temporary / 'restore.db')
        with closing(sqlite3.connect(temporary / 'restore.db')) as restored:
            if restored.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                raise RuntimeError('Disposable restore integrity refused')
        original = hashlib.sha256((temporary / 'pilot-evidence.db').read_bytes()).hexdigest()
        if original != hashlib.sha256((temporary / 'restore.db').read_bytes()).hexdigest():
            raise RuntimeError('Disposable restore differs')
        (temporary / 'manifest.json').write_text(json.dumps({'databaseSha256': original, 'createdAt': stamp, 'restoreVerified': True}))
        with tarfile.open(temporary / 'backup.tar.gz', 'w:gz') as archive:
            archive.add(temporary / 'pilot-evidence.db', arcname='pilot-evidence.db')
            archive.add(temporary / 'manifest.json', arcname='manifest.json')
            archive.add('/etc/flipping-friend/server-config.json', arcname='server-config.json')
        encrypted = temporary / 'backup.tar.gz.age'
        subprocess.run(['age', '-R', '/etc/flipping-friend/backup-recipient.pub', '-o', str(encrypted), str(temporary / 'backup.tar.gz')], check=True)
        client = boto3.client('s3', endpoint_url='https://sfo2.digitaloceanspaces.com', region_name='sfo2',
            aws_access_key_id=configuration['access_key'], aws_secret_access_key=configuration['secret_key'])
        # Retention is on backup bundles only, never original canonical evidence.
        # Keep room below the included allowance, including a daily copy of this bundle.
        used=sum(row['Size'] for page in client.get_paginator('list_objects_v2').paginate(Bucket=configuration['bucket'])
                 for row in page.get('Contents',[]))
        if used + 2*encrypted.stat().st_size > 200*1024**3:
            raise RuntimeError('Backup quota reached; original evidence retained, no paid expansion authorized')
        key = 'hourly/' + stamp + '.tar.gz.age'
        client.upload_file(str(encrypted), configuration['bucket'], key, ExtraArgs={'ACL': 'private'})
        if client.head_object(Bucket=configuration['bucket'], Key=key)['ContentLength'] != encrypted.stat().st_size:
            raise RuntimeError('Object backup size differs')
        daily='daily/'+stamp[:8]+'.tar.gz.age'
        if not client.list_objects_v2(Bucket=configuration['bucket'],Prefix=daily,MaxKeys=1).get('KeyCount',0):
            client.copy_object(Bucket=configuration['bucket'],Key=daily,
                CopySource={'Bucket':configuration['bucket'],'Key':key},ACL='private')
        (root / 'last-backup.json').write_text(json.dumps({'createdAt': stamp, 'key': key, 'restoreVerified': True, 'encrypted': True}))
    print('Consistent evidence snapshot, disposable restore and encrypted object upload verified')


if __name__ == '__main__':
    main()
