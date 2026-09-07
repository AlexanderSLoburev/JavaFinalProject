package com.example.timsort.model;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class Bus implements Serializable {

  // WHY an explicit serialVersionUID: pins the wire identity of the class.
  // Without it the auto-computed value changes on any later refactor (even
  // adding a method), and previously written streams start failing with
  // InvalidClassException.
  @Serial private static final long serialVersionUID = 1L;

  private final int routeNumber;
  private final String model;
  private final long mileage;

  private Bus(int routeNumber, String model, long mileage) {
    String problem = invariantViolation(routeNumber, model, mileage);
    if (problem != null) {
      throw new IllegalArgumentException(problem);
    }
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
   * Returns a human-readable description of the first violated invariant,
   * or null when every argument is valid.
   *
   * <p>WHY a shared helper: the same rules are enforced from two places that
   * throw different exception types — the constructor
   * (IllegalArgumentException) and readObject (InvalidObjectException) — so
   * a single source of checks keeps the two paths (and their messages)
   * from drifting apart.
   */
  private static String invariantViolation(int routeNumber, String model,
                                           long mileage) {
    if (routeNumber < 0) {
      return "routeNumber must be non-negative";
    }
    if (model == null || model.isBlank()) {
      return "model must not be null or blank";
    }
    if (mileage < 0) {
      return "mileage must be non-negative";
    }
    return null;
  }

  @Serial
  private void readObject(ObjectInputStream in)
      throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // WHY revalidate here: deserialization materializes the final fields
    // directly, bypassing both the builder and the constructor, so a
    // hostile or corrupted stream could otherwise produce a Bus with, say,
    // a negative mileage. Throwing from readObject aborts the whole read
    // with InvalidObjectException instead of letting an invalid instance
    // escape into the program.
    String problem = invariantViolation(routeNumber, model, mileage);
    if (problem != null) {
      throw new InvalidObjectException(problem);
    }
  }

  public static final class BusBuilder {
    private int routeNumber;
    private String model;
    private long mileage;
    private boolean routeNumberSet;
    private boolean modelSet;
    private boolean mileageSet;

    private BusBuilder() {}

    public BusBuilder routeNumber(int routeNumber) {
      if (routeNumber < 0) {
        throw new IllegalArgumentException("routeNumber must be non-negative");
      }
      this.routeNumber = routeNumber;
      this.routeNumberSet = true;
      return this;
    }

    public BusBuilder model(String model) {
      if (model == null || model.isBlank()) {
        throw new IllegalArgumentException("model must not be null or blank");
      }
      this.model = model;
      this.modelSet = true;
      return this;
    }

    public BusBuilder mileage(long mileage) {
      if (mileage < 0) {
        throw new IllegalArgumentException("mileage must be non-negative");
      }
      this.mileage = mileage;
      this.mileageSet = true;
      return this;
    }

    public Bus build() {
      StringBuilder missing = new StringBuilder();
      if (!routeNumberSet) {
        missing.append("routeNumber");
      }
      if (!modelSet) {
        if (missing.length() > 0) {
          missing.append(", ");
        }
        missing.append("model");
      }
      if (!mileageSet) {
        if (missing.length() > 0) {
          missing.append(", ");
        }
        missing.append("mileage");
      }
      if (missing.length() > 0) {
        throw new IllegalStateException("Missing fields: " + missing);
      }
      return new Bus(routeNumber, model, mileage);
    }
  }
}