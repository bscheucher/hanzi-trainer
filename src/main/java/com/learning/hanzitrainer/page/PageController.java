package com.learning.hanzitrainer.page;

import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @PostMapping(path = "/lessons/{lessonId}/pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PageDto upload(@PathVariable long lessonId, @RequestParam("file") MultipartFile file,
                          @RequestParam(required = false) @Positive Integer pageNo) throws IOException {
        return pageService.upload(lessonId, file.getBytes(), pageNo);
    }

    @GetMapping("/pages/{id}")
    public PageDto get(@PathVariable long id) {
        return pageService.get(id);
    }

    @GetMapping("/pages/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        PageService.StoredImage image = pageService.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mimeType()))
                .body(image.content());
    }

    @PostMapping("/pages/{id}/extract")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PageDto rerun(@PathVariable long id) {
        return pageService.rerun(id);
    }

    @DeleteMapping("/pages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        pageService.delete(id);
    }
}
