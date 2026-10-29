import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('workflow_soak', Path(__file__).resolve().parents[1] / 'run-workflow-soak.py')
soak = importlib.util.module_from_spec(spec)
spec.loader.exec_module(soak)

class WorkflowCompletionTest(unittest.TestCase):
    def row(self, tests=3, failures=0, transport=0):
        return {'tests': tests, 'failures': failures, 'fixtureRequests': 7, 'fixtureTransportFailures': transport}

    def test_successful_exit_without_worker_completion_is_not_an_hour(self):
        for output in ('', 'COMPLETED_UNINTERRUPTED 0', 'prefix COMPLETED_UNINTERRUPTED 1'):
            with self.assertRaises(ValueError): soak.summarize_worker(0, [self.row()], output)

    def test_empty_worker_cannot_claim_completion(self):
        with self.assertRaises(ValueError): soak.summarize_worker(0, [], 'COMPLETED_UNINTERRUPTED 0')

    def test_failures_remain_failures_even_with_a_completion_marker(self):
        for code, row in ((1, self.row()), (0, self.row(failures=1)), (0, self.row(transport=1))):
            with self.assertRaises(ValueError): soak.summarize_worker(code, [row], 'COMPLETED_UNINTERRUPTED 1')

    def test_real_completion_retains_all_checks_and_requests(self):
        result = soak.summarize_worker(0, [self.row(), self.row(5)], 'COMPLETED_UNINTERRUPTED 2\n')
        self.assertEqual(8, result['executedChecks'])
        self.assertEqual(7, result['fixtureRequests'])
        self.assertTrue(result['completionMarkerVerified'])

if __name__ == '__main__': unittest.main()
