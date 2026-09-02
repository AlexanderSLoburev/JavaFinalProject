package com.example.timsort.model;

import java.util.Objects;

public final class Bus {
  private final int routeNumber;
  private final String model;
  private final long mileage;

  private Bus(int routeNumber, String model, long mileage) {
    if (routeNumber < 0) {
      throw new IllegalArgumentException("routeNumber must be non-negative");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be null or blank");
    }
    if (mileage < 0) {
      throw new IllegalArgumentException("mileage must be non-negative");
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