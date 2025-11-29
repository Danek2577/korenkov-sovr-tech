package ex1.model;

import common.Model;
import static common.Model.IO;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

public class Arithmetic extends Model {
    private enum NumericType {
        DOUBLE("double (с плавающей запятой)"),
        INTEGER("int (32-битное целое)"),
        BYTE("byte (8-битное целое)");

        private final String description;

        NumericType(String description) {
            this.description = description;
        }

        String describe() {
            return description;
        }
    }

    @FunctionalInterface
    private interface NumericBinaryOperator {
        Number apply(NumericType type, Number first, Number second);
    }

    @Override
    public String getDescribeMessage() {
        return "Модель арифметических операций";
    }

    @Override
    public void showCommands() {
        IO.println("\nДоступные команды:");
        IO.println("1. Вывести все таблицы из MySQL.");
        IO.println("2. Создать таблицу в MySQL.");
        IO.println("3. Сложение чисел (double/int/byte).");
        IO.println("4. Вычитание чисел (double/int/byte).");
        IO.println("5. Умножение чисел (double/int/byte).");
        IO.println("6. Деление чисел с целой и дробной частью.");
        IO.println("7. Остаток от деления (int/byte).");
        IO.println("8. Модуль числа с учетом типа.");
        IO.println("9. Возведение числа в степень с учетом типа.");
        IO.println("10. Сохранить результаты из MySQL в Excel.");
    }

    @Override
    public void runCommandWithConnection(String command, Connection connection)
        throws RuntimeException
    {
        switch (command) {
            case "1" -> showTables(connection);
            case "2" -> createTable(connection, "varchar(255)");
            case "3" -> performAddition(connection);
            case "4" -> performSubtraction(connection);
            case "5" -> performMultiplication(connection);
            case "6" -> performDivision(connection);
            case "7" -> performModulo(connection);
            case "8" -> performModule(connection);
            case "9" -> performExponentiation(connection);
            case "10" -> saveToExcel(connection);
            default -> IO.println("Неверный номер команды. Попробуйте снова.");
        }
    }

    private void performAddition(Connection connection) throws RuntimeException {
        performBinaryOperation(
            connection,
            "сложения",
            "первое слагаемое",
            "второе слагаемое",
            this::add,
            "+",
            NumericType.values()
        );
    }

    private void performSubtraction(Connection connection) throws RuntimeException {
        performBinaryOperation(
            connection,
            "вычитания",
            "уменьшаемое",
            "вычитаемое",
            this::subtract,
            "-",
            NumericType.values()
        );
    }

    private void performMultiplication(Connection connection) throws RuntimeException {
        performBinaryOperation(
            connection,
            "умножения",
            "первый множитель",
            "второй множитель",
            this::multiply,
            "*",
            NumericType.values()
        );
    }

    private void performBinaryOperation(
        Connection connection,
        String operationName,
        String firstPrompt,
        String secondPrompt,
        NumericBinaryOperator operator,
        String symbol,
        NumericType... allowedTypes
    ) throws RuntimeException {
        NumericType type = selectNumericType(allowedTypes);
        Number first = readNumber("\nВведите " + firstPrompt + ": ", type);
        Number second = readNumber("Введите " + secondPrompt + ": ", type);

        try {
            Number result = operator.apply(type, first, second);
            String firstValue = formatNumber(first, type);
            String secondValue = formatNumber(second, type);
            String formattedResult = formatNumber(result, type);
            String expression = firstValue + " " + symbol + " " + secondValue + " = " + formattedResult;

            IO.println("\nРезультат " + operationName + ": " + expression);
            finishQuery(connection, formattedResult, expression);
        } catch (ArithmeticException e) {
            IO.println("Ошибка вычисления: " + e.getMessage());
        }
    }

    private void performDivision(Connection connection) throws RuntimeException {
        NumericType type = selectNumericType(NumericType.DOUBLE, NumericType.INTEGER, NumericType.BYTE);
        Number dividend = readNumber("\nВведите делимое: ", type);
        Number divisor = readNumber("Введите делитель: ", type);

        if (isZero(divisor, type)) {
            IO.println("Ошибка: деление на ноль невозможно.");
            return;
        }

        switch (type) {
            case DOUBLE -> handleDoubleDivision(connection, dividend.doubleValue(), divisor.doubleValue());
            case INTEGER -> handleIntegerDivision(connection, dividend.intValue(), divisor.intValue());
            case BYTE -> handleByteDivision(connection, dividend.byteValue(), divisor.byteValue());
            default -> throw new IllegalStateException("Неизвестный тип данных.");
        }
    }

    private void handleDoubleDivision(Connection connection, double dividend, double divisor)
        throws RuntimeException
    {
        double result = dividend / divisor;
        String dividendStr = formatDouble(dividend);
        String divisorStr = formatDouble(divisor);
        String resultStr = formatDouble(result);
        String expression = dividendStr + " / " + divisorStr + " = " + resultStr;

        IO.println("\nРезультат деления: " + expression);
        finishQuery(connection, resultStr, expression);
    }

