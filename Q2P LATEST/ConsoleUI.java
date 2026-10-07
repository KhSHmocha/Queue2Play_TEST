import java.util.*;

// Handles terminal input and shared console display helpers.
// Visual menus are intentionally written with plain System.out.println()
// so they are easy to edit and visualize in the source code.
public class ConsoleUI {
    static final String LINE = "+==========================================================+";
    private final Scanner input = new Scanner(System.in);

    String readText(String prompt) {
        System.out.print(prompt);
        if (!input.hasNextLine()) {
            System.out.println("\nInput ended. Goodbye!");
            System.exit(0);
        }
        return input.nextLine().trim();
    }

    // Password stays visible and uses the same Scanner as every other input.
    String readPassword(String prompt) {
        return readText(prompt);
    }

    int readNumber(String prompt) {
        String text = readText(prompt);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    double readAmount(String prompt) {
        String text = readText(prompt);
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Pushes the previous screen out of view without ANSI clear-screen codes.
    void clearScreen() {
        System.out.print("\n".repeat(35));
    }

    void pause(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Simple loading animation only. No ANSI cursor movement or colors.
    void loading(String message) {
        System.out.print("\n                         " + message);
        try {
            for (int i = 0; i < 3; i++) {
                Thread.sleep(350);
                System.out.print(".");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println();
    }

    void waitForEnter() {
        System.out.print("\n                    Press ENTER to continue...");
        if (input.hasNextLine()) input.nextLine();
    }

    void success(String message) { System.out.println("\n                    " + message); }
    void error(String message) { System.out.println("\n                    " + message); }
    void warning(String message) { System.out.println("\n                    " + message); }
    void info(String message) { System.out.println(message); }

    String platformColor(String platform) { return platform; }
    String membershipColor(boolean vip) { return vip ? "VIP" : "REGULAR"; }
    String statusColor(String status) { return status; }

    // Generic header used by information screens.
    void printHeader(String title) {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.printf("|%58s|%n", center(title, 58));
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
    }

    void printHeader(String path, String title) {
        System.out.println("+==========================================================+");
        System.out.printf("| %-56s |%n", path.length() > 56 ? path.substring(0, 56) : path);
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.printf("|%58s|%n", center(title, 58));
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
    }

    private String center(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        int left = (width - text.length()) / 2;
        int right = width - text.length() - left;
        return " ".repeat(left) + text + " ".repeat(right);
    }

    String money(double amount) { return String.format("$%.2f", amount); }
    String queueLabel(int number) { return String.format("#%03d", number); }
    String rentalLabel(int number) { return String.format("#%03d", number); }

    void showMainMenu() {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                        QUEUE2PLAY                        |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                        [1] Login                         |");
        System.out.println("|                       [2] Register                       |");
        System.out.println("|                        [0] Exit                          |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("---------------------- ENTER CHOICE -----------------------");
    }

    void showLoginUsernameScreen() {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                          LOGIN                           |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|          Enter your username or email to continue.       |");
        System.out.println("|                                                          |");
        System.out.println("|                       [0] Back                           |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("------------------- USERNAME OR EMAIL ---------------------");
    }

    void showLoginPasswordScreen(String username) {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                          LOGIN                           |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.printf("|                    Username: %-27s|%n", username.length() > 27 ? username.substring(0, 27) : username);
        System.out.println("|                                                          |");
        System.out.println("|                       [0] Back                           |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("------------------------ PASSWORD -------------------------");
    }

    void showRegisterFullNameScreen() {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                     CREATE ACCOUNT                       |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|          Enter your complete name using:                 |");
        System.out.println("|          First Name + M.I. + Surname                     |");
        System.out.println("|                                                          |");
        System.out.println("|          Example: Juan D. Cruz                           |");
        System.out.println("|                                                          |");
        System.out.println("|                       [0] Back                           |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("----------------------- FULL NAME -------------------------");
    }

    void showRegisterUsernameScreen(String fullName) {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                     CREATE ACCOUNT                       |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.printf("| Name: %-50s |%n", fullName.length() > 50 ? fullName.substring(0, 50) : fullName);
        System.out.println("|                                                          |");
        System.out.println("|                       [0] Back                           |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("------------------------ USERNAME -------------------------");
    }

    void showRegisterEmailScreen(String fullName, String username) {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                     CREATE ACCOUNT                       |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.printf("| Name: %-50s |%n", fullName.length() > 50 ? fullName.substring(0, 50) : fullName);
        System.out.printf("| Username: %-46s |%n", username.length() > 46 ? username.substring(0, 46) : username);
        System.out.println("|                                                          |");
        System.out.println("|          Valid Email Format Examples:                    |");
        System.out.println("|          juancruz@gmail.com                              |");
        System.out.println("|          juan.cruz@yahoo.com                             |");
        System.out.println("|          juancruz123@outlook.com                         |");
        System.out.println("|                                                          |");
        System.out.println("|                       [0] Back                           |");
        System.out.println("+==========================================================+");
        System.out.println("-------------------------- EMAIL --------------------------");
    }

    void showRegisterPasswordScreen(String fullName, String username, String email) {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                     CREATE ACCOUNT                       |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.printf("| Name: %-50s |%n", fullName.length() > 50 ? fullName.substring(0, 50) : fullName);
        System.out.printf("| Username: %-46s |%n", username.length() > 46 ? username.substring(0, 46) : username);
        System.out.printf("| Email: %-49s |%n", email.length() > 49 ? email.substring(0, 49) : email);
        System.out.println("|                                                          |");
        System.out.println("|          Password must be at least 8 characters.         |");
        System.out.println("|                       [0] Back                           |");
        System.out.println("+==========================================================+");
        System.out.println("------------------------ PASSWORD -------------------------");
    }

    void showPlatformMenu(String heading) {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.printf("|%58s|%n", center(heading, 58));
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                  [1] View All Games                      |");
        System.out.println("|                  [2] PS5 Games                           |");
        System.out.println("|                  [3] Xbox Games                          |");
        System.out.println("|                  [4] Nintendo Games                      |");
        System.out.println("|                  [0] Back                                |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("---------------------- ENTER CHOICE -----------------------");
    }

    void showBrowseMenu() {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                BROWSE & SEARCH GAMES                     |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                  [1] View All Games                      |");
        System.out.println("|                  [2] PS5 Games                           |");
        System.out.println("|                  [3] Xbox Games                          |");
        System.out.println("|                  [4] Nintendo Games                      |");
        System.out.println("|                  [5] Search Game                         |");
        System.out.println("|                  [0] Back                                |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("---------------------- ENTER CHOICE -----------------------");
    }

    void showClientMenu(User client) {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                     CLIENT DASHBOARD                     |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.printf("| Welcome, %-38s [%7s] |%n", client.username.length() > 38 ? client.username.substring(0, 38) : client.username, membershipColor(client.isVip()));
        System.out.println("|                                                          |");
        System.out.println("|             [1] Browse & Search Game Inventory           |");
        System.out.println("|             [2] Sort Game Inventory                      |");
        System.out.println("|             [3] Rent Games / Join Checkout Queue         |");
        System.out.println("|             [4] Cancel My Queue Request                  |");
        System.out.println("|             [5] View My Rental History                   |");
        System.out.println("|             [6] Upgrade to VIP Membership                |");
        System.out.println("|             [0] Logout                                   |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("---------------------- ENTER CHOICE -----------------------");
    }

    void showAdminMenu() {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                     ADMIN DASHBOARD                      |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|              [1] Process Checkout Queue                  |");
        System.out.println("|              [2] Process Game Returns & Late Fees        |");
        System.out.println("|              [3] Manage Game Inventory                   |");
        System.out.println("|              [4] View Overdue Items                      |");
        System.out.println("|              [5] View Admin Audit Log                    |");
        System.out.println("|              [6] View All Active Rentals                 |");
        System.out.println("|              [0] Logout                                  |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("---------------------- ENTER CHOICE -----------------------");
    }

    void showInventoryMenu() {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                  MANAGE GAME INVENTORY                   |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                  [1] View All Games                      |");
        System.out.println("|                  [2] PS5 Games                           |");
        System.out.println("|                  [3] Xbox Games                          |");
        System.out.println("|                  [4] Nintendo Games                      |");
        System.out.println("|                  [5] Add Game                            |");
        System.out.println("|                  [6] Delete Game                         |");
        System.out.println("|                  [7] Update Game Stock                   |");
        System.out.println("|                  [8] Search Game                         |");
        System.out.println("|                  [9] Sort Game Inventory                 |");
        System.out.println("|                  [0] Back                                |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("---------------------- ENTER CHOICE -----------------------");
    }

    void showSortOptions() {
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                       SORT OPTIONS                       |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                     [1] Title A-Z                        |");
        System.out.println("|                     [2] Title Z-A                        |");
        System.out.println("|                     [3] Price Low-High                   |");
        System.out.println("|                     [4] Price High-Low                   |");
        System.out.println("|                     [0] Back                             |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("---------------------- ENTER CHOICE -----------------------");
    }

    void showGames(List<Game> games, String title) {
        printHeader(title);
        if (games.isEmpty()) {
            warning("No games found.");
            return;
        }
        System.out.println("| ID    | GAME TITLE                   | PLATFORM   | PRICE    | STATUS");
        System.out.println("+----------------------------------------------------------+");
        for (Game game : games) {
            System.out.printf("  %-5s | %-28s | %-10s | %8s | %s%n",
                game.gameId, game.title, game.platform, money(game.price), game.getStatus());
        }
    }

    void showQueue(CheckoutQueue queue) {
        if (queue.isEmpty()) {
            System.out.println("                    (The queue is empty)");
            return;
        }
        RentalRequest current = queue.head;
        while (current != null) {
            System.out.printf("#%03d | %-8s | %-12s | %s | Qty: %d%n",
                current.queueNumber,
                current.vip ? "VIP" : "REGULAR",
                current.client.username,
                current.game.title,
                current.quantity);
            current = current.next;
        }
    }
}
