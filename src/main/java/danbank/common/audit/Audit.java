package danbank.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security-relevant events (logins, money movement, account/user closure) go to a
 * dedicated "AUDIT" logger so they can be routed and retained separately from app logs.
 */
public final class Audit {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private Audit() {
    }

    public static void log(String event, String details, Object... args) {
        AUDIT.info("event={} " + details, prepend(event, args));
    }

    private static Object[] prepend(String event, Object[] args) {
        Object[] all = new Object[args.length + 1];
        all[0] = event;
        System.arraycopy(args, 0, all, 1, args.length);
        return all;
    }
}
