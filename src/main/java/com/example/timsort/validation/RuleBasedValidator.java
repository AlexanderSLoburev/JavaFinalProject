package com.example.timsort.validation;

import com.example.timsort.collection.CustomArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Generic validator backed by a list of rules: applies every rule and
 * accumulates all violations. This is the reusable core of the framework —
 * concrete validators only declare their rules.
 *
 * <p>A null input yields a failure instead of an NPE,
 * keeping the "no exceptions in the validation flow" guarantee.</p>
 */
public final class RuleBasedValidator<T> implements Validator<T> {

  private final List<Rule<T>> rules;

  private RuleBasedValidator(List<Rule<T>> rules) {
    this.rules = List.copyOf(rules);
  }

  public static <T> Validator<T> of(List<Rule<T>> rules) {
    return new RuleBasedValidator<>(rules);
  }

  @Override
  public ValidationResult<T> validate(T value) {
    if (value == null) {
      return ValidationResult.failure(
          List.of("The object being validated is not specified (null)"));
    }

    List<String> errors =
        rules.stream()
            .flatMap(rule -> rule.apply(value).stream())
            .collect(Collectors.toCollection(CustomArrayList::new));

    return errors.isEmpty() ? ValidationResult.of(value)
                            : ValidationResult.failure(errors);
  }
}