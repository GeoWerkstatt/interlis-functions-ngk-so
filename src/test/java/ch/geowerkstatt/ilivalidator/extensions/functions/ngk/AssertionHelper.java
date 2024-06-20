package ch.geowerkstatt.ilivalidator.extensions.functions.ngk;

import ch.interlis.iox.IoxLogEvent;
import com.vividsolutions.jts.util.Assert;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;

public final class AssertionHelper {

    private AssertionHelper() {
        // Utility class
    }

    public static void assertConstraintErrors(LogCollector logger, int expectedCount, String oid, String constraintName) {
        int errorsFound = 0;
        for (IoxLogEvent err : logger.getErrs()) {
            if (oid.equals(err.getSourceObjectXtfId()) && err.getEventMsg().contains(String.format(".%s ", constraintName))) {
                errorsFound++;
            }
        }

        Assert.equals(expectedCount, errorsFound,
                String.format("Expected %d but found %d errors with OID <%s> and Source <%s>.", expectedCount, errorsFound, oid, constraintName));
    }

    public static void assertSingleConstraintError(LogCollector logger, int oid, String constraintName) {
        assertConstraintErrors(logger, 1, Integer.toString(oid), constraintName);
    }

    public static void assertConstraintErrors(LogCollector logger, int expectedCount, String constraintName) {
        int errorsFound = 0;
        for (IoxLogEvent err : logger.getErrs()) {
            if (err.getEventMsg().contains(String.format(".%s ", constraintName))) {
                errorsFound++;
            }
        }

        Assert.equals(expectedCount, errorsFound,
                String.format("Expected %s errors with Source <%s> but found %d.", expectedCount, constraintName, errorsFound));
    }

    public static void assertNoConstraintError(LogCollector logger, String constraintName) {
        int errorsFound = 0;
        for (IoxLogEvent err : logger.getErrs()) {
            if (err.getEventMsg().contains(String.format(".%s ", constraintName))) {
                errorsFound++;
            }
        }

        Assert.equals(0, errorsFound,
                String.format("Expected No errors with Source <%s> but found %d.", constraintName, errorsFound));
    }

    public static void assertLogEventsContainMessage(List<IoxLogEvent> logs, String expectedMessageRegex) {
        Pattern pattern = Pattern.compile(expectedMessageRegex);
        if (logs.stream().noneMatch(log -> pattern.matcher(log.getEventMsg()).find())) {
            fail(String.format("The logs are missing the message <%s>.", expectedMessageRegex));
        }
    }

    /**
     * Asserts that the logs contain the expected message regex a specific number of times.
     *
     * @param logs The logs to check.
     * @param expectedMessageRegex The regex to match.
     * @param expectedMatchCount The expected number of matches.
     */
    public static void assertLogEventsMessages(List<IoxLogEvent> logs, String expectedMessageRegex, long expectedMatchCount) {
        Pattern pattern = Pattern.compile(expectedMessageRegex);
        long actualMatchCount = logs.stream().filter(log -> pattern.matcher(log.getEventMsg()).find()).count();
        if (actualMatchCount != expectedMatchCount) {
            fail(String.format("Expected %d messages to match the regex <%s> but found %d.", expectedMatchCount, expectedMessageRegex, actualMatchCount));
        }
    }

    public static void AssertEventMessagesAreEqual(List<IoxLogEvent> events, String... expectedMessages){
        String[] actualMessages = new String[events.size()];
        for (int i = 0; i < events.size(); i++) {
            actualMessages[i] = events.get(i).getEventMsg();
        }

        assertArrayEquals(expectedMessages, actualMessages);
    }
}
