"""HTML-виджеты Telegram (Bot API sendWidget) для сводки, сигналов и ответа на /rate.

Модуль только рисует: числа и подписи приходят уже отформатированными из bank_watch.py.
Ограничения Telegram: до 32768 байт HTML и до 4096 байт после zlib level 9, без сети и внешних ресурсов.
Цвета текста и фона берутся из темы чата (--tg-theme-*), свой фон у виджета не задаётся.
"""

from __future__ import annotations

import html
import zlib

MAX_HTML_BYTES = 32768
MAX_PACKED_BYTES = 4096
MAX_DAY_POINTS = 16
MAX_LINE_POINTS = 48
TREND_EPSILON = 0.001
MIN_SPAN_SHARE = 0.01
MIN_LABEL_GAP = 26
WIDTH = 300
WEEK_HEIGHT = 96
DAY_HEIGHT = 80
AXIS_GAP = 14
PLOT_PAD = 22

UP = "#2fbf71"
DOWN = "#e5484d"
FLAT = "#94a3b8"
ACCENT = "#5b6cff"
TRENDS = {"up": UP, "down": DOWN, "flat": FLAT}
CHIPS = {
    "good": (UP, "ВЫГОДНО"),
    "normal": (FLAT, "ОБЫЧНО"),
    "bad": (DOWN, "НЕ МЕНЯЙ"),
    "warmup": (ACCENT, "МАЛО ДАННЫХ"),
    "night": (ACCENT, "БИРЖА ЗАКРЫТА"),
    "weekend": (ACCENT, "БИРЖА ЗАКРЫТА"),
}

CSS = (
    "*{box-sizing:border-box;margin:0}"
    "body{font:14px/1.35 system-ui,sans-serif;color:var(--tg-theme-text-color,#0f1720);padding:12px}"
    ".m{color:var(--tg-theme-hint-color,#6b7785)}"
    ".h{display:flex;justify-content:space-between;align-items:center;gap:8px;font-size:12px}"
    ".c{font-size:11px;font-weight:700;letter-spacing:.04em;padding:3px 8px;border-radius:99px;white-space:nowrap}"
    ".r{font-size:34px;font-weight:700;letter-spacing:-.02em;margin-top:6px}.u{font-size:13px;font-weight:500;margin-left:4px}"
    ".row{display:flex;justify-content:space-between;gap:8px;font-size:12px;margin-top:6px}"
    ".bar{position:relative;height:6px;border-radius:3px;margin:10px 0 4px;"
    "background:linear-gradient(90deg,#e5484d55,#94a3b833 25%,#94a3b833 75%,#2fbf7155)}"
    ".dot{position:absolute;top:-4px;width:14px;height:14px;border-radius:50%;transform:translateX(-50%);"
    "border:3px solid var(--tg-theme-bg-color,#fff)}"
    "svg{display:block;width:100%;margin-top:8px}"
)


def trend(first: float, last: float) -> str:
    if first <= 0:
        return "flat"
    change = last / first - 1.0
    return "up" if change > TREND_EPSILON else "down" if change < -TREND_EPSILON else "flat"


def downsample(values: list[float], limit: int) -> list[float]:
    if len(values) <= limit:
        return list(values)
    step = (len(values) - 1) / (limit - 1)
    return [values[round(i * step)] for i in range(limit)]


def esc(text: str) -> str:
    return html.escape(text, quote=True)


def _scaler(values: list[float], height: int):
    lo, hi = min(values), max(values)
    span = max(hi - lo, abs(hi + lo) / 2 * MIN_SPAN_SHARE, 1e-12)
    middle = (hi + lo) / 2
    lo, hi = middle - span * 0.54, middle + span * 0.54

    def y(value: float) -> float:
        return round(height - AXIS_GAP - (value - lo) / (hi - lo) * (height - PLOT_PAD), 1)

    return y, lo, hi


def _line(xy: list[tuple[float, float]], color: str, height: int, gradient: str) -> str:
    points = " ".join(f"{x:g},{y:g}" for x, y in xy)
    base = height - AXIS_GAP
    return (
        f'<defs><linearGradient id="{gradient}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{color}" stop-opacity=".28"/>'
        f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></linearGradient></defs>'
        f'<polygon points="{xy[0][0]:g},{base} {points} {xy[-1][0]:g},{base}" fill="url(#{gradient})"/>'
        f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="2.2" stroke-linejoin="round"/>'
        f'<circle cx="{xy[-1][0]:g}" cy="{xy[-1][1]:g}" r="4" fill="{color}" stroke="var(--tg-theme-bg-color,#fff)" stroke-width="2"/>'
    )


def _label(x: float, height: int, text: str, anchor: str = "middle") -> str:
    return f'<text x="{x:g}" y="{height - 1}" font-size="10" text-anchor="{anchor}" fill="var(--tg-theme-hint-color,#8b98a5)">{esc(text)}</text>'


