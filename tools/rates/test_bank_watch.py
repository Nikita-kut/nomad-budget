import datetime as dt
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bank_watch as bw  # noqa: E402

NBSP = " "
MINUS = "−"


def watch(**overrides):
    base = {
        "id": "w1", "provider": "tbank", "from_currency": "RUB", "to_currency": "USD", "category": bw.CATEGORY_EXCHANGE,
        "target": None, "jump_percent": 1.5, "digest_hour": None, "quiet_from": 18, "quiet_to": 10, "timezone": "UTC",
    }
    base.update(overrides)
    return base


def at(hour: int, minute: int = 0, day: int = 10) -> dt.datetime:
    return dt.datetime(2026, 11, day, hour, minute, tzinfo=dt.timezone.utc)


class ParsingTest(unittest.TestCase):
    def test_parse_bank_rates_keeps_buy_and_sell_per_category(self):
        payload = {"payload": {"rates": [
            {"category": "DebitCardsTransfers", "fromCurrency": {"name": "RUB"}, "toCurrency": {"name": "USD"}, "buy": 0.0115, "sell": 0.0122},
            {"category": "DebitCardsOperations", "fromCurrency": {"name": "RUB"}, "toCurrency": {"name": "USD"}, "buy": 0.0118},
            {"fromCurrency": {"name": "RUB"}, "buy": 1},
        ]}}
        rates = bw.parse_bank_rates(payload)
        self.assertEqual({"buy": 0.0115, "sell": 0.0122}, rates["DebitCardsTransfers"])
        self.assertEqual({"buy": 0.0118, "sell": None}, rates["DebitCardsOperations"])
        self.assertEqual(2, len(rates))

    def test_parse_cbr_uses_vunitrate_with_comma_decimals(self):
        xml = (
            '<?xml version="1.0" encoding="windows-1251"?><ValCurs Date="10.11.2026">'
            "<Valute><CharCode>USD</CharCode><Nominal>1</Nominal><Value>82,7536</Value><VunitRate>82,7536</VunitRate></Valute>"
            "<Valute><CharCode>XTS</CharCode><Nominal>10000</Nominal><Value>32,3130</Value><VunitRate>0,0032313</VunitRate></Valute>"
            "<Valute><CharCode>XXX</CharCode><Nominal>100</Nominal><Value>60,80</Value></Valute>"
            "</ValCurs>"
        ).encode("cp1251")
        table = bw.parse_cbr(xml)
        self.assertEqual(1.0, table["RUB"])
        self.assertAlmostEqual(82.7536, table["USD"])
        self.assertAlmostEqual(0.0032313, table["XTS"])
        self.assertAlmostEqual(0.608, table["XXX"])

    def test_cross_rate_goes_through_rub(self):
        table = {"RUB": 1.0, "USD": 80.0, "XTS": 0.004}
        self.assertAlmostEqual(1 / 80, bw.cross_rate(table, "RUB", "USD"))
        self.assertAlmostEqual(80.0, bw.cross_rate(table, "USD", "RUB"))
        self.assertAlmostEqual(20_000.0, bw.cross_rate(table, "USD", "XTS"))
        self.assertIsNone(bw.cross_rate(table, "RUB", "ZZZ"))

    def test_card_rate_drops_placeholder_values(self):
        rates = {bw.CATEGORY_CARD: {"buy": 2.9e-05, "sell": None}}
        self.assertIsNone(bw.card_rate(rates, 0.0115))
        rates = {bw.CATEGORY_CARD: {"buy": 0.0118, "sell": None}}
        self.assertEqual(0.0118, bw.card_rate(rates, 0.0115))
        self.assertEqual(0.0118, bw.card_rate(rates, None))
        self.assertIsNone(bw.card_rate({}, 0.0115))


