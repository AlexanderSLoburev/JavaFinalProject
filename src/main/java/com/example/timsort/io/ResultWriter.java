package com.example.timsort.io;

import java.util.List;

/**
 * Стратегия записи результатов обработки коллекции.
 * <p>
 * Реализации могут сохранять данные в файл, отправлять по сети,
 * выводить в консоль и т.д. Контракт метода {@link #appendAll(List)}
 * — <b>добавление</b>, а не перезапись: повторные вызовы накапливают
 * данные.
 * </p>
 *
 * @param <T> тип записываемых элементов
 */
public interface ResultWriter<T> {
 
    /**
     * Записывает переданные элементы, добавляя их к уже
     * существующим данным (режим APPEND).
     *
     * @param items список элементов для записи; не должен быть {@code null}
     * @throws NullPointerException если {@code items} равен {@code null}
     */
    void appendAll(List<T> items);
}