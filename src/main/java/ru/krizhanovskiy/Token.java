package ru.krizhanovskiy;

public class Token {
     enum TokenType {
        // --- Keywords ---
        INT("int"),
        FLOAT("float"),
        VOID("void"),
        IDENTIFIER(""),
        NUMBER(""),
        IF("if"),
        ELSE("else"),
        WHILE("while"),
        FOR("for"),
        BREAK("break"),
        CONTINUE("continue"),
        RETURN("return"),

        // --- Operators ---
        PLUS("+"),
        MINUS("-"),
        MULTIPLICATION("*"),
        DIVISION("/"),
        EQUALS("="),
        DOUBLE_EQUAL("=="),
        NOT_EQUALS("!="),
        LESS_THAN("<"),
        GREATER_THAN(">"),
        LESS_THAN_EQUALS("<="),
        GREATER_THAN_EQUALS(">="),
        AND("&"),
        OR("|"),
        NOT("!"),

        // --- Punctuation ---
        OPEN_BRACKET("("),
        CLOSE_BRACKET(")"),
        OPEN_CURLY_BRACKET("{"),
        CLOSE_CURLY_BRACKET("}"),
        SEMICOLON(";"),
        COMMA(","),
        ;
        private final String value;

        TokenType(String s) {
            this.value = s;
        }

        public TokenType fromString(String s) {
            for (TokenType t : TokenType.values()) {
                if (t.value.equals(s)) {
                    return t;
                }
            }
            return null;
        }
    }
    private TokenType type;
    private String name;
    private int line;
    private int column;
    private long positionInCharacterTable;
}