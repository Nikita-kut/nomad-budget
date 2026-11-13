import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import check_pr_checklist as c  # noqa: E402

TEMPLATE = c.TEMPLATE.read_text(encoding="utf-8")
CODE = ["tools/rates/bank_watch.py"]


class ChecklistTest(unittest.TestCase):
    def test_template_has_ten_items(self):
        self.assertEqual(10, len(c.required_items(TEMPLATE)))

    def test_docs_only_pr_needs_no_checklist(self):
        self.assertEqual([], c.check("", ["docs/QUALITY.md"], TEMPLATE))

    def test_code_pr_without_checklist_fails(self):
        self.assertEqual(10, len(c.check("fix", CODE, TEMPLATE)))

    def test_template_unchecked_fails_and_checked_passes(self):
        self.assertEqual(10, len(c.check(TEMPLATE, CODE, TEMPLATE)))
        self.assertEqual([], c.check(TEMPLATE.replace("- [ ]", "- [x]"), CODE, TEMPLATE))

    def test_random_ticked_boxes_do_not_pass(self):
        body = "\n".join(f"- [x] пункт {i}" for i in range(10))
        self.assertEqual(10, len(c.check(body, CODE, TEMPLATE)))

    def test_unrelated_open_task_does_not_fail(self):
        body = TEMPLATE.replace("- [ ]", "- [x]") + "\n- [ ] потом: обновить скриншоты"
        self.assertEqual([], c.check(body, CODE, TEMPLATE))

    def test_explanation_after_item_is_allowed(self):
        body = TEMPLATE.replace("- [ ]", "- [x]").replace("замены базы", "замены базы")
        first = c.required_items(TEMPLATE)[0]
        body = body.replace(first, first + " — не применимо: только бот")
        self.assertEqual([], c.check(body, CODE, TEMPLATE))


if __name__ == "__main__":
    unittest.main()
