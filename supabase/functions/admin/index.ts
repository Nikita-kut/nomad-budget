import {
  type AuthUser,
  BAN_FOREVER,
  CORS_HEADERS,
  inviteDays,
  json,
  newCode,
  normalizeEmail,
  sha256Hex,
  siteLink,
  userRow,
  validUserId,
} from "../_shared/access.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SITE_URL = Deno.env.get("SITE_URL") ?? "http://127.0.0.1:3000/";

class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

async function call(path: string, init: RequestInit, key: string, token: string): Promise<unknown> {
  const response = await fetch(`${SUPABASE_URL}${path}`, {
    ...init,
    headers: { apikey: key, Authorization: `Bearer ${token}`, "Content-Type": "application/json", ...(init.headers ?? {}) },
  });
  if (!response.ok) throw new HttpError(response.status >= 500 ? 502 : 400, `${path.split("?")[0]} ${response.status}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

const service = (path: string, init: RequestInit = {}) => call(path, init, SERVICE_KEY, SERVICE_KEY);

async function requireAdmin(request: Request): Promise<string> {
  const token = (request.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) throw new HttpError(401, "no token");
  const user = await call("/auth/v1/user", { method: "GET" }, ANON_KEY, token).catch(() => null) as { id?: string } | null;
  if (!user?.id) throw new HttpError(401, "bad token");
  const admin = await call("/rest/v1/rpc/is_admin", { method: "POST", body: "{}" }, ANON_KEY, token);
  if (admin !== true) throw new HttpError(403, "not admin");
  return user.id;
}

async function getUser(id: string): Promise<AuthUser> {
  return await service(`/auth/v1/admin/users/${id}`, { method: "GET" }) as AuthUser;
}

async function isAdmin(userId: string): Promise<boolean> {
  const rows = await service(`/rest/v1/app_admins?select=user_id&user_id=eq.${userId}`, { method: "GET" }) as unknown[];
  return rows.length > 0;
}

async function audit(userId: string, action: string): Promise<void> {
  await service("/rest/v1/owner_actions", { method: "POST", body: JSON.stringify({ user_id: userId, action }) });
}

async function handle(action: string, body: Record<string, unknown>, adminId: string): Promise<unknown> {
  switch (action) {
    case "invite_create": {
      const email = normalizeEmail(body.email);
      if (!email) throw new HttpError(400, "email");
      const code = newCode();
      const expires = new Date(Date.now() + inviteDays(body.days) * 86_400_000).toISOString();
      await service("/rest/v1/invites", {
        method: "POST",
        body: JSON.stringify({ email, code_hash: await sha256Hex(code), created_by: adminId, expires_at: expires }),
      });
      return { url: siteLink(SITE_URL, "invite", code), email, expires_at: expires };
    }
    case "invite_list":
      return await service("/rest/v1/invites?select=id,email,created_at,expires_at,used_at,revoked_at&order=created_at.desc", { method: "GET" });
    case "invite_revoke": {
      const id = validUserId(body.id);
      if (!id) throw new HttpError(400, "id");
      await service(`/rest/v1/invites?id=eq.${id}&used_at=is.null`, { method: "PATCH", body: JSON.stringify({ revoked_at: new Date().toISOString() }) });
      return { ok: true };
    }
    case "users_list": {
      const page = await service("/auth/v1/admin/users?page=1&per_page=200", { method: "GET" }) as { users: AuthUser[] };
      const now = new Date();
      return page.users.map((u) => userRow(u, now));
    }
    case "reset_link": {
      const id = validUserId(body.user_id);
      if (!id) throw new HttpError(400, "user_id");
      if (id === adminId || await isAdmin(id)) throw new HttpError(400, "admin");
      const user = await getUser(id);
      const link = await service("/auth/v1/admin/generate_link", {
        method: "POST",
        body: JSON.stringify({ type: "recovery", email: user.email }),
      }) as { hashed_token?: string; properties?: { hashed_token?: string } };
      const token = link.hashed_token ?? link.properties?.hashed_token;
      if (!token) throw new HttpError(502, "no token");
      await audit(id, "reset_link_created");
      return { url: siteLink(SITE_URL, "reset", token) };
    }
    case "block":
    case "unblock": {
      const id = validUserId(body.user_id);
      if (!id) throw new HttpError(400, "user_id");
      if (id === adminId || await isAdmin(id)) throw new HttpError(400, "admin");
      const blocking = action === "block";
      if (blocking) {
        await service("/rest/v1/account_blocks", { method: "POST", headers: { Prefer: "resolution=merge-duplicates" }, body: JSON.stringify({ user_id: id }) });
        await service(`/auth/v1/admin/users/${id}`, { method: "PUT", body: JSON.stringify({ ban_duration: BAN_FOREVER }) });
      } else {
        await service(`/auth/v1/admin/users/${id}`, { method: "PUT", body: JSON.stringify({ ban_duration: "none" }) });
        await service(`/rest/v1/account_blocks?user_id=eq.${id}`, { method: "DELETE" });
      }
      await audit(id, blocking ? "blocked" : "unblocked");
      return { ok: true };
    }
    default:
      throw new HttpError(400, "action");
  }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });
  if (request.method !== "POST") return json(405, { error: "method" });
  try {
    const adminId = await requireAdmin(request);
    const body = await request.json().catch(() => ({})) as Record<string, unknown>;
    const action = typeof body.action === "string" ? body.action : "";
    return json(200, await handle(action, body, adminId));
  } catch (error) {
    if (error instanceof HttpError) {
      console.log(`admin: ${error.status} ${error.message}`);
      return json(error.status, { error: error.message });
    }
    console.log("admin: internal error");
    return json(500, { error: "internal" });
  }
});
