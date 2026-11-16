package id.co.example.manifest.dto;

/** Response shared by the normal and retry manifest-generation endpoints. */
public record GeneratedPdfResponse(String fileId, String url, String createdAt, String passwordFormat) {}
