export const MIN_PASSWORD = 10;
export const INVITE_DAYS_DEFAULT = 7;
export const INVITE_DAYS_MAX = 30;
export const BAN_FOREVER = "876000h";
export const GENERIC_INVITE_ERROR = "Приглашение не подошло: проверь почту или попроси новое";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE = /^[A-Za-z0-9_-]{32,64}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function normalizeEmail(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const email = raw.trim().toLowerCase();
  return email.length <= 254 && EMAIL.test(email) ? email : null;
}

export function validCode(raw: unknown): string | null {
  return typeof raw === "string" && CODE.test(raw) ? raw : null;
}

export function validUserId(raw: unknown): string | null {
  return typeof raw === "string" && UUID.test(raw) ? raw : null;
}

export function passwordProblem(raw: unknown): string | null {
  if (typeof raw !== "string" || raw.length < MIN_PASSWORD) return `Пароль — не короче ${MIN_PASSWORD} символов`;
  if (new TextEncoder().encode(raw).length > 72) return "Пароль слишком длинный: до 72 латинских или 36 русских букв";
  return null;
}

export function inviteDays(raw: unknown): number {
  const days = typeof raw === "number" && Number.isInteger(raw) ? raw : INVITE_DAYS_DEFAULT;
  return Math.min(Math.max(days, 1), INVITE_DAYS_MAX);
}

export function newCode(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64url(bytes);
}

export function base64url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function siteLink(site: string, param: "invite" | "reset", value: string): string {
  const base = site.endsWith("/") ? site : `${site}/`;
  return `${base}#${param}=${encodeURIComponent(value)}`;
}

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

export type AuthUser = { id: string; email?: string; created_at?: string; last_sign_in_at?: string | null; banned_until?: string | null };

export function userRow(user: AuthUser, now: Date): Record<string, unknown> {
  return {
    id: user.id,
    email: user.email ?? null,
    created_at: user.created_at ?? null,
    last_sign_in_at: user.last_sign_in_at ?? null,
    blocked: !!user.banned_until && new Date(user.banned_until).getTime() > now.getTime(),
  };
}

export const ADMIN_TABLES = ["invites", "owner_actions", "account_blocks", "app_admins"] as const;
export const ADMIN_RPCS = ["is_admin"] as const;
