import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import check_migrations as c  # noqa: E402

EXISTING = ["supabase/migrations/0021_a.sql", "supabase/migrations/0022_b.sql"]


def run(changes, sources, labels=()):
    return c.check(changes, EXISTING, lambda p: sources[p], set(labels))


class MigrationGuardTest(unittest.TestCase):
    def test_safe_new_migration_passes(self):
        sql = "alter table public.x add column y int;\nupdate public.x set y = 1 where id = 2;"
        self.assertEqual([], run([("A", "supabase/migrations/0023_c.sql")], {"supabase/migrations/0023_c.sql": sql}))

    def test_editing_released_migration_fails(self):
        self.assertEqual(1, len(run([("M", "supabase/migrations/0022_b.sql")], {})))

    def test_deleting_released_migration_fails(self):
        self.assertEqual(1, len(run([("D", "supabase/migrations/0021_a.sql")], {})))

    def test_number_gap_and_duplicate_fail(self):
        self.assertEqual(1, len(run([("A", "supabase/migrations/0025_c.sql")], {"supabase/migrations/0025_c.sql": ""})))
        self.assertEqual(1, len(run([("A", "supabase/migrations/0022_c.sql")], {"supabase/migrations/0022_c.sql": ""})))

    def test_destructive_without_label_fails_and_with_label_passes(self):
        path = "supabase/migrations/0023_c.sql"
        for sql in [
            "drop table public.x;",
            "alter table public.x drop column y;",
            "truncate public.x;",
            "delete from public.x;",
            "update public.x set y = 1;",
            "update public.debts d\nset opened_on = current_date;",
            "alter table public.x alter column y type text;",
        ]:
            with self.subTest(sql=sql):
                self.assertEqual(1, len(run([("A", path)], {path: sql})))
                self.assertEqual([], run([("A", path)], {path: sql}, labels=["destructive-ok"]))

    def test_comment_and_constraint_drop_are_not_destructive(self):
        path = "supabase/migrations/0023_c.sql"
        sql = "-- drop table x\nalter table public.x drop constraint x_fkey;\ndelete from public.x where id = 1;"
        self.assertEqual([], run([("A", path)], {path: sql}))

    def test_owner_bound_references_migration_is_not_destructive(self):
        path = Path(__file__).resolve().parents[2] / "supabase/migrations/0023_owner_bound_references.sql"
        self.assertEqual([], c.destructive(path.read_text(encoding="utf-8")))

    def test_files_outside_migrations_are_ignored(self):
        self.assertEqual([], run([("M", "supabase/tests/02_isolation.sql")], {}))


if __name__ == "__main__":
    unittest.main()
