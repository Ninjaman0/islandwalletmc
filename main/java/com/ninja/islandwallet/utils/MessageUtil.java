package com.ninja.islandwallet.utils;

import org.bukkit.ChatColor;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ENHANCED utility class for message formatting with full pocket color code support
 * Supports all Minecraft color codes (&0-&9, &a-&f) and formatting codes (&k-&o, &r)
 */
public class MessageUtil {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00");

    /**
     * ENHANCED: Translate all pocket color codes to Minecraft color codes
     * Supports: &0-&9 (colors), &a-&f (colors), &k-&o (formatting), &r (reset)
     */
    public static String translateColors(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Replace all color codes (&0-&9, &a-&f)
        message = ChatColor.translateAlternateColorCodes('&', message);

        return message;
    }

    /**
     * Translate colors for a list of strings
     */
    public static List<String> translateColors(List<String> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }

        List<String> translatedMessages = new ArrayList<>();
        for (String message : messages) {
            translatedMessages.add(translateColors(message));
        }
        return translatedMessages;
    }

    /**
     * Format numbers with commas for readability
     */
    public static String formatNumber(long number) {
        if (number < 0) {
            return "0";
        }
        return NUMBER_FORMAT.format(number);
    }

    /**
     * Format currency with proper decimal places and commas
     */
    public static String formatCurrency(double amount) {
        if (amount < 0) {
            return "$0.00";
        }
        return "$" + CURRENCY_FORMAT.format(amount);
    }

    /**
     * ENHANCED: Format time duration in multiple formats
     */
    public static String formatTimeRemaining(long seconds) {
        if (seconds <= 0) {
            return "0 seconds";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        StringBuilder result = new StringBuilder();

        if (days > 0) {
            result.append(days).append(" day").append(days != 1 ? "s" : "");
            if (hours > 0 || minutes > 0 || remainingSeconds > 0) {
                result.append(", ");
            }
        }

        if (hours > 0) {
            result.append(hours).append(" hour").append(hours != 1 ? "s" : "");
            if (minutes > 0 || remainingSeconds > 0) {
                result.append(", ");
            }
        }

        if (minutes > 0) {
            result.append(minutes).append(" minute").append(minutes != 1 ? "s" : "");
            if (remainingSeconds > 0) {
                result.append(", ");
            }
        }

        if (remainingSeconds > 0 || result.length() == 0) {
            result.append(remainingSeconds).append(" second").append(remainingSeconds != 1 ? "s" : "");
        }

        return result.toString();
    }

    /**
     * ENHANCED: Format time remaining in hours only
     */
    public static String formatTimeRemainingHours(long seconds) {
        if (seconds <= 0) {
            return "0";
        }

        double hours = seconds / 3600.0;
        return String.format("%.1f", hours);
    }

    /**
     * ENHANCED: Format time remaining in minutes only
     */
    public static String formatTimeRemainingMinutes(long seconds) {
        if (seconds <= 0) {
            return "0";
        }

        long minutes = seconds / 60;
        return String.valueOf(minutes);
    }

    /**
     * ENHANCED: Format time remaining in compact format (13d 2h 12m)
     */
    public static String formatTimeRemainingCompact(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        StringBuilder result = new StringBuilder();

        if (days > 0) {
            result.append(days).append("d");
            if (hours > 0 || minutes > 0) {
                result.append(" ");
            }
        }

        if (hours > 0) {
            result.append(hours).append("h");
            if (minutes > 0) {
                result.append(" ");
            }
        }

        if (minutes > 0) {
            result.append(minutes).append("m");
        }

        if (result.length() == 0) {
            result.append(remainingSeconds).append("s");
        }

        return result.toString();
    }

    /**
     * ENHANCED: Format time remaining in short format (13:02:12)
     */
    public static String formatTimeRemainingShort(long seconds) {
        if (seconds <= 0) {
            return "00:00:00";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours, minutes, remainingSeconds);
        } else {
            return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
        }
    }

    /**
     * ENHANCED: Format time remaining in DHM format (Days Hours Minutes)
     */
    public static String formatTimeRemainingDHM(long seconds) {
        if (seconds <= 0) {
            return "0 minutes";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        StringBuilder result = new StringBuilder();

        if (days > 0) {
            result.append(days).append(" day").append(days != 1 ? "s" : "");
            if (hours > 0 || minutes > 0) {
                result.append(" ");
            }
        }

        if (hours > 0) {
            result.append(hours).append(" hour").append(hours != 1 ? "s" : "");
            if (minutes > 0) {
                result.append(" ");
            }
        }

        if (minutes > 0 || result.length() == 0) {
            result.append(minutes).append(" minute").append(minutes != 1 ? "s" : "");
        }

        return result.toString();
    }

    /**
     * ENHANCED: Sanitize strings to prevent injection and ensure safe display
     * Removes potential harmful characters while preserving color codes
     */
    public static String sanitizeString(String input) {
        if (input == null) {
            return "";
        }

        // Remove control characters except for color codes
        String sanitized = input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        // Limit length to prevent spam
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 97) + "...";
        }

        // Remove multiple consecutive spaces
        sanitized = sanitized.replaceAll("\\s+", " ");

        return sanitized.trim();
    }

    /**
     * ENHANCED: Strip all color codes from a message for logging/comparison
     */
    public static String stripColors(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        return ChatColor.stripColor(translateColors(message));
    }

    /**
     * ENHANCED: Format large numbers with appropriate suffixes (K, M, B, T)
     */
    public static String formatNumberCompact(long number) {
        if (number < 0) {
            return "0";
        }

        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            return String.format("%.1fK", number / 1000.0);
        } else if (number < 1000000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number < 1000000000000L) {
            return String.format("%.1fB", number / 1000000000.0);
        } else {
            return String.format("%.1fT", number / 1000000000000.0);
        }
    }

    /**
     * ENHANCED: Format money with proper commas and currency symbol
     */
    public static String formatMoney(double amount) {
        if (amount < 0) {
            return "$0.00";
        }
        
        DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");
        return "$" + moneyFormat.format(amount);
    }

    /**
     * ENHANCED: Format large money amounts with suffixes
     */
    public static String formatMoneyCompact(double amount) {
        if (amount < 0) {
            return "$0";
        }

        if (amount < 1000) {
            return String.format("$%.0f", amount);
        } else if (amount < 1000000) {
            return String.format("$%.1fK", amount / 1000.0);
        } else if (amount < 1000000000) {
            return String.format("$%.1fM", amount / 1000000.0);
        } else if (amount < 1000000000000.0) {
            return String.format("$%.1fB", amount / 1000000000.0);
        } else {
            return String.format("$%.1fT", amount / 1000000000000.0);
        }
    }

    /**
     * ENHANCED: Create a progress bar with color codes
     */
    public static String createProgressBar(long current, long max, int length, char filledChar, char emptyChar) {
        if (max <= 0 || current < 0) {
            return "&c" + String.valueOf(emptyChar).repeat(length);
        }

        int filled = (int) Math.min(length, (current * length) / max);
        int empty = length - filled;

        StringBuilder progressBar = new StringBuilder();

        // Color based on percentage
        if (filled >= length * 0.8) {
            progressBar.append("&a"); // Green for 80%+
        } else if (filled >= length * 0.5) {
            progressBar.append("&e"); // Yellow for 50%+
        } else {
            progressBar.append("&c"); // Red for less than 50%
        }

        // Add filled portion
        progressBar.append(String.valueOf(filledChar).repeat(filled));

        // Add empty portion
        if (empty > 0) {
            progressBar.append("&7");
            progressBar.append(String.valueOf(emptyChar).repeat(empty));
        }

        return translateColors(progressBar.toString());
    }

    /**
     * ENHANCED: Validate and fix color codes in messages
     */
    public static String validateColorCodes(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Fix common color code mistakes
        message = message.replace("§", "&"); // Replace section symbols with ampersands
        message = message.replaceAll("&([^0-9a-fk-or])", "&r$1"); // Fix invalid color codes

        return message;
    }

    /**
     * ENHANCED: Center text in chat with specified width
     */
    public static String centerText(String text, int chatWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String strippedText = stripColors(text);
        int textLength = strippedText.length();

        if (textLength >= chatWidth) {
            return text;
        }

        int spaces = (chatWidth - textLength) / 2;
        return " ".repeat(spaces) + text;
    }

    /**
     * ENHANCED: Create a colored separator line
     */
    public static String createSeparator(int length, String color) {
        if (length <= 0) {
            return "";
        }
        return translateColors(color + "━".repeat(Math.min(length, 50)));
    }

    /**
     * ENHANCED: Format time duration (seconds to human readable)
     */
    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "0 seconds";
        }

        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + " hour" + (hours != 1 ? "s" : "");
        } else {
            long days = seconds / 86400;
            return days + " day" + (days != 1 ? "s" : "");
        }
    }

    /**
     * ENHANCED: Parse boolean from string with multiple valid formats
     */
    public static boolean parseBoolean(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        String lowerValue = value.toLowerCase().trim();
        return lowerValue.equals("true") ||
                lowerValue.equals("yes") ||
                lowerValue.equals("1") ||
                lowerValue.equals("enabled") ||
                lowerValue.equals("on");
    }

    /**
     * ENHANCED: Create a hover text component for rich messages
     */
    public static String addHoverInfo(String text, String hoverText) {
        if (hoverText == null || hoverText.isEmpty()) {
            return text;
        }

        // Simple hover format - can be enhanced with JSON components later
        return text + " &7(Hover for info)";
    }

    /**
     * ENHANCED: Wrap long text to multiple lines
     */
    public static List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;

            if (stripColors(testLine).length() <= maxWidth) {
                currentLine = new StringBuilder(testLine);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    // Word is longer than max width, force break
                    lines.add(word);
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * ENHANCED: Replace all placeholders in a message with values
     */
    public static String replacePlaceholders(String message, String... replacements) {
        if (message == null || replacements.length % 2 != 0) {
            return message != null ? message : "";
        }

        String result = message;
        for (int i = 0; i < replacements.length; i += 2) {
            String placeholder = replacements[i];
            String value = replacements[i + 1];

            if (placeholder != null && value != null) {
                result = result.replace(placeholder, value);
            }
        }

        return translateColors(result);
    }

    /**
     * ENHANCED: Get color code from percentage (for progress indicators)
     */
    public static String getPercentageColor(double percentage) {
        if (percentage >= 80.0) {
            return "&a"; // Green
        } else if (percentage >= 60.0) {
            return "&e"; // Yellow
        } else if (percentage >= 40.0) {
            return "&6"; // Orange
        } else if (percentage >= 20.0) {
            return "&c"; // Red
        } else {
            return "&4"; // Dark Red
        }
    }

    /**
     * ENHANCED: Create a debug message with timestamp
     */
    public static String createDebugMessage(String message) {
        return translateColors("&8[DEBUG " + System.currentTimeMillis() + "] &7" + message);
    }
}