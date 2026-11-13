import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import check_pr_checklist as c  # noqa: E402

TEMPLATE = (Path(__file__).parents[2] / ".github" / "pull_request_template.md").read_text(encoding="utf-8")


class ChecklistTest(unittest.TestCase):
    def test_docs_only_pr_needs_no_checklist(self):
        self.assertEqual([], c.check("", ["docs/QUALITY.md"]))

    def test_code_pr_without_checklist_fails(self):
        self.assertTrue(c.check("fix", ["composeApp/src/commonMain/kotlin/X.kt"]))

    def test_template_unchecked_fails_and_checked_passes(self):
        files = ["tools/rates/bank_watch.py"]
        self.assertEqual(c.REQUIRED, len(c.check(TEMPLATE, files)))
        self.assertEqual([], c.check(TEMPLATE.replace("- [ ]", "- [x]"), files))


if __name__ == "__main__":
    unittest.main()
