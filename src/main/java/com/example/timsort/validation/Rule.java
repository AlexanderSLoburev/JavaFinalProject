package com.example.timsort.validation;

import java.util.Optional;

/**
 * Функциональный интерфейс правила валидации.
 * Принимает объект и возвращает Optional с сообщением об ошибке, если правило нарушено.
 *
 * @param <T> тип валидируемого объекта
 */
@FunctionalInterface
public interface Rule<T> {

    /**
     * Применяет правило к объекту.
     *
     * @param value объект для проверки
     * @return Optional с сообщением об ошибке, если правило нарушено; пустой Optional, если всё ок
     */
    Optional<String> apply(T value);
}