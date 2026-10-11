package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.cedict.CedictMatch;
import com.learning.hanzitrainer.cedict.CedictService;
import com.learning.hanzitrainer.cedict.CedictService.ReadingCheck;
import com.learning.hanzitrainer.cedict.HanziText;
import com.learning.hanzitrainer.common.InvalidItemsException;
import com.learning.hanzitrainer.common.InvalidItemsException.ItemError;
import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.page.PageImage;
import com.learning.hanzitrainer.page.PageImageRepository;
import com.learning.hanzitrainer.review.CardType;
import com.learning.hanzitrainer.review.ReviewCard;
import com.learning.hanzitrainer.review.ReviewCardRepository;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.textbook.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns reviewed items into vocabulary (spec 06, "Confirmation"). All items are validated first; if any is invalid,
 * nothing is stored. Idempotent: confirming the same items again reuses words and links.
 */
@Service
@Transactional
public class ConfirmationService {

    private final LessonRepository lessons;
    private final PageImageRepository pages;
    private final WordRepository words;
    private final LessonWordRepository lessonWords;
    private final ReviewCardRepository cards;
    private final CedictService cedict;

    public ConfirmationService(LessonRepository lessons, PageImageRepository pages, WordRepository words,
                               LessonWordRepository lessonWords, ReviewCardRepository cards, CedictService cedict) {
        this.lessons = lessons;
        this.pages = pages;
        this.words = words;
        this.lessonWords = lessonWords;
        this.cards = cards;
        this.cedict = cedict;
    }

    public ConfirmResult confirm(long lessonId, ConfirmRequest request) {
        Lesson lesson = lessons.findById(lessonId).orElseThrow(() -> new NotFoundException("Lesson", lessonId));
        PageImage page = sourcePage(lessonId, request.sourcePageId());
        List<Normalised> items = validate(request.items());

        int created = 0;
        int reused = 0;
        int linked = 0;
        List<WordDto> result = new ArrayList<>();
        for (Normalised item : items) {
            Word word = words.findByHanziAndPinyinNumbered(item.hanzi(), item.check().pinyinNumbered()).orElse(null);
            if (word == null) {
                word = createWord(item);
                created++;
            } else {
                fillEmptyFields(word, item.source());
                reused++;
            }
            if (link(lesson, word, page, trimToNull(item.source().exampleSentence()))) {
                linked++;
            }
            result.add(WordDto.from(word));
        }
        if (page != null && page.getConfirmedAt() == null) {
            page.setConfirmedAt(Instant.now());
        }
        return new ConfirmResult(created, reused, linked, result);
    }

    private PageImage sourcePage(long lessonId, Long pageId) {
        if (pageId == null) {
            return null;
        }
        PageImage page = pages.findById(pageId).orElseThrow(() -> new NotFoundException("Page", pageId));
        if (!page.getLesson().getId().equals(lessonId)) {
            throw new InvalidItemsException("Page " + pageId + " belongs to another lesson", List.of());
        }
        return page;
    }

    private List<Normalised> validate(List<ConfirmItem> items) {
        List<ItemError> errors = new ArrayList<>();
        List<Normalised> normalised = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ConfirmItem item = items.get(i);
            if (item == null) {
                errors.add(new ItemError(i, "Item is missing"));
                continue;
            }
            String hanzi = item.hanzi() == null ? "" : item.hanzi().strip();
            String problem = HanziText.problem(hanzi);
            if (problem != null) {
                errors.add(new ItemError(i, "Hanzi '" + hanzi + "': " + problem));
                continue;
            }
            if (trimToNull(item.meaningEn()) == null) {
                errors.add(new ItemError(i, hanzi + ": English meaning is missing"));
                continue;
            }
            ReadingCheck check = cedict.check(hanzi, item.pinyin());
            if (check.error() != null) {
                errors.add(new ItemError(i, hanzi + ": " + check.error()));
            } else if (check.status() == CedictMatch.Status.PINYIN_MISMATCH && !item.acceptMismatch()) {
                String readings = check.entries().stream().map(e -> e.getPinyin()).distinct()
                        .collect(Collectors.joining(", "));
                errors.add(new ItemError(i, hanzi + ": pinyin '" + check.pinyin() + "' differs from CC-CEDICT ("
                        + readings + "); pick a dictionary reading or keep yours explicitly"));
            } else {
                normalised.add(new Normalised(hanzi, check, item));
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidItemsException(errors.size() + " item(s) need attention", errors);
        }
        return normalised;
    }

    private Word createWord(Normalised item) {
        ConfirmItem source = item.source();
        Word word = new Word(item.hanzi(), item.check().pinyin(), item.check().pinyinNumbered());
        word.setMeaningEn(trimToNull(source.meaningEn()));
        word.setMeaningDe(trimToNull(source.meaningDe()));
        word.setPartOfSpeech(trimToNull(source.partOfSpeech()));
        boolean match = item.check().status() == CedictMatch.Status.MATCH;
        word.setCedictEntry(match ? item.check().entry() : null);
        word.setVerified(match || source.acceptMismatch());
        words.save(word);
        for (CardType type : CardType.values()) {
            cards.save(new ReviewCard(word, type));
        }
        return word;
    }

    /** Never overwrites what the user already has; only fills gaps. */
    private static void fillEmptyFields(Word word, ConfirmItem source) {
        if (word.getMeaningEn() == null) {
            word.setMeaningEn(trimToNull(source.meaningEn()));
        }
        if (word.getMeaningDe() == null) {
            word.setMeaningDe(trimToNull(source.meaningDe()));
        }
        if (word.getPartOfSpeech() == null) {
            word.setPartOfSpeech(trimToNull(source.partOfSpeech()));
        }
    }

    /** @return true if a new lesson link was created */
    private boolean link(Lesson lesson, Word word, PageImage page, String exampleSentence) {
        LessonWord existing = lessonWords.findById(new LessonWordId(lesson.getId(), word.getId())).orElse(null);
        if (existing == null) {
            LessonWord link = new LessonWord(lesson, word);
            link.setSourcePage(page);
            link.setExampleSentence(exampleSentence);
            lessonWords.save(link);
            return true;
        }
        if (exampleSentence != null) {
            existing.setExampleSentence(exampleSentence);
        }
        if (existing.getSourcePage() == null) {
            existing.setSourcePage(page);
        }
        return false;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record Normalised(String hanzi, ReadingCheck check, ConfirmItem source) {
    }
}
