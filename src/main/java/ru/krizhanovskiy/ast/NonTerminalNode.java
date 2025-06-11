package ru.krizhanovskiy.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ru.krizhanovskiy.lexer.token.TokenType;

import java.util.ArrayList;
import java.util.List;

//@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class NonTerminalNode extends Node {
    public String name;
    List<Node> children = new ArrayList<>();
    @JsonIgnore
    NonTerminalNode prev;
    int qtyProductions;

    String value;
    TokenType type;


    public NonTerminalNode(String name) {
        this.name = name;
    }

    public NonTerminalNode(String name, NonTerminalNode prev, int qtyProductions) {
        this.name = name;
        this.prev = prev;
        this.qtyProductions = qtyProductions;
    }

    public void setChildren(List<Node> children) {
        this.children = children;
    }
    public List<Node> getChildren() {
        return children;
    }
    public int getQtyProductions() {
        return qtyProductions;
    }
    public NonTerminalNode getPrev() {
        return prev;
    }
    public void setPrev(NonTerminalNode prev) {
        this.prev = prev;
    }

    public String getValue() {
        return value;
    }

    public TokenType getType() {
        return type;
    }

    public void setValueAndType(String value, TokenType type) {
        this.value = value;
        this.type = type;
    }
}