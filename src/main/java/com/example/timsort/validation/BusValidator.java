package com.example.timsort.validation;

import com.example.timsort.model.Bus;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Валидатор для класса Bus.
 * Проверяет номер маршрута (1-999), модель (2-30 символов,
 * буквы/цифры/дефис/пробел) и пробег (0-2 000 000).
 */
public class BusValidator implements Validator<Bus> {
  private static final int MIN_ROUTE_NUMBER = 1;
  private static final int MAX_ROUTE_NUMBER = 999;
  private static final int MIN_MODEL_LENGTH = 2;
  private static final int MAX_MODEL_LENGTH = 30;
  private static final String MODEL_PATTERN = "[а-яА-Яa-zA-Z0-9\\-\\s]+";
  private static final long MIN_MILEAGE = 0;
  private static final long MAX_MILEAGE = 2_000_000;

  private final List<Rule<Bus>> rules;

  /**
   * Конструктор: инициализирует список правил.
   */
  public BusValidator() {
    this.rules = Arrays.asList(routeNumberRule(), modelRule(), mileageRule());
  }

  /**
   * Применяет все правила к объекту Bus и собирает ошибки.
   *
   * @param bus объект для проверки
   * @return ValidationResult со всеми ошибками или успешный результат
   */
  @Override
  public ValidationResult<Bus> validate(Bus bus) {
    if (bus == null) {
      return ValidationResult.failure(List.of("Объект Bus не задан (null)"));
    }
    List<String> errors =
        rules.stream().flatMap(rule -> rule.apply(bus).stream()).toList();
    return errors.isEmpty() ? ValidationResult.of(bus)
                            : ValidationResult.failure(errors);
  }

  /**
   * Правило для номера маршрута: от 1 до 999.
   */
  private Rule<Bus> routeNumberRule() {
    return bus -> {
      int route = bus.routeNumber();
      if (route < MIN_ROUTE_NUMBER || route > MAX_ROUTE_NUMBER) {
        return Optional.of("Номер маршрута должен быть от " + MIN_ROUTE_NUMBER +
                           " до " + MAX_ROUTE_NUMBER +
                           ", текущее значение: " + route);
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
      if (model.length() < MIN_MODEL_LENGTH ||
          model.length() > MAX_MODEL_LENGTH) {
        return Optional.of("Модель должна содержать от " + MIN_MODEL_LENGTH +
                           " до " + MAX_MODEL_LENGTH +
                           " символов, текущая длина: " + model.length());
      }
      if (!model.matches(MODEL_PATTERN)) {
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
      if (mileage < MIN_MILEAGE || mileage > MAX_MILEAGE) {
        return Optional.of("Пробег должен быть от " + MIN_MILEAGE + " до " +
                           MAX_MILEAGE + ", текущее значение: " + mileage);
      }
      return Optional.empty();
    };
  }
}