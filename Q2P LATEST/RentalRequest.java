// One node of the checkout queue (Doubly Linked List)
public class RentalRequest {
    int queueNumber;
    User client;
    Game game;
    boolean vip;            // membership when the request was made
    int rentalDays = GameRentalSystem.RENTAL_DAYS;   // rental length chosen in the order
    int quantity = 1;       // copies of the game requested in the order

    RentalRequest previous; // link to the node before
    RentalRequest next;     // link to the node after

    RentalRequest(int queueNumber, User client, Game game) {
        this.queueNumber = queueNumber;
        this.client = client;
        this.game = game;
        this.vip = client.isVip();
        this.previous = null;
        this.next = null;
    }
}
