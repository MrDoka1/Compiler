package ru.krizhanovskiy.semantic_analyzer;

import ru.krizhanovskiy.ast.NonTerminalNode;
import ru.krizhanovskiy.lexer.token.TokenType;

public class Variable {
    private final TokenType type;
    private final String name;
    private String value;
    private boolean isMutable = false;
    private boolean used = false;
    private boolean isAnnounced = false;
    private final NonTerminalNode declarationStatement;
    private String lastValue;
    private int index;

    public Variable(TokenType type, String name, NonTerminalNode declarationStatement) {
        this.type = type;
        this.name = name;
        this.declarationStatement = declarationStatement;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setMutable(boolean mutable) {
        isMutable = mutable;
    }

    public TokenType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public boolean isMutable() {
        return isMutable;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public boolean isUsed() {
        return used;
    }

    public boolean isAnnounced() {
        return isAnnounced;
    }

    public void setAnnounced(boolean announced) {
        isAnnounced = announced;
    }

    public NonTerminalNode getDeclarationStatement() {
        return declarationStatement;
    }

    public String getLastValue() {
        return lastValue;
    }

    public void setLastValue(String lastValue) {
        this.lastValue = lastValue;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}