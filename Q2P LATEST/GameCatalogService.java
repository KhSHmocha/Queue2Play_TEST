import java.util.ArrayList;

// Owns inventory lookup and the search/sort algorithms used by the menus.
class GameCatalogService {
    private final GameRentalSystem system;

    GameCatalogService(GameRentalSystem system) {
        this.system = system;
    }

    String generateGameId(String platform) {
        if (platform.equals("PS5")) {
            system.ps5Count++;
            return String.format("P%03d", system.ps5Count);
        } else if (platform.equals("Xbox")) {
            system.xboxCount++;
            return String.format("X%03d", system.xboxCount);
        } else {
            system.nintendoCount++;
            return String.format("N%03d", system.nintendoCount);
        }
    }

    Game addGame(String title, String platform, double price, int copies) {
        String id = generateGameId(platform);
        Game game = new Game(id, title, platform, price, copies);
        system.games.put(id, game);
        return game;
    }

    void loadStartingGames() {
        addGame("Spider-Man 2", "PS5", 5.00, 2);
        addGame("Minecraft", "PS5", 4.00, 1);
        addGame("God of War", "PS5", 5.50, 1);
        addGame("Halo Infinite", "Xbox", 4.50, 1);
        addGame("Forza Horizon 5", "Xbox", 5.00, 2);
        addGame("Minecraft", "Xbox", 4.00, 1);
        addGame("Mario Kart 8", "Nintendo", 5.00, 2);
        addGame("Zelda: Tears of the Kingdom", "Nintendo", 6.00, 1);
        addGame("Animal Crossing", "Nintendo", 4.50, 1);
    }

    ArrayList<Game> getGameList(String platform) {
        ArrayList<Game> list = new ArrayList<>();
        for (Game game : system.games.values()) {
            if (platform.equals("ALL") || game.platform.equals(platform)) {
                list.add(game);
            }
        }
        return list;
    }

    // Linear search checks each game in the selected list for a matching ID, title, or platform.
    ArrayList<Game> linearSearch(ArrayList<Game> list, String keyword) {
        ArrayList<Game> results = new ArrayList<>();
        String key = keyword.toLowerCase();

        for (Game game : list) {
            if (game.gameId.toLowerCase().equals(key)
                    || game.title.toLowerCase().contains(key)
                    || game.platform.toLowerCase().equals(key)) {
                results.add(game);
            }
        }
        return results;
    }

    // Insertion sort shifts games until each one is placed at its selected title or price position.
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

    private boolean shouldMoveRight(Game left, Game current, int option) {
        if (option == 1) {
            return left.title.compareToIgnoreCase(current.title) > 0;
        } else if (option == 2) {
            return left.title.compareToIgnoreCase(current.title) < 0;
        } else if (option == 3) {
            return left.price > current.price;
        } else {
            return left.price < current.price;
        }
    }
}
