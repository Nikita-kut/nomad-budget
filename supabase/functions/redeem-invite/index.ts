import { CORS_HEADERS, GENERIC_INVITE_ERROR, json, normalizeEmail, passwordProblem, sha256Hex, validCode } from "../_shared/access.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

function service(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${SUPABASE_URL}${path}`, {
    ...init,
    headers: { apikey: SERVICE_KEY, Authorization: `Bearer ${SERVICE_KEY}`, "Content-Type": "application/json", ...(init.headers ?? {}) },
  });
}

async function rpc(name: string, args: Record<string, unknown>): Promise<unknown> {
  const response = await service(`/rest/v1/rpc/${name}`, { method: "POST", body: JSON.stringify(args) });
  if (!response.ok) throw new Error(`rpc ${name} ${response.status}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });
  if (request.method !== "POST") return json(405, { error: "method" });

  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return json(400, { error: GENERIC_INVITE_ERROR });
  }
  const email = normalizeEmail(body.email);
  const code = validCode(body.code);
  if (!email || !code) return json(400, { error: GENERIC_INVITE_ERROR });
  const weak = passwordProblem(body.password);
  if (weak) return json(400, { error: weak });

  const inviteId = await rpc("claim_invite", { p_code_hash: await sha256Hex(code), p_email: email }) as string | null;
  if (!inviteId) return json(400, { error: GENERIC_INVITE_ERROR });

  const created = await service("/auth/v1/admin/users", {
    method: "POST",
    body: JSON.stringify({ email, password: body.password, email_confirm: true }),
  });
  if (!created.ok) {
    await rpc("release_invite", { p_id: inviteId });
    const exists = created.status === 422;
    console.log(`redeem-invite: create user failed ${created.status}`);
    return json(400, { error: exists ? "С этой почтой уже есть учётка — войди или попроси владельца сбросить пароль" : "Не удалось создать учётку, попробуй ещё раз" });
  }
  const user = await created.json() as { id: string };
  await rpc("complete_invite", { p_id: inviteId, p_user: user.id });
  return json(200, { ok: true });
});
