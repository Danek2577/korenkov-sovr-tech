package ex4.model;

import common.Model;
import common.Model.StructuredResult;
import common.Model.StructuredResultBuilder;
import common.Model.TableBlueprint;
import static common.Model.IO;

import java.sql.Connection;
import java.util.Set;

public class StringMethods extends Model {
    private String firstString = null;
    private String secondString = null;
    private static final int MIN_STRING_LENGTH = 50;
    private static final TableBlueprint STRING_TABLE_BLUEPRINT = TableBlueprint.builder()
        .addColumn("operation_code", "varchar(32) NOT NULL")
        .addColumn("line_label", "varchar(32)")
        .addColumn("first_value", "TEXT NOT NULL")
        .addColumn("second_value", "TEXT")
        .addColumn("result_value", "TEXT")
        .addColumn("start_index", "INT")
        .addColumn("end_index", "INT")
        .addColumn("found_index", "INT")
        .addColumn("ends_with", "TINYINT(1)")
        .addColumn("lower_case", "TEXT")
        .addColumn("upper_case", "TEXT")
        .addColumn("operation_details", "TEXT")
        .build();
    private static final Set<String> REQUIRED_COLUMNS = STRING_TABLE_BLUEPRINT.columnNames();

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
            case "2" -> createTable(connection, STRING_TABLE_BLUEPRINT);
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

        saveStringSnapshot(connection, "первая", firstString);
        saveStringSnapshot(connection, "вторая", secondString);
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

        saveSubstringResult(
            connection,
            str,
            substring,
            startIndex,
            endIndex,
            ordinal,
            num
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

        saveCaseResult(connection, str, ordinal, lower, upper);
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

            saveSearchResult(
                connection,
                str,
                ordinal,
                num,
                searchSubstring,
                foundIndex,
                false
            );
        } else {
            IO.println("Подстрока '" + searchSubstring + "' найдена в " + ordinal + " строке на позиции: " + foundIndex);
            IO.println(strName + " строка заканчивается на указанную подстроку: " + endsWith);

            saveSearchResult(
                connection,
                str,
                ordinal,
                num,
                searchSubstring,
                foundIndex,
                endsWith
            );
        }
    }

    private void saveStringSnapshot(Connection connection, String label, String value)
        throws RuntimeException
    {
        StructuredResult result = baseResultBuilder(
            "Строка (" + label + ") сохранена",
            "Значение " + label + " строки: " + value,
            "INPUT",
            label,
            value
        )
            .put("result_value", value)
            .build();

        finishStructuredQuery(connection, result);
    }

    private void saveSubstringResult(
        Connection connection,
        String original,
        String substring,
        int startIndex,
        int endIndex,
        String ordinal,
        int lineNum
    ) throws RuntimeException {
        String description = "Подстрока '" + substring + "' из " + ordinal + " строки '" + original
            + "' (индексы: " + startIndex + "-" + endIndex + ")";

        StructuredResult result = baseResultBuilder(
            "Подстрока (" + ordinal + ") = '" + substring + "'",
            description,
            "SUBSTRING",
            ordinal,
            original
        )
            .put("result_value", substring)
            .put("start_index", startIndex)
            .put("end_index", endIndex)
            .put("operation_details", "Строка " + lineNum + ", индексы " + startIndex + "-" + endIndex)
            .build();

        finishStructuredQuery(connection, result);
    }

    private void saveCaseResult(
        Connection connection,
        String original,
        String ordinal,
        String lower,
        String upper
    ) throws RuntimeException {
        String description = "Регистр " + ordinal + " строки '" + original + "': нижний '" + lower
            + "', верхний '" + upper + "'";

        StructuredResult result = baseResultBuilder(
            "Регистры (" + ordinal + ")",
            description,
            "CASE",
            ordinal,
            original
        )
            .put("lower_case", lower)
            .put("upper_case", upper)
            .put("result_value", lower)
            .put("operation_details", "lower/upper для " + ordinal + " строки")
            .build();

        finishStructuredQuery(connection, result);
    }

    private void saveSearchResult(
        Connection connection,
        String original,
        String ordinal,
        int lineNum,
        String searchSubstring,
        int foundIndex,
        boolean endsWith
    ) throws RuntimeException {
        String foundText = foundIndex >= 0
            ? "найдена на позиции " + foundIndex
            : "не найдена";
        String description = "Подстрока '" + searchSubstring + "' " + foundText + " в " + ordinal + " строке.";

        StructuredResultBuilder builder = baseResultBuilder(
            "Поиск (" + ordinal + "): " + foundText,
            description,
            "SEARCH",
            ordinal,
            original
        )
            .put("second_value", searchSubstring)
            .put("found_index", foundIndex >= 0 ? foundIndex : null)
            .put("ends_with", endsWith)
            .put("operation_details", "Строка " + lineNum + ": конец совпадает — " + endsWith);

        if (foundIndex >= 0) {
            builder.put("result_value", "найдено");
        } else {
            builder.put("result_value", "не найдено");
        }

        finishStructuredQuery(connection, builder.build());
    }

    private StructuredResultBuilder baseResultBuilder(
        String preview,
        String description,
        String operationCode,
        String lineLabel,
        String sourceValue
    ) {
        return structuredResultBuilder()
            .preview(preview)
            .description(description)
            .requiredColumns(REQUIRED_COLUMNS)
            .put("operation_code", operationCode)
            .put("line_label", lineLabel)
            .put("first_value", sourceValue);
    }
}

