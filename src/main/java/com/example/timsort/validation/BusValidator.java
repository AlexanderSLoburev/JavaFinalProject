package com.example.timsort.validation;

import com.example.timsort.model.Bus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Валидатор для класса Bus.
 * Проверяет номер маршрута (1-999), модель (2-30 символов, буквы/цифры/дефис/пробел)
 * и пробег (0-2 000 000).
 */
public class BusValidator implements Validator<Bus> {

    private final List<Rule<Bus>> rules;

    /**
     * Конструктор: инициализирует список правил.
     */
    public BusValidator() {
        this.rules = Arrays.asList(
                routeNumberRule(),
                modelRule(),
                mileageRule()
        );
    }

    /**
     * Применяет все правила к объекту Bus и собирает ошибки.
     *
     * @param bus объект для проверки
     * @return ValidationResult со всеми ошибками или успешный результат
     */
    @Override
    public ValidationResult<Bus> validate(Bus bus) {
        List<String> errors = rules.stream()
                .map(rule -> rule.apply(bus))           // Применяем каждое правило
                .filter(Optional::isPresent)            // Оставляем только ошибки
                .map(Optional::get)                     // Извлекаем текст ошибки
                .collect(Collectors.toList());          // Собираем в список

        if (errors.isEmpty()) {
            return ValidationResult.of(bus);
        }
        return ValidationResult.failure(errors);
    }

    /**
     * Правило для номера маршрута: от 1 до 999.
     */
    private Rule<Bus> routeNumberRule() {
        return bus -> {
            int route = bus.routeNumber();
            if (route < 1 || route > 999) {
                return Optional.of(
                        "Номер маршрута должен быть от 1 до 999, текущее значение: " + route);
            }
            return Optional.empty();
        };
    }

    /**
     * Правило для модели: непустая строка 2-30 символов,
     * только буквы, цифры, дефис и пробел.
     */
    private Rule<Bus> modelRule() {
        return bus -> {
            String model = bus.model();
            if (model == null || model.trim().isEmpty()) {
                return Optional.of("Модель не может быть пустой");
            }
            if (model.length() < 2 || model.length() > 30) {
                return Optional.of(
                        "Модель должна содержать от 2 до 30 символов, текущая длина: " + model.length());
            }
            if (!model.matches("[а-яА-Яa-zA-Z0-9\\-\\s]+")) {
                return Optional.of(
                        "Модель может содержать только буквы, цифры, дефис и пробелы");
            }
            return Optional.empty();
        };
    }

    /**
     * Правило для пробега: от 0 до 2 000 000.
     */
    private Rule<Bus> mileageRule() {
        return bus -> {
            long mileage = bus.mileage();
            if (mileage < 0 || mileage > 2_000_000) {
                return Optional.of(
                        "Пробег должен быть от 0 до 2 000 000, текущее значение: " + mileage);
            }
            return Optional.empty();
        };
    }
}