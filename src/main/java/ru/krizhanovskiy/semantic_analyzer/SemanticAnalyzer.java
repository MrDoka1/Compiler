package ru.krizhanovskiy.semantic_analyzer;

import ru.krizhanovskiy.ast.Node;
import ru.krizhanovskiy.ast.NonTerminalNode;
import ru.krizhanovskiy.ast.TokenNode;
import ru.krizhanovskiy.lexer.token.Token;
import ru.krizhanovskiy.lexer.token.TokenType;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public class SemanticAnalyzer {
    private final NonTerminalNode rootAST;
    public final List<Method> methods = new ArrayList<>();
    private Scope[] scopes;
    private Set<String>[] noDeclaredVariables;
    private Set<String>[] noDeclaredMethods;
    private final List<NonTerminalNode> statementsMethod = new ArrayList<>();
    public boolean errors = false;
    private Method currentMethod;
    private boolean[][] usedMethods;
    private final List<NonTerminalNode> endStatementNodes = new ArrayList<>();
    private final List<NonTerminalNode> emptyStatementNodes = new ArrayList<>();
    private final List<NonTerminalNode> clearValueNodes = new ArrayList<>();
    private final List<AddAssignment> addAssignmentNodes = new ArrayList<>();
    record AddAssignment(NonTerminalNode node, List<Variable> variables){}
    private final Map<String, List<ImmutableExpression>> removeImmutableExpression = new HashMap<>();
    record ImmutableExpression(NonTerminalNode node, boolean forWhileUsed){}

    private int currentIndexMethod = 0;
    private NonTerminalNode mainMethod = null;

    private static String yellowColorCode = "\u001B[33m";
    private static String resetColorCode = "\u001B[0m";
    private static void warningPrint(String text) {
        System.out.println(yellowColorCode + text + resetColorCode);
    }

    public SemanticAnalyzer(NonTerminalNode rootAST) {
        this.rootAST = rootAST;
    }

    public void analyze() {
        analyzeMethod((NonTerminalNode) rootAST.getChildren().get(0));
        usedMethods = new boolean[methods.size()][methods.size()];
        scopes = new Scope[methods.size()];
        noDeclaredVariables = new Set[methods.size()];
        noDeclaredMethods = new Set[methods.size()];
        IntStream.range(0, noDeclaredVariables.length).forEach(i -> {
            noDeclaredVariables[i] = new HashSet<>();
            noDeclaredMethods[i] = new HashSet<>();
        });

        for (int i = 0; i < statementsMethod.size(); i++) {
            currentIndexMethod = i;
            currentMethod = methods.get(i);
            Scope scope = new Scope();
            scopes[i] = scope;
            currentMethod.parameters().forEach(scope::addVariable);
            DataStatement dataStatement = analyzeStatements(statementsMethod.get(i), scope);
            if (currentMethod.returnType() != TokenType.VOID && !dataStatement.hasReturn) {
                errors = true;
                System.err.printf("Method \"%s\" does not return a value on all execution paths.\n", currentMethod.name());
            }
        }
        if (mainMethod == null) {
            errors = true;
            System.err.println("Main method not detected");
            return;
        }
        clearValueNodes();
        removeUnreachableCode();
        addAssignmentNodesInAST();
    }

    private void clearValueNodes() {
        clearValueNodes.forEach(this::clearValueNode);
    }
    private boolean clearValueNode(NonTerminalNode node) {
        //if (node.getValue() != null) {
            if (node.name.equals("number")) return false;
            if (node.name.equals("identifier-or-function-call")) {
                node.setValueAndType(null, null);
                return true;
            }

            List<Boolean> list = new ArrayList<>();
            for (Node child : node.getChildren()) {
                if (child instanceof NonTerminalNode n) {
                    list.add(clearValueNode(n));
                }
            }
            if (list.stream().anyMatch(el -> el)) {
                node.setValueAndType(null, null);
                return true;
            }
        //}
        return false;
    }

    private void removeUnreachableCode() {
        endStatementNodes.forEach(statementNode -> {
            // Удаление кода за break, continue, return
            NonTerminalNode statementOptionalUp = statementNode.getPrev();
            NonTerminalNode statementOptionalDown = (NonTerminalNode) statementOptionalUp.getChildren().get(1);
            statementOptionalDown.setChildren(List.of(new TokenNode(null)));
        });

        // TODO: Добавить удаление неиспользуемых методов
        removeUnusedMethods();
        removeEmptyStatementsNodes();
        removeImmutableExpressions();
    }

    private void removeImmutableExpressions() {
        removeImmutableExpression.forEach((k, v) -> v.forEach(immutableExpression -> {
            if (!immutableExpression.forWhileUsed) {
                immutableExpression.node.setChildren(new ArrayList<>(List.of(new TokenNode(null))));
            }
        }));
    }

    private void removeUnusedMethods() {
        NonTerminalNode currentNode = (NonTerminalNode) rootAST.getChildren().get(1);
        boolean[] finalUsedMethods = new boolean[methods.size()];
        finalUsedMethods[0] = true;
        Set<Integer> checked = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        checked.add(0);
        queue.add(0);
        while (!queue.isEmpty()) {
            int currentIndex = queue.poll();
            boolean[] arr = usedMethods[currentIndex];
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] && !checked.contains(i)) {
                    checked.add(i);
                    queue.add(i);
                    finalUsedMethods[i] = true;
                }
            }
        }


        int unusedMethods = 0;
        for (int i = 1; i < methods.size(); i++) {
            if (!finalUsedMethods[i]) {
                NonTerminalNode methodOptionalOrProgram = currentNode.getPrev();
                NonTerminalNode methodOptionalDown = (NonTerminalNode) currentNode.getChildren().get(1);
                methodOptionalOrProgram.getChildren().set(1, methodOptionalDown);
                methodOptionalDown.setPrev(methodOptionalOrProgram);
                currentNode = methodOptionalOrProgram;

                methods.remove(i - unusedMethods);
                unusedMethods++;
            }
            currentNode = (NonTerminalNode) currentNode.getChildren().get(1);
        }

        detectUnusedVariable(finalUsedMethods);
    }

    private void detectUnusedVariable(boolean[] usedMethods) {
        for (int i = 0; i < usedMethods.length; i++) {
            if (usedMethods[i]) {
                detectUnusedVariableInScope(scopes[i]);
            }
        }
    }

    private void detectUnusedVariableInScope(Scope scope) {
        for (Variable var : scope.getVariables()) {
            if (var.isUsed()) continue;
            if (var.getDeclarationStatement() == null) continue;

            var.getDeclarationStatement().setChildren(List.of(new TokenNode(null))); // statement ребёнок ε
        }
        for (Scope scope1 : scope.getChildren()) {
            detectUnusedVariableInScope(scope1);
        }
    }

    private void removeEmptyStatementsNodes() {
        emptyStatementNodes.forEach(emptyStatement -> {
            NonTerminalNode statementOptionalUp = emptyStatement.getPrev();
            NonTerminalNode statementOptionalDown = (NonTerminalNode) emptyStatement.getChildren().get(1);
            int index = 1;
            if (statementOptionalUp.name.equals("method")) {
                index = 6;
                statementOptionalDown.name = "statements";
            }
            statementOptionalDown.setPrev(statementOptionalUp);
            statementOptionalUp.getChildren().set(index, statementOptionalDown);
        });
    }

    private void addAssignmentNodesInAST() {
        for (AddAssignment addAssignment : addAssignmentNodes) {
            NonTerminalNode currentNode = addAssignment.node;
            for (Variable variable : addAssignment.variables) {
                NonTerminalNode assignmentNode = createAssignmentNode(variable);
                if (assignmentNode == null) continue;
                currentNode = insertStatementNode(currentNode, assignmentNode);
            }
        }

    }

    private NonTerminalNode insertStatementNode(NonTerminalNode current, NonTerminalNode statement) {
        NonTerminalNode newNode = new NonTerminalNode("statements-optional", current, 2);
        List<Node> children = current.getChildren();
        children.forEach(child -> {
            if (child instanceof NonTerminalNode) {
                ((NonTerminalNode) child).setPrev(newNode);
            }
        });
        statement.setPrev(current);
        current.setChildren(new ArrayList<>(List.of(statement, newNode)));
        newNode.setChildren(children);

        return newNode;
    }

    private NonTerminalNode createAssignmentNode(Variable variable) {
        String name = variable.getName();
        if (variable.isMutable()) return null;

        NonTerminalNode statement = new NonTerminalNode("statement");

        NonTerminalNode assignmentOrFunctionCall = new NonTerminalNode("assignment-or-function-call", statement, 2);
        statement.setChildren(new ArrayList<>(List.of(assignmentOrFunctionCall)));

        TokenNode identity = new TokenNode(new Token(TokenType.IDENTIFIER, name, -10, -10));
        NonTerminalNode continueAssignmentOrFunctionCall = new NonTerminalNode("continue-assignment-or-function-call", assignmentOrFunctionCall, 2);
        assignmentOrFunctionCall.setChildren(new ArrayList<>(List.of(identity, continueAssignmentOrFunctionCall)));

        TokenNode equal = new TokenNode(new Token(TokenType.EQUALS, "=", -10, -10));
        NonTerminalNode expression = new NonTerminalNode("expression", continueAssignmentOrFunctionCall, 1);
        continueAssignmentOrFunctionCall.setChildren(new ArrayList<>(List.of(equal, expression)));

        NonTerminalNode logicalOrExpression = new NonTerminalNode("logical-or-expression", expression, 1);
        logicalOrExpression.setValueAndType(variable.getValue(), variable.getType());
        expression.setChildren(new ArrayList<>(List.of(logicalOrExpression)));

        return statement;
    }

    private void analyzeMethod(NonTerminalNode method) {
        TokenType returnType = getType((NonTerminalNode) method.getChildren().get(0));
        String name = ((TokenNode) method.getChildren().get(1)).token.name();

        List<Variable> parameters = analyzeParameters((NonTerminalNode) method.getChildren().get(3));

        // Проверка - существует ли уже метод с таким название и параметрами
        AtomicBoolean hasMethod = new AtomicBoolean(false);
        methods.forEach(method1 -> {
            if (method1.name().equals(name) && method1.parameters().size() == parameters.size()) {
                boolean flag = true;
                for (int i = 0; i < parameters.size(); i++) {
                    if (parameters.get(i).getType() != method1.parameters().get(i).getType()) {
                        flag = false;
                        break;
                    }
                }
                hasMethod.set(flag);
            }
        });

        if (hasMethod.get()) {
            errors = true;
            System.err.println("Method \"" + name + "\" has already been analyzed.");
            methods.add(new Method(getReturnType(returnType), parameters, name));
            statementsMethod.add((NonTerminalNode) method.getChildren().get(6));
        } else {
            if (name.equals("main") && returnType == TokenType.VOID && parameters.isEmpty()) {
                mainMethod = method;
                if (methods.isEmpty()) {
                    methods.add(0, new Method(getReturnType(returnType), parameters, name));
                    statementsMethod.add(0, (NonTerminalNode) method.getChildren().get(6));
                } else {
                    NonTerminalNode statement = (NonTerminalNode) method.getChildren().get(6);
                    methods.add(0, new Method(getReturnType(returnType), parameters, name));
                    statementsMethod.add(0, statement);
                    method = analyzeMainMethod(method);
                }
            } else {
                methods.add(new Method(getReturnType(returnType), parameters, name));
                statementsMethod.add((NonTerminalNode) method.getChildren().get(6));
            }
        }

        NonTerminalNode methodOptional = (NonTerminalNode) method.getPrev().getChildren().get(1);
        if (methodOptional.getChildren().get(0) instanceof NonTerminalNode) {
            analyzeMethod((NonTerminalNode) methodOptional.getChildren().get(0));
        }
    }

    // return newCurrentMethod
    private NonTerminalNode analyzeMainMethod(NonTerminalNode method) {
        methods.add(methods.remove(1));
        statementsMethod.add(statementsMethod.remove(1));

        NonTerminalNode methodOptional = method.getPrev();
        NonTerminalNode rotateMethod = (NonTerminalNode) rootAST.getChildren().get(0);
        rotateMethod.setPrev(methodOptional);
        methodOptional.getChildren().set(0, rotateMethod);

        method.setPrev(rootAST);
        rootAST.getChildren().set(0, method);
        return rotateMethod;
    }

    private List<Variable> analyzeParameters(NonTerminalNode parameters) {
        if (parameters.getChildren().get(0) instanceof NonTerminalNode) {
            List<Variable> list = analyzeParameter((NonTerminalNode) parameters.getChildren().get(0)); // parameter
            list.addAll(analyzeParameters((NonTerminalNode) parameters.getChildren().get(1))); // parameter-optional
            return list;
        } else {
            if (((TokenNode) parameters.getChildren().get(0)).token == null) { // ε
                return new ArrayList<>();
            } else {
                List<Variable> list = analyzeParameter((NonTerminalNode) parameters.getChildren().get(1)); // index 0 - ","
                list.addAll(analyzeParameters((NonTerminalNode) parameters.getChildren().get(2)));
                return list;
            }
        }
    }

    private List<Variable> analyzeParameter(NonTerminalNode parameter) {
        TokenType type = getType((NonTerminalNode) parameter.getChildren().get(0));
        TokenType variableType = getReturnType(type);
        String name = ((TokenNode) parameter.getChildren().get(1)).token.name();
        List<Variable> list = new ArrayList<>();
        Variable variable = new Variable(variableType, name, null);
        variable.setAnnounced(true);
        variable.setMutable(true);
        list.add(variable);
        return list;
    }

    private TokenType getType(NonTerminalNode returnTypeOrType) {
        if (returnTypeOrType.getChildren().get(0) instanceof NonTerminalNode) { // returnType
            return ((TokenNode) ((NonTerminalNode) returnTypeOrType.getChildren().get(0))
                    .getChildren().get(0)).token.type();
        }
        return ((TokenNode) returnTypeOrType.getChildren().get(0)).token.type();
    }

    private DataStatement analyzeStatements(NonTerminalNode statements, Scope scope) {
        if (statements.getChildren().get(0) instanceof NonTerminalNode) { // statement
            DataStatement ds1 = analyzeStatement((NonTerminalNode) statements.getChildren().get(0), scope);
            DataStatement ds2 = analyzeStatements((NonTerminalNode) statements.getChildren().get(1), scope);
            return new DataStatement(ds1.hasReturn || ds2.hasReturn);
        }
        return new DataStatement(false);
    }

    private DataStatement analyzeStatement(NonTerminalNode statement, Scope scope) {
        if (statement.getChildren().get(0) instanceof NonTerminalNode current) {
            String firstNonTerminal = current.name;
            switch (firstNonTerminal) {
                case "declaration" -> {
                    analyzeDeclaration(current, scope, false);
                }
                case "assignment-or-function-call" -> {
                    analyzeAssignmentOrFunctionCall(current, scope);
                }
                case "if-statement" -> {
                    return analyzeIfStatement(current, scope);
                }
                case "while-loop" -> {
                    return analyzeWhileStatement(current, scope);
                }
                case "for-loop" -> {
                    return forLoop(current, new Scope(scope));
                }
                case "return-statement" -> {
                    return returnStatement(current, scope);
                }
                default -> throw new RuntimeException(); // Grammar Error

            }
            return new DataStatement(false);
        }
        if (((TokenNode) statement.getChildren().get(0)).token == null) return new DataStatement(false); // ε
        switch (((TokenNode) statement.getChildren().get(0)).token.type()) {
            case OPEN_CURLY_BRACKET -> {
                return analyzeStatements((NonTerminalNode) statement.getChildren().get(1), new Scope(scope));
            }
            case BREAK -> {
                analyzeBreak((NonTerminalNode) statement, scope);
                return new DataStatement(false);
            }
            case CONTINUE -> {
                analyzeContinue((NonTerminalNode) statement, scope);
                return new DataStatement(false);
            }
            default -> throw new RuntimeException(); // Grammar Error
        }
    }

    private void analyzeDeclaration(NonTerminalNode declaration, Scope scope, boolean forLoop) {
        TokenType returnType = getType((NonTerminalNode) declaration.getChildren().get(0));
        String name = ((TokenNode) declaration.getChildren().get(1)).token.name();

        Variable variable = scope.getVariable(name);
        boolean localError = false;
        if (variable != null) {
            errors = true;
            localError = true;
            System.err.printf("The variable \"%s\" has already been declared. Error in line: %d.\n",
                    name, ((TokenNode) declaration.getChildren().get(1)).token.line());
        } else {
            if (forLoop) variable = new Variable(getReturnType(returnType), name, null);
            else variable = new Variable(getReturnType(returnType), name, declaration.getPrev());
        }
        variable.setAnnounced(true);

        NonTerminalNode expression = null;
        if (declaration.getPrev().getChildren().get(1) instanceof NonTerminalNode optionalAssignment) {
            if (((TokenNode) optionalAssignment.getChildren().get(0)).token != null)
                expression = (NonTerminalNode) optionalAssignment.getChildren().get(1);
        } else {
            expression = (NonTerminalNode) declaration.getPrev().getChildren().get(2);
        }

        if (expression != null) {
            TypeExpression typeExpression = analyzeExpression(expression, scope);
            if (typeExpression == null) return;
            if (noMatchReturnType(typeExpression.type(), variable.getType())) {
                errors = true;
                System.err.printf("The expression type does not match the variable type. Expected %s, but got %s. Error in line: %d.\n",
                        getStringFromReturnType(variable.getType()), getStringFromReturnType(typeExpression.type()),
                        ((TokenNode) declaration.getChildren().get(1)).token.line());
                return;
            }
            if (!localError) {
                variable.setAnnounced(true);
                variable.setMutable(typeExpression.mutable());
                variable.setValue(typeExpression.value());
                scope.addVariable(variable);

                if (typeExpression.mutable || forLoop) variable.setUsed(true);
                else {
                    if (!removeImmutableExpression.containsKey(name)) removeImmutableExpression.put(name, new ArrayList<>());
                    removeImmutableExpression.get(name).add(new ImmutableExpression(expression.getPrev(), false));
                    //expression.getPrev().setChildren(new ArrayList<>(List.of(new TokenNode(null))));
                }

                // TODO: в байт код
                return;
            }
        }
        if (!localError) {
            scope.addVariable(variable);
            // TODO: в байт код
        }
    }

    private TokenType getReturnType(TokenType type) {
        return switch (type) {
            case INT -> TokenType.NUMBER;
            case FLOAT -> TokenType.FLOAT_NUMBER;
            default -> type;
        };
    }
    private String getStringFromReturnType(TokenType type) {
        return switch (type) {
            case NUMBER -> "INT";
            case FLOAT_NUMBER -> "FLOAT";
            case BOOLEAN -> "BOOLEAN";
            default -> type.toString();
        };
    }

    private String getStringFromOperand(TokenType type) {
        return switch (type) {
            case PLUS -> "+";
            case MINUS -> "-";
            case MULTIPLICATION -> "*";
            case DIVISION -> "/";
            case EQUALS -> "=";
            case DOUBLE_EQUAL -> "==";
            case NOT_EQUALS -> "!=";
            case LESS_THAN -> "<";
            case GREATER_THAN -> ">";
            case LESS_THAN_EQUALS -> "<=";
            case GREATER_THAN_EQUALS -> ">=";
            default -> type.toString();
        };
    }

    private void analyzeAssignmentOrFunctionCall(NonTerminalNode assignmentOrFunctionCall, Scope scope) {
        Token identity = ((TokenNode) assignmentOrFunctionCall.getChildren().get(0)).token;
        NonTerminalNode continueAssignmentOrFunctionCall = (NonTerminalNode) assignmentOrFunctionCall.getChildren().get(1);
        NonTerminalNode next = (NonTerminalNode) continueAssignmentOrFunctionCall.getChildren().get(1);
        if (((TokenNode) continueAssignmentOrFunctionCall.getChildren().get(0)).token.type() == TokenType.EQUALS) {
            TypeExpression typeExpression = analyzeExpression(next, scope);
            analyzeAssigment(identity, typeExpression, scope, assignmentOrFunctionCall.getPrev().getPrev());
        } else {
            analyzeFunctionCall(next, identity, scope);
        }
    }

    private void analyzeAssigment(Token identity, TypeExpression typeExpression, Scope scope, NonTerminalNode statement) {
        if (typeExpression == null) return;

        Variable variable = scope.getVariable(identity.name());
        if (variable == null) {
            if (!noDeclaredVariables[currentIndexMethod].contains(identity.name())) {
                noDeclaredVariables[currentIndexMethod].add(identity.name());
                errors = true;
                System.err.printf("The variable \"%s\" not declared. Error in line: %d.\n",
                        identity.name(), identity.line());
            }
            return;
        }
        if (noMatchReturnType(typeExpression.type(), variable.getType())) {
            errors = true;
            System.err.println("The expression type does not match the variable type. Error in line: " +
                    identity.line());
            return;
        }
        if (!typeExpression.mutable()) {
            variable.setMutable(false);
            variable.setValue(typeExpression.value());
            if (statement != null) {
                emptyStatementNodes.add(statement);
            }
        } else {
            variable.setUsed(true);
        }

        // TODO: в байт код
    }

    private TypeExpression analyzeFunctionCall(NonTerminalNode arguments, Token identity, Scope scope) {
        // TODO: !!!!!!!!!!! логика не продумана - я всё равно долже продолжать анализировать код !!!!!!!!!!!!!!!
        List<Method> currentMethods = methods.stream().filter(method -> method.name().equals(identity.name())).toList();
        if (!(identity.name().equals("print") || identity.name().equals("intInput") || identity.name().equals("floatInput"))) {
            if (currentMethods.isEmpty()) {
                if (!noDeclaredMethods[currentIndexMethod].contains(identity.name())) {
                    noDeclaredMethods[currentIndexMethod].add(identity.name());
                    errors = true;
                    System.err.printf("Method \"%s\" not found\n", identity.name());
                }
                return null;
            }
        }

        boolean zeroArgumentsOrString = (arguments.getChildren().get(0) instanceof  TokenNode);

        if (zeroArgumentsOrString || arguments.getChildren().get(0) instanceof NonTerminalNode) { // expression
            List<NonTerminalNode> expressions = new ArrayList<>();

            if (!zeroArgumentsOrString) {
                NonTerminalNode argumentList = (NonTerminalNode) arguments.getChildren().get(0);
                do {
                    NonTerminalNode expression = (NonTerminalNode) argumentList.getChildren().get(0);
                    expressions.add(expression);
                    argumentList = (NonTerminalNode) argumentList.getChildren().get(1); // argument-list-optional
                    if (((TokenNode) argumentList.getChildren().get(0)).token == null) break;
                    argumentList = (NonTerminalNode) argumentList.getChildren().get(1);
                } while (true);
            }
            List<Method> filterMethods = currentMethods.stream().filter(method -> method.parameters().size() == expressions.size()).toList();
            if (filterMethods.isEmpty()) {
                if (expressions.isEmpty() && ((TokenNode) arguments.getChildren().get(0)).token != null) { // STRING
                    expressions.add(null);
                }
                if (expressions.isEmpty()) {
                    if (identity.name().equals("intInput")) {
                        // TODO: код добавления в промежуточное представление
                        return new TypeExpression(TokenType.NUMBER, true, null, null, new HashSet<>());
                    }
                    if (identity.name().equals("floatInput")) {
                        // TODO: код добавления в промежуточное представление
                        return new TypeExpression(TokenType.FLOAT_NUMBER, true, null, null, new HashSet<>());
                    }
                }
                if (expressions.size() == 1 && identity.name().equals("print"))  {

                    if (arguments.getChildren().get(0) instanceof TokenNode
                            && ((TokenNode) arguments.getChildren().get(0)).token != null) { // STRING
                        // TODO: Код для вывода строки в консоль
                        return new TypeExpression(TokenType.VOID, false, null, null, new HashSet<>());
                    }
                    TypeExpression typeExpression = analyzeExpression(expressions.get(0), scope);
                    if (typeExpression == null) return null;
                    if (typeExpression.type == TokenType.VOID) {
                        // TODO: тип не воид
                    }
                    // TODO: код добавления в промежуточное представление
                    return new TypeExpression(TokenType.VOID, true, null, null, new HashSet<>());
                }

                System.err.println("No method found with given number of arguments.");
                return null;
            }

            Method method = null;
            Set<String> usedVariables = new HashSet<>();
            for (Method meth : filterMethods) {
                boolean parametersError = false;
                for (int i = 0; i < expressions.size(); i++) {
                    Variable parameter = meth.parameters().get(i);
                    TypeExpression typeExpression = analyzeExpression(expressions.get(i), scope);
                    if (typeExpression == null) continue;
                    usedVariables.addAll(typeExpression.variables);

                    if (noMatchReturnType(typeExpression.type(), parameter.getType())) {
                        parametersError = true;
                    }
                }
                if (!parametersError) {
                    method = meth;
                }
            }

            if (method == null) {
                // TODO: ошибка - метода с данными параметрами не найдено
                return null;
            }

            // TODO: код добавления в промежуточное представление

            // --- Изменение состояния метода на используется ---
            if (!scope.isUnreachable()) {
                for (int i = 0; i < methods.size(); i++) {
                    if (methods.get(i).equals(method)) {
                        usedMethods[currentIndexMethod][i] = true;
                        break;
                    }
                }
            }


            return new TypeExpression(method.returnType(), true, null, null, usedVariables);
        }
        if (((TokenNode) arguments.getChildren().get(0)).token != null) { // STRING
            if (identity.name().equals("print")) {
                // TODO: Код для вывода строки в консоль
                return new TypeExpression(TokenType.VOID, false, null, null, new HashSet<>());
            } else {
                // TODO: Метода с таким аргументом нет
                return null;
            }
        }
        return null;
    }

    private void analyzeBreak(NonTerminalNode statement, Scope scope) {
        // Удаление кода за break
        endStatementNodes.add(statement);
        if (!scope.isForOrWhileScope()) {
            int line = ((TokenNode) statement.getChildren().get(0)).token.line();
            System.err.printf("Break is outside the loop. Error in line: %d.\n", line);
        }

    }

    private void analyzeContinue(NonTerminalNode statement, Scope scope) {
        // Удаление кода за continue
        endStatementNodes.add(statement);
        if (!scope.isForOrWhileScope()) {
            int line = ((TokenNode) statement.getChildren().get(0)).token.line();
            System.err.printf("Continue is outside the loop.. Error in line: %d.\n", line);
        }

    }

    private DataStatement analyzeIfStatement(NonTerminalNode ifStatement, Scope scope) {
        NonTerminalNode expression = (NonTerminalNode) ifStatement.getChildren().get(2);
        TypeExpression typeExpression = analyzeExpression(expression, scope);
        boolean localError = typeExpression == null;
        if (!localError && typeExpression.type() != TokenType.BOOLEAN) {
            errors = true;
            localError = true;
            Token token = ((TokenNode) ifStatement.getChildren().get(0)).token;
            System.err.printf("Invalid data type. Expected boolean. Error in line: %d.\n", token.line());
        }

        NonTerminalNode statement = (NonTerminalNode) ifStatement.getChildren().get(4);

        Scope statementScope = new Scope(scope);
        if (!localError && !typeExpression.mutable() && typeExpression.value().equals("false")) {
            statementScope.setUnreachable(true);
        }

        DataStatement dataStatement = analyzeStatement(statement, statementScope);
        NonTerminalNode elseOptional = (NonTerminalNode) ifStatement.getChildren().get(5);
        NonTerminalNode elseStatement = null;
        DataStatement elseDataStatement = null;
        if (((TokenNode) elseOptional.getChildren().get(0)).token != null) {
            elseStatement = (NonTerminalNode) elseOptional.getChildren().get(1);

            Scope elseStatementScope = new Scope(scope);
            if (!localError && !typeExpression.mutable() && typeExpression.value().equals("true")) {
                elseStatementScope.setUnreachable(true);
            }

            elseDataStatement = analyzeStatement(elseStatement, elseStatementScope);
        }
        if (!localError && !typeExpression.mutable()) {
            NonTerminalNode statementUp = ifStatement.getPrev();
            NonTerminalNode statementOptional = statementUp.getPrev();
            if (typeExpression.value().equals("true")) {
                warningPrint("Warning! Expression is always true.");
                statementOptional.getChildren().set(0, statement);
                statement.setPrev(statementOptional);
                return dataStatement;
            } else {
                warningPrint("Warning! Expression is always false.");
                if (elseStatement == null) {
                    emptyStatementNodes.add(statementOptional);
                } else {
                    statementOptional.getChildren().set(0, elseStatement);
                    elseStatement.setPrev(statementOptional);
                    return elseDataStatement;
                }
            }
        }

        if (elseDataStatement == null) return new DataStatement(false);
        return new DataStatement(dataStatement.hasReturn && elseDataStatement.hasReturn);
    }

    private DataStatement analyzeWhileStatement(NonTerminalNode whileStatement, Scope scope) {
        NonTerminalNode expression;
        NonTerminalNode statement;
        Token token = ((TokenNode) whileStatement.getChildren().get(0)).token;
        boolean doWhile = false;
        if (token.type() == TokenType.WHILE) {
            expression = (NonTerminalNode) whileStatement.getChildren().get(2);
            statement = (NonTerminalNode) whileStatement.getChildren().get(4);
        } else { // do {} while
            doWhile = true;
            expression = (NonTerminalNode) whileStatement.getChildren().get(4);
            statement = (NonTerminalNode) whileStatement.getChildren().get(1);
        }
        TypeExpression typeExpression = analyzeExpression(expression, scope);

        boolean localError = typeExpression == null;
        if (!localError && typeExpression.type() != TokenType.BOOLEAN) {
            localError = true;
            errors = true;
            System.err.printf("Invalid data type. Expected boolean. Error in line: %d.\n", token.line());
        }

        Scope statementScope = new Scope.Builder().setParent(scope).setForOrWhileScope(true).build();
        if (!doWhile && !localError && !typeExpression.mutable() && typeExpression.value().equals("false")) {
            statementScope.setUnreachable(true);
        }

        if (!localError && (typeExpression.mutable || typeExpression.value().equals("true"))) {
            clearValueNodes.add((NonTerminalNode) expression.getChildren().get(0));
        }

        if (!localError && !typeExpression.mutable() && typeExpression.value().equals("true")) {
            NonTerminalNode currentNode = whileStatement.getPrev().getPrev();

            List<Variable> variables = new ArrayList<>();
            for (String var : typeExpression.variables) {
                Variable variable = scope.getVariable(var);
                if (variable.isMutable()) continue;
                variables.add(variable);
                variable.setUsed(true);
            }
            if (!variables.isEmpty()) addAssignmentNodes.add(new AddAssignment(whileStatement.getPrev().getPrev(), variables));
        }
        DataStatement dataStatement = analyzeStatement(statement, statementScope);

        if (!localError && !typeExpression.mutable()) {
            NonTerminalNode statementUp = whileStatement.getPrev();
            NonTerminalNode statementOptional = statementUp.getPrev();
            if (typeExpression.value().equals("true") && typeExpression.variables.isEmpty()) {
                warningPrint("Warning! Expression is always true.");
                return dataStatement;
            } else if (typeExpression.value().equals("false")) {
                warningPrint("Warning! Expression is always false.");
                if (doWhile) {
                    statementOptional.getChildren().set(0, statement);
                    statement.setPrev(statementOptional);
                    return dataStatement;
                } else {
                    emptyStatementNodes.add(statementOptional);
                }
            }
        }
        if (doWhile) return dataStatement;
        return new DataStatement(false);
    }



    private DataStatement forLoop(NonTerminalNode forLoop, Scope scope) {
        Scope forScope = new Scope(scope, true);
        NonTerminalNode declarationAndAssigment = (NonTerminalNode) forLoop.getChildren().get(2);
        analyzeDeclaration((NonTerminalNode) declarationAndAssigment.getChildren().get(0), forScope, true);
        NonTerminalNode expression = (NonTerminalNode) forLoop.getChildren().get(4);
        TypeExpression typeExpression = analyzeExpression(expression, forScope);

        if (typeExpression != null) {
            List<Variable> variables = new ArrayList<>();
            for (String var : typeExpression.variables) {
                Variable variable = forScope.getVariable(var);
                if (variable.isMutable()) continue;
                variables.add(variable);
                variable.setUsed(true);
            }
            if (!variables.isEmpty()) addAssignmentNodes.add(new AddAssignment(forLoop.getPrev().getPrev(), variables));
        }

        NonTerminalNode assignment = (NonTerminalNode) forLoop.getChildren().get(6);
        Token identity = ((TokenNode) assignment.getChildren().get(0)).token;
        TypeExpression typeExpressionAssignment = analyzeExpression((NonTerminalNode) assignment.getChildren().get(2), forScope);
        analyzeAssigment(identity, typeExpressionAssignment, forScope, null);
        NonTerminalNode statement = (NonTerminalNode) forLoop.getChildren().get(8);

        boolean localError = typeExpression == null;
        if (!localError && typeExpression.type() != TokenType.BOOLEAN) {
            localError = true;
            errors = true;
            System.err.print("Invalid data type. Expected boolean.\n");
        }


        if (!localError && !typeExpression.mutable() && typeExpression.value().equals("false")) {
            forScope.setUnreachable(true);
        }
        DataStatement dataStatement = analyzeStatement(statement, forScope);

        if (!localError && (typeExpression.mutable || typeExpression.value().equals("true"))) {
            clearValueNodes.add((NonTerminalNode) expression.getChildren().get(0));
        }

        if (!localError && !typeExpression.mutable()) {
            NonTerminalNode statementUp = forLoop.getPrev();
            NonTerminalNode statementOptional = statementUp.getPrev();

            if (typeExpression.value().equals("true") && typeExpression.variables.isEmpty()) {
                warningPrint("Warning! Expression is always true.");
                return dataStatement;
            } else if (typeExpression.value().equals("false")) {
                warningPrint("Warning! Expression is always false.");
                emptyStatementNodes.add(statementOptional);
            }
        }
        return new DataStatement(false);
    }

    private DataStatement returnStatement(NonTerminalNode returnStatement, Scope scope) {
        endStatementNodes.add(returnStatement.getPrev());
        NonTerminalNode returnExpressionOrVoid = (NonTerminalNode) returnStatement.getChildren().get(1);
        TokenType returnType;
        if (returnExpressionOrVoid.getChildren().get(0) instanceof TokenNode) {
            returnType = TokenType.VOID;
        } else {
            NonTerminalNode expression = (NonTerminalNode) returnExpressionOrVoid.getChildren().get(0);
            TypeExpression typeExpression = analyzeExpression(expression, scope);
            if (typeExpression == null) return new DataStatement(true);
            returnType = typeExpression.type();
        }
        if (noMatchReturnType(returnType, currentMethod.returnType())) {
            errors = true;
            int line = ((TokenNode) returnStatement.getChildren().get(0)).token.line();
            System.err.println("Return type does not match method return type. Error in line: " + line);
        }
        return new DataStatement(true);
    }

    private boolean noMatchReturnType(TokenType returnType, TokenType returnTypeMethod) {
        if (returnType == TokenType.NUMBER && returnTypeMethod == TokenType.INT) return false;
        if (returnType == TokenType.FLOAT_NUMBER && returnTypeMethod == TokenType.FLOAT) return false;
        return returnType != returnTypeMethod;
    }

    private TypeExpression analyzeExpression(NonTerminalNode expression, Scope scope) {
        NonTerminalNode logicalOrExpression = (NonTerminalNode) expression.getChildren().get(0);
        TypeExpression typeLogicalOrExpression = analyzeLogicalOrExpression(logicalOrExpression, scope);
        //System.out.println(typeLogicalOrExpression);
        return typeLogicalOrExpression;
    }

    private TypeExpression analyzeLogicalOrExpression(NonTerminalNode logicalOrExpression, Scope scope) {
        NonTerminalNode logicalAndExpression = (NonTerminalNode) logicalOrExpression.getChildren().get(0);
        TypeExpression typeLogicalAndExpression = analyzeLogicalAndExpression(logicalAndExpression, scope);
        NonTerminalNode logicalOrTail = (NonTerminalNode) logicalOrExpression.getChildren().get(1);
        TypeExpression typeExpressionLogicalOrTail = analyzeLogicalOrTail(logicalOrTail, scope);

        TypeExpression typeExpression = analyzeLogicalOrExpressionAndLogicalOrTail(typeLogicalAndExpression,
                typeExpressionLogicalOrTail, null);
        setStaticValueInNonTerminalNode(logicalOrExpression, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeLogicalOrTail(NonTerminalNode logicalOrTail, Scope scope) { // ε | "||" <logical-and-expression> <logical-or-tail>
        if (((TokenNode) logicalOrTail.getChildren().get(0)).token == null) return null;

        NonTerminalNode logicalAndExpression = (NonTerminalNode) logicalOrTail.getChildren().get(1);
        TypeExpression typeLogicalAndExpression = analyzeLogicalAndExpression(logicalAndExpression, scope);
        NonTerminalNode logicalOrTail2 = (NonTerminalNode) logicalOrTail.getChildren().get(2);
        TypeExpression typeExpressionLogicalOrTail = analyzeLogicalOrTail(logicalOrTail2, scope);

        TypeExpression typeExpression = analyzeLogicalOrExpressionAndLogicalOrTail(typeLogicalAndExpression,
                typeExpressionLogicalOrTail, TokenType.OR);
        setStaticValueInNonTerminalNode(logicalOrTail, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeLogicalOrExpressionAndLogicalOrTail(TypeExpression typeLogicalAndExpression,
                                                                      TypeExpression typeExpressionLogicalOrTail, TokenType newOp) {
        if (typeLogicalAndExpression == null) return null;
        if (typeExpressionLogicalOrTail != null) typeLogicalAndExpression.variables.addAll(typeExpressionLogicalOrTail.variables);

        if (!typeLogicalAndExpression.mutable()) {
            // Правой ветки нет
            if (typeExpressionLogicalOrTail == null) return new TypeExpression(typeLogicalAndExpression.type(),
                    false, typeLogicalAndExpression.value(), newOp, typeLogicalAndExpression.variables);

            if (typeLogicalAndExpression.type() == TokenType.BOOLEAN && typeExpressionLogicalOrTail.type() == TokenType.BOOLEAN) {
                if (!typeExpressionLogicalOrTail.mutable()) {
                    return calcOr(typeLogicalAndExpression, typeExpressionLogicalOrTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(typeExpressionLogicalOrTail.type(), true, null, newOp, typeLogicalAndExpression.variables);
            }

            errors = true;
            // TODO: ошибка - неверный тип выражения
            System.err.println("Error: Incompatible operand types for '||': both operands must be boolean.");
            return null;
        }
        // TODO: написание в байткод


        if (typeExpressionLogicalOrTail != null) {
            // TODO: написание в байткод для правой ветки
            return new TypeExpression(TokenType.BOOLEAN, true, null, newOp, typeLogicalAndExpression.variables);
        }
        return new TypeExpression(typeLogicalAndExpression.type(), true, null, newOp, typeLogicalAndExpression.variables);
    }

    private TypeExpression calcOr(TypeExpression left, TypeExpression right, TokenType newOp) {
        boolean result;
        if (Objects.requireNonNull(right.op()) == TokenType.OR) {
            result = Boolean.parseBoolean(right.value()) || Boolean.parseBoolean(left.value());
        } else {
            throw new RuntimeException(); // Ошибка грамматики
        }

        left.variables.addAll(right.variables);
        return new TypeExpression(TokenType.BOOLEAN, false, String.valueOf(result), newOp, left.variables);
    }

    private TypeExpression analyzeLogicalAndExpression(NonTerminalNode logicalAndExpression, Scope scope) {
        NonTerminalNode equalityExpression = (NonTerminalNode) logicalAndExpression.getChildren().get(0);
        TypeExpression typeEqualityExpression = analyzeEqualityExpression(equalityExpression, scope);
        NonTerminalNode logicalAndTail = (NonTerminalNode) logicalAndExpression.getChildren().get(1);
        TypeExpression typeExpressionLogicalAndTail = analyzeLogicalAndTail(logicalAndTail, scope);

        TypeExpression typeExpression = analyzeLogicalAndExpressionAndLogicalAndTail(typeEqualityExpression,
                typeExpressionLogicalAndTail, null);
        setStaticValueInNonTerminalNode(logicalAndExpression, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeLogicalAndTail(NonTerminalNode logicalAndTail, Scope scope) {
        if (((TokenNode) logicalAndTail.getChildren().get(0)).token == null) return null;

        NonTerminalNode equalityExpression = (NonTerminalNode) logicalAndTail.getChildren().get(1);
        TypeExpression typeEqualityExpression = analyzeEqualityExpression(equalityExpression, scope);
        NonTerminalNode logicalAndTail2 = (NonTerminalNode) logicalAndTail.getChildren().get(2);
        TypeExpression typeExpressionLogicalAndTail = analyzeLogicalAndTail(logicalAndTail2, scope);

        TypeExpression typeExpression = analyzeLogicalAndExpressionAndLogicalAndTail(typeEqualityExpression,
                typeExpressionLogicalAndTail, TokenType.AND);
        setStaticValueInNonTerminalNode(logicalAndTail, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeLogicalAndExpressionAndLogicalAndTail(TypeExpression typeEqualityExpression,
                                                                        TypeExpression typeExpressionLogicalAndTail, TokenType newOp) {
        if (typeEqualityExpression == null) return null;
        if (typeExpressionLogicalAndTail != null) typeEqualityExpression.variables.addAll(typeExpressionLogicalAndTail.variables);

        if (!typeEqualityExpression.mutable()) {
            // Правой ветки нет
            if (typeExpressionLogicalAndTail == null) return new TypeExpression(typeEqualityExpression.type(),
                    false, typeEqualityExpression.value(), newOp, typeEqualityExpression.variables);

            if (typeEqualityExpression.type() == TokenType.BOOLEAN && typeExpressionLogicalAndTail.type() == TokenType.BOOLEAN) {
                if (!typeExpressionLogicalAndTail.mutable()) {
                    return calcAnd(typeEqualityExpression, typeExpressionLogicalAndTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(typeExpressionLogicalAndTail.type(), true, null, newOp, typeEqualityExpression.variables);
            }

            errors = true;
            // TODO: ошибка - неверный тип выражения
            System.err.println("Error: Incompatible operand types for '&&': both operands must be boolean.");
            return null;
        }
        // TODO: написание в байткод


        if (typeExpressionLogicalAndTail != null) {
            // TODO: написание в байткод для правой ветки
            return new TypeExpression(TokenType.BOOLEAN, true, null, newOp, typeEqualityExpression.variables);
        }
        return new TypeExpression(typeEqualityExpression.type(), true, null, newOp, typeEqualityExpression.variables);
    }

    private TypeExpression calcAnd(TypeExpression left, TypeExpression right, TokenType newOp) {
        boolean result;
        if (Objects.requireNonNull(right.op()) == TokenType.AND) {
            result = Boolean.parseBoolean(right.value()) && Boolean.parseBoolean(left.value());
        } else {
            throw new RuntimeException(); // Ошибка грамматики
        }

        left.variables.addAll(right.variables);
        return new TypeExpression(TokenType.BOOLEAN, false, String.valueOf(result), newOp, left.variables);
    }

    private TypeExpression analyzeEqualityExpression(NonTerminalNode equalityExpression, Scope scope) {
        NonTerminalNode relationalExpression = (NonTerminalNode) equalityExpression.getChildren().get(0);
        TypeExpression typeRelationalExpression = analyzeRelationalExpression(relationalExpression, scope);
        NonTerminalNode equalityTail = (NonTerminalNode) equalityExpression.getChildren().get(1);
        TypeExpression typeExpressionEqualityTail = analyzeEqualityTail(equalityTail, scope);

        TypeExpression typeExpression = analyzeEqualityExpressionAndEqualityTail(typeRelationalExpression,
                typeExpressionEqualityTail, null);
        setStaticValueInNonTerminalNode(equalityExpression, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeEqualityTail(NonTerminalNode equalityTail, Scope scope) {
        Token token = ((TokenNode) equalityTail.getChildren().get(0)).token;
        if (token == null) return null;

        NonTerminalNode relationalExpression = (NonTerminalNode) equalityTail.getChildren().get(1);
        TypeExpression typeRelationalExpression = analyzeRelationalExpression(relationalExpression, scope);
        NonTerminalNode equalityTail2 = (NonTerminalNode) equalityTail.getChildren().get(2);
        TypeExpression typeExpressionEqualityTail = analyzeEqualityTail(equalityTail2, scope);

        TypeExpression typeExpression = analyzeEqualityExpressionAndEqualityTail(typeRelationalExpression,
                typeExpressionEqualityTail, token.type());
        setStaticValueInNonTerminalNode(equalityTail, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeEqualityExpressionAndEqualityTail(TypeExpression typeRelationalExpression,
                                                                    TypeExpression typeExpressionEqualityTail, TokenType newOp) {
        if (typeRelationalExpression == null) return null;
        if (typeExpressionEqualityTail != null) typeRelationalExpression.variables.addAll(typeExpressionEqualityTail.variables);

        if (!typeRelationalExpression.mutable()) {
            // Правой ветки нет
            if (typeExpressionEqualityTail == null) return new TypeExpression(typeRelationalExpression.type(),
                    false, typeRelationalExpression.value(), newOp, typeRelationalExpression.variables);

            // TODO: подумать над типами множителей одинаковые они или нет
            if (typeRelationalExpression.type() == TokenType.NUMBER && typeExpressionEqualityTail.type() == TokenType.NUMBER) {
                if (!typeExpressionEqualityTail.mutable()) {
                    return calcEqual(typeRelationalExpression, typeExpressionEqualityTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(TokenType.BOOLEAN, true, null, newOp, typeRelationalExpression.variables);
            }
            if (typeRelationalExpression.type() == TokenType.FLOAT_NUMBER && typeExpressionEqualityTail.type() == TokenType.FLOAT_NUMBER) {
                if (!typeExpressionEqualityTail.mutable()) {
                    return calcEqual(typeRelationalExpression, typeExpressionEqualityTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(TokenType.BOOLEAN, true, null, newOp, typeRelationalExpression.variables);
            }
            if (typeRelationalExpression.type() == TokenType.BOOLEAN && typeExpressionEqualityTail.type() == TokenType.BOOLEAN) {
                if (!typeExpressionEqualityTail.mutable()) {
                    return calcEqual(typeRelationalExpression, typeExpressionEqualityTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(TokenType.BOOLEAN, true, null, newOp, typeRelationalExpression.variables);
            }

            errors = true;
            // TODO: ошибка - неверный тип выражения
            System.err.printf("Error: Incompatible operand types for '%s': found '%s' and '%s', expected matching types.\n",
                    getStringFromOperand(typeExpressionEqualityTail.op),
                    getStringFromReturnType(typeRelationalExpression.type),
                    getStringFromReturnType(typeExpressionEqualityTail.type()));
            return null;
        }
        // TODO: написание в байткод


        if (typeExpressionEqualityTail != null) {
            // TODO: написание в байткод для правой ветки
            return new TypeExpression(TokenType.BOOLEAN, true, null, newOp, typeRelationalExpression.variables);
        }
        return new TypeExpression(typeRelationalExpression.type(), true, null, newOp, typeRelationalExpression.variables);
    }

    private TypeExpression calcEqual(TypeExpression left, TypeExpression right, TokenType newOp) {
        boolean result;
        switch (right.op()) {
            case DOUBLE_EQUAL -> {
                if (left.type == TokenType.BOOLEAN) result = Boolean.parseBoolean(right.value()) == Boolean.parseBoolean(left.value());
                else if (left.type == TokenType.NUMBER) result = Integer.parseInt(right.value()) == Integer.parseInt(left.value());
                else result = Float.parseFloat(right.value()) == Float.parseFloat(left.value());
            }
            case NOT_EQUALS -> {
                if (left.type == TokenType.BOOLEAN) result = Boolean.parseBoolean(right.value()) != Boolean.parseBoolean(left.value());
                else if (left.type == TokenType.NUMBER) result = Integer.parseInt(right.value()) != Integer.parseInt(left.value());
                else result = Float.parseFloat(right.value()) != Float.parseFloat(left.value());
            }
            default -> throw new RuntimeException(); // Ошибка грамматики
        }

        left.variables.addAll(right.variables);
        return new TypeExpression(TokenType.BOOLEAN, false, String.valueOf(result), newOp, left.variables);
    }

    private TypeExpression analyzeRelationalExpression(NonTerminalNode relationalExpression, Scope scope) {
        NonTerminalNode additiveExpression = (NonTerminalNode) relationalExpression.getChildren().get(0);
        TypeExpression typeAdditiveExpression = analyzeAdditiveExpression(additiveExpression, scope);
        NonTerminalNode relationalTail = (NonTerminalNode) relationalExpression.getChildren().get(1);
        TypeExpression typeExpressionRelationalTail = analyzeRelationalTail(relationalTail, scope);

        TypeExpression typeExpression = analyzeRelationalExpressionAndRelationalTail(typeAdditiveExpression,
                typeExpressionRelationalTail, null);
        setStaticValueInNonTerminalNode(relationalExpression, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeRelationalTail(NonTerminalNode relationalTail, Scope scope) {
        Token token = ((TokenNode) relationalTail.getChildren().get(0)).token;
        if (token == null) return null;

        NonTerminalNode additiveExpression = (NonTerminalNode) relationalTail.getChildren().get(1);
        TypeExpression typeAdditiveExpression = analyzeAdditiveExpression(additiveExpression, scope);
        NonTerminalNode relationalTail2 = (NonTerminalNode) relationalTail.getChildren().get(2);
        TypeExpression typeExpressionRelationalTail = analyzeRelationalTail(relationalTail2, scope);

        TypeExpression typeExpression = analyzeRelationalExpressionAndRelationalTail(typeAdditiveExpression,
                typeExpressionRelationalTail, token.type());
        setStaticValueInNonTerminalNode(relationalTail, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeRelationalExpressionAndRelationalTail(TypeExpression typeAdditiveExpression,
                                                                        TypeExpression typeExpressionRelationalTail, TokenType newOp) {
        if (typeAdditiveExpression == null) return null;
        if (typeExpressionRelationalTail != null) typeAdditiveExpression.variables.addAll(typeExpressionRelationalTail.variables);

        if (!typeAdditiveExpression.mutable()) {
            // Правой ветки нет
            if (typeExpressionRelationalTail == null) return new TypeExpression(typeAdditiveExpression.type(),
                    false, typeAdditiveExpression.value(), newOp, typeAdditiveExpression.variables);

            // TODO: подумать над типами множителей одинаковые они или нет
            if (typeAdditiveExpression.type() == TokenType.NUMBER && typeExpressionRelationalTail.type() == TokenType.NUMBER) {
                if (!typeExpressionRelationalTail.mutable()) {
                    return calcComparison(typeAdditiveExpression, typeExpressionRelationalTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(TokenType.BOOLEAN, true, null, newOp,typeAdditiveExpression.variables);
            }
            if (typeAdditiveExpression.type() == TokenType.FLOAT_NUMBER && typeExpressionRelationalTail.type() == TokenType.FLOAT_NUMBER) {
                if (!typeExpressionRelationalTail.mutable()) {
                    return calcComparison(typeAdditiveExpression, typeExpressionRelationalTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(TokenType.BOOLEAN, true, null, newOp,typeAdditiveExpression.variables);
            }

            errors = true;
            System.err.printf("Error: Incompatible operand types for '%s': found '%s' and '%s', expected matching types. " +
                            "(FLOAT and FLOAT) or (INT and INT)\n",
                    getStringFromOperand(typeExpressionRelationalTail.op),
                    getStringFromReturnType(typeAdditiveExpression.type),
                    getStringFromReturnType(typeExpressionRelationalTail.type()));
            return null;
        }
        // TODO: написание в байткод


        if (typeExpressionRelationalTail != null) {
            // TODO: написание в байткод для правой ветки
            return new TypeExpression(TokenType.BOOLEAN, true, null, newOp, typeAdditiveExpression.variables);
        }
        return new TypeExpression(typeAdditiveExpression.type(), true, null, newOp, typeAdditiveExpression.variables);
    }

    private TypeExpression calcComparison(TypeExpression left, TypeExpression right, TokenType newOp) {
        boolean isInteger = left.type() == TokenType.NUMBER;

        Number v1;
        Number v2;

        if (isInteger) {
            v1 = Integer.valueOf(left.value());
            v2 = Integer.valueOf(right.value());
        } else {
            v1 = Float.valueOf(left.value());
            v2 = Float.valueOf(right.value());
        }

        boolean result;
        switch (right.op()) {
            case LESS_THAN -> {
                if (isInteger) result = v2.intValue() < v1.intValue();
                else result = v2.floatValue() < v1.floatValue();
            }
            case GREATER_THAN -> {
                if (isInteger) result = v2.intValue() > v1.intValue();
                else result = v2.floatValue() > v1.floatValue();
            }
            case LESS_THAN_EQUALS -> {
                if (isInteger) result = v2.intValue() <= v1.intValue();
                else result = v2.floatValue() <= v1.floatValue();
            }
            case GREATER_THAN_EQUALS -> {
                if (isInteger) result = v2.intValue() >= v1.intValue();
                else result = v2.floatValue() >= v1.floatValue();
            }
            default -> throw new RuntimeException(); // Ошибка грамматики
        }

        left.variables.addAll(right.variables);
        return new TypeExpression(TokenType.BOOLEAN, false, String.valueOf(result), newOp, left.variables);
    }

    private TypeExpression analyzeAdditiveExpression(NonTerminalNode additiveExpression, Scope scope) {
        NonTerminalNode multiplicativeExpression = (NonTerminalNode) additiveExpression.getChildren().get(0);
        TypeExpression typeMultiplicativeExpression = analyzeMultiplicativeExpression(multiplicativeExpression, scope);
        NonTerminalNode additiveTail = (NonTerminalNode) additiveExpression.getChildren().get(1);
        TypeExpression typeExpressionAdditiveTail = analyzeAdditiveTail(additiveTail, scope);

        TypeExpression typeExpression = analyzeAdditiveExpressionAndAdditiveTail(typeMultiplicativeExpression,
                typeExpressionAdditiveTail, null);
        setStaticValueInNonTerminalNode(additiveExpression, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeAdditiveTail(NonTerminalNode additiveTail, Scope scope) {
        Token token = ((TokenNode) additiveTail.getChildren().get(0)).token;
        if (token == null) return null;

        NonTerminalNode multiplicativeExpression = (NonTerminalNode) additiveTail.getChildren().get(1);
        TypeExpression typeMultiplicativeExpression = analyzeMultiplicativeExpression(multiplicativeExpression, scope);
        NonTerminalNode additiveTail2 = (NonTerminalNode) additiveTail.getChildren().get(2);
        TypeExpression typeExpressionAdditiveTail = analyzeAdditiveTail(additiveTail2, scope);

        TypeExpression typeExpression = analyzeAdditiveExpressionAndAdditiveTail(typeMultiplicativeExpression,
                typeExpressionAdditiveTail, token.type());
        setStaticValueInNonTerminalNode(additiveTail, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeAdditiveExpressionAndAdditiveTail(TypeExpression typeMultiplicativeExpression,
                                                                    TypeExpression typeExpressionAdditiveTail, TokenType newOp) {
        if (typeMultiplicativeExpression == null) return null;
        if (typeExpressionAdditiveTail != null) typeMultiplicativeExpression.variables.addAll(typeExpressionAdditiveTail.variables);

        if (!typeMultiplicativeExpression.mutable()) {
            // Правой ветки нет
            if (typeExpressionAdditiveTail == null) return new TypeExpression(typeMultiplicativeExpression.type(),
                    false, typeMultiplicativeExpression.value(), newOp, typeMultiplicativeExpression.variables);

            // TODO: подумать над типами множителей одинаковые они или нет
            if (typeMultiplicativeExpression.type() == TokenType.NUMBER && typeExpressionAdditiveTail.type() == TokenType.NUMBER) {
                if (!typeExpressionAdditiveTail.mutable()) {
                    return calcSumDifference(typeMultiplicativeExpression, typeExpressionAdditiveTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(typeMultiplicativeExpression.type(), true, null, newOp, typeMultiplicativeExpression.variables);
            }
            if (typeMultiplicativeExpression.type() == TokenType.FLOAT_NUMBER && typeExpressionAdditiveTail.type() == TokenType.FLOAT_NUMBER) {
                if (!typeExpressionAdditiveTail.mutable()) {
                    return calcSumDifference(typeMultiplicativeExpression, typeExpressionAdditiveTail, newOp);
                }
                // TODO: байт код

                return new TypeExpression(typeMultiplicativeExpression.type(), true, null, newOp, typeMultiplicativeExpression.variables);
            }

            errors = true;
            // TODO: ошибка - неверный тип выражения
            System.err.printf("Error: Incompatible operand types for '%s': found '%s' and '%s', expected matching types. " +
                            "(FLOAT and FLOAT) or (INT and INT)\n",
                    getStringFromOperand(typeExpressionAdditiveTail.op),
                    getStringFromReturnType(typeMultiplicativeExpression.type),
                    getStringFromReturnType(typeExpressionAdditiveTail.type()));
            return null;
        }
        // TODO: написание в байткод


        if (typeExpressionAdditiveTail != null) {
            // TODO: написание в байткод для правой ветки
            return new TypeExpression(typeMultiplicativeExpression.type(), true, null, newOp, typeMultiplicativeExpression.variables);
        }
        return new TypeExpression(typeMultiplicativeExpression.type(), true, null, newOp, typeMultiplicativeExpression.variables);
    }

    private TypeExpression calcSumDifference(TypeExpression left, TypeExpression right, TokenType newOp) {
        boolean isInteger = left.type() == TokenType.NUMBER;

        Number v1;
        Number v2;

        if (isInteger) {
            v1 = Integer.valueOf(left.value());
            v2 = Integer.valueOf(right.value());
        } else {
            v1 = Float.valueOf(left.value());
            v2 = Float.valueOf(right.value());
        }


        Number result;
        switch (right.op()) {
            case MINUS -> {
                if (isInteger) result = v2.intValue() - v1.intValue();
                else result = v2.floatValue() - v1.floatValue();
            }
            case PLUS -> {
                if (isInteger)  result = v2.intValue() + v1.intValue();
                else result = v2.floatValue() + v1.floatValue();
            }
            default -> throw new RuntimeException(); // Ошибка грамматики
        }

        left.variables.addAll(right.variables);
        return new TypeExpression(left.type(), false, String.valueOf(result), newOp, left.variables);
    }

    private TypeExpression analyzeMultiplicativeExpression(NonTerminalNode multiplicativeExpression, Scope scope) {
        NonTerminalNode unaryExpression = (NonTerminalNode) multiplicativeExpression.getChildren().get(0);
        TypeExpression typeUnaryExpression = analyzeUnaryExpression(unaryExpression, scope);
        NonTerminalNode multiplicativeTail = (NonTerminalNode) multiplicativeExpression.getChildren().get(1);
        TypeExpression typeExpressionMultiplicativeTail = analyzeMultiplicativeTail(multiplicativeTail, scope);

        TypeExpression typeExpression = analyzeMultiplicativeExpressionAndMultiplicativeTail(typeUnaryExpression,
                typeExpressionMultiplicativeTail, null);
        setStaticValueInNonTerminalNode(multiplicativeExpression, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeMultiplicativeTail(NonTerminalNode multiplicativeTail, Scope scope) {
        Token token = ((TokenNode) multiplicativeTail.getChildren().get(0)).token;
        if (token == null) return null;

        NonTerminalNode unaryExpression = (NonTerminalNode) multiplicativeTail.getChildren().get(1);
        TypeExpression typeUnaryExpression = analyzeUnaryExpression(unaryExpression, scope);
        NonTerminalNode multiplicativeTail2 = (NonTerminalNode) multiplicativeTail.getChildren().get(2);
        TypeExpression typeExpressionMultiplicativeTail = analyzeMultiplicativeTail(multiplicativeTail2, scope);

        TypeExpression typeExpression = analyzeMultiplicativeExpressionAndMultiplicativeTail(typeUnaryExpression,
                typeExpressionMultiplicativeTail, token.type());
        setStaticValueInNonTerminalNode(multiplicativeTail, typeExpression);
        return typeExpression;
    }

    private TypeExpression analyzeMultiplicativeExpressionAndMultiplicativeTail(TypeExpression typeUnaryExpression,
                                                                                TypeExpression typeExpressionMultiplicativeTail,
                                                                                TokenType newOp) {
        if (typeUnaryExpression == null) return null;
        if (typeExpressionMultiplicativeTail != null) typeUnaryExpression.variables.addAll(typeExpressionMultiplicativeTail.variables);

        if (!typeUnaryExpression.mutable()) {
            // Правой ветки нет
            if (typeExpressionMultiplicativeTail == null) return new TypeExpression(typeUnaryExpression.type(),
                    false, typeUnaryExpression.value(), newOp, typeUnaryExpression.variables);

            // TODO: подумать над типами множителей одинаковые они или нет
            if (typeUnaryExpression.type() == TokenType.NUMBER && typeExpressionMultiplicativeTail.type() == TokenType.NUMBER) {
                if (!typeExpressionMultiplicativeTail.mutable()) {
                    return calcMultiOrDiv(typeUnaryExpression, typeExpressionMultiplicativeTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(typeUnaryExpression.type(), true, null, newOp, typeUnaryExpression.variables);
            }
            if (typeUnaryExpression.type() == TokenType.FLOAT_NUMBER && typeExpressionMultiplicativeTail.type() == TokenType.FLOAT_NUMBER) {
                if (!typeExpressionMultiplicativeTail.mutable()) {
                    return calcMultiOrDiv(typeUnaryExpression, typeExpressionMultiplicativeTail, newOp);
                }
                // TODO: байт код
                return new TypeExpression(typeUnaryExpression.type(), true, null, newOp, typeUnaryExpression.variables);
            }

            errors = true;
            // TODO: ошибка - неверный тип выражения
            System.err.printf("Error: Incompatible operand types for '%s': found '%s' and '%s', expected matching types. " +
                            "(FLOAT and FLOAT) or (INT and INT)\n",
                    getStringFromOperand(typeExpressionMultiplicativeTail.op),
                    getStringFromReturnType(typeUnaryExpression.type),
                    getStringFromReturnType(typeExpressionMultiplicativeTail.type()));
            return null;
        }
        // TODO: написание в байткод


        if (typeExpressionMultiplicativeTail != null) {
            // TODO: написание в байткод для правой ветки
        }
        return new TypeExpression(typeUnaryExpression.type(), true, null, newOp, typeUnaryExpression.variables);
    }

    private TypeExpression calcMultiOrDiv(TypeExpression left, TypeExpression right, TokenType newOp) {
        boolean isInteger = left.type() == TokenType.NUMBER;

        Number v1;
        Number v2;

        if (isInteger) {
            v1 = Integer.valueOf(left.value());
            v2 = Integer.valueOf(right.value());
        } else {
            v1 = Float.valueOf(left.value());
            v2 = Float.valueOf(right.value());
        }


        Number result;
        switch (right.op()) {
            case MULTIPLICATION -> {
                if (isInteger) result = v1.intValue() * v2.intValue();
                else result = v1.floatValue() * v2.floatValue();
            }
            case DIVISION -> {
                if (v1.doubleValue() == 0.0) {
                    errors = true;
                    // TODO: ошибка деления на ноль
                    return null;
                }
                if (isInteger)  result = v2.intValue() / v1.intValue();
                else result = v2.floatValue() / v1.floatValue();
            }
            default -> throw new RuntimeException(); // Ошибка грамматики
        }

        left.variables.addAll(right.variables);
        return new TypeExpression(left.type(), false, String.valueOf(result), newOp, left.variables);
    }

    private TypeExpression analyzeUnaryExpression(NonTerminalNode unaryExpression, Scope scope) {
        if (unaryExpression.getChildren().get(0) instanceof NonTerminalNode primaryExpression) {
            TypeExpression typeExpression = analyzePrimaryExpression(primaryExpression, scope);
            setStaticValueInNonTerminalNode(unaryExpression, typeExpression);
            return typeExpression;
        } else {
            Token token = ((TokenNode) unaryExpression.getChildren().get(0)).token;
            TokenType tokenType = token.type();
            NonTerminalNode primaryExpression = (NonTerminalNode) unaryExpression.getChildren().get(1);
            switch (tokenType) {
                case MINUS -> {
                    TypeExpression typeExpression = analyzeUnaryExpression(primaryExpression, scope);
                    if (typeExpression == null) return null;
                    TokenType type = typeExpression.type();
                    if (type != TokenType.NUMBER && type != TokenType.FLOAT_NUMBER) {
                        errors = true;
                        System.err.printf("Invalid data type. Expected int or float. Error in line: %d.\n", token.line());
                        return null;
                    }
                    if (typeExpression.mutable()) {
                        // TODO: В промежуточное представление
                        return typeExpression;
                    }

                    String value = typeExpression.value();
                    if (value.charAt(0) == '-') value = value.substring(1);
                    else value = '-' + value;

                    TypeExpression newTypeExpression = new TypeExpression(type, false, value, null, typeExpression.variables);
                    setStaticValueInNonTerminalNode(unaryExpression, typeExpression);
                    return newTypeExpression;
                }
                case NOT -> {
                    TypeExpression typeExpression = analyzeUnaryExpression(primaryExpression, scope);
                    if (typeExpression == null) return null;
                    TokenType type = typeExpression.type();
                    if (type != TokenType.BOOLEAN) {
                        errors = true;
                        System.err.printf("Invalid data type. Expected boolean. Error in line: %d.\n", token.line());
                        return null;
                    }
                    if (typeExpression.mutable()) {
                        // TODO: В промежуточное представление
                        return typeExpression;
                    }

                    String value = typeExpression.value();
                    value = value.equals("true") ? "false" : "true";

                    TypeExpression newTypeExpression = new TypeExpression(type, false, value, null, typeExpression.variables);
                    setStaticValueInNonTerminalNode(unaryExpression, typeExpression);
                    return newTypeExpression;
                }
                default -> throw new RuntimeException(); // Ошибка грамматики
            }
        }
    }

    private TypeExpression analyzePrimaryExpression(NonTerminalNode primaryExpression, Scope scope) {
        if (primaryExpression.getChildren().get(0) instanceof TokenNode) { // "(" <expression> ")"
            NonTerminalNode expression = (NonTerminalNode) primaryExpression.getChildren().get(1);
            return analyzeExpression(expression, scope);
        } else {
            NonTerminalNode current = (NonTerminalNode) primaryExpression.getChildren().get(0);
            TypeExpression typeExpression;
            switch (current.name) {
                case "identifier-or-function-call" -> {
                    typeExpression = analyzeIdentifierOrFunctionCall(current, scope);
                }
                case "number" -> {
                    typeExpression = analyzeNumber(current);
                }
                case "boolean-literal" -> {
                    typeExpression = analyzeBooleanLiteral(current);
                }
                default -> throw new RuntimeException(); // Ошибка грамматики
            }
            setStaticValueInNonTerminalNode(primaryExpression, typeExpression);
            return typeExpression;
        }
    }

    private TypeExpression analyzeIdentifierOrFunctionCall(NonTerminalNode identifierOrFunctionCall, Scope scope) {
        Token token = ((TokenNode) identifierOrFunctionCall.getChildren().get(0)).token;
        NonTerminalNode identifierEndOrFunctionCall = (NonTerminalNode) identifierOrFunctionCall.getChildren().get(1);
        if (((TokenNode) identifierEndOrFunctionCall.getChildren().get(0)).token == null) {
            return analyzeIdentifier(token, identifierOrFunctionCall, scope);
        } else {
            NonTerminalNode arguments = (NonTerminalNode) identifierEndOrFunctionCall.getChildren().get(1);
            return analyzeFunctionCall(arguments, token, scope);
        }
    }

    private TypeExpression analyzeIdentifier(Token token, NonTerminalNode identifierOrFunctionCall, Scope scope) {
        Variable variable = scope.getVariable(token.name());
        if (variable == null) {
            if (!noDeclaredVariables[currentIndexMethod].contains(token.name())) {
                noDeclaredVariables[currentIndexMethod].add(token.name());
                errors = true;
                System.err.printf("The variable \"%s\" not declared. Error in line: %d.\n",
                        token.name(), token.line());
            }
            return null;
        }
        // Если используется в цикле, то меняем значение на true, чтобы потом не удалился код значения
        if (scope.isForOrWhileScope()) {
            if (removeImmutableExpression.containsKey(variable.getName())) {
                if (!removeImmutableExpression.get(variable.getName()).isEmpty()) {
                    List<ImmutableExpression> list = removeImmutableExpression.get(variable.getName());
                    if (!list.get(list.size()-1).forWhileUsed) {
                        list.add(new ImmutableExpression(list.remove(list.size()-1).node, true));
                    }
                }
            }
        }
        if (!variable.isMutable() && !scope.isForOrWhileScope()) { // const
            String value = variable.getValue();
            if (!variable.isAnnounced()) {
                errors = true;
                System.err.printf("The variable \"%s\" has no defined value. Error in line: %d.\n",
                        token.name(), token.line());
                return null;
            }
            TypeExpression typeExpression = new TypeExpression(variable.getType(), variable.isMutable(), value,
                    null, new HashSet<>(Set.of(token.name())));
            setStaticValueInNonTerminalNode(identifierOrFunctionCall, typeExpression);
            return typeExpression;
        } else {
            variable.setMutable(true); // из-за цикла while
            variable.setUsed(true);
        }
        return new TypeExpression(variable.getType(), variable.isMutable(), null, null, new HashSet<>(Set.of(token.name())));
    }

    private TypeExpression analyzeNumber(NonTerminalNode number) {
        Token token = ((TokenNode) number.getChildren().get(0)).token;
        switch (token.type()) {
            case NUMBER, FLOAT_NUMBER -> {
                String value = getRemoveZerosAndGetNumber(token);

                if (token.type() == TokenType.NUMBER) {
                    try {
                        Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: Integer value exceeds the 32-bit storage limit (-2,147,483,648 to 2,147,483,647).");
                        errors = true;
                    }
                } else {
                    try {
                        float f = Float.parseFloat(value);
                        if (Float.isInfinite(f)) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        System.err.println("Error: Float value exceeds the 32-bit storage limit (approximately ±3.4e38).");
                        errors = true;
                    }
                }

                TypeExpression typeExpression = new TypeExpression(token.type(), false, value, null, new HashSet<>());
                setStaticValueInNonTerminalNode(number, typeExpression);
                return typeExpression;
            }
            default -> throw new RuntimeException();
        }
    }

    // Удаление ненужных нулей
    private String getRemoveZerosAndGetNumber(Token token) {
        String value = token.name();
        if (token.type() == TokenType.FLOAT_NUMBER) value+='0';
        int left = 0;
        int right = value.length();
        while (left != value.length()-1 && value.charAt(left) == '0') left++;
        if (value.charAt(left) == '.') left--;
        if (token.type() == TokenType.FLOAT_NUMBER) {
            while (right - 1 != left && value.charAt(right-1) == '0') right--;
        }
        if (value.charAt(right-1) == '.') right++;
        value = value.substring(left, right);
        return value;
    }

    private TypeExpression analyzeBooleanLiteral(NonTerminalNode booleanLiteral) {
        Token token = ((TokenNode) booleanLiteral.getChildren().get(0)).token;
        TypeExpression typeExpression;
        switch (token.type()) {
            case TRUE -> {
                typeExpression = new TypeExpression(TokenType.BOOLEAN, false, "true", null, new HashSet<>());
            }
            case FALSE -> {
                typeExpression = new TypeExpression(TokenType.BOOLEAN, false, "false", null, new HashSet<>());
            }
            default -> throw new RuntimeException();
        }
        setStaticValueInNonTerminalNode(booleanLiteral, typeExpression);
        return typeExpression;
    }

    private record TypeExpression(TokenType type, boolean mutable, String value, TokenType op, Set<String> variables) {}
    private record DataStatement(boolean hasReturn){}


    private void setStaticValueInNonTerminalNode(NonTerminalNode nonTerminalNode, TypeExpression typeExpression) {
        if (typeExpression == null) return;
        if (typeExpression.mutable) return;

        nonTerminalNode.setValueAndType(typeExpression.value, typeExpression.type);
    }
}
