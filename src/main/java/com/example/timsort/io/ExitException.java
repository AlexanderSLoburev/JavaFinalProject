package com.example.timsort.io;

public class ExitException extends RuntimeException {
    public ExitException() {
        super("Exit requested");
    }
}