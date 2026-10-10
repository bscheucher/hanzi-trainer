package com.learning.hanzitrainer.cedict;

/**
 * Result of comparing a word's pinyin with CC-CEDICT.
 *
 * @param entry the matching entry with the lowest id when {@code status == MATCH}, otherwise null
 */
public record CedictMatch(Status status, CedictEntry entry) {

    public enum Status { MATCH, PINYIN_MISMATCH, NOT_FOUND }

    static CedictMatch match(CedictEntry entry) {
        return new CedictMatch(Status.MATCH, entry);
    }

    static CedictMatch mismatch() {
        return new CedictMatch(Status.PINYIN_MISMATCH, null);
    }

    static CedictMatch notFound() {
        return new CedictMatch(Status.NOT_FOUND, null);
    }
}
