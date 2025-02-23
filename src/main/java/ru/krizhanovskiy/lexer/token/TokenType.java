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
    NUMBER,
    FLOAT_NUMBER,

    INT,
    FLOAT,
    BOOLEAN,
    VOID,
    IF,
    ELSE,
    WHILE,
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
        switch (keyword) {
            case "int": return TokenType.INT;
            case "float": return TokenType.FLOAT;
            case "boolean": return TokenType.BOOLEAN;
            case "void": return TokenType.VOID;
            case "if": return TokenType.IF;
            case "else": return TokenType.ELSE;
            case "while": return TokenType.WHILE;
            case "for": return TokenType.FOR;
            case "break": return TokenType.BREAK;
            case "continue": return TokenType.CONTINUE;
            case "return": return TokenType.RETURN;
            default: return null;

        }
    }

    public static final int MAX_LENGTH_OPERATION = 2;
    public static final boolean CHECK_FURTHER_OPERATION = true;
    public static TokenType getOperation(String operation) {
        switch (operation) {
            case "+": return TokenType.PLUS;
            case "-": return TokenType.MINUS;
            case "*": return TokenType.MULTIPLICATION;
            case "/": return TokenType.DIVISION;
            case "=": return TokenType.EQUALS;
            case "==" : return TokenType.DOUBLE_EQUAL;
            case "!=": return TokenType.NOT_EQUALS;
            case "<": return TokenType.LESS_THAN;
            case ">": return TokenType.GREATER_THAN;
            case "<=": return TokenType.LESS_THAN_EQUALS;
            case ">=": return TokenType.GREATER_THAN_EQUALS;
            case "&": return TokenType.AND;
            case "|": return TokenType.OR;
            case "!" : return TokenType.NOT;
            default: return null;
        }
    }

    public static final int MAX_LENGTH_PUNCTUATION = 1;
    public static final boolean CHECK_FURTHER_PUNCTUATION = false;
    public static TokenType getPunctuation(String punctuation) {
        switch (punctuation) {
            case "(": return TokenType.OPEN_BRACKET;
            case ")": return TokenType.CLOSE_BRACKET;
            case "{": return TokenType.OPEN_CURLY_BRACKET;
            case "}": return TokenType.CLOSE_CURLY_BRACKET;
            case ";": return TokenType.SEMICOLON;
            case ",": return TokenType.COMMA;
            default: return null;
        }
    }
}