package ru.krizhanovskiy.token;

public class Token {
    private TokenType type;
    private String name;
    private int line;
    private int column;
//    private long positionInCharacterTable;

    public Token(TokenType type, String name, int line, int column) {
        this.type = type;
        this.name = name;
        this.line = line;
        this.column = column;
    }
}