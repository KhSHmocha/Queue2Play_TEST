import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

// Handles account registration, login, lookup, and account-file storage.
class AccountService {
    private static final String DATA_FOLDER = "data";
    private static final String ACCOUNTS_FILE = DATA_FOLDER + "/accounts.txt";
    private final GameRentalSystem system;

    AccountService(GameRentalSystem system) {
        this.system = system;
        prepareDataFolder();
    }

    private void prepareDataFolder() {
        new File(DATA_FOLDER).mkdirs();

        File oldFile = new File("accounts.txt");
        File newFile = new File(ACCOUNTS_FILE);
        if (oldFile.exists() && !newFile.exists()) {
            oldFile.renameTo(newFile);
        }
    }

    void loadAccounts() {
        system.users.add(new User("admin", "System Admin", "admin@store.com",
                                  "admin123", "ADMIN", "REGULAR"));

        File file = new File(ACCOUNTS_FILE);
        if (!file.exists()) {
            system.users.add(new User("alex", "Alex R. Reyes", "alex@email.com",
                                      "alex12345", "CLIENT", "VIP"));
            system.users.add(new User("juan", "Juan D. Cruz", "juan@email.com",
                                      "juan12345", "CLIENT", "REGULAR"));
            saveAccounts();
            return;
        }

        try (Scanner fileReader = new Scanner(file)) {
            while (fileReader.hasNextLine()) {
                String line = fileReader.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\|");
                if (parts.length == 6 && parts[4].equals("CLIENT")) {
                    String membership = parts[5].equals("VIP") ? "VIP" : "REGULAR";
                    system.users.add(new User(parts[0], parts[1], parts[2],
                                              parts[3], "CLIENT", membership));
                }
            }
        } catch (FileNotFoundException e) {
            system.ui.error("Could not read data/accounts.txt. Using default accounts only.");
        }
    }

    void saveAccounts() {
        prepareDataFolder();
        try (PrintWriter writer = new PrintWriter(ACCOUNTS_FILE)) {
            for (User user : system.users) {
                if (!user.isAdmin()) {
                    writer.println(user.username + "|" + user.fullName + "|"
                        + user.email + "|" + user.password + "|"
                        + user.role + "|" + user.membership);
                }
            }
        } catch (FileNotFoundException e) {
            system.ui.error("Could not save data/accounts.txt.");
        }
    }

    User findUser(String username) {
        for (User user : system.users) {
            if (user.username.equalsIgnoreCase(username)) return user;
        }
        return null;
    }

    User findUserByLogin(String login) {
        for (User user : system.users) {
            if (user.username.equalsIgnoreCase(login)
                    || user.email.equalsIgnoreCase(login)) {
                return user;
            }
        }
        return null;
    }

    boolean emailExists(String email) {
        for (User user : system.users) {
            if (user.email.equalsIgnoreCase(email)) return true;
        }
        return false;
    }

