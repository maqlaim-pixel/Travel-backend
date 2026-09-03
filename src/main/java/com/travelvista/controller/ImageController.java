package com.travelvista.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
public class ImageController {

    @Autowired
    private Cloudinary cloudinary;

    // Fallback to local storage if Cloudinary is not configured
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/images/";

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            // Check if Cloudinary is configured
            boolean useCloudinary = isCloudinaryConfigured();

            if (useCloudinary) {
                // Upload to Cloudinary
                Map<String, Object> params = ObjectUtils.asMap(
                    "folder", "travelvista",
                    "resource_type", "auto",
                    "unique_filename", true
                );

                @SuppressWarnings("unchecked")
                Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), params);

                String imageUrl = (String) result.get("secure_url");
                String publicId = (String) result.get("public_id");

                return ResponseEntity.ok(Map.of(
                    "url", imageUrl,
                    "publicId", publicId,
                    "originalName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                    "size", file.getSize(),
                    "provider", "cloudinary"
                ));
            } else {
                // Fallback to local storage
                return uploadToLocal(file);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to upload image: " + e.getMessage()));
        }
    }

    private ResponseEntity<?> uploadToLocal(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString() + extension;

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        String imageUrl = "/api/images/" + filename;
        return ResponseEntity.ok(Map.of(
            "url", imageUrl,
            "filename", filename,
            "originalName", originalFilename != null ? originalFilename : "unknown",
            "size", file.getSize(),
            "provider", "local"
        ));
    }

    @GetMapping("/{filename}")
    public ResponseEntity<?> getImage(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(filename);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] imageBytes = Files.readAllBytes(filePath);
            String contentType = determineContentType(filename);

            return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Cache-Control", "public, max-age=31536000")
                .body(imageBytes);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<?> deleteImage(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(filename);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                return ResponseEntity.ok(Map.of("message", "Image deleted"));
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete image"));
        }
    }

    private boolean isCloudinaryConfigured() {
        try {
            String cloudName = System.getProperty("cloudinary.cloud_name");
            String apiKey = System.getProperty("cloudinary.api_key");
            String apiSecret = System.getProperty("cloudinary.api_secret");

            if (cloudName != null && !cloudName.isEmpty() &&
                apiKey != null && !apiKey.isEmpty() &&
                apiSecret != null && !apiSecret.isEmpty()) {
                return true;
            }

            // Check environment variables
            cloudName = System.getenv("CLOUDINARY_CLOUD_NAME");
            apiKey = System.getenv("CLOUDINARY_API_KEY");
            apiSecret = System.getenv("CLOUDINARY_API_SECRET");

            return cloudName != null && !cloudName.isEmpty() &&
                   apiKey != null && !apiKey.isEmpty() &&
                   apiSecret != null && !apiSecret.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String determineContentType(String filename) {
        String ext = filename.toLowerCase();
        if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) return "image/jpeg";
        if (ext.endsWith(".png")) return "image/png";
        if (ext.endsWith(".gif")) return "image/gif";
        if (ext.endsWith(".webp")) return "image/webp";
        if (ext.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
