package ru.krizhanovskiy.lexer.token;


public enum TokenType {
    /**
     *  При добавлении нового токена:
     *  - перейди в нужну категорию и сделай там:
     *      - добавь строку в нужный switch
     *      - проверь переменную максимальной длины, может её нужно обновить
     *      - проверь переменную продолжения проверки, может её нужно обновить
     *          (например, если есть подобные случаи "=" и "==" - true, если нет - false)
     */

    // --- Keywords ---
    IDENTIFIER,
    STRING,
    NUMBER,
    FLOAT_NUMBER,

    TRUE,
    FALSE,

    INT,
    FLOAT,
    BOOLEAN,
    VOID,
    IF,
    ELSE,
    WHILE,
    DO,
    FOR,
    BREAK,
    CONTINUE,
    RETURN,

    // --- Operators ---
    PLUS,
    MINUS,
    MULTIPLICATION,
    DIVISION,
    EQUALS,
    DOUBLE_EQUAL,
    NOT_EQUALS,
    LESS_THAN,
    GREATER_THAN,
    LESS_THAN_EQUALS,
    GREATER_THAN_EQUALS,
    AND,
    OR,
    NOT,

    // --- Punctuation ---
    OPEN_BRACKET,
    CLOSE_BRACKET,
    OPEN_CURLY_BRACKET,
    CLOSE_CURLY_BRACKET,
    SEMICOLON,
    COMMA,
    ;

    public static final int MAX_LENGTH_KEYWORD = 8;
    public static final boolean CHECK_FURTHER_KEYWORD = false;
    public static TokenType getKeyword(String keyword) {
        return switch (keyword) {
            case "true" -> TokenType.TRUE;
            case "false" -> TokenType.FALSE;
            case "int" -> TokenType.INT;
            case "float" -> TokenType.FLOAT;
            case "boolean" -> TokenType.BOOLEAN;
            case "void" -> TokenType.VOID;
            case "if" -> TokenType.IF;
            case "else" -> TokenType.ELSE;
            case "while" -> TokenType.WHILE;
            case "do" -> TokenType.DO;
            case "for" -> TokenType.FOR;
            case "break" -> TokenType.BREAK;
            case "continue" -> TokenType.CONTINUE;
            case "return" -> TokenType.RETURN;
            default -> null;
        };
    }

    public static final int MAX_LENGTH_OPERATION = 2;
    public static final boolean CHECK_FURTHER_OPERATION = true;
    public static TokenType getOperation(String operation) {
        return switch (operation) {
            case "+" -> TokenType.PLUS;
            case "-" -> TokenType.MINUS;
            case "*" -> TokenType.MULTIPLICATION;
            case "/" -> TokenType.DIVISION;
            case "=" -> TokenType.EQUALS;
            case "==" -> TokenType.DOUBLE_EQUAL;
            case "!=" -> TokenType.NOT_EQUALS;
            case "<" -> TokenType.LESS_THAN;
            case ">" -> TokenType.GREATER_THAN;
            case "<=" -> TokenType.LESS_THAN_EQUALS;
            case ">=" -> TokenType.GREATER_THAN_EQUALS;
            case "&&" -> TokenType.AND;
            case "||" -> TokenType.OR;
            case "!" -> TokenType.NOT;
            default -> null;
        };
    }

    public static final int MAX_LENGTH_PUNCTUATION = 1;
    public static final boolean CHECK_FURTHER_PUNCTUATION = false;
    public static TokenType getPunctuation(String punctuation) {
        return switch (punctuation) {
            case "(" -> TokenType.OPEN_BRACKET;
            case ")" -> TokenType.CLOSE_BRACKET;
            case "{" -> TokenType.OPEN_CURLY_BRACKET;
            case "}" -> TokenType.CLOSE_CURLY_BRACKET;
            case ";" -> TokenType.SEMICOLON;
            case "," -> TokenType.COMMA;
            default -> null;
        };
    }
}