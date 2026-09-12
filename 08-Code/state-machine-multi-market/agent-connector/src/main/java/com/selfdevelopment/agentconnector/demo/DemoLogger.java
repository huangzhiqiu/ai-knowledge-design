package com.selfdevelopment.agentconnector.demo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for formatted demo output.
 * <p>
 * Provides consistent, readable logging for demo applications with:
 * <ul>
 *   <li>Timestamps</li>
 *   <li>Visual separators and headers</li>
 *   <li>Color-coded state transitions</li>
 *   <li>Action execution details</li>
 *   <li>Summary statistics</li>
 * </ul>
 */
public class DemoLogger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // ANSI color codes (works in most modern terminals)
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String RED = "\u001B[31m";
    private static final String BOLD = "\u001B[1m";

    private static boolean useColors = true;
    private static int transitionCount = 0;

    /**
     * Enables or disables ANSI colors in output.
     *
     * @param enabled true to enable colors, false to disable
     */
    public static void setColorsEnabled(boolean enabled) {
        useColors = enabled;
    }

    /**
     * Resets the transition counter for a new demo run.
     */
    public static void resetCounter() {
        transitionCount = 0;
    }

    /**
     * Returns the number of transitions logged since last reset.
     *
     * @return transition count
     */
    public static int getTransitionCount() {
        return transitionCount;
    }

    /**
     * Prints a main demo title.
     *
     * @param title the title text
     */
    public static void printTitle(String title) {
        String line = "=".repeat(80);
        System.out.println();
        System.out.println(color(line, CYAN));
        System.out.println(color(BOLD + "  " + title, CYAN));
        System.out.println(color(line, CYAN));
        System.out.println();
    }

    /**
     * Prints a demo section header.
     *
     * @param section the section name
     */
    public static void printSection(String section) {
        String line = "-".repeat(70);
        System.out.println();
        System.out.println(color(line, BLUE));
        System.out.println(color(BOLD + "  " + section, BLUE));
        System.out.println(color(line, BLUE));
    }

    /**
     * Prints the initial state of an interaction.
     *
     * @param state         the initial state
     * @param interactionId the interaction ID
     * @param market        the market
     */
    public static void printInitialState(String state, String interactionId, String market) {
        System.out.println();
        System.out.println(color("  📍 Initial State", BOLD + YELLOW));
        System.out.printf("     %s Interaction: %s%n", timestamp(), interactionId);
        System.out.printf("     %s Market:      %s%n", timestamp(), market);
        System.out.printf("     %s State:       %s%n", timestamp(), color(state, BOLD + GREEN));
        System.out.println();
    }

    /**
     * Prints an action that is about to be executed.
     *
     * @param actionName  the action class name
     * @param description the action description
     */
    public static void printAction(String actionName, String description) {
        System.out.printf("  %s %s %s%n",
                timestamp(),
                color("⚡ ACTION:", BOLD + PURPLE),
                color(actionName, PURPLE));
        System.out.printf("           %s%n", color(description, PURPLE));
    }

    /**
     * Prints a state transition result.
     *
     * @param fromState the source state
     * @param event     the event that triggered the transition
     * @param toState   the target state
     * @param accepted  whether the transition was accepted
     */
    public static void printTransition(String fromState, String event, String toState, boolean accepted) {
        transitionCount++;
        String status = accepted ? color("✓ ACCEPTED", GREEN) : color("✗ REJECTED", RED);
        String arrow = accepted ? "→" : "↛";

        System.out.printf("  %s %s %s %s %s %s %s%n",
                timestamp(),
                color("TRANSITION #" + transitionCount + ":", BOLD + CYAN),
                color(fromState, YELLOW),
                color(arrow, BOLD + CYAN),
                color("[" + event + "]", CYAN),
                color(arrow, BOLD + CYAN),
                color(toState, GREEN));
        System.out.printf("           Status: %s%n", status);
        System.out.println();
    }

    /**
     * Prints an error or exception.
     *
     * @param message the error message
     */
    public static void printError(String message) {
        System.out.printf("  %s %s %s%n",
                timestamp(),
                color("✗ ERROR:", BOLD + RED),
                color(message, RED));
        System.out.println();
    }

    /**
     * Prints an informational message.
     *
     * @param message the info message
     */
    public static void printInfo(String message) {
        System.out.printf("  %s %s %s%n",
                timestamp(),
                color("ℹ INFO:", BOLD + BLUE),
                message);
    }

    /**
     * Prints the final state and summary.
     *
     * @param finalState    the final state
     * @param interactionId the interaction ID
     */
    public static void printFinalState(String finalState, String interactionId) {
        System.out.println();
        System.out.println(color("  🏁 Final State", BOLD + GREEN));
        System.out.printf("     %s Interaction: %s%n", timestamp(), interactionId);
        System.out.printf("     %s State:       %s%n", timestamp(), color(finalState, BOLD + GREEN));
        System.out.printf("     %s Total transitions: %d%n", timestamp(), transitionCount);
        System.out.println();
    }

    /**
     * Prints a demo completion summary.
     *
     * @param demoName the name of the demo
     * @param success  whether the demo completed successfully
     */
    public static void printDemoComplete(String demoName, boolean success) {
        String status = success ? color("✓ SUCCESS", BOLD + GREEN) : color("✗ FAILED", BOLD + RED);
        System.out.println();
        System.out.println(color("  " + "─".repeat(60), CYAN));
        System.out.printf("  %s Demo: %s | %s | Transitions: %d%n",
                timestamp(),
                demoName,
                status,
                transitionCount);
        System.out.println(color("  " + "─".repeat(60), CYAN));
        System.out.println();
    }

    /**
     * Returns a formatted timestamp string.
     *
     * @return formatted timestamp
     */
    private static String timestamp() {
        return color("[" + LocalTime.now().format(TIME_FORMAT) + "]", "");
    }

    /**
     * Applies ANSI color to text if colors are enabled.
     *
     * @param text  the text to color
     * @param color the ANSI color code
     * @return colored text
     */
    private static String color(String text, String color) {
        if (!useColors) {
            return text;
        }
        return color + text + RESET;
    }
}
