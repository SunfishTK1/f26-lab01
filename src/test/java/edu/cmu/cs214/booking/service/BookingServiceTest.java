package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
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
