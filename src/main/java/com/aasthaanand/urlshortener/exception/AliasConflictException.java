package com.urlshortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AliasConflictException extends RuntimeException {
    public AliasConflictException(String alias) {
        super("Alias already taken: " + alias);
    }
}
