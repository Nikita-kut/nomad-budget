import { TBANK_CA } from "./tbank_ca.ts";

export type RefreshContext = {
  provider?: string | null;
  from: string;
  to: string;
  target: number | null;
  threshold: number;
  values: number[];
  median_spread: number | null;
  warmup: boolean;
  cbr: number | null;
  trade_open: number;
  trade_close: number;
  timezone: string;
  holidays: string[];
};

type Assessment = { warmup: boolean; below: number; above: number; kind: string | null };

const NBSP = " ";
const MINUS = "−";
const MAX_QUOTE_UNIT = 1_000_000;
const SHARE_EPSILON = 1e-9;
const EXCHANGE_TIMEZONE = "Europe/Moscow";
const TBANK_URL = "https://api.tinkoff.ru/v1/currency_rates";
const CATEGORY_EXCHANGE = "DebitCardsTransfers";
const FETCH_TIMEOUT_MS = 4000;
const ZONE_LABELS: Record<string, string> = { "Europe/Moscow": "МСК" };
const VERDICT_ICONS: Record<string, string> = {
  good: "🟢",
  bad: "🔴",
  normal: "⚪️",
  warmup: "⏳",
  night: "🌙",
  weekend: "💤",
};
const VERDICT_TEXTS: Record<string, string> = {
  good: "выгодно",
  bad: "не меняй",
  normal: "обычно",
  warmup: "мало данных для сравнения",
  night: "биржа закрыта, ночной курс",
  weekend: "биржа закрыта, курс выходного дня",
};

export function groupThousands(value: number): string {
  const digits = String(value);
  let out = "";
  for (let index = 0; index < digits.length; index++) {
    const remaining = digits.length - index;
    if (index > 0 && remaining % 3 === 0) out += NBSP;
    out += digits[index];
  }
  return out;
}

function formatNumber(value: number, decimals: number): string {
  const factor = 10 ** decimals;
  const scaled = Math.floor(Math.abs(value) * factor + 0.5);
  const whole = Math.floor(scaled / factor);
  const fraction = scaled % factor;
  const sign = value < 0 && scaled !== 0 ? MINUS : "";
  if (decimals === 0) return `${sign}${groupThousands(whole)}`;
  return `${sign}${groupThousands(whole)},${String(fraction).padStart(decimals, "0")}`;
}

export function formatQuote(rate: number): string {
  if (rate >= 100) return groupThousands(Math.floor(rate + 0.5));
  if (rate >= 1) return formatNumber(rate, 2);
  return formatNumber(rate, 4);
}

export function quoteUnit(value: number): number {
  let unit = 1;
  const absolute = Math.abs(value);
  while (absolute > 0 && absolute * unit < 1 && unit < MAX_QUOTE_UNIT) unit *= 10;
  return unit;
}

function roundHalfEven(value: number): number {
  const floor = Math.floor(value);
  const diff = value - floor;
  if (diff > 0.5) return floor + 1;
  if (diff < 0.5) return floor;
  return floor % 2 === 0 ? floor : floor + 1;
}

function formatPercent(share: number): string {
  const tenths = roundHalfEven(share * 1000);
  if (tenths === 0) return "0 %";
  const sign = tenths > 0 ? "+" : MINUS;
  const absolute = Math.abs(tenths);
  const whole = Math.floor(absolute / 10);
  const fraction = absolute % 10;
  const text = fraction === 0 ? `${whole}` : `${whole},${fraction}`;
  return `${sign}${text}${NBSP}%`;
}

function formatShare(share: number): string {
  return `${Math.floor(share * 100 + 0.5)}${NBSP}%`;
}

function unitLabel(unit: number, code: string): string {
  return `${groupThousands(unit)}${NBSP}${code}`;
}

function spread(bank: number, cbr: number | null): number | null {
  if (!cbr) return null;
  return bank / cbr - 1;
}

function assess(context: RefreshContext, rate: number): Assessment {
  const values = context.values;
  const below = values.length ? values.filter((value) => value < rate).length / values.length : 0;
  const above = values.length ? values.filter((value) => value > rate).length / values.length : 0;
  const nowSpread = spread(rate, context.cbr);
  const weekSpread = context.median_spread;
  const spreadOk = weekSpread === null || nowSpread === null || nowSpread > weekSpread;
  const good = !context.warmup && below >= context.threshold - SHARE_EPSILON && spreadOk;
  const bad = !context.warmup && above >= context.threshold - SHARE_EPSILON;
  return { warmup: context.warmup, below, above, kind: good ? "good" : bad ? "bad" : null };
}

