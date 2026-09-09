package com.example.timsort.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Интерфейс валидатора с возможностью композиции.
 *
 * @param <T> тип валидируемого объекта
 */
public interface Validator<T> {

    /**
     * Валидирует объект.
     *
     * @param value объект для проверки
     * @return результат валидации
     */
    ValidationResult<T> validate(T value);

    /**
     * Комбинирует этот валидатор с другим, накапливая ошибки.
     *
     * @param other другой валидатор того же типа
     * @return новый валидатор, применяющий оба правила
     */
    default Validator<T> and(Validator<T> other) {
        return value -> {
            ValidationResult<T> first = this.validate(value);
            ValidationResult<T> second = other.validate(value);

            if (first.isValid() && second.isValid()) {
                return ValidationResult.of(value);
            }

            List<String> allErrors = new ArrayList<>();
            allErrors.addAll(first.errors());
            allErrors.addAll(second.errors());
            return ValidationResult.failure(allErrors);
        };
    }
}