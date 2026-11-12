export const DEFAULT_PAUSE_HOURS = 24;
export const MAX_PAUSE_HOURS = 30 * 24;

const UNIT_HOURS: [RegExp, number][] = [
  [/^(ч|час|часа|часов)$/, 1],
  [/^(д|дн|день|дня|дней|)$/, 24],
  [/^(н|нед|неделя|недели|недель|неделю)$/, 24 * 7],
];

export type PauseRequest = { hours: number } | { error: string };

export function parsePause(argument: string): PauseRequest {
  const text = argument.trim().toLowerCase().replace(",", ".");
  if (!text) return { hours: DEFAULT_PAUSE_HOURS };
  const match = text.match(/^(\d+(?:\.\d+)?)\s*([а-яё]*)\.?$/);
  if (!match) return { error: USAGE };
  const amount = Number(match[1]);
  const unit = UNIT_HOURS.find(([pattern]) => pattern.test(match[2]));
  if (!unit || !(amount > 0)) return { error: USAGE };
  const hours = Math.round(amount * unit[1]);
  if (hours < 1) return { error: USAGE };
  if (hours > MAX_PAUSE_HOURS) return { error: "Пауза — не больше 30 дней. Отписаться совсем — /stop" };
  return { hours };
}

export const USAGE = "Сколько молчать: /pause 12ч, /pause 3д или /pause 2н. Просто /pause — на сутки, максимум 30 дней.";

export function moscowTime(moment: Date): string {
  const parts = new Intl.DateTimeFormat("ru-RU", {
    timeZone: "Europe/Moscow", day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(moment);
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return `${part("day")}.${part("month")} в ${part("hour")}:${part("minute")} МСК`;
}

export function pauseText(until: Date): string {
  return `Пауза до ${moscowTime(until)}: сигналы и сводки не приходят, курс по-прежнему по /rate.\nВернуть раньше — /resume`;
}

export function isPaused(pausedUntil: string | null | undefined, now: Date): boolean {
  return !!pausedUntil && new Date(pausedUntil).getTime() > now.getTime();
}
