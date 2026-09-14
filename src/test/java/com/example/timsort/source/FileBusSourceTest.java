package com.example.timsort.source;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.BusValidator;
import com.example.timsort.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for FileBusSource: valid/invalid line handling, header skipping,
 * line numbering in warnings, count semantics and constructor contracts.
 *
 * <p>WHY Cyrillic model names in the data: they are test data, not
 * messages — and they double as UTF-8 coverage.</p>
 */
class FileBusSourceTest {

  @TempDir Path tempDir;

  private BusCodec codec;
  private Validator<Bus> validator;

  @BeforeEach
  void setUp() {
    codec = new BusCodec();
    validator = new BusValidator();
  }

  /**
   * WHY autoFlush + UTF-8: PrintStream wraps its output in an internal
   * buffer; without autoFlush the warnings never reach the underlying
   * ByteArrayOutputStream, and the default charset is platform-dependent.
   */
  private PrintStream newOutput(ByteArrayOutputStream out) {
    return new PrintStream(out, true, StandardCharsets.UTF_8);
  }

  private FileBusSource source(Path file, ByteArrayOutputStream output) {
    return new FileBusSource(file, codec, validator, newOutput(output));
  }

  private Path createTempFile(List<String> lines) throws IOException {
    Path file = tempDir.resolve("test.csv");
    Files.write(file, lines);
    return file;
  }

  // -------------------------------------------------------------------
  // Loading valid data
  // -------------------------------------------------------------------

