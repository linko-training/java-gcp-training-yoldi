package com.linko.reto1.service;


import com.google.api.gax.paging.Page;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.linko.reto1.exception.FileValidationException;
import com.linko.reto1.model.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf",
            "text/plain", "text/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    // Umbral a partir del cual se usa streaming en lugar de cargar todo en memoria
    private static final long STREAMING_THRESHOLD_BYTES = 10 * 1024 * 1024L; // 10 MB
    private static final int BUFFER_SIZE = 256 * 1024; // 256 KB

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    @Value("${GCS_BUCKET:linko-challenge-files}")
    private String bucketName;

    @Value("${GCS_PREFIX:jr-uploads/}")
    private String prefijo;

    public List<File> getAll() {
        Page<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefijo));
        return blobs.streamAll()
                .filter(blob -> !blob.getName().equals(prefijo))
                .map(this::toFile)
                .collect(Collectors.toList());
    }

    public File upload(MultipartFile file) throws IOException {
        validate(file);

        String sanitized = sanitizeName(file.getOriginalFilename());
        boolean esGrande = file.getSize() > STREAMING_THRESHOLD_BYTES;

        // Campos contextuales: Cloud Logging los expone como labels consultables
        MDC.put("operation", "upload");
        MDC.put("fileName", sanitized);
        MDC.put("fileSize", String.valueOf(file.getSize()));
        MDC.put("streaming", String.valueOf(esGrande));
        try {
            String objectName = prefijo + sanitized;
            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();

            File result = esGrande
                    ? uploadStreaming(file, blobInfo)
                    : toFile(storage.create(blobInfo, file.getBytes()));

            log.info("Archivo subido correctamente: {} ({} bytes, streaming={})",
                    sanitized, file.getSize(), esGrande);
            return result;
        } catch (IOException e) {
            log.error("Fallo de I/O al subir el archivo {} al bucket {}", sanitized, bucketName, e);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    public File update(String name, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new FileValidationException("El nuevo nombre no puede estar vacio");
        }
        String sanitized = sanitizeName(newName);

        Blob source = storage.get(BlobId.of(bucketName, prefijo + name));
        if (source == null) {
            log.warn("Renombrado fallido: no existe el archivo {}", name);
            return null;
        }

        Blob renamed = source.copyTo(BlobId.of(bucketName, prefijo + sanitized)).getResult();
        source.delete();
        log.info("Archivo renombrado: {} -> {}", name, sanitized);
        return toFile(renamed);
    }

    public boolean delete(String name) {
        boolean deleted = storage.delete(BlobId.of(bucketName, prefijo + name));
        if (deleted) {
            log.info("Archivo eliminado: {}", name);
        } else {
            log.warn("Eliminacion fallida: no existe el archivo {}", name);
        }
        return deleted;
    }

    // --- helpers ---

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Archivo rechazado: vacio o ausente");
            throw new FileValidationException("El archivo no puede estar vacio");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            log.warn("Archivo rechazado: sin nombre");
            throw new FileValidationException("El archivo debe tener un nombre");
        }

        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            log.warn("Archivo rechazado por path traversal: nombre={}", originalName);
            throw new FileValidationException("El nombre del archivo contiene caracteres no permitidos");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            log.warn("Archivo rechazado por tipo no permitido: nombre={} tipo={}", originalName, contentType);
            throw new FileValidationException(
                    "Tipo de archivo no permitido: " + contentType +
                    ". Tipos aceptados: " + String.join(", ", ALLOWED_TYPES));
        }
    }

    private String sanitizeName(String name) {
        // Elimina separadores de ruta residuales y caracteres peligrosos
        return name.replaceAll("[/\\\\]", "_").trim();
    }

    private File uploadStreaming(MultipartFile file, BlobInfo blobInfo) throws IOException {
        try (WriteChannel writer = storage.writer(blobInfo);
             InputStream in = file.getInputStream()) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                writer.write(ByteBuffer.wrap(buffer, 0, bytesRead));
            }
        }

        Blob uploaded = storage.get(blobInfo.getBlobId());
        return toFile(uploaded);
    }

    private File toFile(Blob blob) {
        String name = blob.getName().substring(blob.getName().lastIndexOf('/') + 1);
        long size = blob.getSize() == null ? 0 : blob.getSize();
        return new File(name, size, "gs://" + bucketName + "/" + blob.getName());
    }
}
