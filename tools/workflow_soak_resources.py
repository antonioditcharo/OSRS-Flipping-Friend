"""Read-only, bounded OS counters for owned synthetic workers; no shell or WMI providers."""
import collections
import ctypes as c
import os
from pathlib import Path
import time

STATES = {1: 'Closed', 2: 'Listen', 3: 'SynSent', 4: 'SynReceived', 5: 'Established',
          6: 'FinWait1', 7: 'FinWait2', 8: 'CloseWait', 9: 'Closing', 10: 'LastAck',
          11: 'TimeWait', 12: 'DeleteTcb', 100: 'Bound'}

def windows(pids):
    from ctypes import wintypes as w
    class Performance(c.Structure):
        _fields_ = [('cb', w.DWORD)] + [(n, c.c_size_t) for n in ('CommitTotal', 'CommitLimit', 'CommitPeak',
            'PhysicalTotal', 'PhysicalAvailable', 'SystemCache', 'KernelTotal', 'KernelPaged', 'KernelNonpaged', 'PageSize')] + [
            ('HandleCount', w.DWORD), ('ProcessCount', w.DWORD), ('ThreadCount', w.DWORD)]
    class ProcessMemory(c.Structure):
        _fields_ = [('cb', w.DWORD), ('PageFaultCount', w.DWORD)] + [(n, c.c_size_t) for n in (
            'PeakWorkingSetSize', 'WorkingSetSize', 'QuotaPeakPagedPoolUsage', 'QuotaPagedPoolUsage',
            'QuotaPeakNonPagedPoolUsage', 'QuotaNonPagedPoolUsage', 'PagefileUsage', 'PeakPagefileUsage', 'PrivateUsage')]
    class Tcp4(c.Structure):
        _fields_ = [(n, w.DWORD) for n in ('state', 'localAddress', 'localPort', 'remoteAddress', 'remotePort', 'pid')]
    class Tcp6(c.Structure):
        _fields_ = [('localAddress', w.BYTE * 16), ('localScope', w.DWORD), ('localPort', w.DWORD),
            ('remoteAddress', w.BYTE * 16), ('remoteScope', w.DWORD), ('remotePort', w.DWORD), ('state', w.DWORD), ('pid', w.DWORD)]
    kernel = c.WinDLL('kernel32', use_last_error=True)
    psapi = c.WinDLL('psapi', use_last_error=True)
    ip = c.WinDLL('iphlpapi', use_last_error=True)
    psapi.GetPerformanceInfo.argtypes = [c.POINTER(Performance), w.DWORD]
    psapi.GetPerformanceInfo.restype = w.BOOL
    perf = Performance(); perf.cb = c.sizeof(perf)
    if not psapi.GetPerformanceInfo(c.byref(perf), perf.cb): raise c.WinError(c.get_last_error())
    result = {'memory': {'AvailableMBytes': perf.PhysicalAvailable * perf.PageSize // 1024**2,
        'CommittedBytes': perf.CommitTotal * perf.PageSize, 'CommitLimitBytes': perf.CommitLimit * perf.PageSize,
        'PoolNonpagedBytes': perf.KernelNonpaged * perf.PageSize, 'systemHandles': perf.HandleCount}, 'processes': []}
    ip.GetExtendedTcpTable.argtypes = [c.c_void_p, c.POINTER(w.DWORD), w.BOOL, w.ULONG, c.c_int, w.ULONG]
    ip.GetExtendedTcpTable.restype = w.DWORD
    states = collections.Counter(); owned = {pid: collections.Counter() for pid in pids}
    for family, row_type in ((2, Tcp4), (23, Tcp6)):
        buffer = c.create_string_buffer(4 * 1024**2); size = w.DWORD(len(buffer))
        error = ip.GetExtendedTcpTable(buffer, c.byref(size), False, family, 5, 0)
        if error: raise OSError(error, 'Bounded TCP table sampling failed')
        count = w.DWORD.from_buffer(buffer).value
        if c.sizeof(w.DWORD) + count * c.sizeof(row_type) > len(buffer): raise ValueError('Invalid native TCP table size')
        for index in range(count):
            row = row_type.from_buffer(buffer, c.sizeof(w.DWORD) + index * c.sizeof(row_type))
            state = STATES.get(row.state, 'State' + str(row.state)); states[state] += 1
            if row.pid in owned: owned[row.pid][state] += 1
    result['sockets'] = [{'Name': key, 'Count': value} for key, value in sorted(states.items())]
    kernel.OpenProcess.argtypes = [w.DWORD, w.BOOL, w.DWORD]; kernel.OpenProcess.restype = w.HANDLE
    kernel.CloseHandle.argtypes = [w.HANDLE]; kernel.CloseHandle.restype = w.BOOL
    kernel.GetProcessHandleCount.argtypes = [w.HANDLE, c.POINTER(w.DWORD)]
    kernel.GetProcessTimes.argtypes = [w.HANDLE] + [c.POINTER(w.FILETIME)] * 4
    psapi.GetProcessMemoryInfo.argtypes = [w.HANDLE, c.POINTER(ProcessMemory), w.DWORD]
    for pid in pids:
        handle = kernel.OpenProcess(0x410, False, pid)
        if not handle:
            if c.get_last_error() == 87:
                result['processes'].append({'Id': pid, 'status': 'EXITED_DURING_SAMPLE'}); continue
            raise c.WinError(c.get_last_error())
        try:
            memory = ProcessMemory(); memory.cb = c.sizeof(memory); handles = w.DWORD()
            times = [w.FILETIME() for _ in range(4)]
            if not psapi.GetProcessMemoryInfo(handle, c.byref(memory), memory.cb): raise c.WinError(c.get_last_error())
            if not kernel.GetProcessHandleCount(handle, c.byref(handles)): raise c.WinError(c.get_last_error())
            if not kernel.GetProcessTimes(handle, *(c.byref(v) for v in times)): raise c.WinError(c.get_last_error())
            ticks = sum((v.dwHighDateTime << 32) + v.dwLowDateTime for v in times[2:])
            result['processes'].append({'Id': pid, 'Handles': handles.value, 'WorkingSet64': memory.WorkingSetSize,
                'PrivateMemorySize64': memory.PrivateUsage, 'CPU': ticks / 10000000, 'sockets': dict(owned[pid])})
        finally:
            kernel.CloseHandle(handle)
    return result

