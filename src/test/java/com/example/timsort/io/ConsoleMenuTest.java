package com.example.timsort.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsoleMenuTest {

    private ConsoleIO io;
    private ConsoleMenu menu;

    @BeforeEach
    void setUp() {
        io = mock(ConsoleIO.class);
        Session session = new Session();
        menu = new ConsoleMenu(io, session);
    }

    @Test
    @DisplayName("Should execute registered command when valid option is entered")
    void shouldExecuteRegisteredCommand() {
        AtomicInteger counter = new AtomicInteger(0);
        menu.register(1, "Test command", counter::incrementAndGet);

        when(io.readLine()).thenReturn("1", "0");
        menu.register(0, "Exit", () -> { throw new ExitException(); });

        assertThrows(ExitException.class, () -> menu.run());

        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("Should print error and continue when input is not a number")
    void shouldPrintErrorOnNonNumericInput() {
        AtomicInteger exitCounter = new AtomicInteger(0);
        menu.register(0, "Exit", () -> {
            exitCounter.incrementAndGet();
            throw new ExitException();
        });

        when(io.readLine()).thenReturn("abc", "0");

        assertThrows(ExitException.class, () -> menu.run());

        verify(io, atLeastOnce()).print(contains("Ошибка"));
        assertEquals(1, exitCounter.get());
    }

    @Test
    @DisplayName("Should print error and continue when option does not exist")
    void shouldPrintErrorOnUnknownOption() {
        AtomicInteger exitCounter = new AtomicInteger(0);
        menu.register(0, "Exit", () -> {
            exitCounter.incrementAndGet();
            throw new ExitException();
        });

        when(io.readLine()).thenReturn("99", "0");

        assertThrows(ExitException.class, () -> menu.run());

        verify(io, atLeastOnce()).print(contains("Ошибка"));
        assertEquals(1, exitCounter.get());
    }

    @Test
    @DisplayName("Should exit only through exit command")
    void shouldExitOnlyThroughExitCommand() {
        AtomicInteger command1Counter = new AtomicInteger(0);
        menu.register(1, "Command 1", command1Counter::incrementAndGet);
        menu.register(0, "Exit", () -> { throw new ExitException(); });

        when(io.readLine()).thenReturn("1", "1", "0");

        assertThrows(ExitException.class, () -> menu.run());

        assertEquals(2, command1Counter.get());
    }

    @Test
    @DisplayName("Should print menu with all registered commands")
    void shouldPrintMenuWithRegisteredCommands() {
        menu.register(1, "Fill collection", () -> {});
        menu.register(2, "Show collection", () -> {});
        menu.register(0, "Exit", () -> { throw new ExitException(); });

        when(io.readLine()).thenReturn("0");

        assertThrows(ExitException.class, () -> menu.run());

        verify(io, atLeastOnce()).print(contains("Fill collection"));
        verify(io, atLeastOnce()).print(contains("Show collection"));
        verify(io, atLeastOnce()).print(contains("Exit"));
    }

    @Test
    @DisplayName("Should not execute any command when exit is chosen first")
    void shouldNotExecuteAnyCommandOnImmediateExit() {
        AtomicInteger counter = new AtomicInteger(0);
        menu.register(1, "Some command", counter::incrementAndGet);
        menu.register(0, "Exit", () -> { throw new ExitException(); });

        when(io.readLine()).thenReturn("0");

        assertThrows(ExitException.class, () -> menu.run());

        assertEquals(0, counter.get());
    }

    @Test
    @DisplayName("Should handle empty input gracefully")
    void shouldHandleEmptyInput() {
        menu.register(0, "Exit", () -> { throw new ExitException(); });

        when(io.readLine()).thenReturn("", "0");

        assertThrows(ExitException.class, () -> menu.run());

        verify(io, atLeastOnce()).print(contains("Ошибка"));
    }

    @Test
    @DisplayName("Should execute multiple different commands in sequence")
    void shouldExecuteMultipleDifferentCommands() {
        AtomicInteger counter1 = new AtomicInteger(0);
        AtomicInteger counter2 = new AtomicInteger(0);

        menu.register(1, "Command 1", counter1::incrementAndGet);
        menu.register(2, "Command 2", counter2::incrementAndGet);
        menu.register(0, "Exit", () -> { throw new ExitException(); });

        when(io.readLine()).thenReturn("1", "2", "1", "0");

        assertThrows(ExitException.class, () -> menu.run());

        assertEquals(2, counter1.get());
        assertEquals(1, counter2.get());
    }
}