class QuietHoursTest(unittest.TestCase):
    def test_default_send_window_is_ten_to_eighteen(self):
        self.assertTrue(bw.in_quiet_hours(18, 18, 10))
        self.assertTrue(bw.in_quiet_hours(23, 18, 10))
        self.assertTrue(bw.in_quiet_hours(0, 18, 10))
        self.assertTrue(bw.in_quiet_hours(9, 18, 10))
        self.assertFalse(bw.in_quiet_hours(10, 18, 10))
        self.assertFalse(bw.in_quiet_hours(15, 18, 10))
        self.assertFalse(bw.in_quiet_hours(17, 18, 10))

    def test_overnight_quiet_window_and_disabled(self):
        self.assertTrue(bw.in_quiet_hours(23, 23, 8))
        self.assertTrue(bw.in_quiet_hours(7, 23, 8))
        self.assertFalse(bw.in_quiet_hours(8, 23, 8))
        self.assertFalse(bw.in_quiet_hours(5, 9, 9))

    def test_local_now_uses_zone_and_falls_back_to_moscow(self):
        local = bw.local_now(at(12), "Europe/Moscow")
        self.assertEqual(15, local.hour)
        self.assertEqual(15, bw.local_now(at(12), "Nowhere/Nope").hour)
        self.assertEqual(15, bw.local_now(at(12), "").hour)


class EvaluateTest(unittest.TestCase):
    def test_target_fires_once_per_crossing_and_rearms_below(self):
        w = watch(target=0.012, jump_percent=50)
        state, events = bw.evaluate(w, 0.0121, {}, at(12))
        self.assertEqual(["target"], [e["kind"] for e in events])
        self.assertTrue(state["above_target"])
        state, events = bw.evaluate(w, 0.0122, state, at(12, 15))
        self.assertEqual([], events)
        state, events = bw.evaluate(w, 0.0119, state, at(12, 30))
        self.assertEqual([], events)
        self.assertFalse(state["above_target"])
        state, events = bw.evaluate(w, 0.0120, state, at(12, 45))
        self.assertEqual(["target"], [e["kind"] for e in events])

    def test_no_target_means_no_target_signal(self):
        state, events = bw.evaluate(watch(), 1.0, {}, at(12))
        self.assertEqual([], events)
        self.assertFalse(state["above_target"])

    def test_jump_both_directions_by_percent(self):
        w = watch(jump_percent=1.5)
        state = {"last": 100.0}
        _, events = bw.evaluate(w, 101.4, state, at(12))
        self.assertEqual([], events)
        _, events = bw.evaluate(w, 101.5, state, at(12))
        self.assertEqual("jump", events[0]["kind"])
        self.assertAlmostEqual(0.015, events[0]["change"])
        _, events = bw.evaluate(w, 98.0, state, at(12))
        self.assertEqual("jump", events[0]["kind"])
        self.assertLess(events[0]["change"], 0)

    def test_target_takes_precedence_over_jump(self):
        w = watch(target=101.0)
        _, events = bw.evaluate(w, 103.0, {"last": 100.0}, at(12))
        self.assertEqual(["target"], [e["kind"] for e in events])

    def test_events_outside_window_collapse_into_one_message_at_window_start(self):
        w = watch(target=101.0)
        state, events = bw.evaluate(w, 103.0, {"last": 100.0}, at(19))
        self.assertEqual([], events)
        self.assertTrue(state["pending"]["target"])
        self.assertTrue(state["pending"]["jump"])
        state, events = bw.evaluate(w, 99.0, state, at(3, day=11))
        self.assertEqual([], events)
        self.assertEqual(99.0, state["pending"]["min"])
        self.assertEqual(103.0, state["pending"]["max"])
        state, events = bw.evaluate(w, 99.5, state, at(10, day=11))
        self.assertEqual(["quiet_summary"], [e["kind"] for e in events])
        self.assertEqual({}, state["pending"])

    def test_outside_window_without_events_leaves_nothing_pending(self):
        state, events = bw.evaluate(watch(), 100.2, {"last": 100.0}, at(2))
        self.assertEqual({}, state["pending"])
        _, events = bw.evaluate(watch(), 100.3, state, at(11))
        self.assertEqual([], events)

    def test_digest_once_per_local_day_in_chosen_hour(self):
        w = watch(digest_hour=10)
        state, events = bw.evaluate(w, 1.0, {}, at(10, 5))
        self.assertEqual(["digest"], [e["kind"] for e in events])
        self.assertEqual("2026-11-10", state["last_digest_date"])
        state, events = bw.evaluate(w, 1.0, state, at(10, 20))
        self.assertEqual([], events)
        state, events = bw.evaluate(w, 1.0, state, at(11))
        self.assertEqual([], events)
        _, events = bw.evaluate(w, 1.0, state, at(10, 0, day=11))
        self.assertEqual(["digest"], [e["kind"] for e in events])

    def test_digest_is_not_sent_outside_window(self):
        _, events = bw.evaluate(watch(digest_hour=9), 1.0, {}, at(9))
        self.assertEqual([], events)