def week_chart(days: list[dict], low: float | None, high: float | None) -> str:
    days = [{"label": d["label"], "values": downsample(d["values"], MAX_DAY_POINTS)} for d in days if d["values"]]
    flat = [v for d in days for v in d["values"]]
    if len(flat) < 2:
        return ""
    y, lo, hi = _scaler(flat + [v for v in (low, high) if v is not None], WEEK_HEIGHT)
    step = WIDTH / (len(flat) - 1)
    parts = [f'<svg viewBox="0 0 {WIDTH} {WEEK_HEIGHT}">']
    if high is not None:
        parts.append(f'<rect x="0" y="{y(hi):g}" width="{WIDTH}" height="{max(y(high) - y(hi), 0):g}" fill="{UP}" opacity=".12"/>')
    if low is not None:
        parts.append(f'<rect x="0" y="{y(low):g}" width="{WIDTH}" height="{max(y(lo) - y(low), 0):g}" fill="{DOWN}" opacity=".10"/>')
    index = 0
    labels: list[tuple[float, str, str]] = []
    for number, day in enumerate(days):
        x0 = index * step
        x1 = (index + len(day["values"]) - 1) * step
        if number > 0:
            parts.append(f'<line x1="{x0:.1f}" y1="0" x2="{x0:.1f}" y2="{WEEK_HEIGHT - AXIS_GAP}" stroke="{FLAT}" stroke-opacity=".35" stroke-dasharray="2 3"/>')
        last = number == len(days) - 1
        labels.append((round(x1 if last else (x0 + x1) / 2, 1), day["label"], "end" if last else "middle"))
        index += len(day["values"])
    shown: list[float] = []
    for x, text, anchor in reversed(labels):
        if all(abs(x - other) >= MIN_LABEL_GAP for other in shown):
            shown.append(x)
            parts.append(_label(x, WEEK_HEIGHT, text, anchor))
    xy = [(round(i * step, 1), y(v)) for i, v in enumerate(flat)]
    parts.append(_line(xy, TRENDS[trend(flat[0], flat[-1])], WEEK_HEIGHT, "w"))
    parts.append("</svg>")
    return "".join(parts)


def day_chart(values: list[float], night_from: int | None, start_label: str, end_label: str) -> str:
    if len(values) < 2:
        return ""
    if night_from is not None:
        day_part = downsample(values[:night_from], MAX_LINE_POINTS - 8)
        night_part = downsample(values[night_from:], 8)
        night_from = len(day_part)
        values = day_part + night_part
    else:
        values = downsample(values, MAX_LINE_POINTS)
    y, _, _ = _scaler(values, DAY_HEIGHT)
    step = WIDTH / (len(values) - 1)
    parts = [f'<svg viewBox="0 0 {WIDTH} {DAY_HEIGHT}">']
    if night_from is not None and night_from < len(values):
        x = round(max(night_from - 1, 0) * step, 1)
        parts.append(f'<rect x="{x:g}" y="0" width="{WIDTH - x:g}" height="{DAY_HEIGHT - AXIS_GAP}" fill="{FLAT}" opacity=".14"/>')
    xy = [(round(i * step, 1), y(v)) for i, v in enumerate(values)]
    parts.append(_line(xy, TRENDS[trend(values[0], values[-1])], DAY_HEIGHT, "d"))
    parts.append(_label(0, DAY_HEIGHT, start_label, "start"))
    parts.append(_label(WIDTH, DAY_HEIGHT, end_label, "end"))
    parts.append("</svg>")
    return "".join(parts)


def chip(code: str) -> str:
    color, text = CHIPS[code]
    return f'<span class="c" style="color:{color};background:{color}22">{text}</span>'


def position_bar(share: float, code: str) -> str:
    color = CHIPS.get(code, (FLAT, ""))[0]
    left = min(max(round(share * 100), 3), 97)
    return f'<div class="bar"><div class="dot" style="left:{left}%;background:{color}"></div></div>'


def cbr_row(cbr: dict | None) -> str:
    if cbr is None:
        return '<div class="row m"><span>ЦБ: курса нет</span></div>'
    color = TRENDS.get(cbr.get("trend") or "", None) if cbr.get("trend") != "flat" else None
    style = f' style="color:{color}"' if color else ""
    hint = f' <span class="m">· {esc(cbr["hint"])}</span>' if cbr.get("hint") else ""
    return f'<div class="row"><span class="m">{esc(cbr["label"])}</span><span>разница <b{style}>{esc(cbr["spread"])}</b>{hint}</span></div>'


def render(view: dict) -> str:
    code = view["verdict"]
    head_left = (f'<b style="color:{CHIPS[code][0]}">{esc(view["headline"])}</b>' if view.get("headline") else f'<b>{esc(view["title"])}</b>')
    body = [
        f'<div class="h">{head_left}<span class="m">{esc(view["subtitle"])}</span></div>',
        f'<div class="r">{esc(view["rate"])}<span class="u m">{esc(view["unit"])}</span></div>',
    ]
    if view.get("delta"):
        delta_color = TRENDS[view["delta"]["trend"]]
        body.append(f'<div class="row"><span style="color:{delta_color}">{esc(view["delta"]["text"])}</span><span class="m">{esc(view.get("note") or "")}</span></div>')
    else:
        body.append(f'<div class="h" style="margin-top:6px">{chip(code)}<span class="m">{esc(view.get("note") or "")}</span></div>')
    if view.get("position") is not None:
        body.append(position_bar(view["position"], code))
        if view.get("position_labels"):
            body.append('<div class="row m"><span>хуже за неделю</span><span>лучше</span></div>')
    chart = view.get("chart") or {}
    if chart.get("kind") == "week":
        body.append(week_chart(chart["days"], chart.get("low"), chart.get("high")))
    elif chart.get("kind") == "day":
        body.append(day_chart(chart["values"], chart.get("night_from"), chart["start_label"], chart["end_label"]))
    body.append(cbr_row(view.get("cbr")))
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head><body>{"".join(body)}</body></html>'


def fits(document: str) -> bool:
    raw = document.encode()
    return len(raw) <= MAX_HTML_BYTES and len(zlib.compress(raw, 9)) <= MAX_PACKED_BYTES
