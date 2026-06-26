package com.linko.reto1.service;


import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.linko.reto1.model.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StorageService {

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    @Value("${GCS_BUCKET:linko-challenge-files}")
    private String bucketName;

    @Value("${GCS_PREFIX:jr-uploads/}")
    private String prefijo;

    public List<File> getAll() {
        Page<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefijo));
        return blobs.streamAll()
                // saltar el "marcador de carpeta" (objeto con el nombre del prefijo)
                .filter(blob -> !blob.getName().equals(prefijo))
                .map(this::toFile)
                .collect(Collectors.toList());
    }

    public File upload(MultipartFile file) throws IOException {
        String objectName = prefijo + file.getOriginalFilename();

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        return toFile(storage.create(blobInfo, file.getBytes()));
    }

    public File update(String name, String newName) {
        // GCS no renombra in-place: copiar al nuevo nombre y borrar el original
        Blob source = storage.get(BlobId.of(bucketName, prefijo + name));
        if (source == null) return null;

        Blob renamed = source.copyTo(BlobId.of(bucketName, prefijo + newName)).getResult();
        source.delete();
        return toFile(renamed);
    }

    public boolean delete(String name) {
        return storage.delete(BlobId.of(bucketName, prefijo + name));
    }

    private File toFile(Blob blob) {
        // el nombre del objeto (sin prefijo) es el identificador, derivable de GCS
        String name = blob.getName().substring(blob.getName().lastIndexOf('/') + 1);
        long size = blob.getSize() == null ? 0 : blob.getSize();
        return new File(name, size, "gs://" + bucketName + "/" + blob.getName());
    }
}
