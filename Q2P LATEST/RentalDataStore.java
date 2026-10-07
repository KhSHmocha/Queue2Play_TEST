import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

// Reads and writes the system's text files without owning its business rules.
class RentalDataStore {
    private static final String DATA_FOLDER = "data";
    private static final String GAMES_FILE = DATA_FOLDER + "/games.txt";
    private static final String RENTALS_FILE = DATA_FOLDER + "/rentals.txt";
    private static final String QUEUE_FILE = DATA_FOLDER + "/queue.txt";
    private static final String LOG_FILE = DATA_FOLDER + "/logs.txt";
    private static final DateTimeFormatter LOG_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GameRentalSystem system;

    RentalDataStore(GameRentalSystem system) {
        this.system = system;
        prepareDataFolder();
    }

    private void prepareDataFolder() {
        new File(DATA_FOLDER).mkdirs();
        moveLegacyFile("games.txt", GAMES_FILE);
        moveLegacyFile("rentals.txt", RENTALS_FILE);
        moveLegacyFile("queue.txt", QUEUE_FILE);
        moveLegacyFile("logs.txt", LOG_FILE);
    }

    private void moveLegacyFile(String oldName, String newName) {
        File oldFile = new File(oldName);
        File newFile = new File(newName);
        if (oldFile.exists() && !newFile.exists()) {
            oldFile.renameTo(newFile);
        }
    }

    void saveData() {
        saveGames();
        saveRentals();
        saveQueue();
    }

    void saveGames() {
        try (PrintWriter writer = new PrintWriter(GAMES_FILE)) {
            writer.println("#COUNTERS|" + system.ps5Count + "|"
                + system.xboxCount + "|" + system.nintendoCount);
            for (Game game : system.games.values()) {
                writer.println(game.gameId + "|" + game.title + "|" + game.platform + "|"
                    + game.price + "|" + game.availableCopies);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Warning: could not save data/games.txt.");
        }
    }

    void loadGames() {
        File file = new File(GAMES_FILE);
        if (!file.exists()) {
            system.loadStartingGames();
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
                        system.ps5Count = Math.max(system.ps5Count, Integer.parseInt(parts[1]));
                        system.xboxCount = Math.max(system.xboxCount, Integer.parseInt(parts[2]));
                        system.nintendoCount =
                            Math.max(system.nintendoCount, Integer.parseInt(parts[3]));
                    } else if (parts.length == 5) {
                        Game game = new Game(parts[0], parts[1], parts[2],
                            Double.parseDouble(parts[3]), Integer.parseInt(parts[4]));
                        system.games.put(game.gameId, game);
                        system.updateCounterFromId(game.gameId);
                    }
                } catch (NumberFormatException e) {
                    // Ignore damaged records, as before.
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read data/games.txt. Loading the starting games instead.");
            system.loadStartingGames();
        }
    }

    void saveRentals() {
        try (PrintWriter writer = new PrintWriter(RENTALS_FILE)) {
            for (Rental rental : system.allRentals) {
                writer.println(rental.rentalNumber + "|" + rental.client.username + "|"
                    + rental.game.gameId + "|" + rental.game.title + "|" + rental.game.platform + "|"
                    + rental.checkoutDate + "|" + rental.dueDate + "|"
                    + rental.amount + "|" + rental.status + "|" + rental.quantity);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Warning: could not save data/rentals.txt.");
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
                // Older data/rentals.txt lines have 9 fields (no quantity); they count as 1 copy.
                if (parts.length != 9 && parts.length != 10) {
                    continue;
                }

                User client = system.findUser(parts[1]);
                if (client == null) {
                    continue;
                }

                try {
                    Game game = system.games.get(parts[2]);
                    if (game == null) {
                        game = new Game(parts[2], parts[3], parts[4], 0, 0);
                    }

                    int number = Integer.parseInt(parts[0]);
                    LocalDateTime checkout = parseRentalDateTime(parts[5], false);
                    LocalDateTime due = parseRentalDateTime(parts[6], true);
                    Rental rental = new Rental(number, client, game,
                        checkout, due, Double.parseDouble(parts[7]));
                    rental.status = parts[8];
                    if (parts.length == 10) {
                        rental.quantity = Math.max(1, Integer.parseInt(parts[9]));
                    }

                    system.allRentals.add(rental);
                    client.rentalHistory.push(rental);
                    if (rental.status.equals("ACTIVE")) {
                        system.activeRentals.add(rental);
                    }
                    system.nextRentalNumber = Math.max(system.nextRentalNumber, number + 1);
                } catch (NumberFormatException | DateTimeException e) {
                    // Ignore damaged records, as before.
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read data/rentals.txt.");
        }
    }

    // Accepts both new date+time records and older date-only data/rentals.txt records.
    private LocalDateTime parseRentalDateTime(String text, boolean endOfDay) {
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeException e) {
            LocalDate date = LocalDate.parse(text);
            return endOfDay ? date.atTime(23, 59) : date.atStartOfDay();
        }
    }

    void saveQueue() {
        try (PrintWriter writer = new PrintWriter(QUEUE_FILE)) {
            RentalRequest current = system.checkoutQueue.head;
            while (current != null) {
                writer.println(current.queueNumber + "|" + current.client.username + "|"
                    + current.game.gameId + "|" + current.vip + "|" + current.rentalDays
                    + "|" + current.quantity);
                current = current.next;
            }
        } catch (FileNotFoundException e) {
            System.out.println("Warning: could not save data/queue.txt.");
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
                // Older data/queue.txt lines have 4 fields (no rental days) or 5 (no quantity);
                // missing values keep their defaults (3 days, 1 copy).
                if (parts.length < 4 || parts.length > 6) {
                    continue;
                }

                User client = system.findUser(parts[1]);
                Game game = system.games.get(parts[2]);
                if (client == null || game == null) {
                    continue;
                }

                try {
                    int number = Integer.parseInt(parts[0]);
                    RentalRequest request = new RentalRequest(number, client, game);
                    request.vip = Boolean.parseBoolean(parts[3]);
                    if (parts.length >= 5) {
                        request.rentalDays = Math.max(1, Integer.parseInt(parts[4]));
                    }
                    if (parts.length == 6) {
                        request.quantity = Math.max(1, Integer.parseInt(parts[5]));
                    }
                    system.checkoutQueue.addRequest(request);
                    system.nextQueueNumber = Math.max(system.nextQueueNumber, number + 1);
                } catch (NumberFormatException e) {
                    // Ignore damaged records, as before.
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read data/queue.txt.");
        }
    }

    void writeLog(String type, String message) {
        writeLogRecord(LocalDateTime.now().format(LOG_TIME_FORMAT), type, message);
    }

    void writeLogRecord(String timestamp, String type, String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            writer.println(timestamp + "|" + type + "|" + message);
        } catch (IOException e) {
            System.out.println("Warning: could not write to data/logs.txt.");
        }
    }

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
                    system.auditLog.push(parts[0] + "|" + parts[2]);
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not read data/logs.txt.");
        }
    }
}
