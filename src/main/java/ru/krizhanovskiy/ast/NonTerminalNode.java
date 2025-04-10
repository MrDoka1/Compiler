package ru.krizhanovskiy.ast;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import java.util.ArrayList;
import java.util.List;

//@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class NonTerminalNode extends Node {
    public String name;
    List<Node> children = new ArrayList<>();
    @JsonIgnore
    NonTerminalNode prev;
    int qtyProductions;


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
}