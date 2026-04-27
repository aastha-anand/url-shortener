package com.aasthaanand.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BlockDomainRequest {
    @NotBlank(message = "Domain is required")
    private String domain;
    private String reason;
}
