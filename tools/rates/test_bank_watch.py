import datetime as dt
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bank_watch as bw  # noqa: E402

NBSP = " "
MINUS = "−"
SATURDAY = 14
SUNDAY = 15
HOLIDAYS = {"2026-11-04"}
WEEK = [{"buy": float(i), "cbr": None} for i in range(1, 101)]
SMALL_WEEK = [{"buy": 0.0098 + 0.000025 * i - 0.0000125, "cbr": None} for i in range(1, 101)]


def watch(**overrides):
    base = {
        "id": "w1", "provider": "tbank", "from_currency": "RUB", "to_currency": "USD", "category": bw.CATEGORY_EXCHANGE,
        "target": None, "good_percentile": 85, "improve_percent": 0.5, "trade_from": 10, "trade_to": 19,
        "digest_hour": None, "quiet_from": 18, "quiet_to": 10, "timezone": "Europe/Moscow",
    }
    base.update(overrides)
    return base


def at(hour: int, minute: int = 0, day: int = 10) -> dt.datetime:
    return dt.datetime(2026, 11, day, hour, minute, tzinfo=dt.timezone.utc)


def msk(hour: int, minute: int = 0, day: int = 10) -> dt.datetime:
    return dt.datetime(2026, 11, day, hour, minute, tzinfo=dt.timezone(dt.timedelta(hours=3))).astimezone(dt.timezone.utc)


def json_literal(text: str) -> str:
    return '"' + text.replace("\n", "\\n") + '"'


def local(moment: dt.datetime) -> dt.datetime:
    return bw.local_now(moment, "Europe/Moscow")


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


class TradingHoursTest(unittest.TestCase):
    def test_weekdays_by_minutes_of_exchange_time(self):
        self.assertFalse(bw.in_trading_hours(msk(10, 15), 630, 1110, set()))
        self.assertTrue(bw.in_trading_hours(msk(10, 30), 630, 1110, set()))
        self.assertTrue(bw.in_trading_hours(msk(18, 15), 630, 1110, set()))
        self.assertFalse(bw.in_trading_hours(msk(18, 30), 630, 1110, set()))
        self.assertFalse(bw.in_trading_hours(msk(2), 630, 1110, set()))

    def test_no_trading_on_weekend_and_holidays(self):
        self.assertFalse(bw.in_trading_hours(msk(12, day=SATURDAY), 600, 1140, set()))
        self.assertFalse(bw.in_trading_hours(msk(12, day=SUNDAY), 600, 1140, set()))
        self.assertTrue(bw.in_trading_hours(msk(12, day=4), 600, 1140, set()))
        self.assertFalse(bw.in_trading_hours(msk(12, day=4), 600, 1140, HOLIDAYS))
        self.assertTrue(bw.is_trading_day(dt.date(2026, 11, 10), HOLIDAYS))
        self.assertFalse(bw.is_trading_day(dt.date(2026, 11, 4), HOLIDAYS))
        self.assertFalse(bw.is_trading_day(dt.date(2026, 11, 14), set()))

    def test_hours_come_from_watch_with_defaults(self):
        self.assertEqual((630, 1110), bw.trade_hours({}))
        self.assertEqual((615, 1125), bw.trade_hours({"trade_open": 615, "trade_close": 1125, "trade_from": 9, "trade_to": 24}))
        self.assertEqual((540, 1440), bw.trade_hours({"trade_from": 9, "trade_to": 24}))
        self.assertEqual((0, 1110), bw.trade_hours({"trade_from": 0, "trade_to": None}))
        self.assertTrue(bw.in_trading_hours(msk(9, 30), 540, 720, set()))
        self.assertFalse(bw.in_trading_hours(msk(12), 540, 720, set()))

    def test_digest_minute_prefers_new_column(self):
        self.assertEqual(630, bw.digest_minute({"digest_at": 630, "digest_hour": 10}))
        self.assertIsNone(bw.digest_minute({"digest_at": None, "digest_hour": 10}))
        self.assertEqual(600, bw.digest_minute({"digest_hour": 10}))
        self.assertIsNone(bw.digest_minute({}))

    def test_warmup_is_two_trading_days_of_checks(self):
        self.assertEqual(72, bw.warmup_samples(600, 1140))
        self.assertEqual(64, bw.warmup_samples(630, 1110))

    def test_pair_hours_cover_every_watch_of_the_pair(self):
        hours = bw.pair_trade_hours([watch(), watch(trade_from=9, trade_to=15), watch(to_currency="XTS", trade_from=11, trade_to=12)])
        self.assertEqual((540, 1140), hours[("tbank", "RUB", "USD")])
        self.assertEqual((660, 720), hours[("tbank", "RUB", "XTS")])

    def test_snapshot_rows_carry_in_hours_flag(self):
        rates = {bw.CATEGORY_EXCHANGE: {"buy": 0.0115, "sell": 0.0122}, bw.CATEGORY_CARD: {"buy": 0.0118, "sell": None}}
        rows = bw.snapshot_rows(("tbank", "RUB", "USD"), rates, 0.0118, 0.0121, msk(12), True)
        self.assertEqual([True, True], [row["in_hours"] for row in rows])
        self.assertEqual([bw.CATEGORY_EXCHANGE, bw.CATEGORY_CARD], [row["category"] for row in rows])
        rows = bw.snapshot_rows(("tbank", "RUB", "USD"), rates, None, None, msk(20), False)
        self.assertEqual([False], [row["in_hours"] for row in rows])


