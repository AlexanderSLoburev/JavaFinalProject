package com.example.timsort.sort;

public enum BusField {
  ROUTE_NUMBER("Route number"),
  MODEL("Model"),
  MILEAGE("Mileage");

  private String name;

  BusField(String name) { this.name = name; }

  @Override
  public String toString() {
    return name;
  }
}