import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import {
  ADMIN_RPCS,
  ADMIN_TABLES,
  INVITE_DAYS_DEFAULT,
  INVITE_DAYS_MAX,
  inviteDays,
  newCode,
  normalizeEmail,
  passwordProblem,
  sha256Hex,
  siteLink,
  userRow,
  validCode,
  validUserId,
} from "./access.ts";

test("email is trimmed, lowercased and validated", () => {
  assert.equal(normalizeEmail("  New@Example.Test "), "new@example.test");
  assert.equal(normalizeEmail("no-at-sign"), null);
  assert.equal(normalizeEmail("a@b"), null);
  assert.equal(normalizeEmail(42), null);
});

test("invite code is 43 url-safe characters and unique", () => {
  const a = newCode();
  const b = newCode();
  assert.match(a, /^[A-Za-z0-9_-]{43}$/);
  assert.notEqual(a, b);
  assert.equal(validCode(a), a);
  assert.equal(validCode("short"), null);
  assert.equal(validCode("a".repeat(40) + "'"), null);
});

test("sha256 matches postgres digest hex", async () => {
  assert.equal(await sha256Hex("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
});

test("password needs at least 10 characters", () => {
  assert.notEqual(passwordProblem("short"), null);
  assert.notEqual(passwordProblem(undefined), null);
  assert.equal(passwordProblem("long-enough-1"), null);
  assert.notEqual(passwordProblem("x".repeat(73)), null);
  assert.notEqual(passwordProblem("я".repeat(40)), null);
  assert.equal(passwordProblem("я".repeat(36)), null);
});

test("invite lifetime is clamped", () => {
  assert.equal(inviteDays(undefined), INVITE_DAYS_DEFAULT);
  assert.equal(inviteDays(0), 1);
  assert.equal(inviteDays(365), INVITE_DAYS_MAX);
  assert.equal(inviteDays(3.5), INVITE_DAYS_DEFAULT);
});

test("links point to the site with one parameter", () => {
  assert.equal(siteLink("https://x.test/app", "invite", "a_b"), "https://x.test/app/#invite=a_b");
  assert.equal(siteLink("https://x.test/app/", "reset", "t"), "https://x.test/app/#reset=t");
});

test("user rows carry no finance and compute blocked", () => {
  const now = new Date("2026-11-13T00:00:00Z");
  const row = userRow({ id: "u", email: "a@x.test", banned_until: "2126-01-01T00:00:00Z" }, now);
  assert.deepEqual(Object.keys(row).sort(), ["blocked", "created_at", "email", "id", "last_sign_in_at"]);
  assert.equal(row.blocked, true);
  assert.equal(userRow({ id: "u", banned_until: "2020-01-01T00:00:00Z" }, now).blocked, false);
});

test("user id must be a uuid", () => {
  assert.equal(validUserId("00000000-0000-0000-0000-000000000000"), "00000000-0000-0000-0000-000000000000");
  assert.equal(validUserId("1;drop"), null);
});

test("admin function touches only service tables", () => {
  const source = readFileSync(new URL("../admin/index.ts", import.meta.url), "utf8");
  const paths = [...source.matchAll(/\/rest\/v1\/([a-z_]+)(?:\/([a-z_]+))?/g)];
  assert.ok(paths.length > 0);
  for (const [, first, second] of paths) {
    if (first === "rpc") {
      assert.ok((ADMIN_RPCS as readonly string[]).includes(second ?? ""), `admin calls rpc ${second}`);
    } else {
      assert.ok((ADMIN_TABLES as readonly string[]).includes(first), `admin reads ${first}`);
    }
  }
  assert.doesNotMatch(source, /\/rest\/v1\/\$\{/, "admin builds a REST path dynamically");
});