class AssessTest(unittest.TestCase):
    def test_median(self):
        self.assertEqual(2.0, bw.median([3.0, 1.0, 2.0]))
        self.assertEqual(2.5, bw.median([4.0, 1.0, 2.0, 3.0]))
        self.assertIsNone(bw.median([]))

    def test_shares_are_strict_so_flat_week_is_neither_good_nor_bad(self):
        self.assertEqual(0.5, bw.share_below([1.0, 2.0, 3.0, 4.0], 2.5))
        self.assertEqual(0.25, bw.share_above([1.0, 2.0, 3.0, 4.0], 3.0))
        flat = [{"buy": 5.0, "cbr": None}] * 100
        self.assertIsNone(bw.assess(watch(), 5.0, None, flat)["kind"])

    def test_good_is_top_fifteen_percent_of_week(self):
        self.assertEqual("good", bw.assess(watch(), 85.5, None, WEEK)["kind"])
        self.assertIsNone(bw.assess(watch(), 84.5, None, WEEK)["kind"])
        self.assertAlmostEqual(0.85, bw.assess(watch(), 85.5, None, WEEK)["below"])

    def test_bad_is_bottom_fifteen_percent_of_week(self):
        self.assertEqual("bad", bw.assess(watch(), 15.5, None, WEEK)["kind"])
        self.assertIsNone(bw.assess(watch(), 16.5, None, WEEK)["kind"])

    def test_strictness_is_configurable(self):
        self.assertEqual("good", bw.assess(watch(good_percentile=75), 75.5, None, WEEK)["kind"])
        self.assertIsNone(bw.assess(watch(good_percentile=90), 85.5, None, WEEK)["kind"])
        self.assertEqual("bad", bw.assess(watch(good_percentile=75), 25.5, None, WEEK)["kind"])

    def test_good_needs_spread_narrower_than_week_median(self):
        history = [{"buy": float(i), "cbr": i / 0.97} for i in range(1, 101)]
        self.assertAlmostEqual(-0.03, bw.median_spread(history))
        self.assertIsNone(bw.assess(watch(), 95.0, 95.0 / 0.95, history)["kind"])
        self.assertEqual("good", bw.assess(watch(), 95.0, 95.0 / 0.98, history)["kind"])
        self.assertEqual("good", bw.assess(watch(), 95.0, None, history)["kind"])

    def test_spread_does_not_guard_bad_window(self):
        history = [{"buy": float(i), "cbr": i / 0.97} for i in range(1, 101)]
        self.assertEqual("bad", bw.assess(watch(), 5.0, 5.0 / 0.99, history)["kind"])

    def test_warmup_blocks_statistics(self):
        short = WEEK[:71]
        result = bw.assess(watch(), 99.0, None, short)
        self.assertTrue(result["warmup"])
        self.assertIsNone(result["kind"])
        self.assertIsNone(bw.assess(watch(), 0.5, None, short)["kind"])
        self.assertFalse(bw.assess(watch(), 99.0, None, WEEK[:72])["warmup"])

    def test_target_does_not_change_assessment(self):
        self.assertIsNone(bw.assess(watch(target=40.0), 50.0, None, WEEK)["kind"])
        self.assertEqual("bad", bw.assess(watch(target=4.0), 5.0, None, WEEK)["kind"])

    def test_is_improved_by_percent_in_window_direction(self):
        self.assertTrue(bw.is_improved("good", 100.5, 100.0, 0.5))
        self.assertFalse(bw.is_improved("good", 100.4, 100.0, 0.5))
        self.assertFalse(bw.is_improved("good", 99.0, 100.0, 0.5))
        self.assertTrue(bw.is_improved("bad", 99.5, 100.0, 0.5))
        self.assertFalse(bw.is_improved("bad", 100.5, 100.0, 0.5))


