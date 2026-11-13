package dan.bank.bank.device;

/** Turns a User-Agent string into something a person recognises, like "Chrome on Windows". */
public final class DeviceLabel {

    private DeviceLabel() { }

    public static String from(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown device";
        String browser = userAgent.contains("Edg/") ? "Edge"
                : userAgent.contains("OPR/") ? "Opera"
                : userAgent.contains("Firefox/") ? "Firefox"
                : userAgent.contains("Chrome/") || userAgent.contains("CriOS/") ? "Chrome"
                : userAgent.contains("Safari/") ? "Safari" : "Browser";
        // Order matters: Android and iPhone user agents also mention Linux and Mac OS X.
        String system = userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
                : userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Mac OS X") ? "macOS"
                : userAgent.contains("Linux") ? "Linux" : "an unknown system";
        String label = browser + " on " + system;
        return label.length() > 120 ? label.substring(0, 120) : label;
    }
}
