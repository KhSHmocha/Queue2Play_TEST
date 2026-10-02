import java.io.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class GameRentalSystem {

    // ---------- Settings ----------
    static final String ACCOUNTS_FILE = "accounts.txt";
    static final String GAMES_FILE = "games.txt";
    static final String RENTALS_FILE = "rentals.txt";
    static final String QUEUE_FILE = "queue.txt";
    static final String LOG_FILE = "logs.txt";
    static final int RENTAL_DAYS = 3;
    static final double LATE_FEE_PER_DAY = 1.00;
    static final double VIP_DISCOUNT = 0.10;
    static final String LINE = "========================================";
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---------- Data structures ----------
    Scanner input = new Scanner(System.in);

    // HashMap: Game ID -> Game (LinkedHashMap is a HashMap that keeps insertion order)
    HashMap<String, Game> games = new LinkedHashMap<>();

    ArrayList<User> users = new ArrayList<>();

    // Doubly Linked List of pending checkout requests
    CheckoutQueue checkoutQueue = new CheckoutQueue();

    // Min-Heap: the rental with the earliest due date is on top
    PriorityQueue<Rental> activeRentals =
        new PriorityQueue<>((a, b) -> a.dueDate.compareTo(b.dueDate));

    // Every rental ever made (active and completed), in rental-number order.
    // Only used so rentals.txt can be written; the other structures work as before.
    ArrayList<Rental> allRentals = new ArrayList<>();

    // Stack: the most recent admin action is on top
    Stack<String> auditLog = new Stack<>();

    // ---------- Counters ----------
    int ps5Count = 0;
    int xboxCount = 0;
    int nintendoCount = 0;
    int nextQueueNumber = 1;
    int nextRentalNumber = 1;

    // =====================================================
    //  INPUT AND DISPLAY HELPERS
    // =====================================================

    String readText(String prompt) {
        System.out.print(prompt);
        if (!input.hasNextLine()) {
            System.out.println("\nInput ended. Goodbye!");
            System.exit(0);
        }
        return input.nextLine().trim();
    }

    // Returns the number typed, or -1 if the input is not a number
    int readNumber(String prompt) {
        String text = readText(prompt);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Returns the amount typed, or -1 if the input is not a number
    double readAmount(String prompt) {
        String text = readText(prompt);
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    void printHeader(String title) {
        System.out.println();
        System.out.println(LINE);
        System.out.println(title);
        System.out.println(LINE);
    }

    String money(double amount) {
        return String.format("$%.2f", amount);
    }

    String queueLabel(int number) {
        return String.format("#%03d", number);
    }

    String rentalLabel(int number) {
        return String.format("#%03d", number);
    }

    // =====================================================
    //  START, MAIN MENU
    // =====================================================

    public void start() {
        loadAccounts();
        loadGames();
        loadRentals();
        loadQueue();
        loadLogs();

        boolean running = true;
        while (running) {
            printHeader("CONSOLE GAME RENTAL SYSTEM");
            System.out.println("[1] Login");
            System.out.println("[2] Register");
            System.out.println("[0] Exit");
            System.out.println(LINE);

            int choice = readNumber("Enter choice: ");

            if (choice == 1) {
                login();
            } else if (choice == 2) {
                register();
            } else if (choice == 0) {
                System.out.println("Thank you for using the Console Game Rental System. Goodbye!");
                running = false;
            } else {
                System.out.println("Invalid choice. Please enter 1, 2, or 0.");
            }
        }
    }

    // =====================================================
    //  ACCOUNTS (accounts.txt)
    // =====================================================

    void loadAccounts() {
        // The Admin account is predefined and is not saved in the file
        users.add(new User("admin", "System Admin", "admin@store.com",
                           "admin123", "ADMIN", "REGULAR"));

        File file = new File(ACCOUNTS_FILE);

        // First run: create two demo clients and save them
        if (!file.exists()) {
            users.add(new User("alex", "Alex Reyes", "alex@email.com",
                               "alex12345", "CLIENT", "VIP"));
            users.add(new User("juan", "Juan Dela Cruz", "juan@email.com",
                               "juan12345", "CLIENT", "REGULAR"));
            saveAccounts();
            return;
        }

        try {
            Scanner fileReader = new Scanner(file);
            while (fileReader.hasNextLine()) {
                String line = fileReader.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Format: username|fullName|email|password|role|membership
                String[] parts = line.split("\\|");
                if (parts.length == 6 && parts[4].equals("CLIENT")) {
                    String membership = parts[5].equals("VIP") ? "VIP" : "REGULAR";
                    users.add(new User(parts[0], parts[1], parts[2],
                                       parts[3], "CLIENT", membership));
                }
            }
            fileReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("Could not read accounts.txt. Using default accounts only.");
        }
    }

    void saveAccounts() {
        try {
            PrintWriter writer = new PrintWriter(ACCOUNTS_FILE);
            for (User user : users) {
                if (!user.isAdmin()) {
                    writer.println(user.username + "|" + user.fullName + "|"
                        + user.email + "|" + user.password + "|"
                        + user.role + "|" + user.membership);
                }
            }
            writer.close();
        } catch (FileNotFoundException e) {
            System.out.println("Warning: could not save accounts.txt.");
        }
    }

    User findUser(String username) {
        for (User user : users) {
            if (user.username.equalsIgnoreCase(username)) {
                return user;
            }
        }
        return null;
    }

    boolean emailExists(String email) {
        for (User user : users) {
            if (user.email.equalsIgnoreCase(email)) {
                return true;
            }
        }
        return false;
    }

    // =====================================================
    //  REGISTER AND LOGIN
    // =====================================================

    void register() {
        printHeader("CREATE ACCOUNT");

        // Full name
        String fullName = "";
        while (true) {
            fullName = readText("Full Name: ");
            if (fullName.isEmpty()) {
                System.out.println("Full name cannot be empty.");
            } else if (!fullName.matches("[A-Za-z][A-Za-z .'-]*")) {
                System.out.println("Full name must contain a valid name (letters only).");
            } else {
                break;
            }
        }

        // Username
        String username = "";
        while (true) {
            username = readText("Username: ");
            if (username.isEmpty()) {
                System.out.println("Username cannot be empty.");
            } else if (username.contains(" ") || username.contains("|")) {
                System.out.println("Username cannot contain spaces or the | symbol.");
            } else if (findUser(username) != null) {
                System.out.println("Username already exists.");
            } else {
                break;
            }
        }

        // Email
        String email = "";
        while (true) {
            email = readText("Email: ");
            if (email.isEmpty()) {
                System.out.println("Email cannot be empty.");
            } else if (!email.matches("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")) {
                System.out.println("Invalid email format.");
            } else if (emailExists(email)) {
                System.out.println("Email already belongs to another account.");
            } else {
                break;
            }
        }

        // Password and confirmation
        String password = "";
        while (true) {
            password = readText("Password: ");
            if (password.isEmpty()) {
                System.out.println("Password cannot be empty.");
                continue;
            }
            if (password.length() < 8) {
                System.out.println("Password must be at least 8 characters.");
                continue;
            }
            if (password.contains("|")) {
                System.out.println("Password cannot contain the | symbol.");
                continue;
            }

            String confirm = readText("Confirm Password: ");
            if (!confirm.equals(password)) {
                System.out.println("Passwords do not match.");
                continue;
            }
            break;
        }

        // New accounts are always CLIENT and REGULAR
        users.add(new User(username, fullName, email, password, "CLIENT", "REGULAR"));
        saveAccounts();
        logEvent("Registered account: " + username);

        printHeader("ACCOUNT CREATED SUCCESSFULLY");
        System.out.println("Username: " + username);
        System.out.println("Role: CLIENT");
        System.out.println("Membership: REGULAR");
        System.out.println("Your account has been saved locally.");
        System.out.println("You can now log in.");
        System.out.println(LINE);
    }

    void login() {
        printHeader("LOGIN");

        for (int attempt = 1; attempt <= 3; attempt++) {
            String username = readText("Username: ");
            String password = readText("Password: ");

            User user = findUser(username);

            if (user != null && user.password.equals(password)) {
                logEvent("Login: " + user.username + " (" + user.role + ")");
                System.out.println("\nLogin successful!");
                System.out.println("Welcome, " + user.username);
                System.out.println("Role: " + user.role + " | Membership: " + user.membership);

                if (user.isAdmin()) {
                    adminMenu(user);
                } else {
                    clientMenu(user);
                }
                return;
            }

            // General message: does not reveal which part was wrong
            System.out.println("Invalid username or password. Please try again.");
            logEvent("Failed login attempt for username: " + username);
        }

        System.out.println("Too many failed attempts. Returning to the main menu.");
    }

    // =====================================================
    //  SAVING AND LOADING (games, rentals, queue, logs)
    //  All files use the same style as accounts.txt: one record per
    //  line, fields separated by |
    // =====================================================

    // Saves everything that changes during a transaction
    void saveData() {
        saveGames();
        saveRentals();
        saveQueue();
    }

    // games.txt  ->  gameId|title|platform|price|availableCopies
    // The first line (#COUNTERS) remembers the ID counters so IDs are never reused.
    void saveGames() {
        try (PrintWriter writer = new PrintWriter(GAMES_FILE)) {
            writer.println("#COUNTERS|" + ps5Count + "|" + xboxCount + "|" + nintendoCount);
            for (Game game : games.values()) {
                writer.println(game.gameId + "|" + game.title + "|" + game.platform + "|"
                    + game.price + "|" + game.availableCopies);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Warning: could not save games.txt.");
        }
    }

    void loadGames() {
        File file = new File(GAMES_FILE);

        // First run: use the starting games and create the file
        if (!file.exists()) {
            loadStartingGames();
            saveGames();
            return;
        }

        try (Scanner reader = new Scanner(file)) {
            while (reader.hasNextLine()) {
                String line = reader.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|");
                try {
                    if (parts[0].equals("#COUNTERS") && parts.length == 4) {
                        ps5Count = Math.max(ps5Count, Integer.parseInt(parts[1]));
                        xboxCount = Math.max(xboxCount, Integer.parseInt(parts[2]));
                        nintendoCount = Math.max(nintendoCount, Integer.parseInt(parts[3]));
                    } else if (parts.length == 5) {
                        Game game = new Game(parts[0], parts[1], parts[2],
                            Double.parseDouble(parts[3]), Integer.parseInt(parts[4]));
                        games.put(game.gameId, game);
                        updateCounterFromId(game.gameId);
                    }
                } catch (NumberFormatException e) {
                    // skip a damaged line instead of crashing
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read games.txt. Loading the starting games instead.");
            loadStartingGames();
        }
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

    // rentals.txt  ->  rentalNumber|username|gameId|title|platform|checkoutDate|dueDate|amount|status
    // The title and platform are saved too, so history still displays correctly
    // even if the game is removed from the inventory later.
    void saveRentals() {
        try (PrintWriter writer = new PrintWriter(RENTALS_FILE)) {
            for (Rental rental : allRentals) {
                writer.println(rental.rentalNumber + "|" + rental.client.username + "|"
                    + rental.game.gameId + "|" + rental.game.title + "|" + rental.game.platform + "|"
                    + rental.checkoutDate + "|" + rental.dueDate + "|"
                    + rental.amount + "|" + rental.status);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Warning: could not save rentals.txt.");
        }
    }

    void loadRentals() {
        File file = new File(RENTALS_FILE);
        if (!file.exists()) {
            return;
        }

        try (Scanner reader = new Scanner(file)) {
            while (reader.hasNextLine()) {
                String line = reader.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|");
                if (parts.length != 9) {
                    continue;
                }

                User client = findUser(parts[1]);
                if (client == null) {
                    continue;
                }

                try {
                    Game game = games.get(parts[2]);
                    if (game == null) {
                        // The game was removed later: keep a detached copy for the history
                        game = new Game(parts[2], parts[3], parts[4], 0, 0);
                    }

                    int number = Integer.parseInt(parts[0]);
                    Rental rental = new Rental(number, client, game,
                        LocalDate.parse(parts[5]), LocalDate.parse(parts[6]),
                        Double.parseDouble(parts[7]));
                    rental.status = parts[8];

                    allRentals.add(rental);
                    client.rentalHistory.push(rental);
                    if (rental.status.equals("ACTIVE")) {
                        activeRentals.add(rental);
                    }
                    nextRentalNumber = Math.max(nextRentalNumber, number + 1);
                } catch (NumberFormatException | DateTimeParseException e) {
                    // skip a damaged line instead of crashing
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read rentals.txt.");
        }
    }

    // queue.txt  ->  queueNumber|username|gameId|vip   (saved in queue order)
    void saveQueue() {
        try (PrintWriter writer = new PrintWriter(QUEUE_FILE)) {
            RentalRequest current = checkoutQueue.head;
            while (current != null) {
                writer.println(current.queueNumber + "|" + current.client.username + "|"
                    + current.game.gameId + "|" + current.vip);
                current = current.next;
            }
        } catch (FileNotFoundException e) {
            System.out.println("Warning: could not save queue.txt.");
        }
    }

    void loadQueue() {
        File file = new File(QUEUE_FILE);
        if (!file.exists()) {
            return;
        }

        try (Scanner reader = new Scanner(file)) {
            while (reader.hasNextLine()) {
                String line = reader.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|");
                if (parts.length != 4) {
                    continue;
                }

                User client = findUser(parts[1]);
                Game game = games.get(parts[2]);
                if (client == null || game == null) {
                    continue;
                }

                try {
                    int number = Integer.parseInt(parts[0]);
                    RentalRequest request = new RentalRequest(number, client, game);
                    request.vip = Boolean.parseBoolean(parts[3]);   // membership at request time
                    checkoutQueue.addRequest(request);              // same VIP/Regular rule as before
                    nextQueueNumber = Math.max(nextQueueNumber, number + 1);
                } catch (NumberFormatException e) {
                    // skip a damaged line instead of crashing
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read queue.txt.");
        }
    }

    // logs.txt  ->  timestamp|TYPE|message
    // ADMIN lines are the admin audit log. EVENT lines are client/system activity.
    // The file is only ever appended to, so it is a permanent history.
    void writeLog(String type, String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            writer.println(LocalDateTime.now().format(LOG_TIME_FORMAT) + "|" + type + "|" + message);
        } catch (IOException e) {
            System.out.println("Warning: could not write to logs.txt.");
        }
    }

    // Admin action: goes on the audit log Stack AND into logs.txt
    void logAdmin(String message) {
        auditLog.push(message);
        writeLog("ADMIN", message);
    }

    // Any other transaction (register, login, queue request, VIP upgrade...)
    void logEvent(String message) {
        writeLog("EVENT", message);
    }

    // Rebuilds the audit log Stack from logs.txt (oldest first, so the newest ends on top)
    void loadLogs() {
        File file = new File(LOG_FILE);
        if (!file.exists()) {
            return;
        }

        try (Scanner reader = new Scanner(file)) {
            while (reader.hasNextLine()) {
                String line = reader.nextLine().trim();
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3 && parts[1].equals("ADMIN")) {
                    auditLog.push(parts[2]);
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read logs.txt.");
        }
    }

    // =====================================================
    //  GAME INVENTORY (HashMap)
    // =====================================================

    String generateGameId(String platform) {
        if (platform.equals("PS5")) {
            ps5Count++;
            return String.format("P%03d", ps5Count);
        } else if (platform.equals("Xbox")) {
            xboxCount++;
            return String.format("X%03d", xboxCount);
        } else {
            nintendoCount++;
            return String.format("N%03d", nintendoCount);
        }
    }

    Game addGameToInventory(String title, String platform, double price, int copies) {
        String id = generateGameId(platform);
        Game game = new Game(id, title, platform, price, copies);
        games.put(id, game);
        return game;
    }

    void loadStartingGames() {
        addGameToInventory("Spider-Man 2", "PS5", 5.00, 2);
        addGameToInventory("Minecraft", "PS5", 4.00, 1);
        addGameToInventory("God of War", "PS5", 5.50, 1);

        addGameToInventory("Halo Infinite", "Xbox", 4.50, 1);
        addGameToInventory("Forza Horizon 5", "Xbox", 5.00, 2);
        addGameToInventory("Minecraft", "Xbox", 4.00, 1);

        addGameToInventory("Mario Kart 8", "Nintendo", 5.00, 2);
        addGameToInventory("Zelda: Tears of the Kingdom", "Nintendo", 6.00, 1);
        addGameToInventory("Animal Crossing", "Nintendo", 4.50, 1);
    }

    // platform: "ALL", "PS5", "Xbox" or "Nintendo"
    ArrayList<Game> getGameList(String platform) {
        ArrayList<Game> list = new ArrayList<>();
        for (Game game : games.values()) {
            if (platform.equals("ALL") || game.platform.equals(platform)) {
                list.add(game);
            }
        }
        return list;
    }

    void displayGames(ArrayList<Game> list, String title) {
        printHeader(title);

        if (list.isEmpty()) {
            System.out.println("No games found.");
            return;
        }

        for (Game game : list) {
            System.out.printf("%-5s | %-28s | %-8s | %6s | %s%n",
                game.gameId, game.title, game.platform,
                money(game.price), game.getStatus());
        }
    }

    // Shows the platform menu. Returns "ALL", "PS5", "Xbox", "Nintendo" or "BACK"
    String choosePlatform(String heading) {
        while (true) {
            printHeader(heading);
            System.out.println("[1] View All Games");
            System.out.println("[2] PS5 Games");
            System.out.println("[3] Xbox Games");
            System.out.println("[4] Nintendo Games");
            System.out.println("[0] Back");
            System.out.println(LINE);

            int choice = readNumber("Enter choice: ");

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
                System.out.println("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    // View one platform (or all games), then optionally search inside the list
    void viewGames(String platform) {
        ArrayList<Game> list = getGameList(platform);

        if (platform.equals("ALL")) {
            displayGames(list, "ALL GAMES");
        } else {
            displayGames(list, platform.toUpperCase() + " GAMES");
        }

        System.out.println();
        System.out.println("[1] Search within this list");
        System.out.println("[0] Back");
        int choice = readNumber("Enter choice: ");

        if (choice == 1) {
            searchGames(list);
        } else if (choice != 0) {
            System.out.println("Invalid choice.");
        }
    }

    // =====================================================
    //  LINEAR SEARCH
    // =====================================================

    void searchGames(ArrayList<Game> list) {
        String keyword = readText("Enter Game ID, title, or platform: ");

        if (keyword.isEmpty()) {
            System.out.println("Search text cannot be empty.");
            return;
        }

        ArrayList<Game> results = linearSearch(list, keyword);

        if (results.isEmpty()) {
            System.out.println("No matching game found. (Checked " + list.size() + " games)");
        } else {
            displayGames(results, "SEARCH RESULTS");
            System.out.println("(Checked " + list.size() + " games)");
        }
    }

    // Linear Search: check the games one by one from the first to the last
    ArrayList<Game> linearSearch(ArrayList<Game> list, String keyword) {
        ArrayList<Game> results = new ArrayList<>();
        String key = keyword.toLowerCase();

        for (int i = 0; i < list.size(); i++) {
            Game game = list.get(i);

            if (game.gameId.toLowerCase().equals(key)
                    || game.title.toLowerCase().contains(key)
                    || game.platform.toLowerCase().equals(key)) {
                results.add(game);
            }
        }

        return results;
    }

    // =====================================================
    //  INSERTION SORT
    // =====================================================

    void sortInventory() {
        String platform = choosePlatform("SORT GAME INVENTORY");
        if (platform.equals("BACK")) {
            return;
        }

        System.out.println();
        System.out.println("[1] Title A-Z");
        System.out.println("[2] Title Z-A");
        System.out.println("[3] Price Low-High");
        System.out.println("[4] Price High-Low");
        System.out.println("[0] Back");
        int choice = readNumber("Enter choice: ");

        if (choice == 0) {
            return;
        }
        if (choice < 1 || choice > 4) {
            System.out.println("Invalid choice.");
            return;
        }

        ArrayList<Game> list = getGameList(platform);
        insertionSort(list, choice);
        displayGames(list, "SORTED GAMES");
    }

    // Insertion Sort: take one game, shift bigger games to the right,
    // then insert the game in its correct position.
    void insertionSort(ArrayList<Game> list, int option) {
        for (int i = 1; i < list.size(); i++) {
            Game currentGame = list.get(i);
            int j = i - 1;

            while (j >= 0 && shouldMoveRight(list.get(j), currentGame, option)) {
                list.set(j + 1, list.get(j));
                j--;
            }

            list.set(j + 1, currentGame);
        }
    }

    // Returns true if 'left' must come after 'current' for the chosen sort option
    boolean shouldMoveRight(Game left, Game current, int option) {
        if (option == 1) {
            return left.title.compareToIgnoreCase(current.title) > 0;   // A-Z
        } else if (option == 2) {
            return left.title.compareToIgnoreCase(current.title) < 0;   // Z-A
        } else if (option == 3) {
            return left.price > current.price;                          // Low-High
        } else {
            return left.price < current.price;                          // High-Low
        }
    }

    // =====================================================
    //  CLIENT MENU
    // =====================================================

    void clientMenu(User client) {
        boolean loggedIn = true;

        while (loggedIn) {
            printHeader("Welcome, " + client.username + " (Client) ["
                        + (client.isVip() ? "VIP" : "REG") + "]");
            System.out.println("[1] Browse & Search Game Inventory");
            System.out.println("[2] Sort Game Inventory");
            System.out.println("[3] Place Game in Checkout Queue");
            System.out.println("[4] Cancel My Queue Position");
            System.out.println("[5] View My Rental History");
            System.out.println("[6] Upgrade to VIP Membership");
            System.out.println("[0] Logout");
            System.out.println(LINE);

            int choice = readNumber("Enter choice: ");

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
                System.out.println("You have been logged out.");
                loggedIn = false;
            } else {
                System.out.println("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    void browseGames() {
        boolean browsing = true;

        while (browsing) {
            printHeader("GAME INVENTORY");
            System.out.println("[1] View All Games");
            System.out.println("[2] PS5 Games");
            System.out.println("[3] Xbox Games");
            System.out.println("[4] Nintendo Games");
            System.out.println("[5] Search Game");
            System.out.println("[0] Back");
            System.out.println(LINE);

            int choice = readNumber("Enter choice: ");

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
                System.out.println("Invalid choice. Please enter a number from the menu.");
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

    void placeInQueue(User client) {
        String platform = choosePlatform("SELECT GAME PLATFORM");
        if (platform.equals("BACK")) {
            return;
        }

        ArrayList<Game> list = getGameList(platform);
        displayGames(list, "GAMES");

        String id = readText("\nEnter Game ID to rent (0 to go back): ");
        if (id.equals("0")) {
            return;
        }

        Game game = games.get(id.toUpperCase());

        if (game == null || !list.contains(game)) {
            System.out.println("Game ID not found in the list shown.");
            return;
        }
        if (!game.isAvailable()) {
            System.out.println("Sorry, this game is not available right now.");
            return;
        }
        if (checkoutQueue.hasRequest(client, game)) {
            System.out.println("You already have a pending request for this game.");
            return;
        }
        if (hasActiveRental(client, game)) {
            System.out.println("You are already renting this game.");
            return;
        }

        double price = getRentalPrice(client, game);
        System.out.print("\n" + game.title + " (" + game.platform + ") - "
                         + money(price) + " for " + RENTAL_DAYS + " days");
        if (client.isVip()) {
            System.out.print(" (VIP 10% discount applied)");
        }
        System.out.println();

        String confirm = readText("Join the checkout queue? (Y/N): ");
        if (!confirm.equalsIgnoreCase("Y")) {
            System.out.println("Request cancelled.");
            return;
        }

        RentalRequest request = new RentalRequest(nextQueueNumber, client, game);
        nextQueueNumber++;
        checkoutQueue.addRequest(request);
        saveQueue();
        logEvent("Queue request " + queueLabel(request.queueNumber) + ": "
                 + client.username + " - " + game.title);

        System.out.println("Request added to the checkout queue.");
        System.out.println("Queue Number: " + queueLabel(request.queueNumber));
        System.out.println("Your position: " + checkoutQueue.getPosition(request));
    }

    void cancelMyRequest(User client) {
        ArrayList<RentalRequest> myRequests = checkoutQueue.getRequestsOf(client);

        if (myRequests.isEmpty()) {
            System.out.println("You have no pending queue requests.");
            return;
        }

        printHeader("MY QUEUE REQUESTS");
        for (RentalRequest request : myRequests) {
            System.out.println(queueLabel(request.queueNumber) + " | "
                + request.game.title + " | Position: "
                + checkoutQueue.getPosition(request));
        }

        int number = readNumber("\nEnter queue number to cancel (0 to go back): ");
        if (number == 0) {
            return;
        }

        RentalRequest request = checkoutQueue.findByNumber(number);

        // A client can only cancel their own request
        if (request == null || request.client != client) {
            System.out.println("Invalid queue number. You can only cancel your own requests.");
            return;
        }

        checkoutQueue.removeRequest(request);
        saveQueue();
        logEvent("Client cancelled queue request " + queueLabel(number) + ": "
                 + client.username + " - " + request.game.title);
        System.out.println("Queue request " + queueLabel(number) + " has been cancelled.");
    }

    void viewHistory(User client) {
        if (client.rentalHistory.isEmpty()) {
            System.out.println("You have no rental history yet.");
            return;
        }

        printHeader("RENTAL HISTORY (Most Recent First)");

        // Read the stack from the top (most recent) down, without removing items
        int number = 1;
        for (int i = client.rentalHistory.size() - 1; i >= 0; i--) {
            Rental rental = client.rentalHistory.get(i);
            System.out.println(number + ". " + rental.game.title
                + " | Rental " + rentalLabel(rental.rentalNumber)
                + " | " + money(rental.amount)
                + " | Due: " + rental.dueDate.format(DATE_FORMAT)
                + " | " + rental.status);
            number++;
        }
    }

    void upgradeToVip(User client) {
        if (client.isVip()) {
            System.out.println("You are already a VIP member.");
            return;
        }

        printHeader("UPGRADE TO VIP");
        System.out.println("Price: $15/month");
        System.out.println("Rental Discount: 10%");
        System.out.println("Queue Priority: Yes");
        System.out.println("(Demo only: no real payment is processed.)");

        String confirm = readText("Upgrade now? (Y/N): ");
        if (confirm.equalsIgnoreCase("Y")) {
            client.membership = "VIP";
            saveAccounts();
            logEvent("Upgraded to VIP: " + client.username);
            System.out.println("Upgrade successful! You are now a VIP member.");
        } else {
            System.out.println("Upgrade cancelled.");
        }
    }

    // =====================================================
    //  ADMIN MENU
    // =====================================================

    void adminMenu(User admin) {
        boolean loggedIn = true;

        while (loggedIn) {
            printHeader("ADMIN CONTROL PANEL");
            System.out.println("[1] Process Checkout Queue");
            System.out.println("[2] Process Game Returns & Late Fees");
            System.out.println("[3] Manage Game Inventory");
            System.out.println("[4] View Overdue Items");
            System.out.println("[5] View Admin Audit Log");
            System.out.println("[6] View All Active Rentals");
            System.out.println("[0] Logout");
            System.out.println(LINE);

            int choice = readNumber("Enter choice: ");

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
                System.out.println("You have been logged out.");
                loggedIn = false;
            } else {
                System.out.println("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    // First request (from the front) whose game is available
    RentalRequest findNextEligibleRequest() {
        RentalRequest current = checkoutQueue.head;
        while (current != null) {
            if (current.game.isAvailable()) {
                return current;
            }
            current = current.next;
        }
        return null;
    }

    void processCheckoutQueue() {
        if (checkoutQueue.isEmpty()) {
            System.out.println("The checkout queue is empty.");
            return;
        }

        printHeader("CURRENT CHECKOUT QUEUE");
        checkoutQueue.printQueue();

        RentalRequest next = findNextEligibleRequest();
        if (next == null) {
            System.out.println("\nNo eligible request. All queued games are currently rented.");
            return;
        }

        printHeader("NEXT CHECKOUT");
        System.out.println("Customer: " + next.client.username);
        System.out.println("Membership: " + (next.vip ? "VIP" : "REGULAR"));
        System.out.println("Game: " + next.game.title);
        System.out.println("Queue Number: " + queueLabel(next.queueNumber));
        System.out.println("Rental Price: " + money(getRentalPrice(next.client, next.game)));
        System.out.println("[1] Approve Checkout");
        System.out.println("[2] Cancel Request");
        System.out.println("[0] Back");
        System.out.println(LINE);

        int choice = readNumber("Enter choice: ");

        if (choice == 1) {
            approveCheckout(next);
        } else if (choice == 2) {
            checkoutQueue.removeRequest(next);
            logAdmin("Cancelled request: " + next.client.username + " - " + next.game.title);
            saveQueue();
            System.out.println("Request " + queueLabel(next.queueNumber) + " has been cancelled.");
        } else if (choice != 0) {
            System.out.println("Invalid choice.");
        }
    }

    void approveCheckout(RentalRequest request) {
        LocalDate today = LocalDate.now();

        request.game.availableCopies--;

        Rental rental = new Rental(nextRentalNumber, request.client, request.game,
            today, today.plusDays(RENTAL_DAYS),
            getRentalPrice(request.client, request.game));
        nextRentalNumber++;

        allRentals.add(rental);
        activeRentals.add(rental);                   // add to the Min-Heap
        request.client.rentalHistory.push(rental);   // add to the client's Stack
        checkoutQueue.removeRequest(request);        // remove from the queue
        logAdmin("Processed checkout: " + request.client.username
                      + " - " + request.game.title);

        saveData();
        System.out.println("\nCheckout approved!");
        System.out.println("Rental " + rentalLabel(rental.rentalNumber) + " is now ACTIVE.");
        System.out.println("Amount: " + money(rental.amount));
        System.out.println("Due Date: " + rental.dueDate.format(DATE_FORMAT));
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

    void processReturn() {
        if (activeRentals.isEmpty()) {
            System.out.println("There are no active rentals to return.");
            return;
        }

        viewActiveRentals();

        int number = readNumber("\nEnter rental number to return (0 to go back): ");
        if (number == 0) {
            return;
        }

        Rental found = null;
        for (Rental rental : activeRentals) {
            if (rental.rentalNumber == number) {
                found = rental;
            }
        }

        if (found == null) {
            System.out.println("Invalid rental number. That game is not currently rented.");
            return;
        }

        // Demo: the Admin enters how many days late the game was returned
        int daysLate = -1;
        while (daysLate < 0) {
            daysLate = readNumber("Days returned after the due date (0 = on time): ");
            if (daysLate < 0) {
                System.out.println("Please enter 0 or a positive whole number.");
            }
        }

        double lateFee = daysLate * LATE_FEE_PER_DAY;

        activeRentals.remove(found);
        found.status = "COMPLETED";
        found.game.availableCopies++;

        logAdmin("Processed return: " + found.client.username + " - " + found.game.title);
        if (lateFee > 0) {
            logAdmin("Applied late fee " + money(lateFee) + ": " + found.client.username);
        }

        saveData();
        printHeader("RETURN PROCESSED");
        System.out.println("Customer: " + found.client.username);
        System.out.println("Game: " + found.game.title);
        System.out.println("Days Overdue: " + daysLate);
        System.out.println("Late Fee: " + money(lateFee));
        System.out.println("The game is now available again.");
    }

    void viewActiveRentals() {
        printHeader("ACTIVE RENTALS");

        if (activeRentals.isEmpty()) {
            System.out.println("There are no active rentals.");
            return;
        }

        for (Rental rental : getRentalsByDueDate()) {
            System.out.println("Rental " + rentalLabel(rental.rentalNumber));
            System.out.println("Customer: " + rental.client.username);
            System.out.println("Game: " + rental.game.title);
            System.out.println("Due Date: " + rental.dueDate.format(DATE_FORMAT));
            System.out.println("Status: " + rental.status);
            System.out.println();
        }
    }

    void viewOverdueItems() {
        printHeader("OVERDUE ITEMS (Earliest Due Date First)");

        if (activeRentals.isEmpty()) {
            System.out.println("There are no active rentals.");
            return;
        }

        LocalDate today = LocalDate.now();

        for (Rental rental : getRentalsByDueDate()) {
            long daysOverdue = ChronoUnit.DAYS.between(rental.dueDate, today);

            String status = "On time";
            if (daysOverdue > 0) {
                status = "OVERDUE by " + daysOverdue + " day(s), late fee so far "
                         + money(daysOverdue * LATE_FEE_PER_DAY);
            }

            System.out.println("Rental " + rentalLabel(rental.rentalNumber)
                + " | " + rental.client.username
                + " | " + rental.game.title
                + " | Due: " + rental.dueDate.format(DATE_FORMAT)
                + " | " + status);
        }

        Rental top = activeRentals.peek();   // the top of the Min-Heap
        System.out.println("\nHighest priority: Rental " + rentalLabel(top.rentalNumber)
                           + " (earliest due date)");
    }

    void viewAuditLog() {
        printHeader("ADMIN AUDIT LOG (Most Recent First)");

        if (auditLog.isEmpty()) {
            System.out.println("No actions have been recorded yet.");
            return;
        }

        int number = 1;
        for (int i = auditLog.size() - 1; i >= 0; i--) {
            System.out.println(number + ". " + auditLog.get(i));
            number++;
        }
    }

    // =====================================================
    //  MANAGE INVENTORY (ADMIN)
    // =====================================================

    void manageInventory() {
        boolean managing = true;

        while (managing) {
            printHeader("MANAGE INVENTORY");
            System.out.println("[1] View All Games");
            System.out.println("[2] PS5 Games");
            System.out.println("[3] Xbox Games");
            System.out.println("[4] Nintendo Games");
            System.out.println("[5] Add Game");
            System.out.println("[6] Remove Game");
            System.out.println("[7] Search Game");
            System.out.println("[8] Sort Game Inventory");
            System.out.println("[0] Back");
            System.out.println(LINE);

            int choice = readNumber("Enter choice: ");

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
                removeGame();
            } else if (choice == 7) {
                searchGames(getGameList("ALL"));
            } else if (choice == 8) {
                sortInventory();
            } else if (choice == 0) {
                managing = false;
            } else {
                System.out.println("Invalid choice. Please enter a number from the menu.");
            }
        }
    }

    void addGame() {
        printHeader("ADD GAME");

        String title = readText("Title: ");
        if (title.isEmpty()) {
            System.out.println("Title cannot be empty.");
            return;
        }
        if (title.contains("|")) {
            System.out.println("Title cannot contain the | symbol.");
            return;
        }

        System.out.println("Platform: [1] PS5  [2] Xbox  [3] Nintendo");
        int platformChoice = readNumber("Enter choice: ");
        String platform;
        if (platformChoice == 1) {
            platform = "PS5";
        } else if (platformChoice == 2) {
            platform = "Xbox";
        } else if (platformChoice == 3) {
            platform = "Nintendo";
        } else {
            System.out.println("Invalid platform choice.");
            return;
        }

        for (Game game : games.values()) {
            if (game.title.equalsIgnoreCase(title) && game.platform.equals(platform)) {
                System.out.println("That game already exists on " + platform + ".");
                return;
            }
        }

        double price = readAmount("Rental Price: $");
        if (price <= 0) {
            System.out.println("Invalid price. Enter a number greater than 0.");
            return;
        }

        int copies = readNumber("Available Copies: ");
        if (copies < 1) {
            System.out.println("Invalid number of copies. Enter 1 or more.");
            return;
        }

        Game game = addGameToInventory(title, platform, price, copies);
        logAdmin("Added game: " + title + " (" + platform + ")");

        saveGames();
        System.out.println("Game added successfully. Game ID: " + game.gameId);
    }

    void removeGame() {
        String platform = choosePlatform("REMOVE GAME");
        if (platform.equals("BACK")) {
            return;
        }

        ArrayList<Game> list = getGameList(platform);
        displayGames(list, "GAMES");

        String id = readText("\nEnter Game ID to remove (0 to go back): ");
        if (id.equals("0")) {
            return;
        }

        Game game = games.get(id.toUpperCase());

        if (game == null || !list.contains(game)) {
            System.out.println("Game ID not found in the list shown.");
            return;
        }

        // A game with an active rental cannot be removed
        if (gameHasActiveRental(game)) {
            System.out.println("Cannot remove: this game currently has an active rental.");
            return;
        }

        String confirm = readText("Remove " + game.title + " (" + game.platform + ")? (Y/N): ");
        if (!confirm.equalsIgnoreCase("Y")) {
            System.out.println("Removal cancelled.");
            return;
        }

        int cancelled = checkoutQueue.removeRequestsForGame(game);
        games.remove(game.gameId);
        logAdmin("Removed game: " + game.title + " (" + game.platform + ")");

        saveData();
        System.out.println("Game removed successfully.");
        if (cancelled > 0) {
            System.out.println(cancelled + " pending queue request(s) for this game were cancelled.");
        }
    }
}