class EvaluateTest(unittest.TestCase):
    def step(self, w, rate, state, moment, history=None, cbr=None):
        return bw.evaluate(w, rate, cbr, state, moment, WEEK if history is None else history, HOLIDAYS)

    def kinds(self, events):
        return [e["kind"] for e in events]

    def test_one_message_per_good_window_and_repeat_only_on_improvement(self):
        w = watch()
        state, events = self.step(w, 90.0, {}, msk(11))
        self.assertEqual(["good"], self.kinds(events))
        self.assertFalse(events[0]["repeat"])
        state, events = self.step(w, 90.2, state, msk(11, 15))
        self.assertEqual([], events)
        state, events = self.step(w, 90.5, state, msk(11, 30))
        self.assertEqual(["good"], self.kinds(events))
        self.assertTrue(events[0]["repeat"])
        self.assertEqual(90.0, events[0]["previous"])
        state, events = self.step(w, 90.6, state, msk(11, 45))
        self.assertEqual([], events)
        state, events = self.step(w, 89.0, state, msk(12))
        self.assertEqual([], events)
        self.assertEqual("good", state["window"]["kind"])

    def test_window_close_is_silent_and_new_window_sends_again(self):
        w = watch()
        state, _ = self.step(w, 90.0, {}, msk(11))
        state, events = self.step(w, 50.0, state, msk(11, 15))
        self.assertEqual([], events)
        self.assertIsNone(state["window"])
        state, events = self.step(w, 90.0, state, msk(11, 30))
        self.assertEqual(["good"], self.kinds(events))
        self.assertFalse(events[0]["repeat"])

    def test_bad_window_mirrors_good(self):
        w = watch()
        state, events = self.step(w, 10.0, {}, msk(11))
        self.assertEqual(["bad"], self.kinds(events))
        state, events = self.step(w, 9.96, state, msk(11, 15))
        self.assertEqual([], events)
        state, events = self.step(w, 10.5, state, msk(11, 30))
        self.assertEqual([], events)
        state, events = self.step(w, 9.9, state, msk(11, 45))
        self.assertEqual(["bad"], self.kinds(events))
        self.assertTrue(events[0]["repeat"])
        self.assertEqual(10.0, events[0]["previous"])
        state, events = self.step(w, 50.0, state, msk(12))
        self.assertEqual([], events)
        state, events = self.step(w, 90.0, state, msk(12, 15))
        self.assertEqual(["good"], self.kinds(events))
        state, events = self.step(w, 10.0, state, msk(12, 30))
        self.assertEqual(["bad"], self.kinds(events))
        self.assertFalse(events[0]["repeat"])

    def test_no_signal_for_ordinary_changes_however_sharp(self):
        w = watch()
        state, events = self.step(w, 30.0, {}, msk(11))
        self.assertEqual([], events)
        state, events = self.step(w, 70.0, state, msk(11, 15))
        self.assertEqual([], events)
        state, events = self.step(w, 30.0, state, msk(11, 30))
        self.assertEqual([], events)

    def test_no_windows_outside_trading_hours(self):
        w = watch(quiet_from=9, quiet_to=9)
        for moment in (msk(9, 45), msk(19), msk(23), msk(12, day=SATURDAY), msk(12, day=SUNDAY), msk(12, day=4)):
            state, events = self.step(w, 99.0, {}, moment)
            self.assertEqual([], events)
            self.assertIsNone(state["window"])
            state, events = self.step(w, 1.0, {}, moment)
            self.assertEqual([], events)

    def test_evening_drop_and_morning_recovery_produce_nothing(self):
        w = watch()
        state, events = self.step(w, 90.0, {}, msk(17))
        self.assertEqual(["good"], self.kinds(events))
        state, events = self.step(w, 5.0, state, msk(19))
        self.assertEqual([], events)
        self.assertIsNone(state["window"])
        state, events = self.step(w, 5.0, state, msk(9, 45, day=11))
        self.assertEqual([], events)
        state, events = self.step(w, 50.0, state, msk(10, 0, day=11))
        self.assertEqual([], events)

    def test_window_after_send_window_goes_to_digest_line(self):
        w = watch(digest_hour=10)
        state = {"last_digest_date": "2026-11-10"}
        state, events = self.step(w, 95.0, state, msk(18, 15))
        self.assertEqual([], events)
        state, events = self.step(w, 97.0, state, msk(18, 30))
        self.assertEqual([], events)
        self.assertEqual({"good": True, "best": 97.0}, state["notes"])
        state, events = self.step(w, 3.0, state, msk(18, 45))
        self.assertEqual([], events)
        self.assertEqual(3.0, state["notes"]["worst"])
        state, events = self.step(w, 3.0, state, msk(19))
        self.assertEqual([], events)
        self.assertIsNone(state["window"])
        state, events = self.step(w, 50.0, state, msk(10, 0, day=11))
        self.assertEqual(["digest"], self.kinds(events))
        self.assertEqual({"good": True, "best": 97.0, "bad": True, "worst": 3.0}, events[0]["notes"])
        self.assertEqual({}, state["notes"])
        text = bw.message_digest(w, 50.0, None, events[0], local(msk(10, 0, day=11)))
        self.assertIn("Вне окна отправки: было выгодно, до 97,00; было невыгодно, до 3,00", text)

    def test_without_digest_nothing_is_kept_for_later(self):
        state, events = self.step(watch(), 95.0, {}, msk(18, 15))
        self.assertEqual([], events)
        self.assertEqual({}, state["notes"])

    def test_window_opened_before_send_window_is_sent_when_it_starts(self):
        w = watch(quiet_to=12, quiet_from=18)
        state, events = self.step(w, 90.0, {}, msk(11))
        self.assertEqual([], events)
        state, events = self.step(w, 90.0, state, msk(12))
        self.assertEqual(["good"], self.kinds(events))
        self.assertFalse(events[0]["repeat"])
        state, events = self.step(w, 90.0, state, msk(12, 15))
        self.assertEqual([], events)

    def test_digest_once_per_day_and_it_replaces_the_window_message(self):
        w = watch(digest_hour=10)
        state, events = self.step(w, 95.0, {}, msk(10, 5))
        self.assertEqual(["digest"], self.kinds(events))
        self.assertEqual("good", events[0]["verdict"])
        self.assertEqual("2026-11-10", state["last_digest_date"])
        state, events = self.step(w, 95.0, state, msk(10, 20))
        self.assertEqual([], events)
        state, events = self.step(w, 96.0, state, msk(10, 35))
        self.assertEqual(["good"], self.kinds(events))
        self.assertTrue(events[0]["repeat"])
        state, events = self.step(w, 50.0, state, msk(11))
        self.assertEqual([], events)
        state, events = self.step(w, 50.0, state, msk(10, 0, day=11))
        self.assertEqual(["digest"], self.kinds(events))
        self.assertEqual("normal", events[0]["verdict"])

    def test_digest_verdicts(self):
        w = watch(digest_hour=10)
        self.assertEqual("bad", self.step(w, 5.0, {}, msk(10))[1][0]["verdict"])
        self.assertEqual("warmup", self.step(w, 95.0, {}, msk(10), history=WEEK[:12])[1][0]["verdict"])
        self.assertEqual("weekend", self.step(w, 95.0, {}, msk(10, day=SATURDAY))[1][0]["verdict"])
        self.assertEqual("weekend", self.step(w, 95.0, {}, msk(10, day=4))[1][0]["verdict"])
        early = watch(digest_hour=9, quiet_to=9)
        self.assertEqual("night", self.step(early, 95.0, {}, msk(9))[1][0]["verdict"])

    def test_weekend_digest_compares_nothing(self):
        w = watch(digest_hour=10)
        state, events = self.step(w, 95.0, {}, msk(10, day=SATURDAY))
        self.assertIsNone(events[0]["assessment"])
        self.assertIsNone(state["window"])
        text = bw.message_digest(w, 95.0, 100.0, events[0], local(msk(10, day=SATURDAY)))
        self.assertIn("☀️ <b>RUB → USD</b> · утренняя сводка, 10:00 МСК", text)
        self.assertIn("биржа закрыта, курс выходного дня", text)
        self.assertNotIn("замеров", text)
        self.assertIn(f"Обмен: 1{NBSP}RUB = 95,00{NBSP}USD", text)

    def test_warmup_gives_no_signals_and_says_so_in_digest(self):
        w = watch(digest_hour=10)
        short = WEEK[:12]
        state, events = self.step(w, 95.0, {}, msk(11), history=short)
        self.assertEqual([], events)
        state, events = self.step(w, 1.0, state, msk(11, 15), history=short)
        self.assertEqual([], events)
        _, events = self.step(w, 95.0, {}, msk(10), history=short)
        text = bw.message_digest(w, 95.0, None, events[0], local(msk(10)))
        self.assertIn("мало данных для сравнения", text)
        self.assertNotIn("копим", text)
        self.assertNotIn("замеров недели", text)

    def test_target_sends_on_every_change_while_above_and_stops_below(self):
        w = watch(target=40.0)
        state, events = self.step(w, 50.0, {}, msk(11))
        self.assertEqual(["target"], self.kinds(events))
        self.assertIsNone(events[0]["previous"])
        state, events = self.step(w, 50.0, state, msk(11, 15))
        self.assertEqual([], events)
        state, events = self.step(w, 50.01, state, msk(11, 30))
        self.assertEqual(["target"], self.kinds(events))
        self.assertEqual(50.0, events[0]["previous"])
        state, events = self.step(w, 45.0, state, msk(11, 45))
        self.assertEqual(["target"], self.kinds(events))
        state, events = self.step(w, 39.0, state, msk(12))
        self.assertEqual([], events)
        state, events = self.step(w, 38.0, state, msk(12, 15))
        self.assertEqual([], events)
        state, events = self.step(w, 40.0, state, msk(12, 30))
        self.assertEqual(["target"], self.kinds(events))
        self.assertEqual(38.0, events[0]["previous"])

    def test_target_ignores_warmup_and_trading_hours_but_not_send_window(self):
        w = watch(target=40.0, quiet_from=9, quiet_to=9)
        for moment in (msk(11), msk(21), msk(12, day=SATURDAY), msk(12, day=4)):
            _, events = self.step(w, 50.0, {"last": 49.0}, moment, history=WEEK[:5])
            self.assertEqual(["target"], self.kinds(events))
        w = watch(target=40.0, digest_hour=10)
        state, events = self.step(w, 50.0, {"last": 49.0, "last_digest_date": "2026-11-10"}, msk(18, 15))
        self.assertEqual([], events)
        state, events = self.step(w, 52.0, state, msk(21))
        self.assertEqual([], events)
        state, events = self.step(w, 51.0, state, msk(9, 45, day=11))
        self.assertEqual(52.0, state["notes"]["target_max"])
        state, events = self.step(w, 51.0, state, msk(10, 0, day=11))
        self.assertEqual(["digest"], self.kinds(events))
        self.assertEqual(40.0, events[0]["target"])
        text = bw.message_digest(w, 51.0, None, events[0], local(msk(10, 0, day=11)))
        self.assertIn("Вне окна отправки: выше цели было: макс 52,00", text)
        self.assertIn("Выше цели 40,00", text)
        self.assertEqual({}, state["notes"])

    def test_target_wins_over_window_message_but_window_state_moves(self):
        w = watch(target=80.0)
        state, events = self.step(w, 90.0, {}, msk(11))
        self.assertEqual(["target"], self.kinds(events))
        self.assertEqual("good", state["window"]["kind"])
        self.assertTrue(state["window"]["notified"])
        self.assertEqual(90.0, state["window"]["sent"])
        state, events = self.step(w, 90.0, state, msk(11, 15))
        self.assertEqual([], events)
        state, events = self.step(w, 79.0, state, msk(11, 30))
        self.assertEqual([], events)
        state, events = self.step(w, 79.5, state, msk(11, 45))
        self.assertEqual([], events)

    def test_window_message_still_sent_when_rate_is_below_target(self):
        w = watch(target=95.0)
        _, events = self.step(w, 90.0, {}, msk(11))
        self.assertEqual(["good"], self.kinds(events))

    def test_digest_is_not_sent_outside_send_window(self):
        _, events = self.step(watch(digest_hour=9), 50.0, {}, msk(9))
        self.assertEqual([], events)

    def test_state_keeps_last_rate_and_time_for_the_app(self):
        state, _ = self.step(watch(), 50.0, {}, msk(11))
        self.assertEqual(50.0, state["last"])
        self.assertEqual("2026-11-10T08:00:00+00:00", state["last_at"])


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
    def good(self, rate, **overrides):
        event = {"kind": "good", "repeat": False, "previous": None, "assessment": bw.assess(watch(**overrides), rate, None, SMALL_WEEK)}
        return event

    def test_good_message_has_rate_position_and_cbr(self):
        w = watch()
        text = bw.message_signal(w, 0.0121, 0.0125, self.good(0.0121))
        lines = text.split("\n")
        self.assertEqual("🟢 <b>RUB → USD</b>: выгодно менять", lines[0])
        self.assertEqual(f"100{NBSP}RUB = 1,21{NBSP}USD · лучше 92{NBSP}% замеров недели", lines[1])
        self.assertEqual(f"ЦБ: 100{NBSP}RUB = 1,25{NBSP}USD · разница {MINUS}3,2{NBSP}%", lines[2])
        self.assertEqual(3, len(lines))

    def test_repeat_message_names_previous_rate(self):
        event = self.good(0.0122)
        event.update(repeat=True, previous=0.0121)
        text = bw.message_signal(watch(), 0.0122, None, event)
        self.assertIn("стало ещё выгоднее, было 1,21", text)
        self.assertNotIn("Картой", text)
        self.assertIn("ЦБ: курса нет", text)

    def test_target_message(self):
        self.assertEqual("🎯 Курс выше цели 305: 306,10 (было 305,40)", bw.message_target(306.1, 305.0, 305.4))
        self.assertEqual("🎯 Курс выше цели 1,14: 1,15 (было 1,16)", bw.message_target(0.0115, 0.0114, 0.0116))
        self.assertEqual("🎯 Курс выше цели 92: 92,46", bw.message_target(92.456, 92.0, None))

    def test_bad_message(self):
        assessment = bw.assess(watch(), 0.0101, None, SMALL_WEEK)
        text = bw.message_signal(watch(), 0.0101, None, {"kind": "bad", "repeat": False, "previous": None, "assessment": assessment})
        self.assertTrue(text.startswith("🔴 <b>RUB → USD</b>: сейчас не меняй"))
        self.assertIn(f"хуже 88{NBSP}% замеров недели", text)
        self.assertNotIn("Картой", text)
        text = bw.message_signal(watch(), 0.0100, None, {"kind": "bad", "repeat": True, "previous": 0.0101, "assessment": assessment})
        self.assertIn("стало ещё хуже, было 1,01", text)

    def test_digest_is_one_message_with_verdict_and_position(self):
        w = watch(digest_hour=10)
        event = {"kind": "digest", "verdict": "good", "assessment": bw.assess(w, 0.0121, 0.0125, SMALL_WEEK), "notes": {}}
        lines = bw.message_digest(w, 0.0121, 0.0125, event, local(msk(10, 30))).split("\n")
        self.assertEqual("☀️ <b>RUB → USD</b> · курс на начало торгов, 10:30 МСК", lines[0])
        self.assertEqual(f"Обмен: 100{NBSP}RUB = 1,21{NBSP}USD · выгодно", lines[1])
        self.assertEqual(f"ЦБ: 100{NBSP}RUB = 1,25{NBSP}USD · разница {MINUS}3,2{NBSP}%", lines[2])
        self.assertEqual(f"Лучше 92{NBSP}% замеров недели", lines[3])
        self.assertEqual(4, len(lines))

    def test_digest_verdict_texts(self):
        w = watch(digest_hour=10)
        assessment = bw.assess(w, 0.0115, None, SMALL_WEEK)
        for code, expected in (("normal", "· обычно"), ("bad", "· не меняй"), ("night", "· биржа закрыта, ночной курс")):
            event = {"kind": "digest", "verdict": code, "assessment": assessment if code != "night" else None, "notes": {}}
            self.assertIn(expected, bw.message_digest(w, 0.0115, None, event, local(msk(10))))

    def test_notes_line(self):
        self.assertIsNone(bw.notes_line({}, 1))
        self.assertEqual("Вне окна отправки: было выгодно, до 1,22", bw.notes_line({"good": True, "best": 0.0122}, 100))
        self.assertEqual("Вне окна отправки: было невыгодно, до 1,01", bw.notes_line({"bad": True, "worst": 0.0101}, 100))
        self.assertEqual("Вне окна отправки: выше цели было: макс 1,22", bw.notes_line({"target_max": 0.0122}, 100))

    def test_plural_and_share(self):
        self.assertEqual(f"1{NBSP}замер", bw.plural(1, "замер", "замера", "замеров"))
        self.assertEqual(f"3{NBSP}замера", bw.plural(3, "замер", "замера", "замеров"))
        self.assertEqual(f"11{NBSP}замеров", bw.plural(11, "замер", "замера", "замеров"))
        self.assertEqual(f"72{NBSP}замера", bw.plural(72, "замер", "замера", "замеров"))
        self.assertEqual(f"87{NBSP}%", bw.format_share(0.866))
        self.assertEqual(f"1,2{NBSP}%", bw.format_percent_abs(-0.0124))

    def test_subscribe_text_names_pair(self):
        self.assertIn("RUB → USD", bw.subscribe_text(watch()))
        self.assertIn("обмен в приложении", bw.subscribe_text(watch()))
        self.assertIn("/rate", bw.subscribe_text(watch()))

    def test_now_message_shows_rate_verdict_and_position(self):
        w = watch(target=0.012)
        assessment = bw.assess(w, 0.0121, 0.0125, SMALL_WEEK)
        lines = bw.message_now(w, 0.0121, 0.0125, "good", assessment, local(msk(13, 26))).split("\n")
        self.assertEqual("💱 <b>RUB → USD</b> · курс на 13:26 МСК", lines[0])
        self.assertEqual(f"Обмен: 100{NBSP}RUB = 1,21{NBSP}USD · выгодно", lines[1])
        self.assertEqual(f"Лучше 92{NBSP}% замеров недели", lines[3])
        self.assertEqual("Выше цели 1,20", lines[4])
        night = bw.message_now(watch(), 0.0115, None, "night", None, local(msk(22)))
        self.assertIn("биржа закрыта, ночной курс", night)
        self.assertNotIn("замеров", night)

    def test_digest_waits_for_digest_minute_and_sends_once(self):
        w = watch(digest_at=630, trade_open=630, trade_close=1110)
        self.assertEqual([], EvaluateTest.step(None, w, 95.0, {}, msk(10, 15))[1])
        state, events = EvaluateTest.step(None, w, 95.0, {}, msk(10, 30))
        self.assertEqual(["digest"], [e["kind"] for e in events])
        self.assertEqual([], EvaluateTest.step(None, w, 95.0, state, msk(10, 45))[1])
        late = EvaluateTest.step(None, w, 95.0, {}, msk(11, 15))[1]
        self.assertEqual(["digest"], [e["kind"] for e in late])
        self.assertEqual([], [e for e in EvaluateTest.step(None, w, 95.0, {}, msk(11, 30))[1] if e["kind"] == "digest"])

    def test_clock_names_time_zone(self):
        self.assertEqual("13:26 МСК", bw.clock(local(msk(13, 26))))
        self.assertEqual("17:26 UTC+07", bw.clock(bw.local_now(msk(13, 26), "Asia/Bangkok")))

    def test_webhook_texts_match_worker(self):
        source = (Path(__file__).resolve().parents[2] / "supabase" / "functions" / "telegram-webhook" / "index.ts").read_text()
        for text in (bw.HELP_TEXT, bw.STOP_TEXT, bw.NOT_SUBSCRIBED_TEXT):
            self.assertIn(json_literal(text), source)
        self.assertIn("Курс сейчас — /rate, отписаться — /stop", source)


