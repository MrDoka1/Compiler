package ru.krizhanovskiy.semantic_analyzer;

import ru.krizhanovskiy.lexer.token.TokenType;

public record Variable(TokenType type, String name) {
}