class FormatTest(unittest.TestCase):
    def test_group_thousands_and_number(self):
        self.assertEqual(f"1{NBSP}234{NBSP}567", bw.group_thousands(1_234_567))
        self.assertEqual("999", bw.group_thousands(999))
        self.assertEqual(f"{MINUS}1{NBSP}234,50", bw.format_number(-1234.5, 2))
        self.assertEqual("0,0116", bw.format_number(0.01155, 4))

    def test_format_quote_matches_app_rules(self):
        self.assertEqual(f"1{NBSP}235", bw.format_quote(1234.6))
        self.assertEqual("92,46", bw.format_quote(92.456))
        self.assertEqual("0,0116", bw.format_quote(0.011554))

    def test_quote_unit_scales_small_rates(self):
        self.assertEqual(1, bw.quote_unit(92.4))
        self.assertEqual(1, bw.quote_unit(1.0))
        self.assertEqual(100, bw.quote_unit(0.0116))
        self.assertEqual(1_000, bw.quote_unit(0.0032))
        self.assertEqual(1, bw.quote_unit(0.0))

    def test_rate_text_uses_codes_on_both_sides(self):
        self.assertEqual(f"1{NBSP}USD = 92,46{NBSP}RUB", bw.rate_text("USD", "RUB", 92.456))
        self.assertEqual(f"100{NBSP}RUB = 1,16{NBSP}USD", bw.rate_text("RUB", "USD", 0.011554))
        self.assertEqual(f"1{NBSP}000{NBSP}RUB = 11,55{NBSP}USD", bw.rate_text("RUB", "USD", 0.011554, 1000))

    def test_format_percent(self):
        self.assertEqual(f"{MINUS}4,4{NBSP}%", bw.format_percent(-0.0439))
        self.assertEqual(f"+2{NBSP}%", bw.format_percent(0.02))
        self.assertEqual("0 %", bw.format_percent(0.00001))

    def test_spread(self):
        self.assertAlmostEqual(-0.05, bw.spread(95.0, 100.0))
        self.assertIsNone(bw.spread(95.0, None))


class MessageTest(unittest.TestCase):
    def test_messages_mention_pair_rate_and_cbr(self):
        w = watch(target=0.012)
        text = bw.message_target(w, 0.0121, 0.0125, 0.012)
        self.assertIn("RUB → USD", text)
        self.assertIn(f"100{NBSP}RUB = 1,21{NBSP}USD", text)
        self.assertIn("ЦБ", text)
        self.assertIn(f"{MINUS}3,2{NBSP}%", text)
        text = bw.message_jump(w, 0.0121, None, 0.0119, 0.0168)
        self.assertTrue(text.startswith("▲"))
        self.assertIn("было 1,19", text)
        self.assertIn("ЦБ: курса нет", text)

    def test_digest_lists_card_history_and_target(self):
        w = watch(target=0.012, digest_hour=10)
        text = bw.message_digest(w, 0.0115, 0.0118, 0.0121, [0.0110, 0.0119], at(10))
        self.assertIn("Оплата картой", text)
        self.assertIn("мин 1,10 · макс 1,19", text)
        self.assertIn("Цель: 1,20", text)
        text = bw.message_digest(watch(), 0.0115, None, None, [], at(10))
        self.assertNotIn("Оплата картой", text)
        self.assertIn("Цель не задана", text)

    def test_quiet_summary(self):
        text = bw.message_quiet_summary(watch(), 99.0, 95.0, {"target": True, "jump": False, "min": 98.0, "max": 99.5})
        self.assertIn("достигалась цель", text)
        self.assertIn("от 98,00 до 99,50", text)
        self.assertIn("Сейчас", text)
        self.assertIn("событий не было", bw.message_quiet_summary(watch(), 99.0, None, {"min": 98.0, "max": 99.0}))

    def test_subscribe_text_names_category(self):
        self.assertIn("обмен в приложении", bw.subscribe_text(watch()))
        self.assertIn("оплата картой", bw.subscribe_text(watch(category=bw.CATEGORY_CARD)))


if __name__ == "__main__":
    unittest.main()
