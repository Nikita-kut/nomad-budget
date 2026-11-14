import { CORS_HEADERS, GENERIC_INVITE_ERROR, json, normalizeEmail, passwordProblem, sha256Hex, validCode } from "../_shared/access.ts";

const EXISTS_TEXT = "С этой почтой уже есть учётка — войди или попроси владельца сбросить пароль";
const RETRY_TEXT = "Не удалось создать учётку, попробуй ещё раз";

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

async function handle(request: Request): Promise<Response> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return json(400, { error: GENERIC_INVITE_ERROR });
  }
  if (!body || typeof body !== "object") return json(400, { error: GENERIC_INVITE_ERROR });
  const input = body as Record<string, unknown>;
  const email = normalizeEmail(input.email);
  const code = validCode(input.code);
  if (!email || !code) return json(400, { error: GENERIC_INVITE_ERROR });
  const weak = passwordProblem(input.password);
  if (weak) return json(400, { error: weak });

  const claim = await rpc("claim_invite", { p_code_hash: await sha256Hex(code), p_email: email }) as string | null;
  if (!claim) return json(400, { error: GENERIC_INVITE_ERROR });

  const created = await service("/auth/v1/admin/users", {
    method: "POST",
    body: JSON.stringify({ email, password: input.password, email_confirm: true }),
  });
  if (!created.ok) {
    const error = await created.json().catch(() => ({})) as { error_code?: string };
    console.log(`redeem-invite: create user failed ${created.status} ${error.error_code ?? ""}`);
    if (created.status < 500) await rpc("release_invite", { p_claim: claim });
    if (error.error_code === "email_exists") return json(400, { error: EXISTS_TEXT });
    if (error.error_code === "weak_password") return json(400, { error: "Пароль слишком простой, придумай другой" });
    return json(400, { error: RETRY_TEXT });
  }
  const user = await created.json() as { id: string };
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      await rpc("complete_invite", { p_claim: claim, p_user: user.id });
      return json(200, { ok: true });
    } catch {
      console.log(`redeem-invite: complete failed, attempt ${attempt + 1}`);
    }
  }
  return json(200, { ok: true });
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });
  if (request.method !== "POST") return json(405, { error: "method" });
  try {
    return await handle(request);
  } catch {
    console.log("redeem-invite: internal error");
    return json(500, { error: RETRY_TEXT });
  }
});
