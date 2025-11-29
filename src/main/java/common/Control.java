package common;

import com.mysql.cj.jdbc.Driver;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;
import static common.Model.IO;

public class Control<E extends Model> {
    private Connection connection_ = null;
    private final E model_;
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,62}$");

    public Control(E model) throws RuntimeException {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

            DriverManager.registerDriver(new Driver());
        } catch (Exception e) {
            System.err.println("Невозможно зарегистрировать драйвер.");
            throw new RuntimeException(e);
        }

        model_ = model;

        IO.println("Драйвер успешно зарегистрирован.");
        IO.println("Ваша модель: " + model.getDescribeMessage());
    }

    public void connectToLocalDb() {
        while (connection_ == null) {
            String dbName = readSafeDatabaseName();
            String dbUrl = "jdbc:mysql://localhost/" + dbName;

            String username = IO.readln("Имя пользователя: ");
            String password = IO.readln("Пароль: ");

            try {
                connection_ = DriverManager.getConnection(dbUrl, username, password);
            } catch (SQLException e) {
                IO.println("\nОшибка входа. Попробуйте еще раз.");
            }
        }

        IO.println("\nВы успешно подключились к БД.");
    }

    public void handleCommands() throws RuntimeException {
        do {
            model_.showCommands();
            model_.runCommandWithConnection(model_.readCommand(), connection_);
        } while (needContinue());
    }

    private String readSafeDatabaseName() {
        while (true) {
            String dbName = IO.readln("\nВведите название локальной схемы (БД) из MySQL: ").trim();

            if (dbName.isEmpty()) {
                IO.println("Название базы данных не может быть пустым.");
                continue;
            }

            if (DB_NAME_PATTERN.matcher(dbName).matches()) {
                return dbName;
            }

            IO.println(
                "Допустимы латинские буквы, цифры и '_'. Первый символ — буква, длина до 63."
            );
        }
    }

    private boolean needContinue() {
        while (true) {
            String answer = IO.readln("\nПродолжить? Y/n: ");

            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                return true;
            }
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")) {
                return false;
            }
        }
    }
}
