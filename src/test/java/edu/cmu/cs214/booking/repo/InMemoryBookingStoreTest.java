package edu.cmu.cs214.booking.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryBookingStoreTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    /**
     * Pins the root cause of the seeded bug: {@code bookingsForRoom} used to ignore its
     * {@code room} argument and hand back every booking in the store, which made the
     * service treat an unrelated room's booking as a clash.
     */
    @Test
    void bookingsForRoomReturnsOnlyThatRoomsBookings() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        store.addBooking(new Booking("b1", roomA, alice, new TimeInterval(600, 660)));
        store.addBooking(new Booking("b2", roomB, bob, new TimeInterval(600, 660)));

        List<Booking> forA = store.bookingsForRoom(roomA);
        assertEquals(1, forA.size());
        assertEquals("b1", forA.get(0).id());
        assertTrue(forA.stream().allMatch(b -> b.room().id().equals("A")));
    }

    /** A room with no bookings of its own sees an empty list, not everyone else's. */
    @Test
    void bookingsForRoomIsEmptyWhenOnlyOtherRoomsAreBooked() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        store.addBooking(new Booking("b1", roomA, alice, new TimeInterval(600, 660)));

        assertTrue(store.bookingsForRoom(roomB).isEmpty());
    }

    /** {@code allBookings} is the unfiltered view, and stays that way. */
    @Test
    void allBookingsSpansEveryRoom() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        store.addBooking(new Booking("b1", roomA, alice, new TimeInterval(600, 660)));
        store.addBooking(new Booking("b2", roomB, bob, new TimeInterval(600, 660)));

        assertEquals(2, store.allBookings().size());
    }
}
