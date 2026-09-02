package com.example.timsort.model;

public class BusBuilder {
  private int routeNumber;
  private String model;
  private long mileage;
  private boolean routeNumberSet;
  private boolean modelSet;
  private boolean mileageSet;

  public BusBuilder routeNumber(int routeNumber) {
    this.routeNumber = routeNumber;
    this.routeNumberSet = true;
    return this;
  }

  public BusBuilder model(String model) {
    this.model = model;
    this.modelSet = model != null && !model.isBlank();
    return this;
  }

  public BusBuilder mileage(long mileage) {
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
      if (!missing.isEmpty()) {
        missing.append(", ");
      }
      missing.append("model");
    }
    if (!mileageSet) {
      if (!missing.isEmpty()) {
        missing.append(", ");
      }
      missing.append("mileage");
    }

    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing fields: " + missing);
    }

    return new Bus(routeNumber, model, mileage);
  }
}
