import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { composeNow, type RefreshContext } from "./refresh.ts";

type Fixture = { name: string; context: RefreshContext; rate: number; now: string; text: string };

const fixtures = JSON.parse(readFileSync(new URL("./refresh_fixtures.json", import.meta.url), "utf8")) as Fixture[];

for (const fixture of fixtures) {
  test(`refresh text matches the worker: ${fixture.name}`, () => {
    assert.equal(composeNow(fixture.context, fixture.rate, new Date(fixture.now)), fixture.text);
  });
}
