package ru.krizhanovskiy.semantic_analyzer;

import ru.krizhanovskiy.ast.NonTerminalNode;
import ru.krizhanovskiy.ast.TokenNode;
import ru.krizhanovskiy.lexer.token.Token;
import ru.krizhanovskiy.lexer.token.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SemanticAnalyzer {
    private final NonTerminalNode rootAST;
    private final List<Method> methods = new ArrayList<>();
    private final List<NonTerminalNode> statementsMethod = new ArrayList<>();

    public SemanticAnalyzer(NonTerminalNode rootAST) {
        this.rootAST = rootAST;
    }

    public void analyze() {
        analyzeMethod((NonTerminalNode) rootAST.getChildren().get(0));
        for (NonTerminalNode statements : statementsMethod) {
            analyzeStatements(statements);
        }
    }

    private void analyzeMethod(NonTerminalNode method) {
        TokenType returnType = getType((NonTerminalNode) method.getChildren().get(0));
        String name = ((TokenNode) method.getChildren().get(1)).token.name();

        List<Variable> parameters = analyzeParameters((NonTerminalNode) method.getChildren().get(3));

        methods.add(new Method(returnType, parameters, name));
        statementsMethod.add((NonTerminalNode) method.getChildren().get(6));

        NonTerminalNode methodOptional = (NonTerminalNode) method.getPrev().getChildren().get(1);
        if (methodOptional.getChildren().get(0) instanceof NonTerminalNode) {
            analyzeMethod((NonTerminalNode) methodOptional.getChildren().get(0));
        }
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
        String name = ((TokenNode) parameter.getChildren().get(1)).token.name();
        List<Variable> list = new ArrayList<>();
        list.add(new Variable(type, name));
        return list;
    }

    private TokenType getType(NonTerminalNode returnTypeOrType) {
        if (returnTypeOrType.getChildren().get(0) instanceof NonTerminalNode) { // returnType
            return ((TokenNode) ((NonTerminalNode) returnTypeOrType.getChildren().get(0))
                    .getChildren().get(0)).token.type();
        }
        return ((TokenNode) returnTypeOrType.getChildren().get(0)).token.type();
    }

    private void analyzeStatements(NonTerminalNode statements) {
        if (statements.getChildren().get(0) instanceof NonTerminalNode) { // statement
            analyzeStatement((NonTerminalNode) statements.getChildren().get(0));
            analyzeStatements((NonTerminalNode) statements.getChildren().get(1));
        }
    }

    private void analyzeStatement(NonTerminalNode statement) {
        if (statement.getChildren().get(0) instanceof NonTerminalNode current) {
            String firstNonTerminal = current.name;
            switch (firstNonTerminal) {
                case "declaration" -> {
                    analyzeDeclaration(current);
                }
                case "assignment-or-function-call" -> {
                    analyzeAssignmentOrFunctionCall(current);
                }
                case "if-statement" -> {
                    analyzeIfStatement(current);
                }
                case "while-loop" -> {
                    analyzeWhileStatement(current);
                }
                case "for-loop" -> {
                    forLoop(current);
                }
                case "return-statement" -> {
                    returnStatement(current);
                }
                default -> throw new RuntimeException(); // Grammar Error

            }
            return;
        }
        if (((TokenNode) statement.getChildren().get(0)).token == null) return; // ε
        switch (((TokenNode) statement.getChildren().get(0)).token.type()) {
            case OPEN_CURLY_BRACKET: {
                analyzeStatements((NonTerminalNode) statement.getChildren().get(1));
                return;
            }
            case BREAK: {
                analyzeBreak((NonTerminalNode) statement);
                return;
            }
            case CONTINUE: {
                analyzeContinue((NonTerminalNode) statement);
                return;
            }

        }
    }

    private void analyzeDeclaration(NonTerminalNode declaration) {
        TokenType returnType = getType((NonTerminalNode) declaration.getChildren().get(0));
        String name = ((TokenNode) declaration.getChildren().get(1)).token.name();

        NonTerminalNode optionalAssignment = (NonTerminalNode) declaration.getPrev().getChildren().get(1);
        if (((TokenNode) optionalAssignment.getChildren().get(0)).token != null) {
            analyzeExpression((NonTerminalNode) optionalAssignment.getChildren().get(1));
        }
    }

    private void analyzeAssignmentOrFunctionCall(NonTerminalNode assignmentOrFunctionCall) {
        Token identity = ((TokenNode) assignmentOrFunctionCall.getChildren().get(0)).token;
        NonTerminalNode continueAssignmentOrFunctionCall = (NonTerminalNode) assignmentOrFunctionCall.getChildren().get(1);
        NonTerminalNode next = (NonTerminalNode) continueAssignmentOrFunctionCall.getChildren().get(1);
        if (((TokenNode) continueAssignmentOrFunctionCall.getChildren().get(0)).token.type() == TokenType.EQUALS) {
            analyzeExpression(next);
        } else {
            analyzeFunctionCall(next, identity);
        }
    }

    private void analyzeFunctionCall(NonTerminalNode arguments, Token identity) {
        // TODO: !!!!!!!!!!! логика не продумана !!!!!!!!!!!!!!!
        List<Method> currentMethods = methods.stream().filter(method -> method.name().equals(identity.name())).toList();
        if (identity.name().equals("print") || identity.name().equals("input")) {
            // TODO: добавить логику
        } else {
            if (currentMethods.isEmpty()) {
                System.err.println("Method not found");
                return;
            }
        }

        if (arguments.getChildren().get(0) instanceof NonTerminalNode argumentList) { // expression
            List<NonTerminalNode> expressions = new ArrayList<>();

            do {
                NonTerminalNode expression = (NonTerminalNode) argumentList.getChildren().get(0);
                expressions.add(expression);
                argumentList = (NonTerminalNode) argumentList.getChildren().get(1); // argument-list-optional
                if (((TokenNode) argumentList.getChildren().get(0)).token == null) break;
                argumentList = (NonTerminalNode) argumentList.getChildren().get(1);
            } while (true);
            Optional<Method> currentMethod = currentMethods.stream().filter(method -> method.parameters().size() == expressions.size()).findFirst();
            if (currentMethod.isEmpty()) {
                System.err.println("No method found with given number of arguments");
                return;
            }
            Method method = currentMethod.get();
            for (int i = 0; i < expressions.size(); i++) {
                analyzeExpression(expressions.get(i));
                // TODO: Проверка типа аргумента
            }
            return;
        }
        if (((TokenNode) arguments.getChildren().get(0)).token != null) { // STRING
            if (identity.name().equals("print")) {
                // TODO: Код для вывода строки в консоль
            }
        }
    }

    private void analyzeBreak(NonTerminalNode statement) {
        // Удаление кода за break
        NonTerminalNode statementOptionalUp = statement.getPrev();
        NonTerminalNode statementOptionalDown = (NonTerminalNode) statementOptionalUp.getChildren().get(1);
        statementOptionalDown.setChildren(List.of(new TokenNode(null)));


    }

    private void analyzeContinue(NonTerminalNode statement) {
        // Удаление кода за continue
        NonTerminalNode statementOptionalUp = statement.getPrev();
        NonTerminalNode statementOptionalDown = (NonTerminalNode) statementOptionalUp.getChildren().get(1);
        statementOptionalDown.setChildren(List.of(new TokenNode(null)));


    }

    private void analyzeIfStatement(NonTerminalNode ifStatement) {
        NonTerminalNode expression = (NonTerminalNode) ifStatement.getChildren().get(2);
        analyzeExpression(expression);
        // TODO: Проверка типа на boolean
        NonTerminalNode statement = (NonTerminalNode) ifStatement.getChildren().get(4);
        analyzeStatement(statement);
        NonTerminalNode elseOptional = (NonTerminalNode) ifStatement.getChildren().get(5);
        if (((TokenNode) elseOptional.getChildren().get(0)).token != null) {
            NonTerminalNode elseStatement = (NonTerminalNode) elseOptional.getChildren().get(1);
            analyzeStatement(elseStatement);
        }
    }

    private void analyzeWhileStatement(NonTerminalNode whileStatement) {
        if (((TokenNode) whileStatement.getChildren().get(0)).token.type() == TokenType.WHILE) {
            NonTerminalNode expression = (NonTerminalNode) whileStatement.getChildren().get(2);
            analyzeExpression(expression);
            // TODO: Проверка типа на boolean
            NonTerminalNode statement = (NonTerminalNode) whileStatement.getChildren().get(4);
            analyzeStatement(statement);
        } else { // do {} while
            NonTerminalNode expression = (NonTerminalNode) whileStatement.getChildren().get(4);
            analyzeExpression(expression);
            // TODO: Проверка типа на boolean
            NonTerminalNode statement = (NonTerminalNode) whileStatement.getChildren().get(1);
            analyzeStatement(statement);
        }
    }

    private void forLoop(NonTerminalNode forLoop) {
        NonTerminalNode declarationAndAssigment = (NonTerminalNode) forLoop.getChildren().get(2);
        NonTerminalNode expression = (NonTerminalNode) forLoop.getChildren().get(4);
        analyzeExpression(expression);
        // TODO: Проверка типа на boolean
        NonTerminalNode assignment = (NonTerminalNode) forLoop.getChildren().get(6);
        NonTerminalNode statement = (NonTerminalNode) forLoop.getChildren().get(8);
        analyzeStatement(statement);

    }

    private void returnStatement(NonTerminalNode returnStatement) {
        NonTerminalNode returnExpressionOrVoid = (NonTerminalNode) returnStatement.getChildren().get(1);
        TokenType returnType;
        if (returnExpressionOrVoid.getChildren().get(0) instanceof TokenNode) {
            returnType = TokenType.VOID;
        } else {
            NonTerminalNode expression = (NonTerminalNode) returnExpressionOrVoid.getChildren().get(0);
            // TODO: returnType = type(expression)
        }
        // TODO: проверка текущего выхода из метода его типу
    }

    private void analyzeExpression(NonTerminalNode expression) {
        NonTerminalNode logicalOrExpression = (NonTerminalNode) expression.getChildren().get(0);
        analyzeLogicalOrExpression(logicalOrExpression);
    }

    private void analyzeLogicalOrExpression(NonTerminalNode logicalOrExpression) {
        NonTerminalNode logicalAndExpression = (NonTerminalNode) logicalOrExpression.getChildren().get(0);
        analyzeLogicalAndExpression(logicalAndExpression);
        NonTerminalNode logicalOrTail = (NonTerminalNode) logicalOrExpression.getChildren().get(1);
        analyzeLogicalOrTail(logicalOrTail);
    }

    private void analyzeLogicalOrTail(NonTerminalNode logicalOrTail) { // ε | "||" <logical-and-expression> <logical-or-tail>
        if (((TokenNode) logicalOrTail.getChildren().get(0)).token != null) {
            NonTerminalNode logicalAndExpression = (NonTerminalNode) logicalOrTail.getChildren().get(1);
            analyzeLogicalAndExpression(logicalAndExpression);
            NonTerminalNode logicalOrTail2 = (NonTerminalNode) logicalOrTail.getChildren().get(2);
            analyzeLogicalOrTail(logicalOrTail2);
        }
    }

    private void analyzeLogicalAndExpression(NonTerminalNode logicalAndExpression) {
        NonTerminalNode equalityExpression = (NonTerminalNode) logicalAndExpression.getChildren().get(0);
        analyzeEqualityExpression(equalityExpression);
        NonTerminalNode logicalAndTail = (NonTerminalNode) logicalAndExpression.getChildren().get(1);
        analyzeLogicalAndTail(logicalAndTail);
    }

    private void analyzeLogicalAndTail(NonTerminalNode logicalAndTail) {
        if (((TokenNode) logicalAndTail.getChildren().get(0)).token != null) {
            NonTerminalNode equalityExpression = (NonTerminalNode) logicalAndTail.getChildren().get(1);
            analyzeEqualityExpression(equalityExpression);
            NonTerminalNode logicalAndTail2 = (NonTerminalNode) logicalAndTail.getChildren().get(2);
            analyzeLogicalAndTail(logicalAndTail2);
        }
    }

    private void analyzeEqualityExpression(NonTerminalNode equalityExpression) {
        NonTerminalNode relationalExpression = (NonTerminalNode) equalityExpression.getChildren().get(0);
        analyzeRelationalExpression(relationalExpression);
        NonTerminalNode equalityTail = (NonTerminalNode) equalityExpression.getChildren().get(1);
        analyzeEqualityTail(equalityTail);
    }

    private void analyzeEqualityTail(NonTerminalNode equalityTail) {
        Token token = ((TokenNode) equalityTail.getChildren().get(0)).token;
        if (token != null) {
            switch (token.type()) {
                case DOUBLE_EQUAL -> {

                }
                case NOT_EQUALS -> {

                }
                default -> throw new RuntimeException(); // Ошибка грамматики
            }

            NonTerminalNode relationalExpression = (NonTerminalNode) equalityTail.getChildren().get(1);
            analyzeRelationalExpression(relationalExpression);
            NonTerminalNode equalityTail2 = (NonTerminalNode) equalityTail.getChildren().get(2);
            analyzeEqualityTail(equalityTail2);
        }
    }

    private void analyzeRelationalExpression(NonTerminalNode relationalExpression) {
        NonTerminalNode additiveExpression = (NonTerminalNode) relationalExpression.getChildren().get(0);
        analyzeAdditiveExpression(additiveExpression);
        NonTerminalNode relationalTail = (NonTerminalNode) relationalExpression.getChildren().get(1);
        analyzeRelationalTail(relationalTail);
    }

    private void analyzeRelationalTail(NonTerminalNode relationalTail) {
        Token token = ((TokenNode) relationalTail.getChildren().get(0)).token;
        if (token != null) {
            switch (token.type()) {
                case LESS_THAN -> {

                }
                case GREATER_THAN -> {

                }
                case LESS_THAN_EQUALS -> {

                }
                case GREATER_THAN_EQUALS -> {

                }
                default -> throw new RuntimeException(); // Ошибка грамматики
            }

            NonTerminalNode additiveExpression = (NonTerminalNode) relationalTail.getChildren().get(1);
            analyzeAdditiveExpression(additiveExpression);
            NonTerminalNode relationalTail2 = (NonTerminalNode) relationalTail.getChildren().get(2);
            analyzeRelationalTail(relationalTail2);
        }
    }

    private void analyzeAdditiveExpression(NonTerminalNode additiveExpression) {
        NonTerminalNode multiplicativeExpression = (NonTerminalNode) additiveExpression.getChildren().get(0);
        analyzeMultiplicativeExpression(multiplicativeExpression);
        NonTerminalNode additiveTail = (NonTerminalNode) additiveExpression.getChildren().get(1);
        analyzeAdditiveTail(additiveTail);
    }

    private void analyzeAdditiveTail(NonTerminalNode additiveTail) {
        Token token = ((TokenNode) additiveTail.getChildren().get(0)).token;
        if (token != null) {
            switch (token.type()) {
                case PLUS -> {

                }
                case MINUS -> {

                }
                default -> throw new RuntimeException(); // Ошибка грамматики
            }

            NonTerminalNode multiplicativeExpression = (NonTerminalNode) additiveTail.getChildren().get(1);
            analyzeMultiplicativeExpression(multiplicativeExpression);
            NonTerminalNode additiveTail2 = (NonTerminalNode) additiveTail.getChildren().get(2);
            analyzeAdditiveTail(additiveTail2);
        }
    }

    private void analyzeMultiplicativeExpression(NonTerminalNode multiplicativeExpression) {
        NonTerminalNode unaryExpression = (NonTerminalNode) multiplicativeExpression.getChildren().get(0);
        analyzeUnaryExpression(unaryExpression);
        NonTerminalNode multiplicativeTail = (NonTerminalNode) multiplicativeExpression.getChildren().get(1);
        analyzeMultiplicativeTail(multiplicativeTail);
    }

    private void analyzeMultiplicativeTail(NonTerminalNode multiplicativeTail) {
        Token token = ((TokenNode) multiplicativeTail.getChildren().get(0)).token;
        if (token != null) {
            switch (token.type()) {
                case MULTIPLICATION -> {

                }
                case DIVISION -> {

                }
                default -> throw new RuntimeException(); // Ошибка грамматики
            }

            NonTerminalNode unaryExpression = (NonTerminalNode) multiplicativeTail.getChildren().get(1);
            analyzeUnaryExpression(unaryExpression);
            NonTerminalNode multiplicativeTail2 = (NonTerminalNode) multiplicativeTail.getChildren().get(2);
            analyzeMultiplicativeTail(multiplicativeTail2);
        }
    }

    private void analyzeUnaryExpression(NonTerminalNode unaryExpression) {
        if (unaryExpression.getChildren().get(0) instanceof NonTerminalNode primaryExpression) {
            analyzePrimaryExpression(primaryExpression);
        } else {
            TokenType tokenType = ((TokenNode) unaryExpression.getChildren().get(0)).token.type();
            switch (tokenType) {
                case MINUS -> {

                }
                case NOT -> {

                }
                default -> throw new RuntimeException(); // Ошибка грамматики
            }
        }
    }

    private void analyzePrimaryExpression(NonTerminalNode primaryExpression) {
        if (primaryExpression.getChildren().get(0) instanceof TokenNode) { // "(" <expression> ")"
            NonTerminalNode expression = (NonTerminalNode) primaryExpression.getChildren().get(1);
        } else {
            NonTerminalNode current = (NonTerminalNode) primaryExpression.getChildren().get(0);
            switch (current.name) {
                case "identifier-or-function-call" -> {

                }
                case "number" -> {
                    analyzeNumber(current);
                }
                case "boolean-literal" -> {
                    analyzeBooleanLiteral(current);
                }
            }
        }
    }

    private void analyzeIdentifierOrFunctionCall(NonTerminalNode identifierOrFunctionCall) {

    }

    private TypeExpression analyzeNumber(NonTerminalNode number) {
        Token token = ((TokenNode) number.getChildren().get(0)).token;
        switch (token.type()) {
            case NUMBER, FLOAT_NUMBER -> {
                return new TypeExpression(token.type(), false);
            }
            default -> throw new RuntimeException();
        }
    }

    private TypeExpression analyzeBooleanLiteral(NonTerminalNode booleanLiteral) {
        Token token = ((TokenNode) booleanLiteral.getChildren().get(0)).token;
        switch (token.type()) {
            case TRUE, FALSE -> {
                return new TypeExpression(token.type(), false);
            }
            default -> throw new RuntimeException();
        }
    }

    private record TypeExpression(TokenType type, boolean mutable) {
    }

}