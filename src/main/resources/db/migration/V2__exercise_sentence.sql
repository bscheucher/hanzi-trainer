-- The Chinese sentence of a sentence exercise (FILL_BLANK, WORD_ORDER, TRANSLATION), so duplicates per lesson and
-- type are prevented by the database rather than by the generation job (spec 02, spec 07).
ALTER TABLE exercise ADD COLUMN sentence TEXT;

-- The same value GeneratedExercises.sentenceOf computes from the answer key.
UPDATE exercise SET sentence = answer ->> 'fullSentence' WHERE type = 'FILL_BLANK';
UPDATE exercise SET sentence = answer ->> 'reference' WHERE type = 'TRANSLATION';
UPDATE exercise e SET sentence = (
    SELECT string_agg(token, '' ORDER BY position)
    FROM jsonb_array_elements_text(e.answer -> 'accepted' -> 0) WITH ORDINALITY AS t (token, position))
WHERE type = 'WORD_ORDER';

-- Duplicates stored before this migration: keep the oldest; exercise_word rows go with ON DELETE CASCADE.
DELETE FROM exercise e
USING exercise kept
WHERE e.sentence IS NOT NULL
  AND kept.lesson_id = e.lesson_id AND kept.type = e.type AND kept.sentence = e.sentence
  AND kept.id < e.id;

ALTER TABLE exercise ADD CONSTRAINT exercise_sentence_by_type
    CHECK ((type IN ('FILL_BLANK', 'WORD_ORDER', 'TRANSLATION')) = (sentence IS NOT NULL));

CREATE UNIQUE INDEX exercise_lesson_type_sentence_uq ON exercise (lesson_id, type, sentence)
    WHERE sentence IS NOT NULL;
