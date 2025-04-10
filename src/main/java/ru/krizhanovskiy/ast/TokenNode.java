package ru.krizhanovskiy.ast;

import ru.krizhanovskiy.lexer.token.Token;

public class TokenNode extends Node {
    public Token token;
    public TokenNode(Token token) {
        this.token = token;
    }
}