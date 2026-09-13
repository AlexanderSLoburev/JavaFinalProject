package com.example.timsort.validation;

import com.example.timsort.model.Bus;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validator for {@link Bus}: route number 1..999, model 2..30 chars
 * (Cyrillic/Latin letters, digits, hyphen, space), mileage 0..2 000 000.
 *
 * <p>Rule application and error accumulation live in
 * RuleBasedValidator; this class only declares the rules.</p>
 *
 * <p>Bus is a wide carrier and guarantees
 * nothing, so the validator is the only line of defense.</p>
 */
public final class BusValidator implements Validator<Bus> {

  private static final int MIN_ROUTE_NUMBER = 1;
  private static final int MAX_ROUTE_NUMBER = 999;
  private static final int MIN_MODEL_LENGTH = 2;
  private static final int MAX_MODEL_LENGTH = 30;
  private static final Pattern MODEL_PATTERN =
      Pattern.compile("[а-яА-ЯёЁa-zA-Z0-9\\-\\s]+");
  private static final long MIN_MILEAGE = 0;
  private static final long MAX_MILEAGE = 2_000_000;

  private final Validator<Bus> delegate = RuleBasedValidator.of(
      List.of(routeNumberRule(), modelRule(), mileageRule()));

  @Override
  public ValidationResult<Bus> validate(Bus bus) {
    return delegate.validate(bus);
  }

  /** Route number must be between 1 and 999. */
  private static Rule<Bus> routeNumberRule() {
    return bus -> {
      int route = bus.routeNumber();

      if (route < MIN_ROUTE_NUMBER || route > MAX_ROUTE_NUMBER) {
        return Optional.of("The route number must be between " +
                           MIN_ROUTE_NUMBER + " and " + MAX_ROUTE_NUMBER +
                           ", current value: " + route);
      }

      return Optional.empty();
    };
  }

  /**
   * Model: non-null, non-blank, 2..30 characters, allowed symbols only.
   */
  private static Rule<Bus> modelRule() {
    return bus -> {
      String model = bus.model();
      if (model == null || model.isBlank()) {
        return Optional.of("The model cannot be null or blank");
      }

      String normalized = model.strip();
      if (normalized.length() < MIN_MODEL_LENGTH ||
          normalized.length() > MAX_MODEL_LENGTH) {
        return Optional.of(
            "The model must contain between " + MIN_MODEL_LENGTH + " and " +
            MAX_MODEL_LENGTH +
            " characters; current length: " + normalized.length());
      }

      if (!MODEL_PATTERN.matcher(normalized).matches()) {
        return Optional.of(
            "The model can contain only letters, numbers, hyphens, and spaces");
      }

      return Optional.empty();
    };
  }

  /** Mileage must be between 0 and 2 000 000. */
  private static Rule<Bus> mileageRule() {
    return bus -> {
      long mileage = bus.mileage();
      if (mileage < MIN_MILEAGE || mileage > MAX_MILEAGE) {
        return Optional.of("Mileage must be between " + MIN_MILEAGE + " and " +
                           MAX_MILEAGE + ", current value: " + mileage);
      }
      return Optional.empty();
    };
  }
}