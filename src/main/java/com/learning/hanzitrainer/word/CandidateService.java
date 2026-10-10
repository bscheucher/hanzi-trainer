package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.cedict.CedictReadingDto;
import com.learning.hanzitrainer.cedict.CedictService;
import com.learning.hanzitrainer.extraction.ExtractionResult;
import com.learning.hanzitrainer.extraction.ExtractionResult.ExtractedWord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Turns the extracted words of a page into review candidates (spec 06, "Candidates"). */
@Service
@Transactional(readOnly = true)
public class CandidateService {

    private final CedictService cedict;
    private final WordRepository words;
    private final LessonWordRepository lessonWords;

    public CandidateService(CedictService cedict, WordRepository words, LessonWordRepository lessonWords) {
        this.cedict = cedict;
        this.words = words;
        this.lessonWords = lessonWords;
    }

    public List<CandidateDto> candidates(long lessonId, ExtractionResult extraction) {
        List<CandidateDto> candidates = new ArrayList<>();
        List<ExtractedWord> extracted = extraction.words();
        for (int i = 0; i < extracted.size(); i++) {
            ExtractedWord w = extracted.get(i);
            var check = cedict.check(w.hanzi(), w.pinyin());
            Long existingWordId = check.pinyinNumbered() == null ? null
                    : words.findByHanziAndPinyinNumbered(w.hanzi(), check.pinyinNumbered()).map(Word::getId)
                    .orElse(null);
            boolean alreadyInLesson = existingWordId != null
                    && lessonWords.existsById(new LessonWordId(lessonId, existingWordId));
            candidates.add(new CandidateDto(i, w.hanzi(), check.pinyin(), check.pinyinNumbered(), w.meaningEn(),
                    w.meaningDe(), w.partOfSpeech(), w.exampleSentence(), w.confidence(), w.pinyinFromPage(),
                    check.status(), CedictReadingDto.of(check.entries()), check.error(), existingWordId,
                    alreadyInLesson));
        }
        return candidates;
    }
}
