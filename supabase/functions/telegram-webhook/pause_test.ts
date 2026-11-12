import assert from "node:assert/strict";
import { test } from "node:test";
import { DEFAULT_PAUSE_HOURS, isPaused, moscowTime, parsePause } from "./pause.ts";

test("parsePause understands short and long units", () => {
  assert.deepEqual(parsePause(""), { hours: DEFAULT_PAUSE_HOURS });
  assert.deepEqual(parsePause("12ч"), { hours: 12 });
  assert.deepEqual(parsePause("3д"), { hours: 72 });
  assert.deepEqual(parsePause("3 дня"), { hours: 72 });
  assert.deepEqual(parsePause("3"), { hours: 72 });
  assert.deepEqual(parsePause("2н"), { hours: 336 });
  assert.deepEqual(parsePause("1 неделю"), { hours: 168 });
  assert.deepEqual(parsePause("1,5д"), { hours: 36 });
  assert.deepEqual(parsePause("30 дней"), { hours: 720 });
});

test("parsePause rejects garbage and too long", () => {
  assert.ok("error" in parsePause("завтра"));
  assert.ok("error" in parsePause("0д"));
  assert.ok("error" in parsePause("5 лет"));
  assert.ok("error" in parsePause("31д"));
  assert.ok("error" in parsePause("5 нед"));
});

test("moscowTime is UTC+3 with label", () => {
  assert.equal(moscowTime(new Date("2026-11-12T21:30:00Z")), "13.11 в 00:30 МСК");
});

test("isPaused compares with now", () => {
  const now = new Date("2026-11-12T12:00:00Z");
  assert.equal(isPaused("2026-11-12T12:00:01Z", now), true);
  assert.equal(isPaused("2026-11-12T11:59:59Z", now), false);
  assert.equal(isPaused(null, now), false);
});
