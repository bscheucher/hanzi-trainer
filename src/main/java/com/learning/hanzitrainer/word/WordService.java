package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.common.NotFoundException;
import com.learning.hanzitrainer.review.ReviewCard;
import com.learning.hanzitrainer.review.ReviewCardRepository;
import com.learning.hanzitrainer.word.WordDetailDto.CardRef;
import com.learning.hanzitrainer.word.WordDetailDto.LessonRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** The vocabulary browser (M4): search, detail, edit, unlink from a lesson, delete. */
@Service
@Transactional
public class WordService {

    private final WordRepository words;
    private final LessonWordRepository lessonWords;
    private final ReviewCardRepository cards;

    public WordService(WordRepository words, LessonWordRepository lessonWords, ReviewCardRepository cards) {
        this.words = words;
        this.lessonWords = lessonWords;
        this.cards = cards;
    }

    @Transactional(readOnly = true)
    public Page<WordDto> search(Long lessonId, String q, Pageable pageable) {
        WordSearch search = WordSearch.of(q);
        return words.search(lessonId, search.text(), search.pinyinMode(), search.pinyin(), pageable)
                .map(WordDto::from);
    }

    @Transactional(readOnly = true)
    public WordDetailDto detail(long id) {
        Word w = find(id);
        List<LessonRef> lessons = lessonWords.findLinksOfWord(id).stream()
                .map(lw -> new LessonRef(lw.getLesson().getId(), lw.getLesson().getLessonNo(),
                        lw.getLesson().getTextbook().getTitle(), lw.getExampleSentence()))
                .toList();
        List<CardRef> cardRefs = cards.findByWordId(id).stream()
                .sorted(Comparator.comparing(ReviewCard::getCardType))
                .map(c -> new CardRef(c.getCardType(), c.getState(), c.getDue()))
                .toList();
        return new WordDetailDto(w.getId(), w.getHanzi(), w.getPinyin(), w.getPinyinNumbered(), w.getMeaningEn(),
                w.getMeaningDe(), w.getPartOfSpeech(), w.isVerified(), lessons, cardRefs);
    }

    public WordDto update(long id, UpdateWordRequest request) {
        Word w = find(id);
        if (request.meaningEn() != null) {
            w.setMeaningEn(request.meaningEn().strip());
        }
        if (request.meaningDe() != null) {
            w.setMeaningDe(trimToNull(request.meaningDe()));
        }
        if (request.partOfSpeech() != null) {
            w.setPartOfSpeech(trimToNull(request.partOfSpeech()));
        }
        return WordDto.from(w);
    }

    /** Removes the word from one lesson; the word, its cards and its other lessons stay (spec 03). */
    public void unlink(long lessonId, long wordId) {
        LessonWordId id = new LessonWordId(lessonId, wordId);
        if (!lessonWords.existsById(id)) {
            throw new NotFoundException("Word " + wordId + " in lesson", lessonId);
        }
        lessonWords.deleteById(id);
    }

    /** Deletes the word; cards, review logs, lesson links and exercise links go with it (FK cascade). */
    public void delete(long id) {
        words.delete(find(id));
    }

    private Word find(long id) {
        return words.findById(id).orElseThrow(() -> new NotFoundException("Word", id));
    }

    private static String trimToNull(String value) {
        return value.isBlank() ? null : value.strip();
    }
}
