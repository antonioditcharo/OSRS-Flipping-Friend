"""Download and decrypt an encrypted private backup into a NEW disposable local-data directory."""
from contextlib import closing
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tarfile
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[2]
PRIVATE=ROOT/'local-data/hosted-pilot/private'
AGE_SHA='f48d8f8f9ebe903ab5027ed067652f2cc1db94bc206976430133b905dcd8e8c7'


def main(destination):
    allowed=(ROOT/'local-data').resolve();destination=destination.resolve()
    if allowed not in destination.parents or destination.exists():raise ValueError('New disposable local-data path required')
    sys.path.insert(0,str(PRIVATE.parent/'pythondeps'))
    import boto3
    config=json.loads((PRIVATE/'spaces-server.json').read_text())
    client=boto3.client('s3',endpoint_url='https://sfo2.digitaloceanspaces.com',region_name='sfo2',
        aws_access_key_id=config['access_key'],aws_secret_access_key=config['secret_key'])
    rows=[row for page in client.get_paginator('list_objects_v2').paginate(Bucket=config['bucket']) for row in page.get('Contents',[])]
    latest=max(rows,key=lambda row:row['LastModified'])
    if latest['Size']*4+1024**3>shutil.disk_usage(destination.parent).free:raise ValueError('Insufficient disposable restore space')
    age=PRIVATE/'age-v1.3.2/age.exe'
    if not age.is_file():
        archive=PRIVATE/'age-v1.3.2.zip'
        urllib.request.urlretrieve('https://github.com/FiloSottile/age/releases/download/v1.3.2/age-v1.3.2-windows-amd64.zip',archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest()!=AGE_SHA:raise ValueError('age release hash refused')
        with zipfile.ZipFile(archive) as bundle:
            entry=next(row for row in bundle.infolist() if row.filename=='age/age.exe')
            age.parent.mkdir(parents=True,exist_ok=True);age.write_bytes(bundle.read(entry))
    destination.mkdir(parents=True)
    encrypted=destination/'download.age';plain=destination/'restored.tar'
    client.download_file(config['bucket'],latest['Key'],str(encrypted))
    subprocess.run([str(age),'-d','-i',str(PRIVATE/'backup-ed25519'),'-o',str(plain),str(encrypted)],check=True,capture_output=True)
    with tarfile.open(plain,'r:*') as archive:
        members=archive.getmembers()
        if len(members)!=3 or {member.name for member in members}!={'pilot-evidence.db','manifest.json','server-config.json'} or any(not member.isfile() for member in members):
            raise ValueError('Backup contents refused')
        if sum(row.size for row in members)>shutil.disk_usage(destination).free-512*1024**2:raise ValueError('Restore disk allowance refused')
        for member in members:
            with archive.extractfile(member) as source,(destination/member.name).open('wb') as target:shutil.copyfileobj(source,target)
    manifest=json.loads((destination/'manifest.json').read_text())
    database=destination/'pilot-evidence.db'
    digest=hashlib.sha256(database.read_bytes()).hexdigest()
    if digest!=manifest['databaseSha256']:raise ValueError('Restored database hash refused')
    with closing(sqlite3.connect(database.as_uri()+'?mode=ro',uri=True)) as connection:
        if connection.execute('PRAGMA integrity_check').fetchone()[0]!='ok':raise ValueError('Restored SQLite integrity refused')
        count=connection.execute('SELECT COUNT(*) FROM evidence').fetchone()[0]
    result={'status':'OFF_SERVER_RESTORE_VERIFIED','objectKey':latest['Key'],'databaseSha256':digest,'evidenceRows':count}
    (destination/'restore-verification.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('destination',type=Path);main(parser.parse_args().destination)
