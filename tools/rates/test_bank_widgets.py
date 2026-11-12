import datetime as dt
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bank_watch as bw  # noqa: E402
import bank_widgets as w  # noqa: E402

UTC = dt.timezone.utc


def watch(**overrides):
    base = {
        "id": "w1", "provider": "tbank", "from_currency": "RUB", "to_currency": "USD", "target": None,
        "good_percentile": 85, "improve_percent": 0.5, "trade_open": 630, "trade_close": 1110,
        "digest_at": 630, "quiet_from": 18, "quiet_to": 10, "timezone": "Europe/Moscow",
    }
    base.update(overrides)
    return base


def week_history():
    rows = []
    for day in range(9, 14):
        for step in range(32):
            at = dt.datetime(2026, 11, day, 7, 30, tzinfo=UTC) + dt.timedelta(minutes=15 * step)
            rows.append({"buy": 0.0110 + 0.00002 * ((step + day * 7) % 25), "cbr": 0.0118, "at": at, "in_hours": True})
    return rows


def today_rows(night=False):
    rows = []
    start = dt.datetime(2026, 11, 13, 7, 30, tzinfo=UTC)
    for step in range(40 if night else 20):
        in_hours = step < 32
        buy = 0.0111 + 0.00001 * step if in_hours else 0.0104
        rows.append({"buy": buy, "cbr": 0.0118, "at": start + dt.timedelta(minutes=15 * step), "in_hours": in_hours})
    return rows


def local(hour, minute=0):
    return dt.datetime(2026, 11, 13, hour, minute, tzinfo=dt.timezone(dt.timedelta(hours=3)))


class RenderTest(unittest.TestCase):
    def charts(self, night=False):
        return bw.ChartData(week_history(), today_rows(night), 630)

    def test_digest_widget_fits_telegram_limits_and_shows_verdict(self):
        history = week_history()
        assessment = bw.assess(watch(), 0.0116, 0.0118, history)
        event = {"kind": "digest", "verdict": "good", "assessment": assessment, "notes": {}}
        document, caption = bw.widget_digest(watch(), 0.0116, 0.0118, event, local(10, 30), self.charts())
        self.assertTrue(w.fits(document))
        self.assertIn("ВЫГОДНО", document)
        self.assertIn("начало торгов · 10:30", document)
        self.assertIn("хуже за неделю", document)
        self.assertIn("<polyline", document)
        self.assertIn("var(--tg-theme-text-color", document)
        self.assertNotIn("http", document)
        self.assertEqual("☀️ <b>RUB → USD</b>: 100 RUB = 1,16 USD · выгодно", caption)

    def test_week_chart_is_one_continuous_line_with_day_labels(self):
        chart = w.week_chart([{"label": "пн", "values": [1.0, 1.1]}, {"label": "вт", "values": [1.2, 1.3]}], None, None)
        self.assertEqual(1, chart.count("<polyline"))
        self.assertIn(">пн<", chart)
        self.assertIn(">вт<", chart)
        self.assertIn(w.UP, chart)

    def test_line_color_follows_direction(self):
        self.assertEqual("up", w.trend(100.0, 100.2))
        self.assertEqual("down", w.trend(100.0, 99.8))
        self.assertEqual("flat", w.trend(100.0, 100.05))
        self.assertIn(w.DOWN, w.day_chart([3.0, 2.0, 1.0], None, "10:30", "сейчас"))
        self.assertIn(w.FLAT, w.day_chart([1.0, 1.0], None, "10:30", "сейчас"))

    def test_cbr_spread_colored_against_usual(self):
        better = bw.cbr_view(0.0114, 0.0118, -0.046, 100)
        self.assertEqual("up", better["trend"])
        self.assertIn("лучше обычного", better["hint"])
        worse = bw.cbr_view(0.0110, 0.0118, -0.046, 100)
        self.assertEqual("down", worse["trend"])
        same = bw.cbr_view(0.01126, 0.0118, -0.046, 100)
        self.assertEqual("flat", same["trend"])
        self.assertIn("как обычно", same["hint"])
        self.assertIn(w.UP, w.cbr_row(better))
        self.assertNotIn(w.UP, w.cbr_row(same))
        self.assertIn("курса нет", w.cbr_row(None))

    def test_signal_widget_has_change_since_morning(self):
        history = week_history()
        assessment = bw.assess(watch(), 0.0116, 0.0118, history)
        event = {"kind": "good", "repeat": False, "previous": None, "assessment": assessment}
        document, caption = bw.widget_signal(watch(), 0.0116, 0.0118, event, local(14, 15), self.charts())
        self.assertTrue(w.fits(document))
        self.assertIn("Выгодно менять", document)
        self.assertIn("к утру", document)
        self.assertIn("RUB → USD · 14:15", document)
        self.assertTrue(caption.startswith("🟢 <b>RUB → USD</b>"))

    def test_now_widget_shades_evening(self):
        document, _ = bw.widget_now(watch(), 0.0104, 0.0118, "night", None, local(21, 40), self.charts(night=True))
        self.assertTrue(w.fits(document))
        self.assertIn("БИРЖА ЗАКРЫТА", document)
        self.assertIn("ночной курс", document)
        self.assertIn('stroke-dasharray="4 3"', document)
        self.assertIn("ночной курс", document)
        self.assertNotIn("<rect", document)

    def test_today_marks_first_evening_point(self):
        values, night_from, start = self.charts(night=True).today()
        self.assertEqual(40, len(values))
        self.assertEqual(32, night_from)
        self.assertEqual("10:30", start)

    def test_downsample_keeps_ends(self):
        values = list(range(100))
        result = w.downsample(values, 10)
        self.assertEqual(10, len(result))
        self.assertEqual(0, result[0])
        self.assertEqual(99, result[-1])

    def test_text_is_escaped(self):
        self.assertIn("&lt;b&gt;", w.esc("<b>"))

    def test_too_large_widget_is_not_sent(self):
        self.assertFalse(w.fits("x" * (w.MAX_HTML_BYTES + 1)))


class FlakyWidgetTelegram:
    def __init__(self, fail_widget):
        self.fail_widget = fail_widget
        self.sent = []

    def call(self, method, **params):
        if method == "sendWidget" and self.fail_widget:
            raise RuntimeError("telegram sendWidget failed")
        self.sent.append(method)
        return {}

    def send(self, chat_id, text):
        self.sent.append("sendMessage")


class SendTest(unittest.TestCase):
    def test_widget_sent_and_remembered(self):
        tg = FlakyWidgetTelegram(fail_widget=False)
        state = {}
        bw.send_event(tg, 1, "text", ("<html></html>", "caption"), state)
        self.assertEqual(["sendWidget"], tg.sent)
        self.assertTrue(state["widgets_ok"])

    def test_widget_failure_falls_back_to_text(self):
        tg = FlakyWidgetTelegram(fail_widget=True)
        state = {}
        bw.send_event(tg, 1, "text", ("<html></html>", "caption"), state)
        self.assertEqual(["sendMessage"], tg.sent)
        self.assertFalse(state["widgets_ok"])

    def test_without_widget_text_is_sent(self):
        tg = FlakyWidgetTelegram(fail_widget=False)
        bw.send_event(tg, 1, "text", None, {})
        self.assertEqual(["sendMessage"], tg.sent)


if __name__ == "__main__":
    unittest.main()
