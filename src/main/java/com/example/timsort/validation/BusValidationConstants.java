package com.example.timsort.validation;

import java.util.regex.Pattern;

public final class BusValidationConstants {
  public static final int MIN_ROUTE_NUMBER = 1;
  public static final int MAX_ROUTE_NUMBER = 999;
  public static final int MIN_MODEL_LENGTH = 2;
  public static final int MAX_MODEL_LENGTH = 30;
  public static final Pattern MODEL_PATTERN =
      Pattern.compile("[а-яА-ЯёЁa-zA-Z0-9\\- \\t]+");
  public static final long MIN_MILEAGE = 0;
  public static final long MAX_MILEAGE = 2_000_000;

  private BusValidationConstants() {}
}
