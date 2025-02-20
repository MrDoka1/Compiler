package ru.krizhanovskiy;

import java.util.List;

public class Lexer {
    private List<Token> tokens;

    private int pos = 0;
    private int currentLine = 0;

    private String line;

    public Lexer(List<Token> tokens) {
        this.tokens = tokens;
    }

    public void analiseLine(String input) {
        line = input;
        if (line == null || line.isEmpty()) return;

        while (pos < line.length()) {

        }

        currentLine++;
    }

    private void findPunctuation() {

    }

    private void findNumber() {
        int currentPos = pos;
        StringBuilder stringBuilder = new StringBuilder(); // Собираем число
        boolean isFloat = false;
        while (currentPos < line.length()) {
            if (Character.isDigit(line.charAt(currentPos))) {
                stringBuilder.append(line.charAt(currentPos));
            } else if (line.charAt(currentPos) == '-') {}
        }
    }
}