  @Test
  void when_fileWithValidLines_then_returnsExactCount() throws IOException {
    // Arrange
    Path file =
        createTempFile(List.of("routeNumber;model;mileage", // header
                               "1;ЛиАЗ-5292;100000", "2;МАЗ-203;200000",
                               "3;ПАЗ-3204;300000", "4;Волжанин-6270;400000"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(3);

    // Assert
    assertEquals(3, result.size(), "Exactly count records must be returned");

    // The header is skipped: the first three valid data lines are taken
    assertEquals(1, result.get(0).routeNumber());
    assertEquals(2, result.get(1).routeNumber());
    assertEquals(3, result.get(2).routeNumber());

    assertTrue(output.toString(StandardCharsets.UTF_8).isEmpty(),
               "No warnings are expected for valid data");
  }

  @Test
  void when_fileWithHeader_then_skipsFirstLine() throws IOException {
    // Arrange
    Path file = createTempFile(List.of("routeNumber;model;mileage", // header
                                       "1;ЛиАЗ-5292;100000"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(1);

    // Assert
    assertEquals(1, result.size());
    assertEquals(1, result.get(0).routeNumber());
    assertTrue(output.toString(StandardCharsets.UTF_8).isEmpty(),
               "The skipped header is not an error — no warnings expected");
  }

  @Test
  void when_loadedBuses_then_allFieldsArePreserved() throws IOException {
    // Arrange: pins the full field round trip, not just routeNumber
    Path file = createTempFile(
        List.of("routeNumber;model;mileage", "42;ЛиАЗ-5292;150000"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(1);

    // Assert
    Bus bus = result.get(0);
    assertEquals(42, bus.routeNumber());
    assertEquals("ЛиАЗ-5292", bus.model(),
                 "The Cyrillic model must survive the round trip");
    assertEquals(150_000L, bus.mileage());
  }

  // -------------------------------------------------------------------
  // Invalid lines
  // -------------------------------------------------------------------

  @Test
  void when_fileWithInvalidLines_then_skipsThemWithWarnings()
      throws IOException {
    // Arrange
    Path file = createTempFile(
        List.of("routeNumber;model;mileage",
                "1;ЛиАЗ-5292;100000",  // valid
                "invalid;line",        // invalid format
                "2;МАЗ-203;200000",    // valid
                "0;ПАЗ-3204;300000")); // invalid: routeNumber = 0
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(10);

    // Assert
    assertEquals(2, result.size(), "Invalid lines must be skipped");
    assertEquals(1, result.get(0).routeNumber());
    assertEquals(2, result.get(1).routeNumber());

    String warnings = output.toString(StandardCharsets.UTF_8);
    assertTrue(warnings.contains("Line 3"), "A warning for line 3 is expected");
    assertTrue(warnings.contains("Line 5"), "A warning for line 5 is expected");
  }

  @Test
  void when_allLinesInvalid_then_everyLineNumberIsReported()
      throws IOException {
    // Arrange: three different failure kinds, one per line
    Path file = createTempFile(
        List.of("routeNumber;model;mileage", // line 1: header
                "abc;def",                   // line 2: wrong field count
                "0;X;100",           // line 3: decodes, fails validation
                "42;X;notanumber")); // line 4: mileage is not a number
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(10);

    // Assert
    assertTrue(result.isEmpty(), "No record may survive from invalid lines");

    String warnings = output.toString(StandardCharsets.UTF_8);
    // Line numbers are file coordinates (the header is line 1)
    assertTrue(warnings.contains("Line 2"), "A warning for line 2 is expected");
    assertTrue(warnings.contains("Line 3"), "A warning for line 3 is expected");
    assertTrue(warnings.contains("Line 4"), "A warning for line 4 is expected");

    // The warning must explain WHY, not just point at the line:
    // the validator's message for line 3 names the route number
    assertTrue(warnings.contains("route number"),
               "Warnings must include the validation error details");
  }

  @Test
  void when_blankLineInsideFile_then_itNeverBecomesARecord()
      throws IOException {
    // Arrange
    Path file = createTempFile(List.of("routeNumber;model;mileage",
                                       "1;ЛиАЗ-5292;100000",
                                       "", // blank line: decodes to nothing
                                       "2;МАЗ-203;200000"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(10);

    // Assert: only the result composition is pinned — whether a blank
    // line is warned about or skipped silently is an implementation
    // detail
    assertEquals(2, result.size(), "A blank line must not become a record");
    assertEquals(1, result.get(0).routeNumber());
    assertEquals(2, result.get(1).routeNumber());
  }

  // -------------------------------------------------------------------
  // Count semantics and file states
  // -------------------------------------------------------------------

  @Test
  void when_fewerValidLinesThanCount_then_returnsAllAvailable()
      throws IOException {
    // Arrange
    Path file = createTempFile(List.of(
        "routeNumber;model;mileage", "1;ЛиАЗ-5292;100000", "2;МАЗ-203;200000"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(10);

    // Assert
    assertEquals(2, result.size(),
                 "All available valid records must be returned");

    String warnings = output.toString(StandardCharsets.UTF_8);
    assertTrue(
        warnings.contains("valid records") || warnings.contains("Warning"),
        "A message about the missing records is expected, output: " + warnings);
  }

  @Test
  void when_fileContainsOnlyHeader_then_returnsEmptyCollectionWithWarning()
      throws IOException {
    // Arrange
    Path file = createTempFile(List.of("routeNumber;model;mileage"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(2);

    // Assert
    assertTrue(result.isEmpty(),
               "A header-only file must yield an empty collection");
    String warnings = output.toString(StandardCharsets.UTF_8);
    assertTrue(
        warnings.contains("valid records") || warnings.contains("Warning"),
        "A message about the missing records is expected, output: " + warnings);
  }

  @Test
  void when_fileIsEmpty_then_returnsEmptyCollectionWithWarning()
      throws IOException {
    // Arrange: zero bytes, not even a header — the header skip must
    // tolerate this
    Path file = createTempFile(List.of());
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(3);

    // Assert
    assertTrue(result.isEmpty(),
               "An empty file must yield an empty collection");
    String warnings = output.toString(StandardCharsets.UTF_8);
    assertTrue(
        warnings.contains("valid records") || warnings.contains("Warning"),
        "A message about the missing records is expected, output: " + warnings);
  }

  @Test
  void when_countIsZero_then_returnsEmptyCollectionWithoutWarnings()
      throws IOException {
    // Arrange
    Path file = createTempFile(
        List.of("routeNumber;model;mileage", "1;ЛиАЗ-5292;100000"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(file, output).provide(0);

    // Assert
    assertTrue(result.isEmpty(), "count = 0 must return an empty collection");
    assertTrue(output.toString(StandardCharsets.UTF_8).isEmpty(),
               "Nothing is missing when 0 of 0 requested records are returned");
  }

  @Test
  void when_fileDoesNotExist_then_returnsEmptyCollection() {
    // Arrange
    Path nonExistentFile = tempDir.resolve("nonexistent.csv");
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Act
    CustomArrayList<Bus> result = source(nonExistentFile, output).provide(5);

    // Assert
    assertTrue(result.isEmpty(),
               "A missing file must yield an empty collection");
    assertTrue(
        output.toString(StandardCharsets.UTF_8).contains("Failed to read file"),
        "A read-error message is expected");
  }

  @Test
  void when_negativeCount_then_throwsException() throws IOException {
    // Arrange
    Path file = createTempFile(List.of("routeNumber;model;mileage"));
    FileBusSource source = new FileBusSource(file, codec, validator);

    // Act & Assert
    assertThrows(IllegalArgumentException.class,
                 () -> source.provide(-1), "A negative count must throw");
  }

  // -------------------------------------------------------------------
  // Constructor contracts
  // -------------------------------------------------------------------

  @Test
  void when_nullConstructorArguments_then_throwsNpe() {
    // Arrange
    Path file = tempDir.resolve("any.csv");
    PrintStream out = newOutput(new ByteArrayOutputStream());

    // Act & Assert: file, codec and validator are mandatory dependencies
    assertThrows(NullPointerException.class,
                 () -> new FileBusSource(null, codec, validator, out));
    assertThrows(NullPointerException.class,
                 () -> new FileBusSource(file, null, validator, out));
    assertThrows(NullPointerException.class,
                 () -> new FileBusSource(file, codec, null, out));
  }
}