def linux(pids):
    values = {}
    for line in Path('/proc/meminfo').read_text(encoding='ascii').splitlines():
        name, raw = line.split(':', 1); values[name] = int(raw.split()[0]) * 1024
    states = collections.Counter()
    # Linux /proc uses its own state alphabet, not the Windows MIB enumeration.
    names = {1: 'Established', 2: 'SynSent', 3: 'SynReceived', 4: 'FinWait1', 5: 'FinWait2',
             6: 'TimeWait', 7: 'Closed', 8: 'CloseWait', 9: 'LastAck', 10: 'Listen', 11: 'Closing'}
    for name in ('tcp', 'tcp6'):
        with Path('/proc/net', name).open(encoding='ascii') as f:
            raw = f.read(4 * 1024**2 + 1)
        if len(raw) > 4 * 1024**2: raise ValueError('Bounded TCP table sampling failed')
        for line in raw.splitlines()[1:]: states[names.get(int(line.split()[3], 16), 'Unknown')] += 1
    result = {'memory': {'AvailableMBytes': values['MemAvailable'] // 1024**2, 'CommittedBytes': values['Committed_AS'],
                        'PoolNonpagedBytes': None}, 'sockets': [{'Name': k, 'Count': v} for k, v in sorted(states.items())], 'processes': []}
    for pid in pids:
        try:
            status = {}
            for line in Path('/proc', str(pid), 'status').read_text(encoding='ascii').splitlines():
                key, raw = line.split(':', 1); status[key] = raw.strip()
            data = Path('/proc', str(pid), 'stat').read_text(encoding='ascii').rsplit(')', 1)[1].split()
            result['processes'].append({'Id': pid, 'Handles': len(list(Path('/proc', str(pid), 'fd').iterdir())),
                'WorkingSet64': int(status['VmRSS'].split()[0]) * 1024,
                'PrivateDataBytes': int(status['VmData'].split()[0]) * 1024,
                'CPU': (int(data[11]) + int(data[12])) / os.sysconf('SC_CLK_TCK')})
        except FileNotFoundError: result['processes'].append({'Id': pid, 'status': 'EXITED_DURING_SAMPLE'})
    return result

def snapshot(pids):
    start = time.monotonic()
    result = windows(pids) if os.name == 'nt' else linux(pids)
    result['samplerElapsedMs'] = round((time.monotonic() - start) * 1000, 3)
    result['source'] = 'WINDOWS_READ_ONLY_NATIVE_COUNTERS' if os.name == 'nt' else 'LINUX_PROC_READ_ONLY'
    return result
