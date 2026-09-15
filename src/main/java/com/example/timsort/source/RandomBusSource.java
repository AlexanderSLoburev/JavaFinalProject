package com.example.timsort.source;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.BusValidationConstants;
import com.example.timsort.validation.ValidationResult;
import com.example.timsort.validation.Validator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Data source generating random valid Bus objects with the Stream API
 * and ThreadLocalRandom.
 *
 * <p>WHY the validator is injected although generation is valid by
 * construction: it acts as a self-check — if the generator ranges ever
 * drift out of sync with the validator rules, provide fails fast with
 * IllegalStateException instead of silently emitting invalid data.</p>
 *
 * <p>Stateless (a final validator and static constants only), so one
 * instance may be shared between threads.</p>
 */
public class RandomBusSource implements DataSource<Bus> {

  private static final List<String> MODELS_POOL =
      List.of("ЛиАЗ-5292", "МАЗ-203", "ПАЗ-3204", "Волжанин-6270", "НефАЗ-5299",
              "KAvZ-4270", "ГолАЗ-6228", "MAN Lion City");

  private final Validator<Bus> validator;

  /**
   * Constructor.
   *
   * @param validator validates the generated objects
   * @throws NullPointerException if validator is null
   */
  public RandomBusSource(Validator<Bus> validator) {
    this.validator =
        Objects.requireNonNull(validator, "validator must not be null");
  }

  /**
   * Generates exactly {@code count} random valid buses — unlike the
   * other DataSource implementations, the result size is guaranteed.
   *
   * @param count the number of objects (must be >= 0)
   * @return the generated buses
   * @throws IllegalArgumentException if count is negative
   * @throws IllegalStateException if the generator ranges have drifted
   *         out of sync with the validator (an internal assertion,
   *         not a normal scenario)
   */
  @Override
  public CustomArrayList<Bus> provide(int count) {
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative: " +
                                         count);
    }

    return IntStream.range(0, count)
        .mapToObj(i -> generateValidBus())
        .collect(Collectors.toCollection(CustomArrayList::new));
  }

  /**
   * Generates one valid bus: the ranges are valid by construction, the
   * validator is a self-check.
   *
   * @return a valid bus
   */
  private Bus generateValidBus() {
    Bus bus =
        Bus.builder()
            .routeNumber(randomInt(BusValidationConstants.MIN_ROUTE_NUMBER,
                                   BusValidationConstants.MAX_ROUTE_NUMBER))
            .model(randomModel())
            .mileage(randomLong(BusValidationConstants.MIN_MILEAGE,
                                BusValidationConstants.MAX_MILEAGE))
            .build();

    ValidationResult<Bus> result = validator.validate(bus);
    if (!result.isValid()) {
      throw new IllegalStateException("Generated bus failed validation: " +
                                      result.errors());
    }
    return bus;
  }

  /**
   * @return a random model from the pool
   */
  private String randomModel() {
    int index = ThreadLocalRandom.current().nextInt(MODELS_POOL.size());
    return MODELS_POOL.get(index);
  }

  /**
   * @param min the lower bound, inclusive
   * @param max the upper bound, inclusive
   * @return a random int in [min, max]
   */
  private int randomInt(int min, int max) {
    // WHY max + 1: the ThreadLocalRandom bound is exclusive
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  /**
   * @param min the lower bound, inclusive
   * @param max the upper bound, inclusive
   * @return a random long in [min, max]
   */
  private long randomLong(long min, long max) {
    // WHY max + 1: the ThreadLocalRandom bound is exclusive
    return ThreadLocalRandom.current().nextLong(min, max + 1);
  }
}