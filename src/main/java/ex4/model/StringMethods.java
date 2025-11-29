package ex4.model;

import common.Model;
import static common.Model.IO;

import java.sql.Connection;

public class StringMethods extends Model {
    private String firstString = null;
    private String secondString = null;
    private static final int MIN_STRING_LENGTH = 50;

    @Override
    public String getDescribeMessage() {
        return "Модель исследования строковых методов";
    }

    @Override
    public void showCommands() {
        IO.println("\nДоступные команды:");
        IO.println("1. Вывести список таблиц из MySQL.");
        IO.println("2. Создать новую таблицу в MySQL.");
        IO.println("3. Возвращение подстроки по индексам, результат сохранить в MySQL с последующим выводом в консоль.");
        IO.println("4. Перевод строк в верхний и нижний регистры, результат сохранить в MySQL с последующим выводом в консоль.");
        IO.println("5. Поиск подстроки и определение окончания строки, результат сохранить в MySQL с последующим выводом в консоль.");
        IO.println("6. Сохранить все данные (вышеполученные результаты) из MySQL в Excel и вывести на экран.");
    }

    @Override
    public void runCommandWithConnection(String command, Connection connection)
        throws RuntimeException {
        switch (command) {
            case "1" -> showTables(connection);
            case "2" -> createTable(connection, "TEXT");
            case "3" -> extractSubstring(connection);
            case "4" -> convertStringCase(connection);
            case "5" -> searchSubstringAndCheckEnding(connection);
            case "6" -> saveToExcel(connection);
            default -> IO.println("Неверный номер команды. Попробуйте снова.");
        }
    }

    private void inputTwoStrings(Connection connection) throws RuntimeException {
        IO.println("\nВведите две строки (каждая не менее " + MIN_STRING_LENGTH + " символов):");
        
        firstString = readStringWithMinLength("первую", MIN_STRING_LENGTH);
        secondString = readStringWithMinLength("вторую", MIN_STRING_LENGTH);

        IO.println("\nСтроки успешно введены:");
        showStoredStrings();

        finishQuery(connection, firstString, "Первая строка: " + firstString);
        finishQuery(connection, secondString, "Вторая строка: " + secondString);
    }

    private String readStringWithMinLength(String ordinal, int minLength) {
        String input = "";
        while (input.length() < minLength) {
            input = IO.readln("Введите " + ordinal + " строку (не менее " + minLength + " символов): ");
            if (input.length() < minLength) {
                IO.println("Ошибка: строка должна содержать не менее " + minLength + " символов. Текущая длина: " + input.length());
            }
        }
        return input;
    }

