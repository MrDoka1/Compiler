package ru.krizhanovskiy.parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class GrammarParser {
    private static final String filename = "/parser/grammar.csv";
    private static final Map<String, List<List<String>>> grammar = new HashMap<>();
    private static final Set<String> nonTerminals = new HashSet<>();

    /*private static int identity = 0;
    private static final Map<String, Integer> nonTerminalInteger = new HashMap<>(Map.of(
            "number", identity++,
            "identifier", identity++

    ));*/

    private static String line;
    private static String lastNonTerminal;
    private static int pos = 0;

    public static Map<String, List<List<String>>> getGrammar() {
        if (!grammar.isEmpty()) return grammar;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Objects.requireNonNull(GrammarParser.class.getResourceAsStream(filename))))
        ) {

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                pos = 0;
                analise();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return grammar;
    }

    private static void analise() {
        while (pos < line.length()) {
            scipSpace();

            if (line.charAt(pos) == '<') {
                lastNonTerminal = getNonTerminal();
                grammar.put(lastNonTerminal, new ArrayList<>());
                scipSpace();
                if (line.charAt(pos) == ':' && line.charAt(pos+1) == ':' && line.charAt(pos+2) == '=') {
                    pos += 3;
                    grammar.get(lastNonTerminal).add(getRule());
                }
            } else if (line.charAt(pos) == '|') {
                grammar.get(lastNonTerminal).add(getRule());
            }
        }
    }

    private static void scipSpace() {
        while (line.charAt(pos) == ' ') pos++; // scip space
    }

    private static String getNonTerminal() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (line.charAt(pos) != '>') {
            sb.append(line.charAt(pos++));
        }
        pos++;
        String nonTerminal = sb.toString();
        nonTerminals.add(nonTerminal);
        return nonTerminal;
    }
    private static String getTerminal() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (line.charAt(pos) != '"') {
            sb.append(line.charAt(pos++));
        }
        pos++;
        return sb.toString();
    }

    private static List<String> getRule() {
        pos++;
        List<String> rule = new ArrayList<>();
        while (pos < line.length() && line.charAt(pos) != '|') {
            scipSpace();
            if (line.charAt(pos) == '<') {
                rule.add(getNonTerminal());
            } else if (line.charAt(pos) == '"') {
                rule.add(getTerminal());
            } else if (line.charAt(pos) == 'E') {
                pos++;
                return List.of("E");
            }
        }
        return rule;
    }
}