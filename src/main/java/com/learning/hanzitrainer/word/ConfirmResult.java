package com.learning.hanzitrainer.word;

import java.util.List;

/**
 * @param created new words (with three review cards each)
 * @param reused  items that matched an existing word
 * @param linked  new links between the lesson and a word
 * @param words   the words of all items, in request order
 */
public record ConfirmResult(int created, int reused, int linked, List<WordDto> words) {
}
