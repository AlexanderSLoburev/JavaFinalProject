package com.example.timsort.validation;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Иммутабельный контейнер результата валидации.
 * Содержит либо валидное значение, либо список ошибок.
 *
 * @param <T> тип валидируемого объекта
 */
public final class ValidationResult<T> {

    private final Optional<T> value;
    private final List<String> errors;

    /**
     * Приватный конструктор.
     *
     * @param value  валидное значение (может отсутствовать)
     * @param errors список ошибок валидации
     */
    private ValidationResult(Optional<T> value, List<String> errors) {
        this.value = value;
        this.errors = errors;
    }

    /**
     * Создаёт успешный результат с валидным значением.
     *
     * @param value валидное значение
     * @param <T>   тип значения
     * @return успешный ValidationResult
     */
    public static <T> ValidationResult<T> of(T value) {
        return new ValidationResult<>(Optional.of(value), Collections.emptyList());
    }

    /**
     * Создаёт результат с ошибками.
     *
     * @param errors список ошибок
     * @param <T>    тип валидируемого объекта
     * @return ValidationResult с ошибками
     */
    public static <T> ValidationResult<T> failure(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("failure требует непустой список ошибок");
        }
        return new ValidationResult<>(Optional.empty(), List.copyOf(errors));
    }

    /**
     * Проверяет, прошла ли валидация успешно.
     *
     * @return true если ошибок нет
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Возвращает список ошибок.
     *
     * @return неизменяемый список ошибок
     */
    public List<String> errors() {
        return errors;
    }

    /**
     * Возвращает валидное значение, если валидация прошла успешно.
     *
     * @return Optional с значением или пустой
     */
    public Optional<T> value() {
        return value;
    }
}