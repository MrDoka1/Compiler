package ru.krizhanovskiy.parser;

import ru.krizhanovskiy.ast.Node;
import ru.krizhanovskiy.ast.NonTerminalNode;
import ru.krizhanovskiy.ast.TokenNode;
import ru.krizhanovskiy.lexer.token.Token;
import ru.krizhanovskiy.lexer.token.TokenType;

import java.util.*;

public class Parser {
    private final List<Token> tokens;
    private final Map<String, List<List<String>>> grammar;
    private final Set<String> nonTerminals;
    private final Map<String, Set<String>> firstElements;
    private NonTerminalNode rootAST = null;
    private NonTerminalNode currentNode;

    public boolean error = false;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.grammar = GrammarParser.getGrammar();
        this.nonTerminals = grammar.keySet();
        this.firstElements = new HashMap<>();
        createFirstElements();
    }

    public NonTerminalNode getRootAST() {
        return rootAST;
    }

    record SelectProduction(String nonTerminal, int currentNumberProduction, List<Integer> productions, int currentIndexToken,
                            Stack<String> currentStack) {}

    public void parse() {
        Stack<String> stack = new Stack<>();
        stack.push("$");
        stack.push("program");

        tokens.add(new Token(null, "$", 0, 0));

        Stack<SelectProduction> selectProductions = new Stack<>();

        int currentIndexToken = 0;

        while (!stack.isEmpty()) {
//            System.out.println(stack);

            String terminalOrNonTerminal = stack.pop();

            Token currentToken = tokens.get(currentIndexToken);

            if (nonTerminals.contains(terminalOrNonTerminal)) {
                List<List<String>> production = grammar.get(terminalOrNonTerminal);
                List<String> productionList;
                List<Integer> matchProduction = new ArrayList<>();
                for (int i = 0; i < production.size(); i++) {
                    if (productionHaveFirstToken(currentToken, production.get(i).get(0))) {
                        matchProduction.add(i);
                    }
                }

                if (matchProduction.isEmpty()) {
                    if (firstElements.get(terminalOrNonTerminal).contains("ε")) {
                        addNonTerminalToAST(terminalOrNonTerminal, 1);
                        addTerminalToAST(null);
                        // OK
                    } else {
                        // ERROR
                        System.err.println("Syntax Error in token: " + currentToken);
                        stack.push(terminalOrNonTerminal);
                        currentIndexToken++;
                    }
                } else {
                    Stack<String> newStack = new Stack<>();
                    newStack.addAll(stack);
                    selectProductions.push(new SelectProduction(terminalOrNonTerminal, 0,
                            matchProduction, currentIndexToken, newStack));
                    productionList = new ArrayList<>(production.get(matchProduction.get(0)));
                    Collections.reverse(productionList);
                    stack.addAll(productionList);

                    // --- AST ---
                    addNonTerminalToAST(terminalOrNonTerminal, productionList.size());
                    // -----------
                }

            } else {
                if (terminalOrNonTerminal.equals("$")) {
                    if (currentToken.type() == null && currentToken.name().equals("$")) {
                        System.out.println("Ok");
                        // Всё окей, дошли до конца
                    }
                } else if (terminalOrNonTerminal.equals("ε")) {
                    addTerminalToAST(null);
                    continue;
                } else if (equalToken(currentToken, terminalOrNonTerminal)) {
                    // --- AST ---
                    addTerminalToAST(currentToken);
                    // -----------
                    currentIndexToken++;
                } else {
                    System.out.println(currentToken + " " + terminalOrNonTerminal);
                    while (!selectProductions.isEmpty()) {
                        SelectProduction selectProduction = selectProductions.pop();
                        int currentNumberProduction = selectProduction.currentNumberProduction + 1;
                        if (currentNumberProduction == selectProduction.productions.size()) {
                            continue;
                        }
                        selectProductions.add(new SelectProduction(selectProduction.nonTerminal, currentNumberProduction,
                                selectProduction.productions, selectProduction.currentIndexToken, selectProduction.currentStack));
                        stack.clear();
                        stack.addAll(selectProduction.currentStack);
                        currentIndexToken = selectProduction.currentIndexToken;
                        List<String> productionList = new ArrayList<>(grammar.get(selectProduction.nonTerminal).get(selectProduction.productions.get(currentNumberProduction)));
                        Collections.reverse(productionList);
                        stack.addAll(productionList);
                        System.out.println("Warning!!! " + stack + selectProduction);
                        System.out.println(tokens.get(currentIndexToken));
                        break;
                    }

                    // TODO: Откат или ошибка.
                    if (checkMissingSemicolon(terminalOrNonTerminal, currentToken)) {
                        System.err.println("Syntax error: missing semicolon in line " + tokens.get(currentIndexToken - 1).line());
                    } else {
                        System.err.println("Syntax Error in token: " + currentToken + ", expected: " + terminalOrNonTerminal);
                    }
                }
            }
//            System.out.println(currentIndexToken);
        }
        System.out.println(tokens.get(currentIndexToken));
    }

    private boolean productionHaveFirstToken(Token token, String terminalOrNonTerminal) {
        if (nonTerminals.contains(terminalOrNonTerminal)) {
            return equalToken(token, firstElements.get(terminalOrNonTerminal));
        }
        return equalToken(token, terminalOrNonTerminal);
    }

    private boolean equalToken(Token token, Set<String> firstElementsSet) {
        if (firstElementsSet.contains(token.name())) return true;
        if (token.type() == TokenType.IDENTIFIER && firstElementsSet.contains("identifier")) return true;
        if (token.type() == TokenType.NUMBER && firstElementsSet.contains("int-literal")) return true;
        if (token.type() == TokenType.FLOAT_NUMBER && firstElementsSet.contains("float-literal")) return true;
        if (token.type() == TokenType.STRING && firstElementsSet.contains("string-literal")) return true;
        return false;
    }

    private boolean equalToken(Token token, String tokenName) {
        if (nonTerminals.contains(tokenName)) return false;
        if (token.type() == TokenType.IDENTIFIER && tokenName.equals("identifier")) return true;
        if (token.type() == TokenType.NUMBER && tokenName.equals("int-literal")) return true;
        if (token.type() == TokenType.FLOAT_NUMBER && tokenName.equals("float-literal")) return true;
        if (token.type() == TokenType.STRING && tokenName.equals("string-literal")) return true;
        if (token.name().equals(tokenName)) return true;
        return false;
    }

    private void createFirstElements() {
        Set<String> visited = new HashSet<>();
        for (String nonTerminal : nonTerminals) {
            getFirstElements(visited, nonTerminal);
        }
    }
    private Set<String> getFirstElements(Set<String> visited, String string) {
        if (visited.contains(string)) return firstElements.get(string);
        firstElements.put(string, new HashSet<>());
        visited.add(string);
        Set<String> currentSet = firstElements.get(string);
        for (List<String> production : grammar.get(string)) {
            String productionString = production.get(0);
            if (nonTerminals.contains(productionString)) {
                currentSet.addAll(getFirstElements(visited, productionString));
            } else {
                currentSet.add(productionString);
            }
        }
        return currentSet;
    }

    private boolean checkMissingSemicolon(String terminalOrNonTerminal, Token currentToken) {
        if (terminalOrNonTerminal.equals(";")) {
            if (productionHaveFirstToken(currentToken, "statement")) return true;
            if (!currentToken.name().isEmpty() && currentToken.name().equals("}")) return true;
        }
        return false;
    }

     /* ------------------- AST Actions ---------------------- */
    private void addNonTerminalToAST(String terminalOrNonTerminal, int qtyProductions) {
        if (rootAST == null) {
            rootAST = new NonTerminalNode(terminalOrNonTerminal, null, qtyProductions);
            currentNode = rootAST;
            return;
        }
        NonTerminalNode newNode = new NonTerminalNode(terminalOrNonTerminal, currentNode, qtyProductions);
        currentNode.getChildren().add(newNode);
        currentNode = newNode;
    }
    private void addTerminalToAST(Token currentToken) {
        currentNode.getChildren().add(new TokenNode(currentToken));
        while (currentNode != null && currentNode.getChildren().size() == currentNode.getQtyProductions()) {
            currentNode = currentNode.getPrev();
        }
    }
    /* ------------------- AST Actions END -------------------- */
}