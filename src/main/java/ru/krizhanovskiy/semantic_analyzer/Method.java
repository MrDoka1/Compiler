package ru.krizhanovskiy.semantic_analyzer;

import ru.krizhanovskiy.lexer.token.TokenType;

import java.util.List;

public record Method(TokenType returnType, List<Variable> parameters, String name) {
}