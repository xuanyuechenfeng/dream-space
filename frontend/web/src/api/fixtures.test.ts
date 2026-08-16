import { describe, expect, it } from "vitest";
import { fixtureInspirations, fixturePage } from "./fixtures";

describe("deterministic inspiration fixtures", () => {
  it("contains the frozen asset inventory without duplicate ids or slugs", () => {
    expect(fixtureInspirations.length).toBe(52);
    expect(new Set(fixtureInspirations.map((item) => item.id)).size).toBe(fixtureInspirations.length);
    expect(new Set(fixtureInspirations.map((item) => item.slug)).size).toBe(fixtureInspirations.length);
  });

  it("filters by category and query without mutating the source", () => {
    const before = fixtureInspirations.map((item) => item.slug);
    const result = fixturePage("portrait", "study 02");
    expect(result.items).toHaveLength(1);
    expect(result.items[0]?.slug).toBe("portrait-02");
    expect(fixtureInspirations.map((item) => item.slug)).toEqual(before);
  });
});
