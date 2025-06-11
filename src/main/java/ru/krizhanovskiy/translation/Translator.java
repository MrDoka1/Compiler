package ru.krizhanovskiy.translation;

import javassist.*;
import ru.krizhanovskiy.ast.NonTerminalNode;
import ru.krizhanovskiy.ast.TokenNode;
import ru.krizhanovskiy.lexer.token.TokenType;
import ru.krizhanovskiy.lexer.token.Token;
import ru.krizhanovskiy.semantic_analyzer.Method;
import ru.krizhanovskiy.semantic_analyzer.Variable;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Translator {
    private final NonTerminalNode ast;
    private final List<Method> methods;
    private CtClass mainClass;
    private ClassPool pool;
    private boolean hasMainMethod;

    public Translator(NonTerminalNode ast, List<Method> methods) {
        this.ast = ast;
        this.methods = methods;
        this.pool = ClassPool.getDefault();
        this.hasMainMethod = false;
    }

    public void translate() throws Exception {
        // Create the Main class
        pool.insertClassPath(new ClassClassPath(this.getClass()));
        mainClass = pool.makeClass("Main");

        // Add Scanner field
        CtField scannerField = CtField.make(
                "private static final java.util.Scanner scanner = new java.util.Scanner(System.in);",
                mainClass
        );
        mainClass.addField(scannerField);

        addBaseMethods();

        // Declare all methods first (signatures only)
        declareMethods();

        // Translate method bodies
        translateMethodBodies();

        // Add base methods after (to ensure main() exists if it’s in the AST)
        addBaseMethods2();

        // Write the class file
        mainClass.writeFile();
    }

    private void addBaseMethods() throws Exception {
        // intInput method
        CtMethod intInputMethod = CtMethod.make(
                "private static int intInput() { return scanner.nextInt(); }",
                mainClass
        );
        mainClass.addMethod(intInputMethod);

        // floatInput method
        CtMethod floatInputMethod = CtMethod.make(
                "private static float floatInput() { return scanner.nextFloat(); }",
                mainClass
        );
        mainClass.addMethod(floatInputMethod);

        // print methods
        CtMethod printStringMethod = CtMethod.make(
                "private static void print(String s) { System.out.println(s); }",
                mainClass
        );
        mainClass.addMethod(printStringMethod);

        CtMethod printIntMethod = CtMethod.make(
                "private static void print(int s) { System.out.println(s); }",
                mainClass
        );
        mainClass.addMethod(printIntMethod);

        CtMethod printFloatMethod = CtMethod.make(
                "private static void print(float s) { System.out.println(s); }",
                mainClass
        );
        mainClass.addMethod(printFloatMethod);

        CtMethod printBooleanMethod = CtMethod.make(
                "private static void print(boolean s) { System.out.println(s); }",
                mainClass
        );
        mainClass.addMethod(printBooleanMethod);
    }

    private void addBaseMethods2() throws Exception {
        // Add entry point main method only if main() exists
        if (hasMainMethod) {
            CtMethod mainMethod = CtMethod.make(
                    "public static void main(String[] args) { main(); }",
                    mainClass
            );
            mainClass.addMethod(mainMethod);
        } else {
            throw new CannotCompileException("No main() method found in the AST");
        }
    }

    private void declareMethods() throws Exception {
        NonTerminalNode current = (NonTerminalNode) ast.getChildren().get(0); // First method
        int methodIndex = 0;

        while (current != null && current.getChildren().get(0) instanceof NonTerminalNode) {
            if (methodIndex >= methods.size()) {
                throw new IllegalStateException("Mismatch: More AST method nodes than methods in list");
            }
            Method method = methods.get(methodIndex);
            if (method.name().equals("main") && method.parameters().isEmpty()) {
                hasMainMethod = true;
            }
            declareMethod(current, method);
            NonTerminalNode methodOptional = (NonTerminalNode) current.getPrev().getChildren().get(1);
            current = (methodOptional.getChildren().get(0) instanceof NonTerminalNode) ?
                    (NonTerminalNode) methodOptional.getChildren().get(0) : null;
            methodIndex++;
        }
        if (methodIndex < methods.size()) {
            throw new IllegalStateException("Mismatch: Fewer AST method nodes than methods in list");
        }
    }

    private void declareMethod(NonTerminalNode methodNode, Method method) throws Exception {
        String returnType = getJavaType(method.returnType());
        StringBuilder methodSignature = new StringBuilder("private static ");
        methodSignature.append(returnType).append(" ").append(method.name()).append("(");

        // Add parameters
        List<Variable> parameters = method.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) methodSignature.append(", ");
            methodSignature.append(getJavaType(parameters.get(i).getType()))
                    .append(" ")
                    .append(parameters.get(i).getName());
        }
        methodSignature.append(") {");

        // Add default return for non-void methods
        if (method.returnType() != TokenType.VOID) {
            methodSignature.append(" return ").append(getDefaultReturnValue(method.returnType())).append(";");
        }
        methodSignature.append("}");

        CtMethod ctMethod = CtMethod.make(methodSignature.toString(), mainClass);
        mainClass.addMethod(ctMethod);
    }

    private void translateMethodBodies() throws Exception {
        NonTerminalNode current = (NonTerminalNode) ast.getChildren().get(0); // First method
        int methodIndex = 0;

        while (current != null && current.getChildren().get(0) instanceof NonTerminalNode) {
            if (methodIndex >= methods.size()) {
                throw new IllegalStateException("Mismatch: More AST method nodes than methods in list");
            }
            translateMethodBody(current, methods.get(methodIndex));
            NonTerminalNode methodOptional = (NonTerminalNode) current.getPrev().getChildren().get(1);
            current = (methodOptional.getChildren().get(0) instanceof NonTerminalNode) ?
                    (NonTerminalNode) methodOptional.getChildren().get(0) : null;
            methodIndex++;
        }
        if (methodIndex < methods.size()) {
            throw new IllegalStateException("Mismatch: Fewer AST method nodes than methods in list");
        }
    }

    private void translateMethodBody(NonTerminalNode methodNode, Method method) throws Exception {
        String returnType = getJavaType(method.returnType());
        StringBuilder methodSignature = new StringBuilder("private static ");
        methodSignature.append(returnType).append(" ").append(method.name()).append("(");

        // Add parameters
        List<Variable> parameters = method.parameters();
        Set<String> parameterNames = new HashSet<>();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) methodSignature.append(", ");
            String paramType = getJavaType(parameters.get(i).getType());
            if (paramType == null || paramType.isEmpty()) {
                throw new IllegalStateException("Invalid parameter type for parameter: " + parameters.get(i).getName());
            }
            methodSignature.append(paramType)
                    .append(" ")
                    .append(parameters.get(i).getName());
            parameterNames.add(parameters.get(i).getName());
        }
        methodSignature.append(") {");

        // Method body
        StringBuilder methodBody = new StringBuilder();
        methodBody.append("\n");
        NonTerminalNode statements = (NonTerminalNode) methodNode.getChildren().get(6);
        translateStatements(statements, methodBody, method, parameterNames);

        methodBody.append("}");

        // Combine signature and body
        String fullMethodCode = methodSignature.toString() + methodBody.toString();

        // Find and remove the existing method
        CtMethod ctMethod = findMethodBySignature(method);
        if (ctMethod == null) {
            throw new IllegalStateException("Method not found: " + method.name() + " with " + parameters.size() + " parameters");
        }
        try {
            mainClass.removeMethod(ctMethod);
        } catch (NotFoundException e) {
            System.err.println("Failed to remove method: " + method.name());
            throw new NotFoundException("Failed to remove method: " + method.name(), e);
        }

        // Create and add the new method
        try {
            CtMethod newMethod = CtMethod.make(fullMethodCode, mainClass);
            mainClass.addMethod(newMethod);
        } catch (CannotCompileException e) {
            System.err.println("Failed to compile new method: " + method.name() + "\nCode: " + fullMethodCode);
            throw new CannotCompileException("Failed to compile new method: " + method.name() + "\nCode: " + fullMethodCode, e);
        }
    }

    private CtMethod findMethodBySignature(Method method) throws NotFoundException {
        CtMethod[] declaredMethods = mainClass.getDeclaredMethods(method.name());
        for (CtMethod ctMethod : declaredMethods) {
            CtClass[] paramTypes = ctMethod.getParameterTypes();
            List<Variable> methodParams = method.parameters();
            if (paramTypes.length == methodParams.size()) {
                boolean match = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    String expectedType = getJavaType(methodParams.get(i).getType());
                    if (!paramTypes[i].getName().equals(expectedType)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return ctMethod;
                }
            }
        }
        return null;
    }

    private void translateStatements(NonTerminalNode statements, StringBuilder methodBody, Method method, Set<String> parameterNames) throws Exception {
        if (statements.getChildren().get(0) instanceof NonTerminalNode) {
            translateStatement((NonTerminalNode) statements.getChildren().get(0), methodBody, method, parameterNames);
            translateStatements((NonTerminalNode) statements.getChildren().get(1), methodBody, method, parameterNames);
        }
    }

    private void translateStatement(NonTerminalNode statement, StringBuilder methodBody, Method method, Set<String> parameterNames) throws Exception {
        if (statement.getChildren().get(0) instanceof NonTerminalNode current) {
            switch (current.name) {
                case "declaration":
                    translateDeclaration(current, methodBody, statement.getPrev(), parameterNames);
                    break;
                case "assignment-or-function-call":
                    translateAssignmentOrFunctionCall(current, methodBody, method, parameterNames);
                    break;
                case "if-statement":
                    translateIfStatement(current, methodBody, method, parameterNames);
                    break;
                case "while-loop":
                    translateWhileStatement(current, methodBody, method, parameterNames);
                    break;
                case "for-loop":
                    translateForLoop(current, methodBody, method, parameterNames);
                    break;
                case "return-statement":
                    translateReturnStatement(current, methodBody, method, parameterNames);
                    break;
            }
        } else {
            TokenNode tokenNode = (TokenNode) statement.getChildren().get(0);
            if (tokenNode.token == null) return; // ε
            switch (tokenNode.token.type()) {
                case OPEN_CURLY_BRACKET:
                    methodBody.append("{\n");
                    translateStatements((NonTerminalNode) statement.getChildren().get(1), methodBody, method, parameterNames);
                    methodBody.append("}\n");
                    break;
                case BREAK:
                    methodBody.append("break;\n");
                    break;
                case CONTINUE:
                    methodBody.append("continue;\n");
                    break;
            }
        }
    }

    private void translateDeclaration(NonTerminalNode declaration, StringBuilder methodBody, NonTerminalNode statement, Set<String> parameterNames) throws Exception {
        TokenType type = getType((NonTerminalNode) declaration.getChildren().get(0));
        String name = ((TokenNode) declaration.getChildren().get(1)).token.name();
        methodBody.append(getJavaType(type)).append(" ").append(name);

        if (declaration.getPrev().name.equals("declaration-and-assigment")) {
            methodBody.append(" = ");
            translateExpression((NonTerminalNode) declaration.getPrev().getChildren().get(2), methodBody, parameterNames);
        } else if (declaration.getPrev().getChildren().get(1) instanceof NonTerminalNode optionalAssignment) {
            if (((TokenNode) optionalAssignment.getChildren().get(0)).token != null) {
                methodBody.append(" = ");
                translateExpression((NonTerminalNode) optionalAssignment.getChildren().get(1), methodBody, parameterNames);
            }
        }
        methodBody.append(";\n");
    }

    private void translateAssignmentOrFunctionCall(NonTerminalNode node, StringBuilder methodBody, Method method, Set<String> parameterNames) throws Exception {
        Token identity = ((TokenNode) node.getChildren().get(0)).token;
        NonTerminalNode continueNode = (NonTerminalNode) node.getChildren().get(1);
        if (((TokenNode) continueNode.getChildren().get(0)).token.type() == TokenType.EQUALS) {
            /*if (!parameterNames.contains(identity.name())) {
                throw new IllegalStateException("Assignment to undefined variable: " + identity.name());
            }*/
            methodBody.append(identity.name()).append(" = ");
            translateExpression((NonTerminalNode) continueNode.getChildren().get(1), methodBody, parameterNames);
            methodBody.append(";\n");
        } else {
            methodBody.append(identity.name()).append("(");
            translateArguments((NonTerminalNode) continueNode.getChildren().get(1), methodBody, parameterNames);
            methodBody.append(");\n");
        }
    }

    private void translateIfStatement(NonTerminalNode ifStatement, StringBuilder methodBody, Method method, Set<String> parameterNames) throws Exception {
        methodBody.append("if (");
        translateExpression((NonTerminalNode) ifStatement.getChildren().get(2), methodBody, parameterNames);
        methodBody.append(") {\n");
        translateStatement((NonTerminalNode) ifStatement.getChildren().get(4), methodBody, method, parameterNames);
        methodBody.append("}");

        NonTerminalNode elseOptional = (NonTerminalNode) ifStatement.getChildren().get(5);
        if (((TokenNode) elseOptional.getChildren().get(0)).token != null) {
            methodBody.append(" else {\n");
            translateStatement((NonTerminalNode) elseOptional.getChildren().get(1), methodBody, method, parameterNames);
            methodBody.append("}\n");
        } else {
            methodBody.append("\n");
        }
    }

    private void translateWhileStatement(NonTerminalNode whileStatement, StringBuilder methodBody, Method method, Set<String> parameterNames) throws Exception {
        Token token = ((TokenNode) whileStatement.getChildren().get(0)).token;
        if (token.type() == TokenType.WHILE) {
            methodBody.append("while (");
            translateExpression((NonTerminalNode) whileStatement.getChildren().get(2), methodBody, parameterNames);
            methodBody.append(") {\n");
            translateStatement((NonTerminalNode) whileStatement.getChildren().get(4), methodBody, method, parameterNames);
            methodBody.append("}\n");
        } else { // do-while
            methodBody.append("do {\n");
            translateStatement((NonTerminalNode) whileStatement.getChildren().get(1), methodBody, method, parameterNames);
            methodBody.append("} while (");
            translateExpression((NonTerminalNode) whileStatement.getChildren().get(4), methodBody, parameterNames);
            methodBody.append(");\n");
        }
    }

    private void translateForLoop(NonTerminalNode forLoop, StringBuilder methodBody, Method method, Set<String> parameterNames) throws Exception {
        methodBody.append("for (");
        NonTerminalNode declAssignment = (NonTerminalNode) forLoop.getChildren().get(2);
        translateDeclaration((NonTerminalNode) declAssignment.getChildren().get(0), methodBody, declAssignment, parameterNames);
        methodBody.setLength(methodBody.length() - 2); // Remove semicolon
        methodBody.append("; ");
        translateExpression((NonTerminalNode) forLoop.getChildren().get(4), methodBody, parameterNames);
        methodBody.append("; ");
        NonTerminalNode assignment = (NonTerminalNode) forLoop.getChildren().get(6);
        methodBody.append(((TokenNode) assignment.getChildren().get(0)).token.name()).append(" = ");
        translateExpression((NonTerminalNode) assignment.getChildren().get(2), methodBody, parameterNames);
        methodBody.append(") {\n");
        translateStatement((NonTerminalNode) forLoop.getChildren().get(8), methodBody, method, parameterNames);
        methodBody.append("}\n");
    }

    private void translateReturnStatement(NonTerminalNode returnStatement, StringBuilder methodBody, Method method, Set<String> parameterNames) throws Exception {
        NonTerminalNode returnExpr = (NonTerminalNode) returnStatement.getChildren().get(1);
        methodBody.append("return");
        if (returnExpr.getChildren().get(0) instanceof NonTerminalNode) {
            methodBody.append(" ");
            translateExpression((NonTerminalNode) returnExpr.getChildren().get(0), methodBody, parameterNames);
        }
        methodBody.append(";\n");
    }

    private void translateExpression(NonTerminalNode expression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        NonTerminalNode logicalOrExpression = (NonTerminalNode) expression.getChildren().get(0);
        if (logicalOrExpression.getValue() != null) {
            methodBody.append(getValue(logicalOrExpression));
        } else {
            translateLogicalOrExpression(logicalOrExpression, methodBody, parameterNames);
        }
    }

    private void translateLogicalOrExpression(NonTerminalNode logicalOrExpression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        NonTerminalNode logicalAndExpression = (NonTerminalNode) logicalOrExpression.getChildren().get(0);
        NonTerminalNode logicalOrTail = (NonTerminalNode) logicalOrExpression.getChildren().get(1);
        if (logicalOrExpression.getValue() != null) {
            methodBody.append(getValue(logicalOrExpression));
        } else {
            if (((TokenNode) logicalOrTail.getChildren().get(0)).token != null) {
                translateLogicalOrTail(logicalOrTail, methodBody, parameterNames);
                methodBody.append(" || ");
            }
            translateLogicalAndExpression(logicalAndExpression, methodBody, parameterNames);
        }
    }

    private void translateLogicalOrTail(NonTerminalNode logicalOrTail, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (((TokenNode) logicalOrTail.getChildren().get(0)).token == null) return;
        NonTerminalNode logicalAndExpression = (NonTerminalNode) logicalOrTail.getChildren().get(1);
        NonTerminalNode nextTail = (NonTerminalNode) logicalOrTail.getChildren().get(2);
        if (((TokenNode) nextTail.getChildren().get(0)).token != null) {
            translateLogicalOrTail(nextTail, methodBody, parameterNames);
            methodBody.append(" || ");
        }
        translateLogicalAndExpression(logicalAndExpression, methodBody, parameterNames);
    }

    private void translateLogicalAndExpression(NonTerminalNode logicalAndExpression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        NonTerminalNode equalityExpression = (NonTerminalNode) logicalAndExpression.getChildren().get(0);
        NonTerminalNode logicalAndTail = (NonTerminalNode) logicalAndExpression.getChildren().get(1);
        if (logicalAndExpression.getValue() != null) {
            methodBody.append(getValue(logicalAndExpression));
        } else {
            if (((TokenNode) logicalAndTail.getChildren().get(0)).token != null) {
                translateLogicalAndTail(logicalAndTail, methodBody, parameterNames);
                methodBody.append(" && ");
            }
            translateEqualityExpression(equalityExpression, methodBody, parameterNames);
        }
    }

    private void translateLogicalAndTail(NonTerminalNode logicalAndTail, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (((TokenNode) logicalAndTail.getChildren().get(0)).token == null) return;
        NonTerminalNode equalityExpression = (NonTerminalNode) logicalAndTail.getChildren().get(1);
        NonTerminalNode nextTail = (NonTerminalNode) logicalAndTail.getChildren().get(2);
        if (((TokenNode) nextTail.getChildren().get(0)).token != null) {
            translateLogicalAndTail(nextTail, methodBody, parameterNames);
            methodBody.append(" && ");
        }
        translateEqualityExpression(equalityExpression, methodBody, parameterNames);
    }

    private void translateEqualityExpression(NonTerminalNode equalityExpression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        NonTerminalNode relationalExpression = (NonTerminalNode) equalityExpression.getChildren().get(0);
        NonTerminalNode equalityTail = (NonTerminalNode) equalityExpression.getChildren().get(1);
        if (equalityExpression.getValue() != null) {
            methodBody.append(getValue(equalityExpression));
        } else {
            if (((TokenNode) equalityTail.getChildren().get(0)).token != null) {
                translateEqualityTail(equalityTail, methodBody, parameterNames);
                TokenType op = ((TokenNode) equalityTail.getChildren().get(0)).token.type();
                methodBody.append(" ").append(op == TokenType.DOUBLE_EQUAL ? "==" : "!=").append(" ");
            }
            translateRelationalExpression(relationalExpression, methodBody, parameterNames);
        }
    }

    private void translateEqualityTail(NonTerminalNode equalityTail, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (((TokenNode) equalityTail.getChildren().get(0)).token == null) return;
        NonTerminalNode relationalExpression = (NonTerminalNode) equalityTail.getChildren().get(1);
        NonTerminalNode nextTail = (NonTerminalNode) equalityTail.getChildren().get(2);
        if (((TokenNode) nextTail.getChildren().get(0)).token != null) {
            translateEqualityTail(nextTail, methodBody, parameterNames);
            TokenType op = ((TokenNode) nextTail.getChildren().get(0)).token.type();
            methodBody.append(" ").append(op == TokenType.DOUBLE_EQUAL ? "==" : "!=").append(" ");
        }
        translateRelationalExpression(relationalExpression, methodBody, parameterNames);
    }

    private void translateRelationalExpression(NonTerminalNode relationalExpression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        NonTerminalNode additiveExpression = (NonTerminalNode) relationalExpression.getChildren().get(0);
        NonTerminalNode relationalTail = (NonTerminalNode) relationalExpression.getChildren().get(1);
        if (relationalExpression.getValue() != null) {
            methodBody.append(getValue(relationalExpression));
        } else {
            if (((TokenNode) relationalTail.getChildren().get(0)).token != null) {
                translateRelationalTail(relationalTail, methodBody, parameterNames);
                TokenType op = ((TokenNode) relationalTail.getChildren().get(0)).token.type();
                String opStr = switch (op) {
                    case LESS_THAN -> "<";
                    case GREATER_THAN -> ">";
                    case LESS_THAN_EQUALS -> "<=";
                    case GREATER_THAN_EQUALS -> ">=";
                    default -> throw new IllegalStateException("Invalid relational operator");
                };
                methodBody.append(" ").append(opStr).append(" ");
            }
            translateAdditiveExpression(additiveExpression, methodBody, parameterNames);
        }
    }

    private void translateRelationalTail(NonTerminalNode relationalTail, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (((TokenNode) relationalTail.getChildren().get(0)).token == null) return;
        NonTerminalNode additiveExpression = (NonTerminalNode) relationalTail.getChildren().get(1);
        NonTerminalNode nextTail = (NonTerminalNode) relationalTail.getChildren().get(2);
        if (((TokenNode) nextTail.getChildren().get(0)).token != null) {
            translateRelationalTail(nextTail, methodBody, parameterNames);
            TokenType op = ((TokenNode) relationalTail.getChildren().get(0)).token.type();
            String opStr = switch (op) {
                case LESS_THAN -> "<";
                case GREATER_THAN -> ">";
                case LESS_THAN_EQUALS -> "<=";
                case GREATER_THAN_EQUALS -> ">=";
                default -> throw new IllegalStateException("Invalid relational operator");
            };
            methodBody.append(" ").append(opStr).append(" ");
        }
        translateAdditiveExpression(additiveExpression, methodBody, parameterNames);
    }

    private void translateAdditiveExpression(NonTerminalNode additiveExpression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        NonTerminalNode multiplicativeExpression = (NonTerminalNode) additiveExpression.getChildren().get(0);
        NonTerminalNode additiveTail = (NonTerminalNode) additiveExpression.getChildren().get(1);
        if (additiveExpression.getValue() != null) {
            methodBody.append(getValue(additiveExpression));
        } else {
            if (((TokenNode) additiveTail.getChildren().get(0)).token != null) {
                translateAdditiveTail(additiveTail, methodBody, parameterNames);
                TokenType op = ((TokenNode) additiveTail.getChildren().get(0)).token.type();
                methodBody.append(" ").append(op == TokenType.PLUS ? "+" : "-").append(" ");
            }
            translateMultiplicativeExpression(multiplicativeExpression, methodBody, parameterNames);
        }
    }

    private void translateAdditiveTail(NonTerminalNode additiveTail, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (((TokenNode) additiveTail.getChildren().get(0)).token == null) return;
        NonTerminalNode multiplicativeExpression = (NonTerminalNode) additiveTail.getChildren().get(1);
        NonTerminalNode nextTail = (NonTerminalNode) additiveTail.getChildren().get(2);
        if (((TokenNode) nextTail.getChildren().get(0)).token != null) {
            translateAdditiveTail(nextTail, methodBody, parameterNames);
            TokenType op = ((TokenNode) additiveTail.getChildren().get(0)).token.type();
            methodBody.append(" ").append(op == TokenType.PLUS ? "+" : "-").append(" ");
        }
        translateMultiplicativeExpression(multiplicativeExpression, methodBody, parameterNames);
    }

    private void translateMultiplicativeExpression(NonTerminalNode multiplicativeExpression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        NonTerminalNode unaryExpression = (NonTerminalNode) multiplicativeExpression.getChildren().get(0);
        NonTerminalNode multiplicativeTail = (NonTerminalNode) multiplicativeExpression.getChildren().get(1);
        if (multiplicativeExpression.getValue() != null) {
            methodBody.append(getValue(multiplicativeExpression));
        } else {
            if (((TokenNode) multiplicativeTail.getChildren().get(0)).token != null) {
                translateMultiplicativeTail(multiplicativeTail, methodBody, parameterNames);
                TokenType op = ((TokenNode) multiplicativeTail.getChildren().get(0)).token.type();
                methodBody.append(" ").append(op == TokenType.MULTIPLICATION ? "*" : "/").append(" ");
            }
            translateUnaryExpression(unaryExpression, methodBody, parameterNames);
        }
    }

    private void translateMultiplicativeTail(NonTerminalNode multiplicativeTail, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (((TokenNode) multiplicativeTail.getChildren().get(0)).token == null) return;
        NonTerminalNode unaryExpression = (NonTerminalNode) multiplicativeTail.getChildren().get(1);
        NonTerminalNode nextTail = (NonTerminalNode) multiplicativeTail.getChildren().get(2);
        if (((TokenNode) nextTail.getChildren().get(0)).token != null) {
            translateMultiplicativeTail(nextTail, methodBody, parameterNames);
            TokenType op = ((TokenNode) multiplicativeTail.getChildren().get(0)).token.type();
            methodBody.append(" ").append(op == TokenType.MULTIPLICATION ? "*" : "/").append(" ");
        }
        translateUnaryExpression(unaryExpression, methodBody, parameterNames);
    }

    private void translateUnaryExpression(NonTerminalNode unaryExpression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (unaryExpression.getChildren().get(0) instanceof NonTerminalNode primaryExpression) {
            if (unaryExpression.getValue() != null) {
                methodBody.append(getValue(unaryExpression));
            } else {
                translatePrimaryExpression(primaryExpression, methodBody, parameterNames);
            }
        } else {
            TokenType op = ((TokenNode) unaryExpression.getChildren().get(0)).token.type();
            methodBody.append(op == TokenType.NOT ? "!" : "-");
            translateUnaryExpression((NonTerminalNode) unaryExpression.getChildren().get(1), methodBody, parameterNames);
        }
    }

    private void translatePrimaryExpression(NonTerminalNode primaryExpression, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (primaryExpression.getChildren().get(0) instanceof TokenNode) { // ( expression )
            translateExpression((NonTerminalNode) primaryExpression.getChildren().get(1), methodBody, parameterNames);
        } else {
            NonTerminalNode current = (NonTerminalNode) primaryExpression.getChildren().get(0);
            if (primaryExpression.getValue() != null) {
                methodBody.append(getValue(primaryExpression));
            } else {
                switch (current.name) {
                    case "identifier-or-function-call":
                        translateIdentifierOrFunctionCall(current, methodBody, parameterNames);
                        break;
                    case "number":
                        translateNumber((NonTerminalNode) current.getChildren().get(0), methodBody);
                        break;
                    case "boolean-literal":
                        methodBody.append(((TokenNode) current.getChildren().get(0)).token.name());
                        break;
                }
            }
        }
    }

    private void translateNumber(NonTerminalNode numberNode, StringBuilder methodBody) {
        TokenNode tokenNode = (TokenNode) numberNode.getChildren().get(0);
        String value = tokenNode.token.name();
        if (numberNode.name.equals("float-number") || tokenNode.token.type() == TokenType.FLOAT_NUMBER) {
            // Ensure float literals end with 'f'
            if (!value.endsWith("f") && !value.endsWith("F")) {
                value += "f";
            }
        }
        methodBody.append(value);
    }

    private void translateIdentifierOrFunctionCall(NonTerminalNode identifierOrFunctionCall, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        Token token = ((TokenNode) identifierOrFunctionCall.getChildren().get(0)).token;
        NonTerminalNode identifierEndOrFunctionCall = (NonTerminalNode) identifierOrFunctionCall.getChildren().get(1);
        if (identifierOrFunctionCall.getValue() != null) {
            methodBody.append(getValue(identifierOrFunctionCall));
        } else {
            if (((TokenNode) identifierEndOrFunctionCall.getChildren().get(0)).token == null) {
                /*if (!parameterNames.contains(token.name())) {
                    throw new IllegalStateException("Identifier " + token.name() + " is not a parameter or local variable in method");
                }*/
                methodBody.append(token.name());
            } else {
                methodBody.append(token.name()).append("(");
                translateArguments((NonTerminalNode) identifierEndOrFunctionCall.getChildren().get(1), methodBody, parameterNames);
                methodBody.append(")");
            }
        }
    }

    private void translateArguments(NonTerminalNode arguments, StringBuilder methodBody, Set<String> parameterNames) throws Exception {
        if (arguments.getChildren().get(0) instanceof TokenNode tokenNode) {
            if (tokenNode.token != null && tokenNode.token.type() == TokenType.STRING) {
                methodBody.append("\"").append(tokenNode.token.name()).append("\"");
            }
        } else {
            NonTerminalNode argumentList = (NonTerminalNode) arguments.getChildren().get(0);
            do {
                translateExpression((NonTerminalNode) argumentList.getChildren().get(0), methodBody, parameterNames);
                argumentList = (NonTerminalNode) argumentList.getChildren().get(1);
                if (((TokenNode) argumentList.getChildren().get(0)).token == null) break;
                methodBody.append(", ");
                argumentList = (NonTerminalNode) argumentList.getChildren().get(1);
            } while (true);
        }
    }

    private String getValue(NonTerminalNode node) {
        return node.getType() == TokenType.FLOAT_NUMBER ? node.getValue() + "f" : node.getValue();
    }

    private String getJavaType(TokenType type) {
        if (type == null) {
            throw new IllegalArgumentException("TokenType is null");
        }
        return switch (type) {
            case NUMBER, INT -> "int";
            case FLOAT_NUMBER, FLOAT -> "float";
            case BOOLEAN -> "boolean";
            case VOID -> "void";
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    private String getDefaultReturnValue(TokenType type) {
        if (type == null) {
            throw new IllegalArgumentException("TokenType is null");
        }
        return switch (type) {
            case NUMBER -> "0";
            case FLOAT_NUMBER -> "0.0f";
            case BOOLEAN -> "false";
            default -> "";
        };
    }

    private TokenType getType(NonTerminalNode typeNode) {
        if (typeNode.getChildren().get(0) instanceof NonTerminalNode) {
            return ((TokenNode) ((NonTerminalNode) typeNode.getChildren().get(0)).getChildren().get(0)).token.type();
        }
        return ((TokenNode) typeNode.getChildren().get(0)).token.type();
    }
}