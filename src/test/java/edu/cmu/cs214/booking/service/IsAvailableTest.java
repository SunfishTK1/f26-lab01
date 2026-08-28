package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reviews the proposed {@code isAvailable} from {@code changes/agent-attempt.patch}.
 *
 * <p>The contract that matters is not "does this method return a boolean" but
 * "does it agree with the booking invariant". A query that reports a taken slot
 * as available invites a caller to create the overlap the service exists to prevent.
 */
class IsAvailableTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    private BookingService serviceHolding(TimeInterval... booked) {
        BookingService svc = new BookingService(new InMemoryBookingStore());
        for (TimeInterval i : booked) {
            svc.book(roomA, alice, i);
        }
        return svc;
    }

    /** 10:00-13:00 is held, so 11:00-12:00 sitting inside it is not available. */
    @Test
    void slotInsideAnExistingBookingIsNotAvailable() {
        BookingService svc = serviceHolding(new TimeInterval(600, 780));
        assertFalse(svc.isAvailable(roomA, new TimeInterval(660, 720)));
    }

    /** An existing booking that starts earlier and runs into the slot still blocks it. */
    @Test
    void existingBookingRunningIntoTheSlotIsNotAvailable() {
        BookingService svc = serviceHolding(new TimeInterval(600, 700));
        assertFalse(svc.isAvailable(roomA, new TimeInterval(660, 720)));
    }

    /** Half-open intervals that merely touch do not clash, so the slot is available. */
    @Test
    void touchingIntervalsAreAvailable() {
        BookingService svc = serviceHolding(new TimeInterval(600, 660));
        assertTrue(svc.isAvailable(roomA, new TimeInterval(660, 720)));
        assertTrue(svc.isAvailable(roomA, new TimeInterval(540, 600)));
    }

    /** An empty room is available for anything. */
    @Test
    void emptyRoomIsAvailable() {
        assertTrue(serviceHolding().isAvailable(roomA, new TimeInterval(600, 660)));
    }

    /**
     * The check that convinces: {@code isAvailable} must answer exactly what
     * {@code book} will do. Any probe where it says "free" but book waitlists is a
     * caller being told it is safe to take a slot that is not.
     */
    @Test
    void isAvailableAgreesWithBook() {
        TimeInterval held = new TimeInterval(600, 720);
        List<TimeInterval> probes = List.of(
            new TimeInterval(540, 600),   // before, touching
            new TimeInterval(540, 610),   // overlaps the front
            new TimeInterval(600, 720),   // identical
            new TimeInterval(630, 660),   // strictly inside
            new TimeInterval(660, 780),   // overlaps the back
            new TimeInterval(540, 780),   // contains it
            new TimeInterval(720, 780),   // after, touching
            new TimeInterval(780, 840));  // clear of it

        for (TimeInterval probe : probes) {
            boolean claimedFree = serviceHolding(held).isAvailable(roomA, probe);
            boolean actuallyFree = serviceHolding(held).book(roomA, bob, probe)
                instanceof BookingResult.Confirmed;
            assertEquals(
                actuallyFree, claimedFree,
                "isAvailable disagrees with book for probe " + probe + " against " + held);
        }
    }
}