    void register() {
        String fullName = "";
        String username = "";
        String email = "";
        String password = "";
        int step = 1;

        while (true) {
            if (step == 1) {
                system.ui.clearScreen();
                system.ui.showRegisterFullNameScreen();
                System.out.print("                            > ");
                String value = system.readText("");

                if (value.equals("0")) return;

                // Required format: First Name + M.I. + Surname.
                // Multi-word first names and surnames are allowed.
                if (!value.matches("^[A-Za-z]+(?:\\s+[A-Za-z]+)*\\s+[A-Za-z]\\.\\s+[A-Za-z]+(?:\\s+[A-Za-z]+)*$")) {
                    System.out.println("\n                 Invalid full name!");
                    System.out.println("        Use: First Name + M.I. + Surname");
                    System.out.println("              Example: Juan D. Cruz");
                    system.ui.waitForEnter();
                    continue;
                }

                fullName = value;
                step = 2;
            }

            else if (step == 2) {
                system.ui.clearScreen();
                system.ui.showRegisterUsernameScreen(fullName);
                System.out.print("                            > ");
                String value = system.readText("");

                if (value.equals("0")) {
                    step = 1;
                    continue;
                }
                if (value.isEmpty()) {
                    system.ui.error("Username cannot be empty.");
                    system.ui.waitForEnter();
                    continue;
                }
                if (value.contains(" ") || value.contains("|")) {
                    system.ui.error("Username cannot contain spaces or the | symbol.");
                    system.ui.waitForEnter();
                    continue;
                }
                if (findUser(value) != null) {
                    system.ui.error("Username is already taken. Please try another one.");
                    system.ui.waitForEnter();
                    continue;
                }

                username = value;
                step = 3;
            }

            else if (step == 3) {
                system.ui.clearScreen();
                system.ui.showRegisterEmailScreen(fullName, username);
                System.out.print("                            > ");
                String value = system.readText("");

                if (value.equals("0")) {
                    step = 2;
                    continue;
                }
                if (value.isEmpty()) {
                    system.ui.error("Email cannot be empty.");
                    system.ui.waitForEnter();
                    continue;
                }
                if (!value.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    system.ui.error("Invalid email format. Example: juan@gmail.com");
                    system.ui.waitForEnter();
                    continue;
                }
                if (emailExists(value)) {
                    system.ui.error("Email is already registered. Please use another email.");
                    system.ui.waitForEnter();
                    continue;
                }

                email = value;
                step = 4;
            }

            else if (step == 4) {
                system.ui.clearScreen();
                system.ui.showRegisterPasswordScreen(fullName, username, email);
                System.out.print("                            > ");
                String value = system.readPassword("");

                if (value.equals("0")) {
                    step = 3;
                    continue;
                }
                if (value.length() < 8) {
                    system.ui.error("Password must be at least 8 characters.");
                    system.ui.waitForEnter();
                    continue;
                }
                if (value.contains("|")) {
                    system.ui.error("Password cannot contain the | symbol.");
                    system.ui.waitForEnter();
                    continue;
                }

                System.out.println("-------------------- CONFIRM PASSWORD ---------------------");
                System.out.print("                            > ");
                String confirm = system.readPassword("");

                if (confirm.equals("0")) continue;
                if (!confirm.equals(value)) {
                    system.ui.error("Passwords do not match. Please try again.");
                    system.ui.waitForEnter();
                    continue;
                }

                password = value;
                break;
            }
        }

        system.ui.loading("Creating account");
        system.users.add(new User(username, fullName, email, password, "CLIENT", "REGULAR"));
        saveAccounts();
        system.logEvent("Registered account: " + username);

        system.ui.clearScreen();
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|                   ACCOUNT CREATED!                       |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        System.out.println("|                                                          |");
        System.out.println("|              Your account is ready to use.               |");
        System.out.println("|                                                          |");
        System.out.println("+==========================================================+");
        system.ui.waitForEnter();
    }

    void login() {
        while (true) {
            system.ui.clearScreen();
            system.ui.showLoginUsernameScreen();
            System.out.print("                            > ");
            String login = system.readText("");

            if (login.equals("0")) return;
            if (login.isEmpty()) {
                system.ui.error("Username or email cannot be empty.");
                system.ui.waitForEnter();
                continue;
            }

            User user = findUserByLogin(login);
            if (user == null) {
                system.logEvent("Failed login attempt for account: " + login);
                system.ui.error("Username or email was not found.");
                system.ui.waitForEnter();
                continue;
            }

            boolean backToLoginName = false;
            while (true) {
                system.ui.clearScreen();
                system.ui.showLoginPasswordScreen(user.username);
                System.out.print("                            > ");
                String password = system.readPassword("");

                if (password.equals("0")) {
                    backToLoginName = true;
                    break;
                }

                system.ui.loading("Verifying account");

                if (!user.password.equals(password)) {
                    system.logEvent("Incorrect password attempt for: " + user.username);
                    system.ui.error("Incorrect password. Please try again.");
                    system.ui.waitForEnter();
                    continue;
                }

                system.logEvent("Login: " + user.username + " (" + user.role + ")");
                system.ui.success("LOGIN SUCCESSFUL - Welcome, " + user.fullName + "!");
                system.ui.pause(500);
                system.ui.loading("Loading dashboard");

                if (user.isAdmin()) system.adminMenu(user);
                else system.clientMenu(user);
                return;
            }

            if (!backToLoginName) return;
        }
    }
}
