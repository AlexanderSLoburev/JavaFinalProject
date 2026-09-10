package com.example.timsort.sort;

import com.example.timsort.model.Bus;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Static registry of Comparator implementations for Bus,
 * keyed by BusField enum.
 *
 * Usage: `BusComparators.byField(BusField.MILEAGE)`. The map is
 * populated once in a static initializer, so all call sites share the same
 * comparator instances and there is no per-call construction cost.
 */
public final class BusComparators {

  private static final Map<BusField, Comparator<Bus>> COMPARATORS;

  static {
    Map<BusField, Comparator<Bus>> registry = new EnumMap<>(BusField.class);

    // Note: a comparator built from the primary
    // field alone returns 0 for two *different* buses sharing that field,
    // which makes their relative order arbitrary and inconsistent with
    // Bus.equals. Chaining the remaining fields yields a total order that
    // agrees with equals, so the sorted result is fully deterministic.
    Comparator<Bus> byRouteNumber =
        Comparator
            .comparingInt(Bus::routeNumber)   // primary: ascending
            .thenComparing(Bus::model)        // tie-break: lexicographic
            .thenComparingLong(Bus::mileage); // final tie-break

    Comparator<Bus> byModel =
        Comparator
            .comparing(Bus::model) // primary: lexicographic
            .thenComparingInt(Bus::routeNumber)
            .thenComparingLong(Bus::mileage);

    Comparator<Bus> byMileage =
        Comparator
            .comparingLong(Bus::mileage) // primary: ascending
            .thenComparingInt(Bus::routeNumber)
            .thenComparing(Bus::model);

    registry.put(BusField.ROUTE_NUMBER, byRouteNumber);
    registry.put(BusField.MODEL, byModel);
    registry.put(BusField.MILEAGE, byMileage);

    COMPARATORS = Collections.unmodifiableMap(registry);
  }

  private BusComparators() {
    // Static-only holder — instances have no state and no meaning.
  }

  /**
   * Returns the comparator registered for the given field.
   *
   * @param field sort key, must not be {@code null}
   * @return comparator ordering buses by that field (numeric fields
   *         ascending, model lexicographically) with deterministic
   *         tie-breaks on the remaining fields
   * @throws NullPointerException if field is {@code null}
   * @throws IllegalArgumentException if the constant has no registered
   *         comparator (defensive: every constant is covered today, but a
   *         future enum value without a {@code put} would otherwise fail
   *         as a bare NPE deep inside a sort instead of failing loudly here)
   */
  public static Comparator<Bus> byField(BusField field) {
    Objects.requireNonNull(field, "field must not be null");
    Comparator<Bus> comparator = COMPARATORS.get(field);
    if (comparator == null) {
      throw new IllegalArgumentException("No comparator registered for " +
                                         field);
    }
    return comparator;
  }
}