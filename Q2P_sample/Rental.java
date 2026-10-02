import java.time.LocalDate;

public class Rental {
    int rentalNumber;
    User client;
    Game game;
    LocalDate checkoutDate;
    LocalDate dueDate;
    double amount;
    String status;   // ACTIVE or COMPLETED

    Rental(int rentalNumber, User client, Game game,
           LocalDate checkoutDate, LocalDate dueDate, double amount) {
        this.rentalNumber = rentalNumber;
        this.client = client;
        this.game = game;
        this.checkoutDate = checkoutDate;
        this.dueDate = dueDate;
        this.amount = amount;
        this.status = "ACTIVE";
    }
}
