import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { stripTones, toNumbered, toToneMarks } from "./pinyin";
import { SYLLABLES } from "./pinyin-syllables";

// Same table as PinyinConverterTests.java (spec 04).
const SPEC_TABLE: [string, string][] = [
  ["ni3 hao3", "nǐhǎo"],
  ["xue2 xi2", "xuéxí"],
  ["le5", "le"],
  ["nv3", "nǚ"],
  ["lv4 se4", "lǜsè"],
  ["gou3", "gǒu"],
  ["liu2", "liú"],
  ["gui4", "guì"],
  ["xi1 an1", "xī'ān"],
  ["peng2 you5", "péngyou"],
  ["er4", "èr"],
  ["zhuang1", "zhuāng"],
  ["xi1 nan2", "xīnán"],
  ["yi1 dian3 r5", "yīdiǎnr"],
  ["Bei3 jing1", "Běijīng"],
  ["A A zhi4", "AA zhì"],
];

describe("spec 04 test table", () => {
  it.each(SPEC_TABLE)("toToneMarks(%s) = %s", (numbered, marked) => {
    expect(toToneMarks(numbered)).toBe(marked);
  });

  it.each(SPEC_TABLE)("toNumbered reverses %s", (numbered, marked) => {
    const expected = numbered === "Bei3 jing1" ? "bei3 jing1" : numbered;
    expect(toNumbered(marked, numbered.split(" ").length)).toBe(expected);
  });
});

describe("toToneMarks edge cases", () => {
  it.each([
    ["nu:3", "nǚ"],
    ["Lu:3", "Lǚ"],
    ["lve4", "lüè"],
    ["Ao4 men2", "Àomén"],
    ["Xi1 an1", "Xī'ān"],
    ["nv3 er2", "nǚ'ér"],
    ["wan2 r5 ming4", "wánrmìng"],
    ["ka3 la1 O K", "kǎlā OK"],
    ["T xu4", "T xù"],
    ["yi1 lu4 , shun4 feng1", "yīlù , shùnfēng"],
    ["xx5", "xx"],
    ["m2", "m"],
    ["", ""],
  ])("%s → %s", (numbered, marked) => {
    expect(toToneMarks(numbered)).toBe(marked);
  });
});

describe("toNumbered edge cases", () => {
  it.each([
    ["Àomén", 2, "ao4 men2"],
    ["lüè", 1, "lve4"],
    ["nü", 1, "nv5"],
    ["xī ān", 2, "xi1 an1"],
    ["Xī’ān", 2, "xi1 an1"],
    ["kǎlāOK", 4, "ka3 la1 O K"],
    ["kǎlā OK", 4, "ka3 la1 O K"],
    ["T xù", 2, "T xu4"],
    ["wánrmìng", 3, "wan2 r5 ming4"],
    ["pīnyīn", 2, "pin1 yin1"],
    ["Zhōngguó", 2, "zhong1 guo2"],
    ["shízài", 2, "shi2 zai4"],
    ["tiān'ānmén", 3, "tian1 an1 men2"],
    ["hng", 1, "hng5"],
    ["xiān", 1, "xian1"],
    ["fāngàn", 2, "fan1 gan4"],
    ["fāng'àn", 2, "fang1 an4"],
  ] as const)("%s (%i) → %s", (marked, count, numbered) => {
    expect(toNumbered(marked, count)).toBe(numbered);
  });

  it.each([
    ["r", 1],
    ["xuéxí", 3],
    ["yīdiǎnr", 2],
    ["pīnyīn", 3],
    ["qqq", 1],
    ["学习", 2],
    ["", 1],
    ["xué2", 1],
    ["xuéé", 1],
  ] as const)("rejects %s (%i)", (marked, count) => {
    expect(() => toNumbered(marked, count)).toThrow();
  });
});

describe("stripTones", () => {
  it("removes tone digits", () => {
    expect(stripTones("xue2 xi2")).toBe("xue xi");
    expect(stripTones("nv3 er2")).toBe("nv er");
    expect(stripTones("ka3 la1 O K")).toBe("ka la O K");
  });
});

describe("syllable list", () => {
  it("matches the backend resource", () => {
    const javaList = readFileSync(resolve(__dirname, "../../../src/main/resources/pinyin/syllables.txt"), "utf8")
      .split("\n")
      .map((l) => l.trim())
      .filter((l) => l && !l.startsWith("#"));
    expect([...SYLLABLES].sort()).toEqual(javaList.sort());
  });
});
