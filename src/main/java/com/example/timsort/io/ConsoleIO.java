package com.example.timsort.io;

public interface ConsoleIO {
    String readLine();
    void print(String message);
    void printf(String format, Object... args);
}