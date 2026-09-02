package com.example.timsort.model;

public class BusBuilder {
  private int routeNumber;
  private String model;
  private long mileage;
  private boolean routeNumberSet;
  private boolean modelSet;
  private boolean mileageSet;

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