import importlib.util
import os
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('soak_resources', Path(__file__).resolve().parents[1] / 'workflow_soak_resources.py')
resources = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resources)

class WorkflowResourceTest(unittest.TestCase):
    def test_read_only_native_sample_includes_own_process_memory_handles_and_socket_states(self):
        sample = resources.snapshot([os.getpid()])
        self.assertGreaterEqual(sample['samplerElapsedMs'], 0)
        self.assertGreater(sample['memory']['AvailableMBytes'], 0)
        process = sample['processes'][0]
        self.assertEqual(process['Id'], os.getpid())
        self.assertGreater(process['Handles'], 0)
        self.assertGreater(process['WorkingSet64'], 0)
        self.assertGreaterEqual(process['CPU'], 0)
        self.assertTrue(all(row['Count'] > 0 for row in sample['sockets']))

    def test_sampling_has_no_required_processes_or_background_service(self):
        sample = resources.snapshot([])
        self.assertEqual([], sample['processes'])
        self.assertGreater(sample['memory']['CommittedBytes'], 0)

if __name__ == '__main__': unittest.main()
