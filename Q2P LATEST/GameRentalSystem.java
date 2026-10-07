import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class GameRentalSystem {

    // ---------- Settings ----------
    static final int RENTAL_DAYS = 3;
    static final int LONG_RENTAL_DAYS = 30;   // longer than this needs the client's confirmation
    static final double LATE_FEE_PER_DAY = 1.00;
    static final double VIP_DISCOUNT = 0.10;
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy | hh:mm a");
    static final DateTimeFormatter LOG_STORAGE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final DateTimeFormatter LOG_DISPLAY_DATE = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    static final DateTimeFormatter LOG_DISPLAY_TIME = DateTimeFormatter.ofPattern("hh:mm a");
    static final int GAME_PAGE_SIZE = 10;
    static final int DETAIL_PAGE_SIZE = 3;
    static final int LOG_PAGE_SIZE = 10;

    // ---------- Data structures ----------
    ConsoleUI ui = new ConsoleUI();
    final AccountService accountService;
    final RentalDataStore dataStore;
    final GameCatalogService gameCatalog;

    // Hash table maps game IDs to games; LinkedHashMap keeps inventory display order.
    HashMap<String, Game> games = new LinkedHashMap<>();

    // ArrayLists store accounts for lookup and all rentals for file saving.
    ArrayList<User> users = new ArrayList<>();

    // Doubly Linked List of pending checkout requests
    CheckoutQueue checkoutQueue = new CheckoutQueue();

    // Min-heap: peek gives the active rental with the earliest due date.
    PriorityQueue<Rental> activeRentals =
        new PriorityQueue<>((a, b) -> a.dueDate.compareTo(b.dueDate));

    // Every rental ever made (active and completed), in rental-number order.
    // Only used so rentals.txt can be written; the other structures work as before.
    ArrayList<Rental> allRentals = new ArrayList<>();

    // Stack: admin actions are displayed newest first.
    Stack<String> auditLog = new Stack<>();

    // ---------- Counters ----------
    int ps5Count = 0;
    int xboxCount = 0;
    int nintendoCount = 0;
    int nextQueueNumber = 1;
    int nextRentalNumber = 1;

    GameRentalSystem() {
        accountService = new AccountService(this);
        dataStore = new RentalDataStore(this);
        gameCatalog = new GameCatalogService(this);
    }

    String readText(String prompt) {
        return ui.readText(prompt);
    }

    String readPassword(String prompt) {
        return ui.readPassword(prompt);
    }

    int readNumber(String prompt) {
        return ui.readNumber(prompt);
    }

    double readAmount(String prompt) {
        return ui.readAmount(prompt);
    }

    void printHeader(String title) {
        ui.printHeader(title);
    }

    void printHeader(String path, String title) {
        ui.printHeader(path, title);
    }

    String money(double amount) {
        return ui.money(amount);
    }

    String queueLabel(int number) {
        return ui.queueLabel(number);
    }

    String rentalLabel(int number) {
        return ui.rentalLabel(number);
    }

    // =====================================================
    //  START, MAIN MENU
    // =====================================================

    // Load saved data once, then keep showing the main menu until exit is chosen.
    public void start() {
        accountService.loadAccounts();
        dataStore.loadGames();
        dataStore.loadRentals();
        dataStore.loadQueue();
        dataStore.loadLogs();

        boolean running = true;
        while (running) {
            ui.showMainMenu();
            int choice = readNumber("                            > ");

            if (choice == 1) {
                accountService.login();
            } else if (choice == 2) {
                accountService.register();
            } else if (choice == 0) {
                System.out.println("Thank you for using QUEUE2PLAY. Goodbye!");
                running = false;
            } else {
                ui.error("Invalid choice. Please enter 1, 2, or 0.");
            }
        }
    }

    void saveAccounts() {
        accountService.saveAccounts();
    }

    User findUser(String username) {
        return accountService.findUser(username);
    }

    // Saves everything that changes during a transaction
    void saveData() {
        dataStore.saveData();
    }

    // games.txt  ->  gameId|title|platform|price|availableCopies
    // The first line (#COUNTERS) remembers the ID counters so IDs are never reused.
    void saveGames() {
        dataStore.saveGames();
    }

    // Makes sure the next generated ID is higher than every ID already in the file
    void updateCounterFromId(String id) {
        if (id.length() < 2) {
            return;
        }
        int number;
        try {
            number = Integer.parseInt(id.substring(1));
        } catch (NumberFormatException e) {
            return;
        }

        if (id.startsWith("P")) {
            ps5Count = Math.max(ps5Count, number);
        } else if (id.startsWith("X")) {
            xboxCount = Math.max(xboxCount, number);
        } else if (id.startsWith("N")) {
            nintendoCount = Math.max(nintendoCount, number);
        }
    }

    // rentals.txt  ->  rentalNumber|username|gameId|title|platform|checkoutDate|dueDate|amount|status|quantity
    // The title and platform are saved too, so history still displays correctly
    // even if the game is removed from the inventory later.
    // queue.txt  ->  queueNumber|username|gameId|vip|rentalDays|quantity   (saved in queue order)
    void saveQueue() {
        dataStore.saveQueue();
    }

    // logs.txt  ->  timestamp|TYPE|message
    // ADMIN lines are the admin audit log. EVENT lines are client/system activity.
    // The file is only ever appended to, so it is a permanent history.
    void writeLog(String type, String message) {
        dataStore.writeLog(type, message);
    }

    // Admin action: goes on the audit log Stack AND into logs.txt
    void logAdmin(String message) {
        String timestamp = LocalDateTime.now().format(LOG_STORAGE_FORMAT);
        auditLog.push(timestamp + "|" + message);
        dataStore.writeLogRecord(timestamp, "ADMIN", message);
    }

    // Any other transaction (register, login, queue request, VIP upgrade...)
    void logEvent(String message) {
        writeLog("EVENT", message);
    }

    // =====================================================
    //  GAME INVENTORY (HashMap)
    // =====================================================

    String generateGameId(String platform) {
        return gameCatalog.generateGameId(platform);
    }

    Game addGameToInventory(String title, String platform, double price, int copies) {
        return gameCatalog.addGame(title, platform, price, copies);
    }

    void loadStartingGames() {
        gameCatalog.loadStartingGames();
    }

    // platform: "ALL", "PS5", "Xbox" or "Nintendo"
    ArrayList<Game> getGameList(String platform) {
        return gameCatalog.getGameList(platform);
    }

    void displayGames(ArrayList<Game> list, String title) {
        paginateGames(list, title, false);
    }

    // Pagination for compact game lists: 10 records per page.
    // When selectable is true, the method returns the selected Game.
    Game paginateGames(ArrayList<Game> list, String title, boolean selectable) {
        if (list.isEmpty()) {
            printHeader(title);
            ui.info("No games found.");
            ui.waitForEnter();
            return null;
        }

        int page = 0;
        int totalPages = (list.size() + GAME_PAGE_SIZE - 1) / GAME_PAGE_SIZE;

        while (true) {
            int start = page * GAME_PAGE_SIZE;
            int end = Math.min(start + GAME_PAGE_SIZE, list.size());

            printHeader(title);
            System.out.println("Showing " + (start + 1) + "-" + end + " of " + list.size() + " games");
            System.out.println();

            for (int i = start; i < end; i++) {
                Game game = list.get(i);
                int shownNumber = i - start + 1;
                System.out.printf("[%d] %-28s | %-18s | Stock: %-3d | %s%n",
                    shownNumber, game.title, ui.platformColor(game.platform),
                    game.availableCopies, money(game.price));
            }

            System.out.println();
            if (page < totalPages - 1) System.out.println("[N] Next Page");
            if (page > 0) System.out.println("[P] Previous Page");
            System.out.println("[S] Search Game");
            System.out.println("[0] Back");
            System.out.println("---------------------- ENTER CHOICE -----------------------");

            String choice = readText("                            > ");

            if (choice.equalsIgnoreCase("N") && page < totalPages - 1) {
                page++;
            } else if (choice.equalsIgnoreCase("P") && page > 0) {
                page--;
            } else if (choice.equalsIgnoreCase("S")) {
                System.out.println("----------------------- SEARCH GAME -----------------------");
                String keyword = readText("                            > ");
                if (keyword.isEmpty()) {
                    ui.error("Search text cannot be empty.");
                    continue;
                }
                ArrayList<Game> results = linearSearch(list, keyword);
                if (results.isEmpty()) {
                    ui.warning("No matching game found.");
                    continue;
                }
                Game found = paginateGames(results, "SEARCH RESULTS", selectable);
                if (selectable && found != null) return found;
            } else if (choice.equals("0")) {
                return null;
            } else if (selectable) {
                try {
                    int number = Integer.parseInt(choice);
                    if (number >= 1 && number <= end - start) {
                        return list.get(start + number - 1);
                    }
                    ui.error("Invalid game selection.");
                } catch (NumberFormatException e) {
                    ui.error("Invalid choice.");
                }
            } else {
                ui.error("Invalid choice.");
            }
        }
    }

    // Shows the platform menu. Returns "ALL", "PS5", "Xbox", "Nintendo" or "BACK"
    String choosePlatform(String heading) {
        while (true) {
            ui.showPlatformMenu(heading);

            int choice = readNumber("                            > ");

            if (choice == 1) {
                return "ALL";
            } else if (choice == 2) {
                return "PS5";
            } else if (choice == 3) {
                return "Xbox";
            } else if (choice == 4) {
                return "Nintendo";
            } else if (choice == 0) {
                return "BACK";
            } else {
                ui.error("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    // View one platform (or all games) with pagination and search.
    void viewGames(String platform) {
        ArrayList<Game> list = getGameList(platform);
        paginateGames(list, platform.equals("ALL") ? "ALL GAMES" : platform.toUpperCase() + " GAMES", false);
    }

    // =====================================================
    //  LINEAR SEARCH
    // =====================================================

    void searchGames(ArrayList<Game> list) {
        while (true) {
            printHeader("SEARCH GAME");
            System.out.println("----------------------- SEARCH GAME -----------------------");
            System.out.println("        Enter Game ID, title, or platform. [0] Back");
            String keyword = readText("                            > ");

            if (keyword.equals("0")) {
                return;
            }
            if (keyword.isEmpty()) {
                ui.error("Search text cannot be empty.");
                continue;
            }

            ArrayList<Game> results = linearSearch(list, keyword);
            if (results.isEmpty()) {
                ui.warning("No matching game found. Checked " + list.size() + " games.");
                continue;
            }

            paginateGames(results, "SEARCH RESULTS", false);
        }
    }

    // Linear Search checks each game in the selected list for an ID, title, or platform match.
    ArrayList<Game> linearSearch(ArrayList<Game> list, String keyword) {
        return gameCatalog.linearSearch(list, keyword);
    }

    // =====================================================
    //  INSERTION SORT
    // =====================================================

    void sortInventory() {
        while (true) {
            String platform = choosePlatform("SORT GAME INVENTORY");
            if (platform.equals("BACK")) {
                return;
            }

            while (true) {
                ui.showSortOptions();
                int choice = readNumber("                            > ");

                if (choice == 0) {
                    break;
                }
                if (choice < 1 || choice > 4) {
                    ui.error("Invalid choice. Please enter 1, 2, 3, 4, or 0.");
                    continue;
                }

                ArrayList<Game> list = getGameList(platform);
                insertionSort(list, choice);
                paginateGames(list, "SORTED GAMES", false);
                break;
            }
        }
    }

    // Insertion Sort: take one game, shift bigger games to the right,
    // then insert the game in its correct position.
    void insertionSort(ArrayList<Game> list, int option) {
        gameCatalog.insertionSort(list, option);
    }

    // =====================================================
    //  CLIENT MENU
    // =====================================================

    void clientMenu(User client) {
        boolean loggedIn = true;

        while (loggedIn) {
            ui.showClientMenu(client);

            int choice = readNumber("                            > ");

            if (choice == 1) {
                browseGames();
            } else if (choice == 2) {
                sortInventory();
            } else if (choice == 3) {
                placeInQueue(client);
            } else if (choice == 4) {
                cancelMyRequest(client);
            } else if (choice == 5) {
                viewHistory(client);
            } else if (choice == 6) {
                upgradeToVip(client);
            } else if (choice == 0) {
                ui.loading("Logging out");
                ui.success("You have been logged out.");
                ui.pause(500);
                loggedIn = false;
            } else {
                ui.error("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    void browseGames() {
        boolean browsing = true;

        while (browsing) {
            ui.showBrowseMenu();

            int choice = readNumber("                            > ");

            if (choice == 1) {
                viewGames("ALL");
            } else if (choice == 2) {
                viewGames("PS5");
            } else if (choice == 3) {
                viewGames("Xbox");
            } else if (choice == 4) {
                viewGames("Nintendo");
            } else if (choice == 5) {
                searchGames(getGameList("ALL"));
            } else if (choice == 0) {
                browsing = false;
            } else {
                ui.error("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    double getRentalPrice(User client, Game game) {
        if (client.isVip()) {
            return game.price * (1 - VIP_DISCOUNT);
        }
        return game.price;
    }

    boolean hasActiveRental(User client, Game game) {
        for (Rental rental : activeRentals) {
            if (rental.client == client && rental.game == game) {
                return true;
            }
        }
        return false;
    }

    boolean gameHasActiveRental(Game game) {
        for (Rental rental : activeRentals) {
            if (rental.game == game) {
                return true;
            }
        }
        return false;
    }

    // Price of a rental of any length. The game's listed price covers RENTAL_DAYS,
    // so a 3-day rental costs exactly what it did before; other lengths scale from it.
    double getRentalPrice(User client, Game game, int days) {
        double price = getRentalPrice(client, game) * days / RENTAL_DAYS;
        return Math.round(price * 100) / 100.0;
    }

    // Price of 'quantity' copies for 'days' days. The per-copy price is rounded to cents first,
    // so the order summary, the admin screen and rentals.txt always agree.
    double getRentalPrice(User client, Game game, int days, int quantity) {
        return Math.round(getRentalPrice(client, game, days) * quantity * 100) / 100.0;
    }

    // Y/N question used by the cart and order screens
    boolean askYesNo(String question) {
        while (true) {
            System.out.println("------------------------- Y / N ---------------------------");
            System.out.println("                 " + question);
            String answer = readText("                            > ");
            if (answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("YES")) {
                return true;
            }
            if (answer.equalsIgnoreCase("N") || answer.equalsIgnoreCase("NO")) {
                return false;
            }
            ui.error("Please enter Y or N.");
        }
    }

    // Same platform list / Game ID / availability checks as before, plus a stock check that
    // counts what is already in the cart. Returns null if the client backs out of the platform menu.
    Game chooseOneGame(User client, LinkedHashMap<Game, Integer> cart) {
        while (true) {
            String platform = choosePlatform("CLIENT > RENT GAMES > SELECT PLATFORM");
            if (platform.equals("BACK")) {
                return null;
            }

            ArrayList<Game> list = getGameList(platform);
            while (true) {
                Game game = paginateGames(list, "SELECT GAME", true);
                if (game == null) {
                    break; // back to platform selection
                }

                if (!game.isAvailable()) {
                    ui.error("Sorry, this game is not available right now.");
                } else if (checkoutQueue.hasRequest(client, game)) {
                    ui.error("You already have a pending request for this game.");
                } else if (hasActiveRental(client, game)) {
                    ui.error("You are already renting this game.");
                } else if (game.availableCopies - cart.getOrDefault(game, 0) <= 0) {
                    ui.error("All available copies of this game are already in your cart.");
                } else {
                    return game;
                }
            }
        }
    }

    // Asks for a quantity that fits the stock still free after what is already in the cart.
    // Returns 0 if the client decides not to add this game after all.
    int askQuantity(Game game, int inCart) {
        int remaining = game.availableCopies - inCart;
        printHeader("CLIENT > RENT GAMES", "SELECT QUANTITY");
        System.out.println("Game      : " + game.title);
        System.out.println("Platform  : " + ui.platformColor(game.platform));
        System.out.println("Available : " + remaining + " copy/copies");
        System.out.println("Enter 0 to go back.\n");

        while (true) {
            System.out.println("--------------------- ENTER QUANTITY ----------------------");
            int quantity = readNumber("                            > ");
            if (quantity == 0) {
                return 0;
            }
            if (quantity < 0) {
                ui.error("Please enter 0 or a positive whole number.");
            } else if (quantity <= remaining) {
                return quantity;
            } else {
                ui.error("Only " + remaining + (remaining == 1 ? " copy is" : " copies are")
                    + " currently available.");
            }
        }
    }

    // Game selection + quantity, repeated while the client wants to add more games.
    // The same game can be added again; its quantity is added to the cart line.
    // Returns true if the cart holds at least one game.
    boolean addGamesToCart(User client, LinkedHashMap<Game, Integer> cart) {
        boolean adding = true;
        while (adding) {
            Game game = chooseOneGame(client, cart);
            if (game == null) {
                break;
            }

            int inCart = cart.getOrDefault(game, 0);
            int quantity = askQuantity(game, inCart);
            if (quantity == 0) {
                continue;   // NO -> straight back to game selection
            }

            cart.put(game, inCart + quantity);
            ui.success(quantity + " x " + game.title + " added to your cart.");
            adding = askYesNo("Do you want to add another game?");
        }
        return !cart.isEmpty();
    }

    // Returns the number of rental days, or 0 if the client goes back.
    int chooseRentalDays() {
        while (true) {
            printHeader("SELECT RENTAL DURATION");
            System.out.println("[1] 1 Day");
            System.out.println("[2] 3 Days");
            System.out.println("[3] 7 Days");
            System.out.println("[4] Custom Duration");
            System.out.println("[0] Back");
            System.out.println("---------------------- ENTER CHOICE -----------------------");

            int choice = readNumber("                            > ");

            if (choice == 1) {
                return 1;
            } else if (choice == 2) {
                return 3;
            } else if (choice == 3) {
                return 7;
            } else if (choice == 4) {
                System.out.println("-------------------- RENTAL DURATION ----------------------");
                int days = readNumber("                            > ");
                if (days <= 0) {
                    ui.error("Please enter a positive whole number of days.");
                } else if (days > LONG_RENTAL_DAYS) {
                    if (askYesNo("The rental period exceeds one month. Do you want to continue?")) {
                        return days;
                    }
                } else {
                    return days;
                }
            } else if (choice == 0) {
                return 0;
            } else {
                ui.error("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    void showOrderSummary(User client, LinkedHashMap<Game, Integer> cart, int days) {
        printHeader("ORDER SUMMARY");

        double total = 0;
        int totalQuantity = 0;
        int line = 1;
        System.out.println("Game(s):");
        for (Map.Entry<Game, Integer> entry : cart.entrySet()) {
            Game game = entry.getKey();
            int quantity = entry.getValue();
            double subtotal = getRentalPrice(client, game, days, quantity);
            total += subtotal;
            totalQuantity += quantity;
            System.out.println("  " + line + ". " + game.title + " (" + ui.platformColor(game.platform) + ") | Qty: "
                               + quantity + " x " + money(getRentalPrice(client, game, days))
                               + " = " + money(subtotal));
            line++;
        }

        System.out.println("Total Quantity: " + totalQuantity);
        System.out.println("Rental Duration: " + days + (days == 1 ? " day" : " days"));
        if (client.isVip()) {
            System.out.println("Membership: " + ui.membershipColor(true) + " - 10% discount applied");
        }
        System.out.println("Total Cost: " + money(total));
        System.out.println(ConsoleUI.LINE);
    }

    // "Continue game selection": the order stays in the cart and can be changed.
    // Returns the (possibly changed) rental duration, or 0 if the client cancels an empty order.
    int editOrder(User client, LinkedHashMap<Game, Integer> cart, int days) {
        while (true) {
            printHeader("EDIT ORDER");
            int line = 1;
            for (Map.Entry<Game, Integer> entry : cart.entrySet()) {
                System.out.println(line + ". " + entry.getKey().title + " ("
                                   + ui.platformColor(entry.getKey().platform) + ") | Qty: " + entry.getValue());
                line++;
            }
            if (cart.isEmpty()) {
                System.out.println("(Your cart is empty)");
            }
            System.out.println("Rental Duration: " + days + (days == 1 ? " day" : " days"));
            System.out.println("[1] Add a game");
            System.out.println("[2] Remove a game from the cart");
            System.out.println("[3] Change rental duration");
            System.out.println("[0] Review order");
            System.out.println("---------------------- ENTER CHOICE -----------------------");

            int choice = readNumber("                            > ");

            if (choice == 1) {
                addGamesToCart(client, cart);
            } else if (choice == 2) {
                System.out.println("---------------------- LINE NUMBER ------------------------");
                System.out.println("                         [0] Back");
                int number = readNumber("                            > ");
                if (number >= 1 && number <= cart.size()) {
                    Game removed = new ArrayList<>(cart.keySet()).get(number - 1);
                    cart.remove(removed);
                    ui.success(removed.title + " removed from your cart.");
                } else if (number != 0) {
                    ui.error("Invalid line number.");
                }
            } else if (choice == 3) {
                int newDays = chooseRentalDays();
                if (newDays != 0) {
                    days = newDays;
                }
            } else if (choice == 0) {
                if (!cart.isEmpty()) {
                    return days;
                }
                ui.warning("Your cart is empty.");
                if (askYesNo("Do you want to cancel this order?")) {
                    return 0;   // 0 = order cancelled
                }
            } else {
                ui.error("Invalid choice.");
            }
        }
    }

    void placeInQueue(User client) {
        LinkedHashMap<Game, Integer> cart = new LinkedHashMap<>();
        if (!addGamesToCart(client, cart)) {
            return;
        }

        int days = chooseRentalDays();
        if (days == 0) {
            return;
        }

        // Show the summary until the client confirms or cancels. Declining lets them edit.
        while (true) {
            showOrderSummary(client, cart, days);

            if (askYesNo("Is your order correct?")) {
                break;
            }
            if (!askYesNo("Do you want to continue game selection?")) {
                ui.info("Order cancelled.");
                ui.pause(700);
                return;
            }
            days = editOrder(client, cart, days);
            if (days == 0) {
                ui.info("Order cancelled.");
                ui.pause(700);
                return;
            }
        }

        // Confirmed: each game in the cart becomes its own request (with its quantity) in the queue.
        ui.loading("Submitting order");
        printHeader("CLIENT > RENT GAMES", "ORDER SUBMITTED");
        ui.success("Your order was added to the checkout queue.");
        for (Map.Entry<Game, Integer> entry : cart.entrySet()) {
            Game game = entry.getKey();
            RentalRequest request = new RentalRequest(nextQueueNumber, client, game);
            request.rentalDays = days;
            request.quantity = entry.getValue();
            nextQueueNumber++;
            checkoutQueue.addRequest(request);
            logEvent("Queue request " + queueLabel(request.queueNumber) + ": "
                     + client.username + " - " + game.title + " x" + request.quantity
                     + " (" + days + " day(s))");

            System.out.println("Queue Number: " + queueLabel(request.queueNumber)
                               + " | " + game.title + " | " + ui.platformColor(game.platform)
                               + " | Qty: " + request.quantity
                               + " | Position: " + checkoutQueue.getPosition(request));
        }
        saveQueue();
        ui.waitForEnter();
    }

    void cancelMyRequest(User client) {
        while (true) {
            ArrayList<RentalRequest> myRequests = checkoutQueue.getRequestsOf(client);
            if (myRequests.isEmpty()) {
                printHeader("CLIENT > QUEUE REQUESTS", "CANCEL QUEUE REQUEST");
                ui.info("You have no pending queue requests.");
                ui.waitForEnter();
                return;
            }

            printHeader("CLIENT > QUEUE REQUESTS", "MY QUEUE REQUESTS");
            for (RentalRequest request : myRequests) {
                System.out.println(queueLabel(request.queueNumber) + " | "
                    + request.game.title + " | " + ui.platformColor(request.game.platform)
                    + " | Qty: " + request.quantity + " | Position: "
                    + checkoutQueue.getPosition(request));
            }

            System.out.println("---------------------- QUEUE NUMBER -----------------------");
            System.out.println("                         [0] Back");
            int number = readNumber("                            > ");
            if (number == 0) {
                return;
            }

            RentalRequest request = checkoutQueue.findByNumber(number);
            if (request == null || request.client != client) {
                ui.error("Invalid queue number. You can only cancel your own requests.");
                continue;
            }

            if (!askYesNo("Cancel request " + queueLabel(number) + " for " + request.game.title + "?")) {
                continue;
            }

            ui.loading("Cancelling request");
            checkoutQueue.removeRequest(request);
            saveQueue();
            logEvent("Client cancelled queue request " + queueLabel(number) + ": "
                     + client.username + " - " + request.game.title + " x" + request.quantity);
            ui.success("Queue request " + queueLabel(number) + " has been cancelled.");

            if (checkoutQueue.getRequestsOf(client).isEmpty()
                    || !askYesNo("Do you want to cancel another queue request?")) {
                return;
            }
        }
    }

    void viewHistory(User client) {
        if (client.rentalHistory.isEmpty()) {
            printHeader("CLIENT > RENTAL HISTORY", "RENTAL HISTORY");
            ui.info("You have no rental history yet.");
            ui.waitForEnter();
            return;
        }

        ArrayList<Rental> history = new ArrayList<>();
        for (int i = client.rentalHistory.size() - 1; i >= 0; i--) {
            history.add(client.rentalHistory.get(i));
        }

        int page = 0;
        int totalPages = (history.size() + DETAIL_PAGE_SIZE - 1) / DETAIL_PAGE_SIZE;
        while (true) {
            int start = page * DETAIL_PAGE_SIZE;
            int end = Math.min(start + DETAIL_PAGE_SIZE, history.size());
            printHeader("RENTAL HISTORY (Most Recent First)");
            System.out.println("Showing " + (start + 1) + "-" + end + " of " + history.size() + " rentals");

            for (int i = start; i < end; i++) {
                Rental rental = history.get(i);
                System.out.println();
                System.out.println("Rental  : " + rentalLabel(rental.rentalNumber));
                System.out.println("Game    : " + rental.game.title + " x" + rental.quantity);
                System.out.println("Rented  : " + rental.checkoutDate.format(DATE_FORMAT));
                System.out.println("Due     : " + rental.dueDate.format(DATE_FORMAT));
                System.out.println("Amount  : " + money(rental.amount));
                System.out.println("Status  : " + ui.statusColor(rental.status));
                System.out.println("----------------------------------------");
            }

            if (page < totalPages - 1) System.out.println("[N] Next Page");
            if (page > 0) System.out.println("[P] Previous Page");
            System.out.println("[0] Back");
            String choice = readText("                            > ");
            if (choice.equalsIgnoreCase("N") && page < totalPages - 1) page++;
            else if (choice.equalsIgnoreCase("P") && page > 0) page--;
            else if (choice.equals("0")) return;
            else ui.error("Invalid choice.");
        }
    }

    void upgradeToVip(User client) {
        if (client.isVip()) {
            printHeader("CLIENT > MEMBERSHIP", "VIP MEMBERSHIP");
            ui.info("You are already a VIP member.");
            ui.waitForEnter();
            return;
        }

        printHeader("CLIENT > MEMBERSHIP", "UPGRADE TO VIP");
        System.out.println("Price           : $15/month");
        System.out.println("Rental Discount : 10%");
        System.out.println("Queue Priority  : Yes");
        System.out.println("Membership      : " + ui.membershipColor(true));
        System.out.println("(Demo only: no real payment is processed.)\n");

        if (askYesNo("Upgrade now?")) {
            ui.loading("Upgrading membership");
            client.membership = "VIP";
            saveAccounts();
            logEvent("Upgraded to VIP: " + client.username);
            ui.success("Upgrade successful! You are now a VIP member.");
        } else {
            ui.info("Upgrade cancelled.");
        }
        ui.waitForEnter();
    }

    // =====================================================
    //  ADMIN MENU
    // =====================================================

    void adminMenu(User admin) {
        boolean loggedIn = true;

        while (loggedIn) {
            ui.showAdminMenu();

            int choice = readNumber("                            > ");

            if (choice == 1) {
                processCheckoutQueue();
            } else if (choice == 2) {
                processReturn();
            } else if (choice == 3) {
                manageInventory();
            } else if (choice == 4) {
                viewOverdueItems();
            } else if (choice == 5) {
                viewAuditLog();
            } else if (choice == 6) {
                viewActiveRentals();
            } else if (choice == 0) {
                ui.loading("Logging out");
                ui.success("You have been logged out.");
                ui.pause(500);
                loggedIn = false;
            } else {
                ui.error("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    // First request (from the front) whose game is available
    RentalRequest findNextEligibleRequest() {
        RentalRequest current = checkoutQueue.head;
        while (current != null) {
            if (current.game.availableCopies >= current.quantity) {
                return current;
            }
            current = current.next;
        }
        return null;
    }

    void processCheckoutQueue() {
        while (true) {
            if (checkoutQueue.isEmpty()) {
                printHeader("ADMIN > CHECKOUT QUEUE", "PROCESS CHECKOUT QUEUE");
                ui.info("The checkout queue is empty.");
                ui.waitForEnter();
                return;
            }

            printHeader("ADMIN > CHECKOUT QUEUE", "CURRENT CHECKOUT QUEUE");
            ui.showQueue(checkoutQueue);

            RentalRequest next = findNextEligibleRequest();
            if (next == null) {
                ui.warning("No request can be processed because the requested games do not have enough stock.");
                ui.waitForEnter();
                return;
            }

            printHeader("ADMIN > CHECKOUT QUEUE", "NEXT CHECKOUT");
            System.out.println("Customer        : " + next.client.username);
            System.out.println("Membership      : " + ui.membershipColor(next.vip));
            System.out.println("Game            : " + next.game.title);
            System.out.println("Platform        : " + ui.platformColor(next.game.platform));
            System.out.println("Queue Number    : " + queueLabel(next.queueNumber));
            System.out.println("Quantity        : " + next.quantity);
            System.out.println("Rental Duration : " + next.rentalDays + " day(s)");
            System.out.println("Rental Price    : "
                + money(getRentalPrice(next.client, next.game, next.rentalDays, next.quantity)));
            System.out.println();
            System.out.println("[1] Approve Checkout");
            System.out.println("[2] Cancel Request");
            System.out.println("[0] Back");
            System.out.println("---------------------- ENTER CHOICE -----------------------");

            int choice = readNumber("                            > ");
            if (choice == 0) {
                return;
            }
            if (choice == 1) {
                ui.loading("Processing checkout");
                approveCheckout(next);
            } else if (choice == 2) {
                if (!askYesNo("Cancel request " + queueLabel(next.queueNumber) + "?")) {
                    continue;
                }
                checkoutQueue.removeRequest(next);
                logAdmin("Cancelled request: " + next.client.username + " - " + next.game.title
                         + " x" + next.quantity);
                saveQueue();
                ui.success("Request " + queueLabel(next.queueNumber) + " has been cancelled.");
            } else {
                ui.error("Invalid choice. Please enter 1, 2, or 0.");
                continue;
            }

            if (!askYesNo("Do you want to process another checkout request?")) {
                return;
            }
        }
    }

    void approveCheckout(RentalRequest request) {
        LocalDateTime today = LocalDateTime.now();

        request.game.availableCopies -= request.quantity;

        Rental rental = new Rental(nextRentalNumber, request.client, request.game,
            today, today.plusDays(request.rentalDays),
            getRentalPrice(request.client, request.game, request.rentalDays, request.quantity));
        rental.quantity = request.quantity;
        nextRentalNumber++;

        allRentals.add(rental);
        activeRentals.add(rental);                   // add to the Min-Heap
        request.client.rentalHistory.push(rental);   // add to the client's Stack
        checkoutQueue.removeRequest(request);        // remove from the queue
        logAdmin("Processed checkout: " + request.client.username
                      + " - " + request.game.title + " x" + request.quantity);

        saveData();
        System.out.println("\nCheckout approved!");
        System.out.println("Rental " + rentalLabel(rental.rentalNumber) + " is now ACTIVE.");
        System.out.println("Quantity: " + rental.quantity);
        System.out.println("Amount: " + money(rental.amount));
        System.out.println("Rented On: " + rental.checkoutDate.format(DATE_FORMAT));
        System.out.println("Due On: " + rental.dueDate.format(DATE_FORMAT));
    }

    // Copy the Min-Heap and poll it so rentals come out earliest due date first
    ArrayList<Rental> getRentalsByDueDate() {
        ArrayList<Rental> sorted = new ArrayList<>();
        PriorityQueue<Rental> copy = new PriorityQueue<>(activeRentals);

        while (!copy.isEmpty()) {
            sorted.add(copy.poll());
        }

        return sorted;
    }

    Rental selectActiveRentalForReturn() {
        ArrayList<Rental> rentals = getRentalsByDueDate();
        if (rentals.isEmpty()) {
            return null;
        }

        int page = 0;
        int pageSize = 10;
        int totalPages = (rentals.size() + pageSize - 1) / pageSize;

        while (true) {
            int start = page * pageSize;
            int end = Math.min(start + pageSize, rentals.size());
            printHeader("ADMIN > RETURNS", "SELECT RENTAL TO RETURN");
            System.out.println("Showing " + (start + 1) + "-" + end + " of " + rentals.size() + " active rentals\n");

            for (int i = start; i < end; i++) {
                Rental rental = rentals.get(i);
                int shown = i - start + 1;
                System.out.println("[" + shown + "] " + rentalLabel(rental.rentalNumber)
                    + " | " + rental.client.username
                    + " | " + rental.game.title + " x" + rental.quantity
                    + " | Due: " + rental.dueDate.format(DATE_FORMAT));
            }

            System.out.println();
            if (page < totalPages - 1) System.out.println("[N] Next Page");
            if (page > 0) System.out.println("[P] Previous Page");
            System.out.println("[0] Back");
            System.out.println(ConsoleUI.LINE);

            System.out.println("---------------------- ENTER CHOICE -----------------------");
            String choice = readText("                            > ");
            if (choice.equalsIgnoreCase("N") && page < totalPages - 1) {
                page++;
            } else if (choice.equalsIgnoreCase("P") && page > 0) {
                page--;
            } else if (choice.equals("0")) {
                return null;
            } else {
                try {
                    int number = Integer.parseInt(choice);
                    if (number >= 1 && number <= end - start) {
                        return rentals.get(start + number - 1);
                    }
                    ui.error("Invalid rental selection.");
                } catch (NumberFormatException e) {
                    ui.error("Invalid choice.");
                }
            }
        }
    }

    void processReturn() {
        while (true) {
            if (activeRentals.isEmpty()) {
                printHeader("ADMIN > RETURNS", "PROCESS GAME RETURN");
                ui.info("There are no active rentals to return.");
                ui.waitForEnter();
                return;
            }

            Rental found = selectActiveRentalForReturn();
            if (found == null) {
                return;
            }

            int daysLate;
            while (true) {
                printHeader("ADMIN > RETURNS", "PROCESS RETURN");
                System.out.println("Rental   : " + rentalLabel(found.rentalNumber));
                System.out.println("Customer : " + found.client.username);
                System.out.println("Game     : " + found.game.title);
                System.out.println("Platform : " + ui.platformColor(found.game.platform));
                System.out.println("Quantity : " + found.quantity);
                System.out.println("Due On   : " + found.dueDate.format(DATE_FORMAT));
                System.out.println();

                System.out.println("----------------------- DAYS LATE -------------------------");
                daysLate = readNumber("                            > ");
                if (daysLate < 0) {
                    ui.error("Please enter 0 or a positive whole number.");
                    continue;
                }
                break;
            }

            double lateFee = daysLate * LATE_FEE_PER_DAY * found.quantity;
            ui.loading("Processing return");

            activeRentals.remove(found);
            found.status = "COMPLETED";
            found.game.availableCopies += found.quantity;

            logAdmin("Processed return: " + found.client.username + " - " + found.game.title
                     + " x" + found.quantity);
            if (lateFee > 0) {
                logAdmin("Applied late fee " + money(lateFee) + ": " + found.client.username);
            }

            saveData();
            printHeader("ADMIN > RETURNS", "RETURN PROCESSED");
            ui.success("Game return processed successfully.");
            System.out.println("Customer          : " + found.client.username);
            System.out.println("Game              : " + found.game.title);
            System.out.println("Quantity Returned : " + found.quantity);
            System.out.println("Days Overdue      : " + daysLate);
            System.out.println("Late Fee          : " + money(lateFee));
            System.out.println("Current Stock     : " + found.game.availableCopies);

            if (!askYesNo("Do you want to process another return?")) {
                return;
            }
        }
    }

    void viewActiveRentals() {
        ArrayList<Rental> rentals = getRentalsByDueDate();
        if (rentals.isEmpty()) {
            printHeader("ADMIN > ACTIVE RENTALS", "ACTIVE RENTALS");
            ui.info("There are no active rentals.");
            ui.waitForEnter();
            return;
        }

        int page = 0;
        int totalPages = (rentals.size() + DETAIL_PAGE_SIZE - 1) / DETAIL_PAGE_SIZE;
        while (true) {
            int start = page * DETAIL_PAGE_SIZE;
            int end = Math.min(start + DETAIL_PAGE_SIZE, rentals.size());
            printHeader("ACTIVE RENTALS - Earliest Due Date First");
            System.out.println("Showing " + (start + 1) + "-" + end + " of " + rentals.size() + " rentals");

            for (int i = start; i < end; i++) {
                Rental rental = rentals.get(i);
                System.out.println();
                System.out.println("Rental   : " + rentalLabel(rental.rentalNumber));
                System.out.println("Customer : " + rental.client.username);
                System.out.println("Game     : " + rental.game.title + " | " + ui.platformColor(rental.game.platform));
                System.out.println("Quantity : " + rental.quantity);
                System.out.println("Rented On: " + rental.checkoutDate.format(DATE_FORMAT));
                System.out.println("Due On   : " + rental.dueDate.format(DATE_FORMAT));
                System.out.println("Status   : " + ui.statusColor(rental.status));
                System.out.println("----------------------------------------");
            }

            if (page < totalPages - 1) System.out.println("[N] Next Page");
            if (page > 0) System.out.println("[P] Previous Page");
            System.out.println("[0] Back");
            String choice = readText("                            > ");
            if (choice.equalsIgnoreCase("N") && page < totalPages - 1) page++;
            else if (choice.equalsIgnoreCase("P") && page > 0) page--;
            else if (choice.equals("0")) return;
            else ui.error("Invalid choice.");
        }
    }

    void viewOverdueItems() {
        ArrayList<Rental> rentals = getRentalsByDueDate();
        if (rentals.isEmpty()) {
            printHeader("ADMIN > OVERDUE ITEMS", "OVERDUE ITEMS");
            ui.info("There are no active rentals.");
            ui.waitForEnter();
            return;
        }

        int page = 0;
        int totalPages = (rentals.size() + DETAIL_PAGE_SIZE - 1) / DETAIL_PAGE_SIZE;
        while (true) {
            int start = page * DETAIL_PAGE_SIZE;
            int end = Math.min(start + DETAIL_PAGE_SIZE, rentals.size());
            printHeader("OVERDUE ITEMS - Earliest Due Date First");
            System.out.println("Showing " + (start + 1) + "-" + end + " of " + rentals.size() + " rentals");

            LocalDateTime now = LocalDateTime.now();
            for (int i = start; i < end; i++) {
                Rental rental = rentals.get(i);
                long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(rental.dueDate, now));
                String status = now.isAfter(rental.dueDate)
                    ? "OVERDUE" + (daysOverdue > 0 ? " by " + daysOverdue + " day(s)" : "")
                    : "On time";

                System.out.println();
                System.out.println("Rental   : " + rentalLabel(rental.rentalNumber));
                System.out.println("Customer : " + rental.client.username);
                System.out.println("Game     : " + rental.game.title + " | " + ui.platformColor(rental.game.platform) + " | Qty: " + rental.quantity);
                System.out.println("Due On   : " + rental.dueDate.format(DATE_FORMAT));
                System.out.println("Status   : " + ui.statusColor(status));
                if (now.isAfter(rental.dueDate)) {
                    System.out.println("Late Fee : " + money(daysOverdue * LATE_FEE_PER_DAY * rental.quantity));
                }
                System.out.println("----------------------------------------");
            }

            Rental top = activeRentals.peek();
            System.out.println("Highest Priority: Rental " + rentalLabel(top.rentalNumber)
                + " | Due: " + top.dueDate.format(DATE_FORMAT));
            if (page < totalPages - 1) System.out.println("[N] Next Page");
            if (page > 0) System.out.println("[P] Previous Page");
            System.out.println("[0] Back");

            String choice = readText("                            > ");
            if (choice.equalsIgnoreCase("N") && page < totalPages - 1) page++;
            else if (choice.equalsIgnoreCase("P") && page > 0) page--;
            else if (choice.equals("0")) return;
            else ui.error("Invalid choice.");
        }
    }

    void viewAuditLog() {
        if (auditLog.isEmpty()) {
            printHeader("ADMIN > AUDIT LOG", "ADMIN AUDIT LOG");
            ui.info("No actions have been recorded yet.");
            ui.waitForEnter();
            return;
        }

        ArrayList<String> logs = new ArrayList<>();
        for (int i = auditLog.size() - 1; i >= 0; i--) logs.add(auditLog.get(i));

        int page = 0;
        int totalPages = (logs.size() + LOG_PAGE_SIZE - 1) / LOG_PAGE_SIZE;
        while (true) {
            int start = page * LOG_PAGE_SIZE;
            int end = Math.min(start + LOG_PAGE_SIZE, logs.size());
            printHeader("ADMIN AUDIT LOG (Most Recent First)");
            System.out.println("Showing " + (start + 1) + "-" + end + " of " + logs.size() + " records");
            System.out.printf("%-13s %-10s %s%n", "DATE", "TIME", "ACTION");
            System.out.println("------------------------------------------------------------");

            for (int i = start; i < end; i++) {
                String entry = logs.get(i);
                String[] parts = entry.split("\\|", 2);
                if (parts.length == 2) {
                    try {
                        LocalDateTime stamp = LocalDateTime.parse(parts[0], LOG_STORAGE_FORMAT);
                        System.out.printf("%-13s %-10s %s%n",
                            stamp.format(LOG_DISPLAY_DATE), stamp.format(LOG_DISPLAY_TIME), parts[1]);
                    } catch (DateTimeException e) {
                        System.out.println(entry);
                    }
                } else {
                    System.out.println(entry);
                }
            }

            System.out.println("------------------------------------------------------------");
            if (page < totalPages - 1) System.out.println("[N] Next Page");
            if (page > 0) System.out.println("[P] Previous Page");
            System.out.println("[0] Back");
            String choice = readText("                            > ");
            if (choice.equalsIgnoreCase("N") && page < totalPages - 1) page++;
            else if (choice.equalsIgnoreCase("P") && page > 0) page--;
            else if (choice.equals("0")) return;
            else ui.error("Invalid choice.");
        }
    }

    // =====================================================
    //  MANAGE INVENTORY (ADMIN)
    // =====================================================

    void manageInventory() {
        boolean managing = true;

        while (managing) {
            ui.showInventoryMenu();

            int choice = readNumber("                            > ");

            if (choice == 1) {
                viewGames("ALL");
            } else if (choice == 2) {
                viewGames("PS5");
            } else if (choice == 3) {
                viewGames("Xbox");
            } else if (choice == 4) {
                viewGames("Nintendo");
            } else if (choice == 5) {
                addGame();
            } else if (choice == 6) {
                deleteGame();
            } else if (choice == 7) {
                updateGameStock();
            } else if (choice == 8) {
                searchGames(getGameList("ALL"));
            } else if (choice == 9) {
                sortInventory();
            } else if (choice == 0) {
                managing = false;
            } else {
                ui.error("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    void addGame() {
        while (true) {
            printHeader("ADMIN > MANAGE INVENTORY > ADD GAME", "ADD GAME");
            System.out.println("Type 0 as the title to go back.\n");

            String title;
            while (true) {
                System.out.println("------------------------ GAME TITLE -----------------------");
                title = readText("                            > ");
                if (title.equals("0")) return;
                if (title.isEmpty()) {
                    ui.error("Title cannot be empty.");
                } else if (title.contains("|")) {
                    ui.error("Title cannot contain the | symbol.");
                } else {
                    break;
                }
            }

            String platform;
            while (true) {
                System.out.println("\n[1] " + ui.platformColor("PS5")
                    + "   [2] " + ui.platformColor("Xbox")
                    + "   [3] " + ui.platformColor("Nintendo")
                    + "   [0] Cancel");
                System.out.println("------------------------ PLATFORM -------------------------");
                int platformChoice = readNumber("                            > ");
                if (platformChoice == 0) return;
                if (platformChoice == 1) platform = "PS5";
                else if (platformChoice == 2) platform = "Xbox";
                else if (platformChoice == 3) platform = "Nintendo";
                else {
                    ui.error("Invalid platform choice. Please enter 1, 2, 3, or 0.");
                    continue;
                }
                break;
            }

            boolean duplicate = false;
            for (Game game : games.values()) {
                if (game.title.equalsIgnoreCase(title) && game.platform.equals(platform)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                ui.error("That game already exists on " + platform + ".");
                continue;
            }

            double price;
            while (true) {
                System.out.println("--------------------- RENTAL PRICE ($) --------------------");
                price = readAmount("                            > ");
                if (price <= 0) {
                    ui.error("Invalid price. Enter a number greater than 0.");
                } else {
                    break;
                }
            }

            int copies;
            while (true) {
                System.out.println("-------------------- AVAILABLE COPIES ---------------------");
                copies = readNumber("                            > ");
                if (copies < 1) {
                    ui.error("Invalid number of copies. Enter 1 or more.");
                } else {
                    break;
                }
            }

            ui.loading("Saving game");
            Game game = addGameToInventory(title, platform, price, copies);
            logAdmin("Added game: " + title + " (" + platform + ")");
            saveGames();

            printHeader("ADMIN > MANAGE INVENTORY > ADD GAME", "GAME ADDED");
            ui.success("Game added successfully.");
            System.out.println("Game ID  : " + game.gameId);
            System.out.println("Title    : " + game.title);
            System.out.println("Platform : " + ui.platformColor(game.platform));
            System.out.println("Stock    : " + game.availableCopies);
            System.out.println("Price    : " + money(game.price));

            if (!askYesNo("Do you want to add another game?")) {
                return;
            }
        }
    }

    void deleteGame() {
        while (true) {
            String platform = choosePlatform("ADMIN > MANAGE INVENTORY > DELETE GAME");
            if (platform.equals("BACK")) return;

            while (true) {
                ArrayList<Game> list = getGameList(platform);
                Game game = paginateGames(list, "SELECT GAME TO DELETE", true);
                if (game == null) {
                    break;
                }

                if (gameHasActiveRental(game)) {
                    ui.error("Cannot delete this game because it currently has an active rental.");
                    continue;
                }

                if (!askYesNo("Delete " + game.title + " (" + game.platform + ")?")) {
                    continue;
                }

                ui.loading("Deleting game");
                int cancelled = checkoutQueue.removeRequestsForGame(game);
                games.remove(game.gameId);
                logAdmin("Deleted game: " + game.title + " (" + game.platform + ")");
                saveData();

                printHeader("ADMIN > MANAGE INVENTORY > DELETE GAME", "GAME DELETED");
                ui.success(game.title + " was deleted successfully.");
                if (cancelled > 0) {
                    ui.warning(cancelled + " pending queue request(s) for this game were cancelled.");
                }

                if (!askYesNo("Do you want to delete another game?")) {
                    return;
                }
                break;
            }
        }
    }

    void updateGameStock() {
        while (true) {
            String platform = choosePlatform("ADMIN > MANAGE INVENTORY > UPDATE STOCK");
            if (platform.equals("BACK")) return;

            while (true) {
                Game game = paginateGames(getGameList(platform), "SELECT GAME TO UPDATE", true);
                if (game == null) {
                    break;
                }

                boolean selectAnotherGame = false;
                while (!selectAnotherGame) {
                    printHeader("ADMIN > MANAGE INVENTORY > UPDATE STOCK", "UPDATE STOCK");
                    System.out.println("Game          : " + game.title);
                    System.out.println("Platform      : " + ui.platformColor(game.platform));
                    System.out.println("Current Stock : " + game.availableCopies);
                    System.out.println();
                    System.out.println("[1] Add Stock");
                    System.out.println("[2] Decrease Stock");
                    System.out.println("[0] Back to Game List");
                    System.out.println("---------------------- ENTER CHOICE -----------------------");

                    int choice = readNumber("                            > ");
                    if (choice == 0) {
                        selectAnotherGame = true;
                        continue;
                    }
                    if (choice != 1 && choice != 2) {
                        ui.error("Invalid choice. Please enter 1, 2, or 0.");
                        continue;
                    }

                    int quantity;
                    while (true) {
                        System.out.println(choice == 1
                            ? "-------------------- QUANTITY TO ADD ---------------------"
                            : "----------------- QUANTITY TO DECREASE ------------------");
                        quantity = readNumber("                            > ");

                        if (quantity <= 0) {
                            ui.error("Quantity must be greater than 0.");
                            continue;
                        }
                        if (choice == 2 && quantity > game.availableCopies) {
                            ui.error("Cannot decrease by " + quantity
                                + ". Current stock is only " + game.availableCopies + ".");
                            continue;
                        }
                        break;
                    }

                    int previous = game.availableCopies;
                    game.availableCopies += (choice == 1 ? quantity : -quantity);
                    saveGames();

                    String action = choice == 1 ? "Added stock" : "Decreased stock";
                    logAdmin(action + ": " + game.title + " (" + game.platform + ") "
                        + previous + " -> " + game.availableCopies);

                    ui.loading("Updating stock");
                    printHeader("ADMIN > MANAGE INVENTORY > UPDATE STOCK", "STOCK UPDATED");
                    ui.success("Stock updated successfully.");
                    System.out.println("Game           : " + game.title);
                    System.out.println("Previous Stock : " + previous);
                    System.out.println((choice == 1 ? "Added Stock    : " : "Removed Stock  : ") + quantity);
                    System.out.println("Current Stock  : " + game.availableCopies);

                    if (!askYesNo("Do you want to update another game's stock?")) {
                        return;
                    }
                    selectAnotherGame = true;
                }
            }
        }
    }
}