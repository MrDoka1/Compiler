package ru.krizhanovskiy.semantic_analyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scope {
    private final Map<String, Variable> variables = new HashMap<>();
    private Scope parent;
    private final List<Scope> children = new ArrayList<>();
    private boolean forOrWhileScope = false;
    private boolean unreachable = false;


    public Scope() {}

    public Scope(Scope parent) {
        this.parent = parent;
        this.forOrWhileScope = parent.isForOrWhileScope();
        this.unreachable = parent.isUnreachable();
        parent.addChildren(this);
    }

    public Scope(Scope parent, boolean forOrWhileScope) {
        this.parent = parent;
        this.forOrWhileScope = forOrWhileScope;
        this.unreachable = parent.isUnreachable();
        parent.addChildren(this);
    }

    public Variable getVariable(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        if (parent == null) return null;
        return parent.getVariable(name);
    }

    public void addVariable(Variable variable) {
        variables.put(variable.getName(), variable);
    }

    public List<Variable> getVariables() {
        return variables.values().stream().toList();
    }

    public boolean isForOrWhileScope() {
        return forOrWhileScope;
    }

    public void addChildren(Scope scope) {
        children.add(scope);
    }

    public List<Scope> getChildren() {
        return children;
    }

    public boolean isUnreachable() {
        return unreachable;
    }

    public void setUnreachable(boolean unreachable) {
        this.unreachable = unreachable;
    }

    public static class Builder {
        private boolean forOrWhileScope = false;
        private Scope parent;

        public Builder setForOrWhileScope(boolean forOrWhileScope) {
            this.forOrWhileScope = forOrWhileScope;
            return this;
        }

        public Builder setParent(Scope parent) {
            this.parent = parent;
            return this;
        }

        public Scope build() {
            return new Scope(parent, forOrWhileScope);
        }
    }
}