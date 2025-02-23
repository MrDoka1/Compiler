package ru.krizhanovskiy.lexer.token;

public record Token(TokenType type,
                    String name,
                    int line,
                    int column) {

    public Token(TokenType type, String name, int line, int column) {
        this.type = type;
        this.name = name;
        this.line = line;
        this.column = column + 1;
    }
}