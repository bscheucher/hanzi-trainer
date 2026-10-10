import { describe, expect, it } from "vitest";
import { fitWithin, MAX_IMAGE_PX } from "./image";

describe("fitWithin", () => {
  it("scales the long side down to the limit", () => {
    expect(fitWithin(4032, 3024, MAX_IMAGE_PX)).toEqual({ width: 2576, height: 1932 });
    expect(fitWithin(3000, 4000, 2000)).toEqual({ width: 1500, height: 2000 });
  });

  it("never upscales", () => {
    expect(fitWithin(800, 600, MAX_IMAGE_PX)).toEqual({ width: 800, height: 600 });
  });
});
