package com.linko.reto1.controller;


import com.linko.reto1.model.File;
import com.linko.reto1.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
class FileController {

    private final StorageService service;

    FileController(StorageService service) {
        this.service = service;
    }

    @GetMapping
    public List<File> getAll() {
        return service.getAll();
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public File upload(@RequestParam MultipartFile file) throws IOException {
        return service.upload(file);
    }

    @PutMapping("/{name}")
    public ResponseEntity<File> update(@PathVariable String name, @RequestBody Map<String, String> body) {
        File updated = service.update(name, body.get("name"));
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        boolean deleted = service.delete(name);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
