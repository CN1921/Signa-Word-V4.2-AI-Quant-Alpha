package com.keywordstock.exception;

import com.keywordstock.model.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<Result> handleValidation(MethodArgumentNotValidException ex) {
        StringBuilder sb = new StringBuilder();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            sb.append(fe.getField()).append(": ").append(fe.getDefaultMessage()).append("; ");
        }
        return ResponseEntity.badRequest().body(Result.fail(sb.toString()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Result> handleAll(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.status(500).body(Result.fail(ex.getMessage()));
    }
}
