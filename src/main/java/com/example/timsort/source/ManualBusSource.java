package com.example.timsort.source;

import com.example.timsort.app.ConsoleIO;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.BusValidationConstants;
import com.example.timsort.validation.ValidationResult;
import com.example.timsort.validation.Validator;
import java.util.Objects;

/**
 * Data source that asks the user to enter buses via the console.
 * Implements the Strategy pattern (DataSource).
 *
 * <p>WHY the validator is injected: semantic rules (ranges, model
 * format) have a single source of truth — the validator; this class
 * only orchestrates the dialog and the retries.</p>
 */
public class ManualBusSource implements DataSource<Bus> {

  /** Attempts per bus before the element is skipped. */
  private static final int MAX_ATTEMPTS = 3;

  private final ConsoleIO console;
  private final Validator<Bus> validator;

  /**
   * Constructor.
   *
   * @param console   console interaction adapter
   * @param validator validator for the entered data
   * @throws NullPointerException if any parameter is null
   */
  public ManualBusSource(ConsoleIO console, Validator<Bus> validator) {
    this.console = Objects.requireNonNull(console, "console must not be null");
    this.validator =
        Objects.requireNonNull(validator, "validator must not be null");
  }

  /**
   * Asks the user for {@code count} buses. Each bus gets up to
   * {@value #MAX_ATTEMPTS} attempts; an exhausted element is skipped.
   * A closed input stream (EOF) terminates the whole dialog and returns
   * whatever has been collected so far.
   *
   * @param count number of objects to request (must be >= 0)
   * @return successfully entered buses
   * @throws IllegalArgumentException if count is negative
   */
  @Override
  public CustomArrayList<Bus> provide(int count) {
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative: " +
                                         count);
    }

    CustomArrayList<Bus> result = new CustomArrayList<>();

    for (int i = 0; i < count; i++) {
      console.print("\n--- Bus " + (i + 1) + " of " + count + " ---");
      Bus bus;
      try {
        bus = readBusWithRetries();
      } catch (InputClosedSignal e) {
        console.print("Input closed. Entered " + result.size() + " of " +
                      count + " buses.");
        return result;
      }

      if (bus != null) {
        result.add(bus);
      } else {
        console.print("Attempts exceeded. The element is skipped.");
      }
    }

    return result;
  }

  /**
   * Reads and validates one bus with a bounded number of attempts.
   *
   * @return a valid bus, or null when the attempts are exhausted
   * @throws InputClosedSignal when the input stream is closed (EOF)
   */
  private Bus readBusWithRetries() {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        // keep the hints in sync with the BusValidator ranges
        console.print("Enter the route number (" +
                      BusValidationConstants.MAX_ROUTE_NUMBER + "-" +
                      BusValidationConstants.MAX_ROUTE_NUMBER + "): ");
        int routeNumber = Integer.parseInt(readRequiredLine().trim());

        console.print("Enter the bus model: ");
        // UI-level normalization; the validator applies its own strip() too
        String model = readRequiredLine().trim();

        console.print("Enter the mileage (" +
                      BusValidationConstants.MIN_MILEAGE + "-" +
                      BusValidationConstants.MAX_MILEAGE + "): ");
        long mileage = Long.parseLong(readRequiredLine().trim());

        Bus candidate = Bus.builder()
                            .routeNumber(routeNumber)
                            .model(model)
                            .mileage(mileage)
                            .build();

        ValidationResult<Bus> validation = validator.validate(candidate);
        if (validation.isValid()) {
          return candidate;
        }
        console.print("Validation error: " +
                      String.join(", ", validation.errors()));

      } catch (NumberFormatException e) {
        console.print("Error: not a valid number. Try again.");
      }

      if (attempt < MAX_ATTEMPTS) {
        console.print("Attempts left: " + (MAX_ATTEMPTS - attempt));
      }
    }
    return null;
  }

  /**
   * WHY a signal exception instead of null checks at every read site:
   * EOF must terminate the whole dialog, not just one field — the signal
   * unwinds from any depth of the input scenario at once and is caught
   * once, in provide().
   */
  private String readRequiredLine() {
    String line = console.readLine();
    if (line == null) {
      throw new InputClosedSignal();
    }
    return line;
  }

  /** Terminal signal: the input stream is closed (readLine() returned null). */
  private static final class InputClosedSignal extends RuntimeException {

    InputClosedSignal() {
      // WHY no stack trace: a control-flow signal, never meant for logs
      super("input closed", null, false, false);
    }
  }
}