class RetryTest(unittest.TestCase):
    def setUp(self):
        self.slept = []
        self.original_sleep = bw.sleep
        bw.sleep = self.slept.append

    def tearDown(self):
        bw.sleep = self.original_sleep

    def flaky(self, errors):
        calls = []

        def action():
            calls.append(1)
            if len(calls) <= len(errors):
                raise errors[len(calls) - 1]
            return "ok"
        return action, calls

    def test_transient_error_is_retried(self):
        action, calls = self.flaky([bw.urllib.error.URLError(ConnectionResetError(104, "reset"))])
        self.assertEqual("ok", bw.with_retries(action))
        self.assertEqual(2, len(calls))
        self.assertEqual([2], self.slept)

    def test_gives_up_after_three_attempts(self):
        reset = ConnectionResetError(104, "reset")
        action, calls = self.flaky([reset, reset, reset])
        with self.assertRaises(ConnectionResetError):
            bw.with_retries(action)
        self.assertEqual(3, len(calls))

    def test_client_error_is_not_retried(self):
        action, calls = self.flaky([bw.urllib.error.HTTPError("u", 400, "bad", None, None)])
        with self.assertRaises(bw.urllib.error.HTTPError):
            bw.with_retries(action)
        self.assertEqual(1, len(calls))

    def test_server_error_is_retried(self):
        action, calls = self.flaky([bw.urllib.error.HTTPError("u", 502, "bad gateway", None, None)])
        self.assertEqual("ok", bw.with_retries(action))

    def test_unsafe_request_is_not_retried(self):
        action, calls = self.flaky([ConnectionResetError(104, "reset")])
        with self.assertRaises(ConnectionResetError):
            bw.with_retries(action, retry=False)
        self.assertEqual(1, len(calls))


