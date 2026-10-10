package com.learning.hanzitrainer.cedict;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dev endpoints; disabled with {@code hanzi.admin.enabled=false}. */
@RestController
@RequestMapping("/api/admin/cedict")
@ConditionalOnProperty(prefix = "hanzi.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CedictAdminController {

    private final CedictImporter importer;

    public CedictAdminController(CedictImporter importer) {
        this.importer = importer;
    }

    @PostMapping("/import")
    public CedictImporter.ImportResult importCedict() {
        return importer.importConfiguredFile();
    }
}
