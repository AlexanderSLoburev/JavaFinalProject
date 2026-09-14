package com.example.timsort.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A bus: route number, model and mileage.
 *
 * <p>Why no invariants: Bus is a wide data carrier; all business rules
 * (ranges, model format, null-ness) live in BusValidator — the single
 * source of truth. This keeps invalid values representable, so the
 * validator is testable for every rule.</p>
 */
public final class Bus {

  private final int routeNumber;
  private final String model;
  private final long mileage;

  private Bus(int routeNumber, String model, long mileage) {
    this.routeNumber = routeNumber;
    this.model = model;
    this.mileage = mileage;
  }

  public static BusBuilder builder() { return new BusBuilder(); }

  public int routeNumber() { return routeNumber; }

  public String model() { return model; }

  public long mileage() { return mileage; }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Bus bus)) {
      return false;
    }
    return routeNumber == bus.routeNumber && mileage == bus.mileage &&
        Objects.equals(model, bus.model);
  }

  @Override
  public int hashCode() {
    return Objects.hash(routeNumber, model, mileage);
  }

  @Override
  public String toString() {
    return "Bus{" + routeNumber + ", " + model + ", " + mileage + "}";
  }

  /**
   * Builder with mandatory-field tracking.
   */
  public static final class BusBuilder {
    private int routeNumber;
    private String model;
    private long mileage;
    private boolean routeNumberSet;
    private boolean modelSet;
    private boolean mileageSet;

    private BusBuilder() {}

    public BusBuilder routeNumber(int routeNumber) {
      this.routeNumber = routeNumber;
      this.routeNumberSet = true;
      return this;
    }

    public BusBuilder model(String model) {
      this.model = model;
      this.modelSet = true;
      return this;
    }

    public BusBuilder mileage(long mileage) {
      this.mileage = mileage;
      this.mileageSet = true;
      return this;
    }

    /**
     * @throws IllegalStateException if any field was not set
     */
    public Bus build() {
      List<String> missing = new ArrayList<>();
      if (!routeNumberSet) {
        missing.add("routeNumber");
      }
      if (!modelSet) {
        missing.add("model");
      }
      if (!mileageSet) {
        missing.add("mileage");
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing fields: " +
                                        String.join(", ", missing));
      }

      return new Bus(routeNumber, model, mileage);
    }
  }
}