    private void handleIntegerDivision(Connection connection, int dividend, int divisor)
        throws RuntimeException
    {
        int quotient = dividend / divisor;
        int remainder = dividend % divisor;
        double precise = (double) dividend / divisor;

        String dividendStr = formatNumber(dividend, NumericType.INTEGER);
        String divisorStr = formatNumber(divisor, NumericType.INTEGER);
        String preciseStr = formatDouble(precise);
        String expression = dividendStr + " / " + divisorStr + " = " + preciseStr
            + " (целая часть " + quotient + ", остаток " + remainder + ")";

        IO.println("\nРезультат деления: " + expression);
        finishQuery(connection, preciseStr, expression);
    }

    private void handleByteDivision(Connection connection, byte dividend, byte divisor)
        throws RuntimeException
    {
        byte quotient = (byte) (dividend / divisor);
        byte remainder = (byte) (dividend % divisor);
        double precise = (double) dividend / divisor;

        String dividendStr = formatNumber(dividend, NumericType.BYTE);
        String divisorStr = formatNumber(divisor, NumericType.BYTE);
        String preciseStr = formatDouble(precise);
        String expression = dividendStr + " / " + divisorStr + " = " + preciseStr
            + " (целая часть " + quotient + ", остаток " + remainder + ")";

        IO.println("\nРезультат деления: " + expression);
        finishQuery(connection, preciseStr, expression);
    }

    private void performModulo(Connection connection) throws RuntimeException {
        NumericType type = selectNumericType(NumericType.INTEGER, NumericType.BYTE);
        Number number = readNumber("\nВведите число: ", type);
        Number modulus = readNumber("Введите модуль: ", type);

        if (isZero(modulus, type)) {
            IO.println("Ошибка: модуль не может быть равен нулю.");
            return;
        }

        Number result = switch (type) {
            case INTEGER -> Integer.valueOf(number.intValue() % modulus.intValue());
            case BYTE -> Byte.valueOf((byte) (number.byteValue() % modulus.byteValue()));
            default -> throw new IllegalStateException("Тип не поддерживает операцию по модулю.");
        };

        String numberStr = formatNumber(number, type);
        String modulusStr = formatNumber(modulus, type);
        String formattedResult = formatNumber(result, type);
        String expression = numberStr + " % " + modulusStr + " = " + formattedResult;

        IO.println("\nОстаток от деления: " + expression);
        finishQuery(connection, formattedResult, expression);
    }

    private void performModule(Connection connection) throws RuntimeException {
        NumericType type = selectNumericType(NumericType.DOUBLE, NumericType.INTEGER, NumericType.BYTE);
        Number number = readNumber("\nВведите число: ", type);

        try {
            Number result = switch (type) {
                case DOUBLE -> Math.abs(number.doubleValue());
                case INTEGER -> {
                    int value = number.intValue();
                    if (value == Integer.MIN_VALUE) {
                        throw new ArithmeticException("Невозможно получить модуль от -2^31.");
                    }
                    yield Math.abs(value);
                }
                case BYTE -> Byte.valueOf(absByte(number.byteValue()));
            };

            String numberStr = formatNumber(number, type);
            NumericType resultType = type == NumericType.DOUBLE ? NumericType.DOUBLE : type;
            String formattedResult = formatNumber(result, resultType);
            String expression = "|" + numberStr + "| = " + formattedResult;

            IO.println("\nМодуль числа: " + expression);
            finishQuery(connection, formattedResult, expression);
        } catch (ArithmeticException e) {
            IO.println("Ошибка вычисления: " + e.getMessage());
        }
    }

    private void performExponentiation(Connection connection) throws RuntimeException {
        NumericType type = selectNumericType(NumericType.DOUBLE, NumericType.INTEGER, NumericType.BYTE);
        Number base = readNumber("\nВведите основание: ", type);

        switch (type) {
            case DOUBLE -> handleDoubleExponent(connection, base.doubleValue());
            case INTEGER -> handleIntegerExponent(connection, base.intValue());
            case BYTE -> handleByteExponent(connection, base.byteValue());
            default -> throw new IllegalStateException("Неизвестный тип данных.");
        }
    }

    private void handleDoubleExponent(Connection connection, double base) throws RuntimeException {
        double exponent = readNumber("Введите показатель степени: ", NumericType.DOUBLE).doubleValue();
        double result = Math.pow(base, exponent);

        String baseStr = formatDouble(base);
        String exponentStr = formatDouble(exponent);
        String resultStr = formatDouble(result);
        String expression = baseStr + " ^ " + exponentStr + " = " + resultStr;

        IO.println("\nРезультат возведения в степень: " + expression);
        finishQuery(connection, resultStr, expression);
    }

    private void handleIntegerExponent(Connection connection, int base) throws RuntimeException {
        int exponent = readNumber(
            "Введите показатель степени (целое неотрицательное): ",
            NumericType.INTEGER
        ).intValue();

        if (exponent < 0) {
            IO.println("Ошибка: отрицательная степень для целых чисел не поддерживается.");
            return;
        }

        try {
            int result = powInt(base, exponent);
            String baseStr = formatNumber(base, NumericType.INTEGER);
            String exponentStr = formatNumber(exponent, NumericType.INTEGER);
            String resultStr = formatNumber(result, NumericType.INTEGER);
            String expression = baseStr + " ^ " + exponentStr + " = " + resultStr;

            IO.println("\nРезультат возведения в степень: " + expression);
            finishQuery(connection, resultStr, expression);
        } catch (ArithmeticException e) {
            IO.println("Ошибка вычисления: " + e.getMessage());
        }
    }

