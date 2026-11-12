import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import backfill_rates as br  # noqa: E402

CATALOG = b'''<?xml version="1.0"?><Valuta><Item ID="R01235"><Nominal>1</Nominal><ISO_Char_Code>USD</ISO_Char_Code></Item>
<Item ID="R01015"><Nominal>1000</Nominal><ISO_Char_Code>ATS</ISO_Char_Code></Item><Item ID="R09999"><ISO_Char_Code></ISO_Char_Code></Item></Valuta>'''

DYNAMIC = '''<?xml version="1.0"?><ValCurs ID="R01235"><Record Date="03.02.2026" Id="R01235"><Nominal>1</Nominal><Value>77,0223</Value></Record>
<Record Date="04.02.2026" Id="R01235"><Nominal>100</Nominal><Value>7 698,17</Value></Record></ValCurs>'''.encode()


class BackfillTest(unittest.TestCase):
    def test_catalog_maps_iso_codes_to_ids(self):
        self.assertEqual({"USD": ["R01235"], "ATS": ["R01015"]}, br.parse_catalog(CATALOG))

    def test_dynamic_divides_by_nominal(self):
        rates = br.parse_dynamic(DYNAMIC)
        self.assertAlmostEqual(77.0223, rates["2026-02-03"])
        self.assertAlmostEqual(76.9817, rates["2026-02-04"])

    def test_only_dates_before_first_known_rate(self):
        rows = br.missing_rows("USD", {"2026-02-03": 77.0, "2026-09-20": 90.0}, "2026-09-10")
        self.assertEqual(["2026-02-03"], [row["rate_date"] for row in rows])
        self.assertEqual({"base": "RUB", "quote": "USD", "source": "cbr"}, {k: rows[0][k] for k in ("base", "quote", "source")})
        self.assertEqual(2, len(br.missing_rows("USD", {"2026-02-03": 77.0, "2026-09-20": 90.0}, None)))

    def test_dynamic_url_format(self):
        import datetime as dt
        url = br.dynamic_url("R01235", dt.date(2026, 2, 1), dt.date(2026, 2, 5))
        self.assertIn("date_req1=01%2F02%2F2026", url)
        self.assertIn("VAL_NM_RQ=R01235", url)


if __name__ == "__main__":
    unittest.main()