class FakeDb:
    def __init__(self):
        self.upserts = []
        self.deletes = []

    def select(self, table, query=""):
        return []

    def upsert(self, table, rows, prefer=""):
        self.upserts.append(table)

    def delete(self, table, query):
        self.deletes.append(table)


class BrokenTelegram:
    def call(self, method, **params):
        raise bw.urllib.error.URLError(ConnectionResetError(104, "reset"))


class RecordingTelegram:
    def __init__(self):
        self.calls = []

    def call(self, method, **params):
        self.calls.append(method)
        return {"username": "bot"} if method == "getMe" else True


class BotInfoDb(FakeDb):
    def __init__(self, info):
        super().__init__()
        self.info = info
        self.rows = []

    def select(self, table, query=""):
        return [self.info] if table == "bot_info" else []

    def upsert(self, table, rows, prefer=""):
        self.rows.append(rows)


class WebhookTest(unittest.TestCase):
    WEBHOOK = ("https://example.supabase.co/functions/v1/telegram-webhook", "secret")

    def test_webhook_mode_sets_webhook_and_commands_once(self):
        tg = RecordingTelegram()
        db = BotInfoDb({"id": 1})
        bw.sync_bot(db, tg, at(9), self.WEBHOOK)
        self.assertEqual(["getMe", "setWebhook", "setMyCommands"], tg.calls)
        signature = db.rows[0]["webhook_signature"]
        tg = RecordingTelegram()
        bw.sync_bot(BotInfoDb({"id": 1, "webhook_signature": signature}), tg, at(9, 15), self.WEBHOOK)
        self.assertEqual(["getMe"], tg.calls)

    def test_new_secret_resets_webhook(self):
        old = bw.webhook_signature(self.WEBHOOK[0], "old")
        tg = RecordingTelegram()
        bw.sync_bot(BotInfoDb({"id": 1, "webhook_signature": old}), tg, at(9), self.WEBHOOK)
        self.assertIn("setWebhook", tg.calls)

    def test_polling_mode_reads_updates(self):
        tg = RecordingTelegram()
        tg.call = lambda method, **params: (tg.calls.append(method), [] if method == "getUpdates" else {"username": "bot"})[1]
        db = BotInfoDb({"id": 1, "update_offset": 5})
        bw.sync_bot(db, tg, at(9))
        self.assertEqual(["getMe", "getUpdates"], tg.calls)
        self.assertEqual(5, db.rows[0]["update_offset"])


class RunTest(unittest.TestCase):
    def test_telegram_failure_does_not_stop_check(self):
        db = FakeDb()
        original = bw.fetch_cbr
        bw.fetch_cbr = lambda: {}
        try:
            bw.run(db, BrokenTelegram(), at(9))
        finally:
            bw.fetch_cbr = original
        self.assertEqual([], db.upserts)
        self.assertEqual(["bank_rate_snapshots"], db.deletes)


if __name__ == "__main__":
    unittest.main()
