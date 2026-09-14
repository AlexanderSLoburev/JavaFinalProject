package com.example.timsort.validation;

import com.example.timsort.model.Bus;
import java.util.List;
import java.util.Optional;

/**
 * Validator for {@link Bus}: route number 1..999, model 2..30 chars
 * (Cyrillic/Latin letters, digits, hyphen, space, tab), mileage
 * 0..2 000 000.
 *
 * <p>Rule application and error accumulation live in
 * RuleBasedValidator; this class only declares the rules.</p>
 *
 * <p>Bus is a wide carrier and guarantees
 * nothing, so the validator is the only line of defense.</p>
 */
public final class BusValidator implements Validator<Bus> {

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

      if (route < BusValidationConstants.MIN_ROUTE_NUMBER ||
          route > BusValidationConstants.MAX_ROUTE_NUMBER) {
        return Optional.of("The route number must be between " +
                           BusValidationConstants.MIN_ROUTE_NUMBER + " and " +
                           BusValidationConstants.MAX_ROUTE_NUMBER +
                           ", current value: " + route);
      }

      return Optional.empty();
    };
  }

  /**
   * Model: non-null, non-blank, 2..30 characters, allowed symbols only.
   *
   * <p>WHY the pattern lives in BusValidationConstants as a precompiled
   * Pattern: compiling once at class load avoids recompilation per call,
   * and a single shared constant keeps the format description in one
   * place.</p>
   */
  private static Rule<Bus> modelRule() {
    return bus -> {
      String model = bus.model();
      if (model == null || model.isBlank()) {
        return Optional.of("The model cannot be null or blank");
      }

      String normalized = model.strip();
      if (normalized.length() < BusValidationConstants.MIN_MODEL_LENGTH ||
          normalized.length() > BusValidationConstants.MAX_MODEL_LENGTH) {
        return Optional.of(
            "The model must contain between " +
            BusValidationConstants.MIN_MODEL_LENGTH + " and " +
            BusValidationConstants.MAX_MODEL_LENGTH +
            " characters; current length: " + normalized.length());
      }

      if (!BusValidationConstants.MODEL_PATTERN.matcher(normalized).matches()) {
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
      if (mileage < BusValidationConstants.MIN_MILEAGE ||
          mileage > BusValidationConstants.MAX_MILEAGE) {
        return Optional.of("Mileage must be between " +
                           BusValidationConstants.MIN_MILEAGE + " and " +
                           BusValidationConstants.MAX_MILEAGE +
                           ", current value: " + mileage);
      }
      return Optional.empty();
    };
  }
}