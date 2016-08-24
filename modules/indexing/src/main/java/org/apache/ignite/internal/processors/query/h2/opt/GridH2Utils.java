
package org.apache.ignite.internal.processors.query.h2.opt;

import java.lang.reflect.Array;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;
import org.h2.util.JdbcUtils;
import org.h2.value.DataType;
import org.h2.value.Value;
import org.h2.value.ValueArray;
import org.h2.value.ValueTimestamp;
import org.h2.value.ValueUuid;

/**
 *
 */
@SuppressWarnings({"JavaAbbreviationUsage", "GridBracket"})
public class GridH2Utils {
    /** Copy/pasted from org.h2.util.DateTimeUtils */
    private static final int SHIFT_YEAR = 9;

    /** Copy/pasted from org.h2.util.DateTimeUtils */
    private static final int SHIFT_MONTH = 5;

    /** Static calendar. */
    private static final Calendar staticCalendar = Calendar.getInstance();

    /** */
    private static final ThreadLocal<Calendar> localCalendar = new ThreadLocal<>();

    /**
     * @return The instance of calendar for local thread.
     */
    public static Calendar getLocalCalendar() {
        Calendar res = localCalendar.get();

        if (res == null) {
            res = (Calendar)staticCalendar.clone();

            localCalendar.set(res);
        }

        return res;
    }

    /**
     * Get or create a timestamp value for the given timestamp.
     *
     * Copy/pasted from org.h2.value.ValueTimestamp#get(java.sql.Timestamp)
     *
     * @param timestamp The timestamp.
     * @return The value.
     */
    public static ValueTimestamp toValueTimestamp(Timestamp timestamp) {
        long ms = timestamp.getTime();
        long nanos = timestamp.getNanos() % 1000000;

        Calendar calendar = getLocalCalendar();

        calendar.clear();
        calendar.setTimeInMillis(ms);

        long dateValue = dateValueFromCalendar(calendar);

        nanos += nanosFromCalendar(calendar);

        return ValueTimestamp.fromDateValueAndNanos(dateValue, nanos);
    }

    /**
     * Calculate the nanoseconds since midnight from a given calendar.
     *
     * Copy/pasted from org.h2.util.DateTimeUtils#nanosFromCalendar(java.util.Calendar).
     *
     * @param cal The calendar.
     * @return Nanoseconds.
     */
    private static long nanosFromCalendar(Calendar cal) {
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        int s = cal.get(Calendar.SECOND);
        int millis = cal.get(Calendar.MILLISECOND);

        return ((((((h * 60L) + m) * 60) + s) * 1000) + millis) * 1000000;
    }

    /**
     * Calculate the date value from a given calendar.
     *
     * Copy/pasted from org.h2.util.DateTimeUtils#dateValueFromCalendar(java.util.Calendar)
     *
     * @param cal The calendar.
     * @return The date value.
     */
    private static long dateValueFromCalendar(Calendar cal) {
        int year, month, day;

        year = getYear(cal);
        month = cal.get(Calendar.MONTH) + 1;
        day = cal.get(Calendar.DAY_OF_MONTH);

        return ((long) year << SHIFT_YEAR) | (month << SHIFT_MONTH) | day;
    }

    /**
     * Get the year (positive or negative) from a calendar.
     *
     * Copy/pasted from org.h2.util.DateTimeUtils#getYear(java.util.Calendar)
     *
     * @param calendar The calendar.
     * @return The year.
     */
    private static int getYear(Calendar calendar) {
        int year = calendar.get(Calendar.YEAR);

        if (calendar.get(Calendar.ERA) == GregorianCalendar.BC) {
            year = 1 - year;
        }

        return year;
    }
}