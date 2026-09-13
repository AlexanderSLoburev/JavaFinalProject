package com.example.timsort.app;

/**
 * Интерфейс для работы с консолью.
 * Абстрагирует ввод-вывод, отделяя бизнес-логику от System.out/Scanner.
 * Это позволяет тестировать код, подменяя реальную консоль на FakeConsoleIO.
 */
public interface ConsoleIO {

    /**
     * Читает строку с консоли.
     *
     * @return введённая пользователем строка
     */
    String readLine();

    /**
     * Выводит сообщение в консоль.
     *
     * @param message сообщение для вывода
     */
    void print(String message);

    /**
     * Выводит форматированное сообщение в консоль.
     *
     * @param format  формат строки (как в String.format)
     * @param args    аргументы для форматирования
     */
    void printf(String format, Object... args);
}