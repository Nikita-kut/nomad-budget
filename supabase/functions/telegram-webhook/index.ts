import { MAX_PAUSE_HOURS, parsePause, pauseText } from "./pause.ts";

const SECRET = Deno.env.get("TELEGRAM_WEBHOOK_SECRET") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const HELP_TEXT = "Бот присылает курс банка и сигналы, когда менять выгодно.\n/rate — курс сейчас\n/pause 3д — помолчать (12ч, 3д, 2н; до 30 дней)\n/resume — снова присылать\n/stop — отписаться\nПодписка включается по ссылке из приложения.";
const STOP_TEXT = "Подписка выключена.";
const NOT_SUBSCRIBED_TEXT = "Ты пока не подписан. Подписка включается по ссылке из приложения.";
const NOT_CHECKED_TEXT = "Курс ещё не проверялся, загляни через 15 минут.";
const BAD_LINK_TEXT = "Ссылка не подошла: открой приложение и нажми «Подписаться в Telegram» ещё раз.";
const TITLE_LIMIT = 64;
const RESUME_TEXT = "Пауза снята, сигналы и сводки снова приходят.";
const NOT_PAUSED_TEXT = "Паузы нет, сигналы и сводки приходят.";
const HOUR_MS = 3_600_000;

type Chat = { id: number; first_name?: string; username?: string; title?: string };
type WatchState = { now_text?: string; now_widget?: string; now_caption?: string; widgets_ok?: boolean };
type Watch = { id: string; user_id?: string; from_currency: string; to_currency: string; state?: WatchState };
type Answer = { text: string; widget?: { html: string; caption: string } };

async function rest(path: string, init: RequestInit = {}): Promise<unknown> {
  const response = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
  if (!response.ok) throw new Error(`rest ${response.status}`);
  const body = await response.text();
  return body ? JSON.parse(body) : null;
}

function subscribeText(watch: Watch): string {
  return `Подписка на курс включена: <b>${watch.from_currency} → ${watch.to_currency}</b>, обмен в приложении.\n` +
    "Курс сейчас — /rate, отписаться — /stop";
}

async function isBlocked(userId: string | undefined): Promise<boolean> {
  if (!userId) return false;
  const rows = await rest(`account_blocks?select=user_id&user_id=eq.${encodeURIComponent(userId)}`) as unknown[];
  return rows.length > 0;
}

async function nowAnswer(chatId: number): Promise<Answer> {
  const all = await rest(`telegram_subscribers?select=rate_watches(user_id,state)&chat_id=eq.${chatId}`) as { rate_watches?: Watch }[];
  const rows = [];
  for (const row of all) {
    if (!(await isBlocked(row.rate_watches?.user_id))) rows.push(row);
  }
  if (rows.length === 0) return { text: NOT_SUBSCRIBED_TEXT };
  const states = rows.map((row) => row.rate_watches?.state).filter((state): state is WatchState => !!state);
  const texts = states.map((state) => state.now_text).filter((text): text is string => !!text);
  const text = texts.length > 0 ? texts.join("\n\n") : NOT_CHECKED_TEXT;
  const only = states.length === 1 ? states[0] : undefined;
  if (only?.widgets_ok === true && only.now_widget && only.now_caption) {
    return { text, widget: { html: only.now_widget, caption: only.now_caption } };
  }
  return { text };
}

async function subscribe(chat: Chat, code: string): Promise<string> {
  const watches = await rest(`rate_watches?select=id,user_id,from_currency,to_currency,state&link_code=eq.${encodeURIComponent(code)}&limit=1`) as Watch[];
  const watch = watches[0];
  if (!watch || await isBlocked(watch.user_id)) return BAD_LINK_TEXT;
  const title = (chat.first_name ?? chat.username ?? chat.title ?? "").slice(0, TITLE_LIMIT) || null;
  await rest("telegram_subscribers?on_conflict=watch_id,chat_id", {
    method: "POST",
    headers: { Prefer: "resolution=ignore-duplicates,return=minimal" },
    body: JSON.stringify({ watch_id: watch.id, chat_id: chat.id, title }),
  });
  const now = watch.state?.now_text;
  return now ? `${subscribeText(watch)}\n\n${now}` : subscribeText(watch);
}

async function isSubscribed(chatId: number): Promise<boolean> {
  const rows = await rest(`telegram_subscribers?select=id&chat_id=eq.${chatId}&limit=1`) as unknown[];
  return rows.length > 0;
}

async function setPause(chatId: number, until: Date | null): Promise<void> {
  await rest("telegram_chat_prefs?on_conflict=chat_id", {
    method: "POST",
    headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify({ chat_id: chatId, paused_until: until?.toISOString() ?? null, updated_at: new Date().toISOString() }),
  });
}

async function pause(chatId: number, argument: string): Promise<string> {
  if (!(await isSubscribed(chatId))) return NOT_SUBSCRIBED_TEXT;
  const request = parsePause(argument);
  if ("error" in request) return request.error;
  const until = new Date(Date.now() + Math.min(request.hours, MAX_PAUSE_HOURS) * HOUR_MS);
  await setPause(chatId, until);
  return pauseText(until);
}

async function resume(chatId: number): Promise<string> {
  const rows = await rest(`telegram_chat_prefs?select=paused_until&chat_id=eq.${chatId}&paused_until=gt.${encodeURIComponent(new Date().toISOString())}`) as unknown[];
  if (rows.length === 0) return NOT_PAUSED_TEXT;
  await setPause(chatId, null);
  return RESUME_TEXT;
}

async function reply(chat: Chat, text: string): Promise<Answer> {
  const [head, ...rest_] = text.trim().split(/\s+/);
  const command = (head ?? "").split("@")[0].toLowerCase();
  const argument = rest_.join(" ").trim();
  if (command === "/start" && argument) return { text: await subscribe(chat, argument) };
  if (command === "/start" || command === "/help") return { text: HELP_TEXT };
  if (command === "/pause") return { text: await pause(chat.id, argument) };
  if (command === "/resume") return { text: await resume(chat.id) };
  if (command === "/stop") {
    await rest(`telegram_subscribers?chat_id=eq.${chat.id}`, { method: "DELETE", headers: { Prefer: "return=minimal" } });
    await rest(`telegram_chat_prefs?chat_id=eq.${chat.id}`, { method: "DELETE", headers: { Prefer: "return=minimal" } });
    return { text: STOP_TEXT };
  }
  return await nowAnswer(chat.id);
}

Deno.serve(async (request) => {
  if (request.method !== "POST" || !SECRET || request.headers.get("X-Telegram-Bot-Api-Secret-Token") !== SECRET) {
    return new Response("forbidden", { status: 403 });
  }
  const update = await request.json().catch(() => null);
  const message = update?.message;
  const chat = message?.chat as Chat | undefined;
  const text = typeof message?.text === "string" ? message.text : "";
  if (!chat || !text.trim()) return new Response("ok");
  try {
    const answer = await reply(chat, text);
    if (answer.widget) {
      return Response.json({ method: "sendWidget", chat_id: chat.id, html: answer.widget.html, caption: answer.widget.caption, parse_mode: "HTML" });
    }
    return Response.json({ method: "sendMessage", chat_id: chat.id, text: answer.text, parse_mode: "HTML", disable_web_page_preview: true });
  } catch (error) {
    console.error(`webhook failed: ${error instanceof Error ? error.message : "unknown"}`);
    return new Response("ok");
  }
});