    private void handleByteExponent(Connection connection, byte base) throws RuntimeException {
        int exponent = readNumber(
            "Введите показатель степени (целое неотрицательное): ",
            NumericType.INTEGER
        ).intValue();

        if (exponent < 0) {
            IO.println("Ошибка: отрицательная степень для типа byte не поддерживается.");
            return;
        }

        try {
            byte result = powByte(base, exponent);
            String baseStr = formatNumber(base, NumericType.BYTE);
            String exponentStr = formatNumber(exponent, NumericType.INTEGER);
            String resultStr = formatNumber(result, NumericType.BYTE);
            String expression = baseStr + " ^ " + exponentStr + " = " + resultStr;

            IO.println("\nРезультат возведения в степень: " + expression);
            finishQuery(connection, resultStr, expression);
        } catch (ArithmeticException e) {
            IO.println("Ошибка вычисления: " + e.getMessage());
        }
    }

    private Number add(NumericType type, Number first, Number second) {
        return switch (type) {
            case DOUBLE -> first.doubleValue() + second.doubleValue();
            case INTEGER -> Math.addExact(first.intValue(), second.intValue());
            case BYTE -> Byte.valueOf(safeByte(first.byteValue() + second.byteValue()));
        };
    }

    private Number subtract(NumericType type, Number first, Number second) {
        return switch (type) {
            case DOUBLE -> first.doubleValue() - second.doubleValue();
            case INTEGER -> Math.subtractExact(first.intValue(), second.intValue());
            case BYTE -> Byte.valueOf(safeByte(first.byteValue() - second.byteValue()));
        };
    }

    private Number multiply(NumericType type, Number first, Number second) {
        return switch (type) {
            case DOUBLE -> first.doubleValue() * second.doubleValue();
            case INTEGER -> Math.multiplyExact(first.intValue(), second.intValue());
            case BYTE -> Byte.valueOf(safeByte(first.byteValue() * second.byteValue()));
        };
    }

    private NumericType selectNumericType(NumericType... allowedTypes) {
        List<NumericType> options = Arrays.asList(allowedTypes);

        while (true) {
            IO.println("\nВыберите тип чисел для операции:");
            for (int i = 0; i < options.size(); i++) {
                IO.println((i + 1) + ". " + options.get(i).describe());
            }

            String answer = IO.readln("Тип: ").trim();
            try {
                int index = Integer.parseInt(answer) - 1;
                if (index >= 0 && index < options.size()) {
                    return options.get(index);
                }
                IO.println("Ошибка: номер типа вне допустимого диапазона.");
            } catch (NumberFormatException e) {
                IO.println("Ошибка: необходимо ввести целое число.");
            }
        }
    }

    private Number readNumber(String prompt, NumericType type) {
        while (true) {
            String raw = IO.readln(prompt);
            try {
                return switch (type) {
                    case DOUBLE -> Double.parseDouble(raw);
                    case INTEGER -> Integer.parseInt(raw);
                    case BYTE -> Byte.parseByte(raw);
                };
            } catch (NumberFormatException e) {
                IO.println("Ошибка: некорректное значение для типа " + type.name().toLowerCase() + ".");
            }
        }
    }

    private String formatNumber(Number value, NumericType type) {
        return switch (type) {
            case DOUBLE -> formatDouble(value.doubleValue());
            case INTEGER -> Integer.toString(value.intValue());
            case BYTE -> Byte.toString(value.byteValue());
        };
    }

    private String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private byte safeByte(int value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new ArithmeticException("Результат выходит за пределы типа byte.");
        }
        return (byte) value;
    }

    private byte absByte(byte value) {
        if (value == Byte.MIN_VALUE) {
            throw new ArithmeticException("Невозможно получить модуль от -128.");
        }
        return (byte) Math.abs(value);
    }

    private boolean isZero(Number value, NumericType type) {
        return switch (type) {
            case DOUBLE -> value.doubleValue() == 0.0d;
            case INTEGER -> value.intValue() == 0;
            case BYTE -> value.byteValue() == 0;
        };
    }

    private int powInt(int base, int exponent) {
        int result = 1;
        int factor = base;
        int power = exponent;

        while (power > 0) {
            if ((power & 1) == 1) {
                result = Math.multiplyExact(result, factor);
            }
            power >>= 1;
            if (power > 0) {
                factor = Math.multiplyExact(factor, factor);
            }
        }

        return result;
    }

    private byte powByte(byte base, int exponent) {
        int result = 1;
        int factor = base;
        int power = exponent;

        while (power > 0) {
            if ((power & 1) == 1) {
                result = safeByte(result * factor);
            }
            power >>= 1;
            if (power > 0) {
                factor = safeByte(factor * factor);
            }
        }

        return (byte) result;
    }
}