type LocalParts = { year: number; month: number; day: number; hour: number; minute: number; weekday: number };

function localParts(now: Date, timeZone: string): LocalParts {
  let zone = timeZone;
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: zone });
  } catch {
    zone = EXCHANGE_TIMEZONE;
  }
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-US", {
      timeZone: zone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    }).formatToParts(now).map((part) => [part.type, part.value]),
  );
  const year = Number(parts.year);
  const month = Number(parts.month);
  const day = Number(parts.day);
  const weekday = (new Date(Date.UTC(year, month - 1, day)).getUTCDay() + 6) % 7;
  return { year, month, day, hour: Number(parts.hour), minute: Number(parts.minute), weekday };
}

function isoDate(parts: LocalParts): string {
  return `${parts.year}-${String(parts.month).padStart(2, "0")}-${String(parts.day).padStart(2, "0")}`;
}

function verdict(assessment: Assessment | null, exchange: LocalParts, holidays: Set<string>): string {
  if (assessment === null) return exchange.weekday < 5 && !holidays.has(isoDate(exchange)) ? "night" : "weekend";
  if (assessment.kind) return assessment.kind;
  return assessment.warmup ? "warmup" : "normal";
}

function zoneLabel(now: Date, timeZone: string, local: LocalParts): string {
  const known = ZONE_LABELS[timeZone];
  if (known) return known;
  const asUtc = Date.UTC(local.year, local.month - 1, local.day, local.hour, local.minute);
  const offsetMinutes = Math.round((asUtc - Math.floor(now.getTime() / 60000) * 60000) / 60000);
  if (offsetMinutes === 180) return "МСК";
  const sign = offsetMinutes < 0 ? "-" : "+";
  return `UTC${sign}${String(Math.floor(Math.abs(offsetMinutes) / 60)).padStart(2, "0")}`;
}

function clock(now: Date, timeZone: string): string {
  const local = localParts(now, timeZone);
  const time = `${String(local.hour).padStart(2, "0")}:${String(local.minute).padStart(2, "0")}`;
  return `${time} ${zoneLabel(now, timeZone, local)}`;
}

export function composeNow(context: RefreshContext, rate: number, now: Date): string {
  const holidays = new Set(context.holidays);
  const exchange = localParts(now, EXCHANGE_TIMEZONE);
  const minute = exchange.hour * 60 + exchange.minute;
  const tradingDay = exchange.weekday < 5 && !holidays.has(isoDate(exchange));
  const trading = tradingDay && context.trade_open <= minute && minute < context.trade_close;
  const assessment = trading ? assess(context, rate) : null;
  const code = verdict(assessment, exchange, holidays);
  const unit = quoteUnit(rate);
  const lines = [
    `💱 <b>${context.from} → ${context.to}</b>`,
    `<b>${formatQuote(rate * unit)}${NBSP}${context.to}</b> за ${unitLabel(unit, context.from)} · ${VERDICT_ICONS[code]} ${VERDICT_TEXTS[code]}`,
    context.cbr === null
      ? "ЦБ: курса нет"
      : `ЦБ ${formatQuote(context.cbr * unit)} · разница ${formatPercent(spread(rate, context.cbr) ?? 0)}`,
  ];
  if (assessment !== null && !assessment.warmup) lines.push(`📊 лучше ${formatShare(assessment.below)} замеров недели`);
  if (context.target !== null && rate >= context.target) lines.push(`🎯 выше цели ${formatQuote(context.target * unit)}`);
  lines.push(`<i>курс на ${clock(now, context.timezone)}</i>`);
  return lines.join("\n");
}

let client: Deno.HttpClient | null = null;

export async function fetchExchangeRate(context: RefreshContext): Promise<number | null> {
  if ((context.provider ?? "tbank") !== "tbank") return null;
  client ??= Deno.createHttpClient({ caCerts: [TBANK_CA] });
  const query = new URLSearchParams({ from: context.from, to: context.to });
  const response = await fetch(`${TBANK_URL}?${query}`, { client, signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
  if (!response.ok) return null;
  const payload = await response.json() as { payload?: { rates?: { category?: string; buy?: unknown }[] } };
  const entry = (payload.payload?.rates ?? []).find((item) => item.category === CATEGORY_EXCHANGE);
  const buy = Number(entry?.buy);
  return Number.isFinite(buy) && buy > 0 ? buy : null;
}
