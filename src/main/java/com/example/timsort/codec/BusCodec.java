package com.example.timsort.codec;

import com.example.timsort.model.Bus;
import java.util.Objects;
import java.util.Optional;


/**
 * Codec for serializing and deserializing Bus objects.
 *
 * <p>This codec converts between {@link Bus} objects and their CSV string
 * representation using semicolon ({@code ;}) as delimiter.
 * The format is: {@code routeNumber;model;mileage}</p>
 *
 * <p>This codec is stateless and thread-safe. It performs syntactic
 * conversion only — semantic validity (ranges, model format) is
 * BusValidator's job, not this class's.</p>
 *
 * <p>WHY null model is rejected on encode: String.format would render a
 * null model as the literal string {@code "null"}, corrupting the data
 * silently — a raw bus decoded back would carry the model "null" and
 * even pass validation. Failing fast is the honest alternative.</p>
 */
public class BusCodec {

  private static final String DELIMITER = ";";

  /**
   * Encodes a {@link Bus} object into its CSV string representation.
   *
   * <p>The resulting format is: {@code routeNumber;model;mileage}</p>
   *
   * <p>Example: {@code 42;LiAZ-5292;150000}</p>
   *
   * @param bus the Bus object to encode, must not be null
   * @return CSV string representation of the bus
   * @throws NullPointerException     if bus is null
   * @throws IllegalArgumentException if the model is null — the CSV
   *         format cannot represent it without silent corruption
   */
  public String encode(Bus bus) {
    Objects.requireNonNull(bus, "Bus must not be null");
    if (bus.model() == null) {
      throw new IllegalArgumentException(
          "Cannot encode a bus with a null model: " + bus);
    }
    return bus.routeNumber() + DELIMITER + bus.model() + DELIMITER +
        bus.mileage();
  }

  /**
   * Decodes a CSV string into a {@link Bus} object.
   *
   * <p>The string must contain exactly three parts separated by
   * semicolon: route number (int), model (String), and mileage (long).
   * All parts are trimmed. A syntactically valid string with semantically
   * invalid values (negative numbers, blank model) decodes successfully
   * into a raw bus — BusValidator is the semantic gate, see FileBusSource
   * for the validating pipeline.</p>
   *
   * <p>A known limitation: the model itself must not contain the
   * delimiter — {@code "A;B"} splits into four parts and yields an empty
   * Optional (visible failure, unlike the null-model corruption).</p>
   *
   * @param csv the CSV string to decode, may be null or empty
   * @return Optional containing the decoded Bus if the string is
   *         syntactically valid, or Optional.empty() otherwise
   */
  public Optional<Bus> decode(String csv) {
    if (csv == null || csv.isEmpty()) {
      return Optional.empty();
    }

    // WHY the -1 limit: without it split() silently drops trailing empty
    // strings, and "42;model;150000;" would decode as a valid 3-part line
    String[] parts = csv.split(DELIMITER, -1);
    if (parts.length != 3) {
      return Optional.empty();
    }

    try {
      int routeNumber = Integer.parseInt(parts[0].trim());
      String model = parts[1].trim();
      long mileage = Long.parseLong(parts[2].trim());

      return Optional.of(Bus.builder()
                             .routeNumber(routeNumber)
                             .model(model)
                             .mileage(mileage)
                             .build());
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}