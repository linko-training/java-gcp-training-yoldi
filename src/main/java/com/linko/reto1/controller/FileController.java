package com.linko.reto1.controller;


import com.linko.reto1.model.File;
import com.linko.reto1.service.StorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
class  FileController{

    private final StorageService service;

    FileController(StorageService service) {
        this.service = service;
    }


    //obtener todos
    @GetMapping
    public List<File> getAll() {
        return service.getAll();
    }

    //crear producto
    @PostMapping("/upload")
    public File create(@RequestParam MultipartFile file) throws IOException {
         return service.upload(file);

    }


//actualizar producto
    @PutMapping("/{name}")
    public File update(@PathVariable String name, @RequestBody Map<String, String> body) {

        return service.update(name, body.get("name"));
    }

    @DeleteMapping("/{name}")
    public boolean delete(@PathVariable String name) {

        return service.delete(name);

    }

}

