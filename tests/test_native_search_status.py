#!/usr/bin/env python3
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'stack.kotlin/test-runner/src/main/kotlin/org/webservices/testrunner/framework/ServiceClient.kt'

class NativeSearchStatusTests(unittest.TestCase):
    def test_search_never_succeeds_after_native_missing_index(self):
        text = SOURCE.read_text()
        self.assertNotIn('return SearchResult(true', text)
        self.assertNotIn('emptySearchResult', text)
        self.assertIn('response.status == HttpStatusCode.OK', text)

    def test_explicit_safe_index_keeps_original_default(self):
        text = SOURCE.read_text()
        self.assertIn('indexName: String = "knowledge"', text)
        self.assertIn('matches(indexName)', text)
        self.assertIn('/$indexName/_search', text)

if __name__ == '__main__':
    unittest.main()
