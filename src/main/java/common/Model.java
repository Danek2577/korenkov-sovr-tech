package common;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class Model {
    protected final ArrayList<SavedQuery> savedQueries_ = new ArrayList<>();
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,62}$");
    private static final Set<String> ALLOWED_RESULT_COLUMN_TYPES = Set.of(
        "CHAR", "VARCHAR", "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT",
        "DOUBLE", "FLOAT", "DECIMAL", "NUMERIC",
        "INT", "INTEGER", "BIGINT", "SMALLINT", "MEDIUMINT", "TINYINT", "BIT"
    );
    private static final Set<String> NUMERIC_COLUMN_TYPES = Set.of(
        "BIGINT", "INT", "INTEGER", "SMALLINT", "MEDIUMINT", "TINYINT"
    );

    public static final class TableBlueprint {
        private final LinkedHashMap<String, String> columns;

        private TableBlueprint(LinkedHashMap<String, String> columns) {
            if (columns == null || columns.isEmpty()) {
                throw new IllegalArgumentException("Необходимо определить хотя бы один столбец.");
            }
            this.columns = columns;
        }

        public LinkedHashMap<String, String> columns() {
            return new LinkedHashMap<>(columns);
        }

        public Set<String> columnNames() {
            return Collections.unmodifiableSet(columns.keySet());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private final LinkedHashMap<String, String> columns = new LinkedHashMap<>();

            public Builder addColumn(String name, String definition) {
                columns.put(name, definition);
                return this;
            }

            public TableBlueprint build() {
                return new TableBlueprint(new LinkedHashMap<>(columns));
            }
        }
    }

    protected record StructuredResult(
        String preview,
        String description,
        LinkedHashMap<String, Object> columnValues,
        Set<String> requiredColumns
    ) { }

    public static class StructuredResultBuilder {
        private final LinkedHashMap<String, Object> columnValues = new LinkedHashMap<>();
        private String preview;
        private String description;
        private Set<String> requiredColumns = Set.of("result");

        public StructuredResultBuilder preview(String preview) {
            this.preview = preview;
            return this;
        }

        public StructuredResultBuilder description(String description) {
            this.description = description;
            return this;
        }

        public StructuredResultBuilder requiredColumns(Set<String> requiredColumns) {
            if (requiredColumns != null && !requiredColumns.isEmpty()) {
                this.requiredColumns = requiredColumns;
            }
            return this;
        }

        public StructuredResultBuilder put(String column, Object value) {
            columnValues.put(column, value);
            return this;
        }

        public StructuredResult build() {
            if (preview == null || preview.isBlank()) {
                throw new IllegalStateException("Не задан краткий результат для сохранения.");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalStateException("Не задано описание операции.");
            }
            if (columnValues.isEmpty()) {
                throw new IllegalStateException("Не переданы значения столбцов для сохранения.");
            }

            Set<String> safeRequiredColumns = requiredColumns == null
                ? Set.of("result")
                : Set.copyOf(requiredColumns);

            return new StructuredResult(
                preview,
                description,
                new LinkedHashMap<>(columnValues),
                safeRequiredColumns
            );
        }
    }

    protected StructuredResultBuilder structuredResultBuilder() {
        return new StructuredResultBuilder();
    }

    public abstract String getDescribeMessage();
    public abstract void showCommands();

    public String readCommand() {
        return IO.readln("\nВведите номер команды: ");
    }

    static class IO {
        private static final java.util.Scanner scanner = new java.util.Scanner(System.in);

        public static void println(String message) {
            System.out.println(message);
        }

        public static String readln(String prompt) {
            System.out.print(prompt);
            return scanner.nextLine();
        }
    }

    public abstract void runCommandWithConnection(String command, Connection connection);

    void showTables(Connection connection) throws RuntimeException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("SHOW TABLES")) {
            IO.println("\nДоступные таблицы:");

            while (resultSet.next()) {
                IO.println("- " + resultSet.getString(1)); // `1`: Table name.
            }
        } catch (SQLException e) {
            System.err.println("Невозможно выполнить запрос: `SHOW TABLES`.");
            throw new RuntimeException(e);
        }
    }

    private String readIdentifier(String prompt) {
        while (true) {
            String candidate = IO.readln(prompt).trim();

            if (candidate.isEmpty()) {
                IO.println("Название не может быть пустым.");
                continue;
            }

            if (!IDENTIFIER_PATTERN.matcher(candidate).matches()) {
                IO.println(
                    "Допустимы латинские буквы, цифры и '_'. Первый символ — буква, длина до 63."
                );
                continue;
            }

            return candidate;
        }
    }

    private void validateColumnName(String name) {
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Название столбца `" + name + "` должно содержать латинские символы и цифры и начинаться с буквы."
            );
        }
    }

    void createTable(Connection connection, String resultType) {
        String tableName = readIdentifier("\nВведите название новой таблицы: ");
        String query = "CREATE TABLE IF NOT EXISTS `" + tableName + "` "
            + "(id int AUTO_INCREMENT PRIMARY KEY, result " + resultType + ")";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(query);
        } catch (SQLException e) {
            System.err.println("Невозможно создать таблицу.");
            throw new RuntimeException(e);
        }

        IO.println("Таблица создана.");
    }

    void createTable(Connection connection, TableBlueprint blueprint) {
        String tableName = readIdentifier("\nВведите название новой таблицы: ");
        StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS `")
            .append(tableName)
            .append("` (id int AUTO_INCREMENT PRIMARY KEY");

        for (Map.Entry<String, String> column : blueprint.columns().entrySet()) {
            String columnName = column.getKey();
            validateColumnName(columnName);
            query.append(", `")
                .append(columnName)
                .append("` ")
                .append(column.getValue());
        }

        query.append(")");

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(query.toString());
        } catch (SQLException e) {
            System.err.println("Невозможно создать таблицу по шаблону.");
            throw new RuntimeException(e);
        }

        IO.println("Таблица создана.");
    }

    private String chooseTableToSave(Connection connection, String result) throws RuntimeException {
        ArrayList<String> possibleTablesToSave = findCorrectTables(connection);

        return chooseTableFromList(
            possibleTablesToSave,
            "Нет доступных таблиц для сохранения.",
            "\nВыберите таблицу для сохранения результата `" + result + "`:"
        );
    }

    private String chooseTableWithColumns(
        Connection connection,
        String result,
        Set<String> requiredColumns
    ) throws RuntimeException {
        ArrayList<String> possibleTablesToSave = findTablesWithColumns(connection, requiredColumns);

        return chooseTableFromList(
            possibleTablesToSave,
            "Нет подходящих таблиц для сохранения структурированных данных.",
            "\nВыберите таблицу для сохранения результата `" + result + "`:"
        );
    }

    private String chooseTableFromList(
        ArrayList<String> tables,
        String emptyMessage,
        String header
    ) {
        if (tables.isEmpty()) {
            IO.println(emptyMessage);
            return null;
        }

        IO.println(header);
        for (int i = 0; i < tables.size(); ++i) {
            IO.println((i + 1) + ". " + tables.get(i));
        }

        while (true) {
            String answer = IO.readln("\nВведите номер таблицы: ").trim();
            try {
                int index = Integer.parseInt(answer) - 1;
                if (index >= 0 && index < tables.size()) {
                    return tables.get(index);
                }
                IO.println("Ошибка: номер таблицы вне допустимого диапазона.");
            } catch (NumberFormatException e) {
                IO.println("Ошибка: необходимо ввести целое число из списка.");
            }
        }
    }

    private ArrayList<String> findAllTables(Connection connection) throws RuntimeException {
        ArrayList<String> tablesList = new ArrayList<>();

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (
                ResultSet tables =
                    metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})
            ) {
                while (tables.next()) {
                    tablesList.add(tables.getString("TABLE_NAME"));
                }
            }
            return tablesList;
        } catch (SQLException e) {
            System.err.println("Невозможно получить список таблиц.");
            throw new RuntimeException(e);
        }
    }

    private ArrayList<String> findCorrectTables(Connection connection) throws RuntimeException {
        ArrayList<String> correctTables = new ArrayList<>();

        try {
            DatabaseMetaData metaData = connection.getMetaData();

            try (
                ResultSet tables =
                    metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})
            ) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");

                    if (isTableCorrect(metaData, tableName)) {
                        correctTables.add(tableName);
                    }
                }
            }

            return correctTables;
        } catch (SQLException e) {
            System.err.println("Невозможен поиск подходящих таблиц.");
            throw new RuntimeException(e);
        }
    }

    private ArrayList<String> findTablesWithColumns(
        Connection connection,
        Set<String> requiredColumns
    ) throws RuntimeException {
        ArrayList<String> tablesList = new ArrayList<>();

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (
                ResultSet tables =
                    metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})
            ) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");

                    if (hasCorrectPrimaryKey(metaData, tableName)
                        && hasAllColumns(metaData, tableName, requiredColumns)
                    ) {
                        tablesList.add(tableName);
                    }
                }
            }

            return tablesList;
        } catch (SQLException e) {
            System.err.println("Невозможно получить список таблиц.");
            throw new RuntimeException(e);
        }
    }

    private boolean isTableCorrect(DatabaseMetaData metaData, String tableName)
        throws RuntimeException
    {
        return hasCorrectPrimaryKey(metaData, tableName)
            && hasCorrectResultColumn(metaData, tableName);
    }

    private boolean hasCorrectPrimaryKey(DatabaseMetaData metaData, String tableName)
        throws RuntimeException
    {
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, tableName)) {
            while (primaryKeys.next()) {
                String pkColumn = primaryKeys.getString("COLUMN_NAME");
                try (ResultSet columns = metaData.getColumns(null, null, tableName, pkColumn)) {
                    if (columns.next()) {
                        String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
                        String typeName = columns.getString("TYPE_NAME");
                        if ("YES".equalsIgnoreCase(isAutoIncrement) && isNumericType(typeName)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } catch (SQLException e) {
            System.err.println("Невозможно получить первичные ключи.");
            throw new RuntimeException(e);
        }
    }

    private boolean hasCorrectResultColumn(DatabaseMetaData metaData, String tableName) {
        try (ResultSet columns = metaData.getColumns(null, null, tableName, "result")) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String typeName = columns.getString("TYPE_NAME");

                if (isAllowedResultColumn(typeName) && !isPrimaryKeyColumn(metaData, tableName, columnName)) {
                    return true;
                }
            }

            return false;
        } catch (SQLException e) {
            System.err.println("Невозможно получить нужные столбцы.");
            throw new RuntimeException(e);
        }
    }

    private boolean hasAllColumns(
        DatabaseMetaData metaData,
        String tableName,
        Set<String> requiredColumns
    ) throws RuntimeException {
        if (requiredColumns == null || requiredColumns.isEmpty()) {
            return true;
        }

        try {
            for (String column : requiredColumns) {
                try (ResultSet columns = metaData.getColumns(null, null, tableName, column)) {
                    if (!columns.next()) {
                        return false;
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Невозможно проверить структуру таблицы.");
            throw new RuntimeException(e);
        }
    }

    private boolean isPrimaryKeyColumn(
        DatabaseMetaData metaData, String tableName, String columnName
    ) {
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, tableName)) {
            while (primaryKeys.next()) {
                if (columnName.equals(primaryKeys.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean isAllowedResultColumn(String typeName) {
        return typeName != null && ALLOWED_RESULT_COLUMN_TYPES.contains(typeName.toUpperCase());
    }

    private boolean isNumericType(String typeName) {
        return typeName != null && NUMERIC_COLUMN_TYPES.contains(typeName.toUpperCase());
    }

    private String saveToTable(Connection connection, String tableToSave, String result) {
        String query = "INSERT INTO `" + tableToSave + "` (result) VALUES (?)";

        try (
            PreparedStatement statement =
                connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setString(1, result);
            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException();
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getString(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Не удалось сохранить значение в таблицу.");
            System.err.println("Сообщение: " + e.getMessage());
        }

        return null;
    }

    private String saveStructuredRow(
        Connection connection,
        String tableToSave,
        LinkedHashMap<String, Object> columnValues
    ) {
        if (columnValues.isEmpty()) {
            return null;
        }

        columnValues.keySet().forEach(this::validateColumnName);

        StringBuilder columnsBuilder = new StringBuilder();
        StringBuilder placeholdersBuilder = new StringBuilder();
        boolean first = true;

        for (String column : columnValues.keySet()) {
            if (!first) {
                columnsBuilder.append(", ");
                placeholdersBuilder.append(", ");
            }
            columnsBuilder.append("`").append(column).append("`");
            placeholdersBuilder.append("?");
            first = false;
        }

        String query = "INSERT INTO `" + tableToSave + "` (" + columnsBuilder + ") VALUES ("
            + placeholdersBuilder + ")";

        try (
            PreparedStatement statement =
                connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
        ) {
            int index = 1;
            for (Object value : columnValues.values()) {
                if (value == null) {
                    statement.setObject(index++, null);
                } else if (value instanceof Boolean bool) {
                    statement.setBoolean(index++, bool);
                } else if (value instanceof Integer integer) {
                    statement.setInt(index++, integer);
                } else if (value instanceof Long longValue) {
                    statement.setLong(index++, longValue);
                } else if (value instanceof Double doubleValue) {
                    statement.setDouble(index++, doubleValue);
                } else if (value instanceof String str) {
                    statement.setString(index++, str);
                } else {
                    statement.setObject(index++, value);
                }
            }

            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException();
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getString(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Не удалось сохранить структурированные данные.");
            System.err.println("Сообщение: " + e.getMessage());
        }

        return null;
    }

    void finishQuery(Connection connection, String result, String query) throws RuntimeException {
        String tableToSave = chooseTableToSave(connection, result);

        if (tableToSave == null) {
            return;
        }

        String id = saveToTable(connection, tableToSave, result);

        savedQueries_.add(new SavedQuery(id, query, tableToSave));

        IO.println("\nЗначение сохранено.");
    }

    protected void finishStructuredQuery(Connection connection, StructuredResult structuredResult)
        throws RuntimeException
    {
        String tableToSave = chooseTableWithColumns(
            connection,
            structuredResult.preview(),
            structuredResult.requiredColumns()
        );

        if (tableToSave == null) {
            return;
        }

        String id = saveStructuredRow(connection, tableToSave, structuredResult.columnValues());

        savedQueries_.add(new SavedQuery(id, structuredResult.description(), tableToSave));

        IO.println("\nЗначение сохранено.");
    }

    private void checkQueries() {
        if (savedQueries_.isEmpty()) {
            IO.println("\nНет данных, полученных в ходе данной сессии.");
        } else {
            IO.println("\nДанные, полученные в ходе текущей сессии:");
            for (SavedQuery query : savedQueries_) {
                query.showInfo();
            }
        }
    }

    void saveToExcel(Connection connection) {
        checkQueries();

        String tableName = chooseTableFromList(
            findAllTables(connection),
            "Нет таблиц для экспорта.",
            "\nВыберите таблицу, которую вы хотите сохранить в Excel:"
        );

        if (tableName == null) {
            return;
        }

        String selectQuery = "SELECT * FROM `" + tableName + "`";

        try (
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(selectQuery);
            Workbook workbook = new XSSFWorkbook();
            FileOutputStream fos = new FileOutputStream("build/" + tableName + ".xlsx")
        ) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            Sheet sheet = workbook.createSheet(tableName);
            Row headerRow = sheet.createRow(0);

            for (int i = 1; i <= columnCount; i++) {
                headerRow.createCell(i - 1).setCellValue(metaData.getColumnName(i));
            }

            int rowNum = 1;
            while (resultSet.next()) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 1; i <= columnCount; i++) {
                    Object value = resultSet.getObject(i);
                    if (value != null) {
                        row.createCell(i - 1).setCellValue(value.toString());
                    }
                }
            }

            for (int i = 0; i < columnCount; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(fos);

            System.out.println("Таблица экспортирована.");
        } catch (Exception e) {
            System.err.println(
                "Невозможно сохранить результат в Excel. Может быть такой таблицы нет."
            );
        }
    }
}

