package com.learning.hanzitrainer.exercise;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Test double for {@link TranslationGrader}: grades every answer half right and notes whether a transaction ran. */
public class FakeTranslationGrader implements TranslationGrader {

    private volatile Boolean calledInTransaction;

    @Override
    public TranslationGrade grade(String source, String reference, String answer) {
        calledInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        return new TranslationGrade(false, 0.5, "Almost.", reference);
    }

    /** Null until the first call. */
    public Boolean calledInTransaction() {
        return calledInTransaction;
    }
}
