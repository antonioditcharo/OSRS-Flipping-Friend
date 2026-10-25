"""One pinned SSH tunnel per installation, with reconnect and no provider token inheritance."""
import json
import os
from pathlib import Path
import subprocess
import sys
import time

sys.path.insert(0,str(Path(__file__).parent))
import pilot


def main():
    # An OS lease releases on crash; a stale PID file cannot prevent recovery.
    import msvcrt
    with (pilot.PRIVATE/'tunnel.lock').open('a+b') as lock:
        if lock.tell()==0:lock.write(b'0');lock.flush()
        lock.seek(0)
        try:msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        except OSError:return
        receipt=json.loads(pilot.STATE.read_text())
        command=[str(pilot.SSH),*pilot.ssh_arguments(receipt)[:-1],'-N','-o','ExitOnForwardFailure=yes',
                 '-o','ConnectTimeout=10','-o','ServerAliveInterval=15','-o','ServerAliveCountMax=2',
                 '-L','127.0.0.1:38888:127.0.0.1:38888',pilot.ssh_arguments(receipt)[-1]]
        environment=os.environ.copy();environment.pop('FF_DO_TOKEN',None)
        delay=2
        while True:
            # Removing this control file is an explicit operator stop, not a trading dependency.
            if not (pilot.PRIVATE/'tunnel.enabled').exists():return
            started=time.monotonic()
            process=subprocess.Popen(command,stdin=subprocess.DEVNULL,env=environment,
                creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
            while process.poll() is None:
                if not (pilot.PRIVATE/'tunnel.enabled').exists():
                    process.terminate();process.wait(timeout=15);return
                time.sleep(1)
            delay=2 if time.monotonic()-started>60 else min(60,delay*2)
            time.sleep(delay)


if __name__=='__main__':main()
