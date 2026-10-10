package com.learning.hanzitrainer.page;

/** Published after a page was uploaded or its extraction re-requested; extraction starts after commit. */
public record PageUploadedEvent(long pageId) {
}