    private boolean ensureStringsPrepared(Connection connection) throws RuntimeException {
        if (firstString == null || secondString == null) {
            IO.println("\nДля выполнения операции необходимо задать две строки.");
            inputTwoStrings(connection);
            return firstString != null && secondString != null;
        }

        while (true) {
            String answer = IO.readln("\nИспользовать ранее введенные строки? (Y/n): ").trim();

            if (answer.isEmpty()
                || answer.equalsIgnoreCase("y")
                || answer.equalsIgnoreCase("yes")
            ) {
                IO.println("\nТекущие строки:");
                showStoredStrings();
                return true;
            }

            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")) {
                inputTwoStrings(connection);
                return firstString != null && secondString != null;
            }

            IO.println("Введите 'Y' или 'N'.");
        }
    }

    private void showStoredStrings() {
        if (firstString != null && secondString != null) {
            IO.println("Первая строка: " + firstString);
            IO.println("Вторая строка: " + secondString);
        }
    }

    private void extractSubstring(Connection connection) throws RuntimeException {
        if (!ensureStringsPrepared(connection)) {
            return;
        }

        try {
            extractSubstringForString(connection, firstString, "первой", 1);
            extractSubstringForString(connection, secondString, "второй", 2);
        } catch (IndexOutOfBoundsException e) {
            IO.println("Ошибка: индекс выходит за границы строки.");
        } catch (NumberFormatException e) {
            IO.println("Ошибка: введен некорректный индекс.");
        }
    }

    private void extractSubstringForString(Connection connection, String str, String ordinal, int num) {
        IO.println("\nИзвлечение подстроки из " + ordinal + " строки:");
        int startIndex = Integer.parseInt(IO.readln("Введите начальный индекс подстроки для " + ordinal + " строки: "));
        int endIndex = Integer.parseInt(IO.readln("Введите конечный индекс подстроки для " + ordinal + " строки: "));

        if (startIndex < 0 || endIndex > str.length() || startIndex >= endIndex) {
            IO.println("Ошибка: введены некорректные границы индексов для " + ordinal + " строки.");
            return;
        }

        String substring = str.substring(startIndex, endIndex);
        IO.println("\n" + (num == 1 ? "Первая" : "Вторая") + " строка: '" + str + "'");
        IO.println("Извлеченная подстрока из " + ordinal + " строки: '" + substring + "'");
        IO.println("Индексы: с " + startIndex + " по " + endIndex);

        finishQuery(
            connection, substring,
            "Подстрока '" + substring + "' из " + ordinal + " строки '" + str + "' (индексы: " + startIndex + "-" + endIndex + ")"
        );
    }

    private void convertStringCase(Connection connection) throws RuntimeException {
        if (!ensureStringsPrepared(connection)) {
            return;
        }

        IO.println("\nПреобразование всех строк в верхний и нижний регистры:");
        convertCaseForString(connection, firstString, "первой");
        convertCaseForString(connection, secondString, "второй");
    }

    private void convertCaseForString(Connection connection, String str, String ordinal) {
        String lower = str.toLowerCase();
        String upper = str.toUpperCase();

        IO.println("\n" + (ordinal.equals("первой") ? "Первая" : "Вторая") + " строка: '" + str + "'");
        IO.println("В нижнем регистре: '" + lower + "'");
        IO.println("В верхнем регистре: '" + upper + "'");

        finishQuery(connection, lower, "Нижний регистр " + ordinal + " строки '" + str + "' -> '" + lower + "'");
        finishQuery(connection, upper, "Верхний регистр " + ordinal + " строки '" + str + "' -> '" + upper + "'");
    }

    private void searchSubstringAndCheckEnding(Connection connection) throws RuntimeException {
        if (!ensureStringsPrepared(connection)) {
            return;
        }

        String searchSubstring = IO.readln("\nВведите подстроку для поиска: ");

        searchSubstringInString(connection, firstString, "первой", 1, searchSubstring);
        searchSubstringInString(connection, secondString, "второй", 2, searchSubstring);
    }

    private void searchSubstringInString(Connection connection, String str, String ordinal, int num, String searchSubstring) {
        IO.println("\nПоиск подстроки в " + ordinal + " строке:");
        int foundIndex = str.indexOf(searchSubstring);
        boolean endsWith = str.endsWith(searchSubstring);

        String strName = num == 1 ? "Первая" : "Вторая";
        String strNum = " (строка " + num + ")";

        if (foundIndex == -1) {
            IO.println("Подстрока '" + searchSubstring + "' не найдена в " + ordinal + " строке '" + str + "'");
            IO.println(strName + " строка не заканчивается на указанную подстроку: false");

            finishQuery(connection, "не найдено" + strNum, "Подстрока '" + searchSubstring + "' отсутствует в " + ordinal + " строке '" + str + "'");
            finishQuery(connection, "false" + strNum, strName + " строка '" + str + "' не заканчивается на '" + searchSubstring + "'");
        } else {
            IO.println("Подстрока '" + searchSubstring + "' найдена в " + ordinal + " строке на позиции: " + foundIndex);
            IO.println(strName + " строка заканчивается на указанную подстроку: " + endsWith);

            finishQuery(connection, foundIndex + strNum, "Подстрока '" + searchSubstring + "' найдена в " + ordinal + " строке '" + str + "' на позиции " + foundIndex);
            finishQuery(connection, endsWith + strNum, strName + " строка '" + str + "' заканчивается на '" + searchSubstring + "': " + endsWith);
        }
    }
}

