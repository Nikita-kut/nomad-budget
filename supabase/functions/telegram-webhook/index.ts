const SECRET = Deno.env.get("TELEGRAM_WEBHOOK_SECRET") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const HELP_TEXT = "Бот присылает курс банка и сигналы, когда менять выгодно.\n/rate — курс сейчас\n/stop — отписаться\nПодписка включается по ссылке из приложения.";
const STOP_TEXT = "Подписка выключена.";
const NOT_SUBSCRIBED_TEXT = "Ты пока не подписан. Подписка включается по ссылке из приложения.";
const NOT_CHECKED_TEXT = "Курс ещё не проверялся, загляни через 15 минут.";
const BAD_LINK_TEXT = "Ссылка не подошла: открой приложение и нажми «Подписаться в Telegram» ещё раз.";
const TITLE_LIMIT = 64;

type Chat = { id: number; first_name?: string; username?: string; title?: string };
type Watch = { id: string; from_currency: string; to_currency: string; state?: { now_text?: string } };

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

async function nowText(chatId: number): Promise<string> {
  const rows = await rest(`telegram_subscribers?select=rate_watches(state)&chat_id=eq.${chatId}`) as { rate_watches?: Watch }[];
  if (rows.length === 0) return NOT_SUBSCRIBED_TEXT;
  const texts = rows.map((row) => row.rate_watches?.state?.now_text).filter((text): text is string => !!text);
  return texts.length > 0 ? texts.join("\n\n") : NOT_CHECKED_TEXT;
}

async function subscribe(chat: Chat, code: string): Promise<string> {
  const watches = await rest(`rate_watches?select=id,from_currency,to_currency,state&link_code=eq.${encodeURIComponent(code)}&limit=1`) as Watch[];
  const watch = watches[0];
  if (!watch) return BAD_LINK_TEXT;
  const title = (chat.first_name ?? chat.username ?? chat.title ?? "").slice(0, TITLE_LIMIT) || null;
  await rest("telegram_subscribers?on_conflict=watch_id,chat_id", {
    method: "POST",
    headers: { Prefer: "resolution=ignore-duplicates,return=minimal" },
    body: JSON.stringify({ watch_id: watch.id, chat_id: chat.id, title }),
  });
  const now = watch.state?.now_text;
  return now ? `${subscribeText(watch)}\n\n${now}` : subscribeText(watch);
}

async function reply(chat: Chat, text: string): Promise<string> {
  const [head, ...rest_] = text.trim().split(/\s+/);
  const command = (head ?? "").split("@")[0].toLowerCase();
  const argument = rest_.join(" ").trim();
  if (command === "/start" && argument) return await subscribe(chat, argument);
  if (command === "/start" || command === "/help") return HELP_TEXT;
  if (command === "/stop") {
    await rest(`telegram_subscribers?chat_id=eq.${chat.id}`, { method: "DELETE", headers: { Prefer: "return=minimal" } });
    return STOP_TEXT;
  }
  return await nowText(chat.id);
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
    return Response.json({ method: "sendMessage", chat_id: chat.id, text: answer, parse_mode: "HTML", disable_web_page_preview: true });
  } catch (error) {
    console.error(`webhook failed: ${error instanceof Error ? error.message : "unknown"}`);
    return new Response("ok");
  }
});
