import java.time.LocalDateTime;

public class Rental {
    int rentalNumber;
    User client;
    Game game;
    LocalDateTime checkoutDate;
    LocalDateTime dueDate;
    double amount;
    String status;   // ACTIVE or COMPLETED
    int quantity = 1;   // number of copies rented in this order line

    Rental(int rentalNumber, User client, Game game,
           LocalDateTime checkoutDate, LocalDateTime dueDate, double amount) {
        this.rentalNumber = rentalNumber;
        this.client = client;
        this.game = game;
        this.checkoutDate = checkoutDate;
        this.dueDate = dueDate;
        this.amount = amount;
        this.status = "ACTIVE";
    }
}
