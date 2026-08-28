package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingServiceTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");
    private final User carol = new User("u3", "Carol");

    private InMemoryBookingStore store = new InMemoryBookingStore();

    private BookingService newService() {
        store = new InMemoryBookingStore();
        return new BookingService(store);
    }

    /** The id of a booking a {@code book} call confirmed. */
    private static String idOf(BookingResult result) {
        return assertInstanceOf(BookingResult.Confirmed.class, result).booking().id();
    }

    @Test
    void bookConfirmsWhenRoomIsFree() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void bookWaitlistsWhenSlotIsTaken() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, r);
    }

    @Test
    void backToBackBookingsAreConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(660, 720));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void sameSlotInDifferentRoomsAreBothConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomB, bob, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void cancelFreesTheSlot() {
        BookingService svc = newService();
        BookingResult first = svc.book(roomA, alice, new TimeInterval(600, 660));
        String id = assertInstanceOf(BookingResult.Confirmed.class, first).booking().id();

        svc.cancelBooking(id);

        assertEquals(0, svc.listBookings(roomA).size());
        assertInstanceOf(
            BookingResult.Confirmed.class, svc.book(roomA, bob, new TimeInterval(600, 660)));
    }

    @Test
    void cancelUnknownIdDoesNothing() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));

        svc.cancelBooking("no-such-booking");

        assertEquals(1, svc.listBookings(roomA).size());
    }

    @Test
    void cancelRemovesOnlyTheNamedBooking() {
        BookingService svc = newService();
        BookingResult first = svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(660, 720));
        String id = assertInstanceOf(BookingResult.Confirmed.class, first).booking().id();

        svc.cancelBooking(id);

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(660, svc.listBookings(roomA).get(0).interval().start());
    }

    /**
     * The cross-cutting behaviour TASK.md asks for: cancelling a booking promotes the
     * user who was waiting behind it into a confirmed booking.
     */
    @Test
    void cancellingPromotesTheWaitingUser() {
        BookingService svc = newService();
        String id = idOf(svc.book(roomA, alice, new TimeInterval(600, 660)));
        assertInstanceOf(
            BookingResult.Waitlisted.class, svc.book(roomA, bob, new TimeInterval(630, 700)));

        svc.cancelBooking(id);

        List<Booking> held = svc.listBookings(roomA);
        assertEquals(1, held.size());
        assertEquals(bob, held.get(0).user());
        assertEquals(new TimeInterval(630, 700), held.get(0).interval());
        assertTrue(store.waitlistForRoom(roomA).isEmpty(), "promoted waiter should leave the waitlist");
        assertNoOverlaps(svc, roomA);
    }

    /** First-come-first-served: the earlier waiter wins, not whoever fits. */
    @Test
    void promotionFollowsArrivalOrder() {
        BookingService svc = newService();
        String id = idOf(svc.book(roomA, alice, new TimeInterval(600, 660)));
        svc.book(roomA, bob, new TimeInterval(600, 660));   // seq 1
        svc.book(roomA, carol, new TimeInterval(610, 650)); // seq 2

        svc.cancelBooking(id);

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(bob, svc.listBookings(roomA).get(0).user());
    }

    /** A waiter who would still clash is skipped, and the next one that fits is taken. */
    @Test
    void promotionSkipsWaitersThatWouldStillOverlap() {
        BookingService svc = newService();
        String id = idOf(svc.book(roomA, alice, new TimeInterval(600, 660)));
        svc.book(roomA, alice, new TimeInterval(660, 720));  // touching, stays confirmed
        svc.book(roomA, bob, new TimeInterval(630, 700));    // seq 1, clashes with 660-720
        svc.book(roomA, carol, new TimeInterval(600, 630));  // seq 2, fits once 600-660 goes

        svc.cancelBooking(id);

        assertEquals(2, svc.listBookings(roomA).size());
        assertTrue(
            svc.listBookings(roomA).stream().anyMatch(b -> b.user().equals(carol)),
            "Carol should be promoted after Bob is skipped");
        assertEquals(1, store.waitlistForRoom(roomA).size(), "Bob should still be waiting");
        assertNoOverlaps(svc, roomA);
    }

    /** At most one promotion per cancellation. */
    @Test
    void promotionTakesAtMostOneWaiterPerCancellation() {
        BookingService svc = newService();
        String id = idOf(svc.book(roomA, alice, new TimeInterval(600, 660)));
        svc.book(roomA, bob, new TimeInterval(600, 620));
        svc.book(roomA, carol, new TimeInterval(620, 660));  // would also fit

        svc.cancelBooking(id);

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(1, store.waitlistForRoom(roomA).size());
    }

    /** No waiter fits, so nobody is promoted and nobody is dropped. */
    @Test
    void promotionLeavesEveryoneWaitingWhenNoneFit() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        String id = idOf(svc.book(roomA, alice, new TimeInterval(660, 720)));
        svc.book(roomA, bob, new TimeInterval(600, 660)); // still clashes after 660-720 goes

        svc.cancelBooking(id);

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(1, store.waitlistForRoom(roomA).size());
        assertNoOverlaps(svc, roomA);
    }

    /** Cancelling in one room must not promote someone waiting on another. */
    @Test
    void promotionIsScopedToTheCancelledBookingsRoom() {
        BookingService svc = newService();
        String id = idOf(svc.book(roomA, alice, new TimeInterval(600, 660)));
        svc.book(roomB, alice, new TimeInterval(600, 660));
        svc.book(roomB, bob, new TimeInterval(600, 660)); // waiting on B, not A

        svc.cancelBooking(id);

        assertEquals(0, svc.listBookings(roomA).size());
        assertEquals(1, svc.listBookings(roomB).size());
        assertEquals(1, store.waitlistForRoom(roomB).size());
    }

    /**
     * The core invariant: no two confirmed bookings in a room may overlap. Asserted
     * after a run of overlapping, touching, and unrelated-room requests.
     */
    @Test
    void bookNeverCreatesOverlappingBookingsInARoom() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(630, 700));   // overlaps -> waitlisted
        svc.book(roomA, alice, new TimeInterval(660, 720)); // touches  -> confirmed
        svc.book(roomA, bob, new TimeInterval(540, 610));   // overlaps -> waitlisted
        svc.book(roomB, bob, new TimeInterval(600, 660));   // other room -> confirmed

        assertNoOverlaps(svc, roomA);
        assertNoOverlaps(svc, roomB);
        assertEquals(2, svc.listBookings(roomA).size());
        assertEquals(1, svc.listBookings(roomB).size());
    }

    /** Fails if any two confirmed bookings held for {@code room} overlap. */
    static void assertNoOverlaps(BookingService svc, Room room) {
        List<Booking> held = svc.listBookings(room);
        for (int i = 0; i < held.size(); i++) {
            for (int j = i + 1; j < held.size(); j++) {
                assertFalse(
                    held.get(i).interval().overlaps(held.get(j).interval()),
                    "Invariant violated in room " + room.id() + ": "
                        + held.get(i) + " overlaps " + held.get(j));
            }
        }
    }

    @Test
    void listBookingsReturnsConfirmedBookings() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(660, 720));
        assertEquals(2, svc.listBookings(roomA).size());
    }
}
