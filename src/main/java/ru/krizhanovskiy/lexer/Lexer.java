package ru.krizhanovskiy.lexer;

import ru.krizhanovskiy.lexer.token.Token;
import ru.krizhanovskiy.lexer.token.TokenType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class Lexer {
    public boolean error = false;

    private final List<Token> tokens;

    private int pos = 0;
    private int line = 0;

    private String string;

    private boolean multilineComment = false;

    public Lexer(List<Token> tokens) {
        this.tokens = tokens;
    }

    public void analiseLine(String input) {
        line++;
        if (input == null || input.isEmpty()) return;

        pos = 0;
        string = input;

        while (pos < string.length()) {
            if (string.charAt(pos) == ' ') {
                pos++;
                continue;
            }

            char ch = string.charAt(pos);

            if (multilineComment) {
                if (!findMultilineCommentEnd()) return; // Строка кончилась и не найден конец комментария
                else continue;
            }

            if (findComment()) return; // пропускаем однострочные комментарии
            if (findMultilineComment()) { multilineComment = true;  continue; }

            if (find(findPunctuation, TokenType.MAX_LENGTH_PUNCTUATION, TokenType.CHECK_FURTHER_PUNCTUATION)) continue;
            if (find(findOperation, TokenType.MAX_LENGTH_OPERATION, TokenType.CHECK_FURTHER_OPERATION)) continue;
            if (find(findKeyword, TokenType.MAX_LENGTH_KEYWORD, TokenType.CHECK_FURTHER_KEYWORD)) continue;
            if (findNumber()) continue;
            if (findString()) continue;
            findIdentify();
        }
    }

    private boolean findComment() {
        return string.charAt(pos) == '/' && string.length() - pos > 1 && string.charAt(pos + 1) == '/';
    }
    private boolean findMultilineComment() {
        if (string.charAt(pos) == '/' && string.length() - pos > 1 && string.charAt(pos + 1) == '*') {
            pos+=2;
            return true;
        }
        return false;
    }
    private boolean findMultilineCommentEnd() {
        while (pos < string.length()) {
            if (string.charAt(pos) == '*' && string.length() - pos > 1 && string.charAt(pos + 1) == '/') {
                pos+=2;
                multilineComment = false;
                return true;
            }
            pos++;
        }
        return false;
    }

    private boolean find(Function<String, TokenType> function, int maxLength, boolean checkFurther) {
        TokenType currentTokenType = null;
        int currentStringLength = 0;
        String currentString = null;
        maxLength = Math.min(maxLength, string.length() - pos);

        for (int i = 1; i <= maxLength; i++) {
            String str = string.substring(pos, pos + i);
            TokenType type = function.apply(str);
            if (type != null) {
                currentTokenType = type;
                currentStringLength = i;
                currentString = str;
                if (!checkFurther) break;
            }
        }

        if (currentTokenType == null) return false;

        /*
          В ситуации if1 - надётся токен IF, но это идентификатор, поэтому проверяем сдедующий символ
         */
        if (function.getClass() == FindKeyword.class) {
            if (!(pos + currentStringLength == string.length()
                    || ignoreCharacter.contains(string.charAt(pos + currentStringLength )))) return false;
        }

        tokens.add(new Token(currentTokenType, currentString, line, pos));
        pos += currentStringLength;
        return true;
    }

    private static final Function<String, TokenType> findPunctuation = new FindPunctuation();
    static class FindPunctuation implements Function<String, TokenType> {
        @Override
        public TokenType apply(String string) {
            return TokenType.getPunctuation(string);
        }
    }
    private static final Function<String, TokenType> findKeyword = new FindKeyword();
    static class FindKeyword implements Function<String, TokenType> {
        @Override
        public TokenType apply(String string) {
            return TokenType.getKeyword(string);
        }
    }
    private static final Function<String, TokenType> findOperation = new FindOperation();
    static class FindOperation implements Function<String, TokenType> {
        @Override
        public TokenType apply(String string) {
            return TokenType.getOperation(string);
        }
    }

    private final Set<Character> ignoreCharacter = new HashSet<>(List.of(' ', '(', ')', '{', '}', ';', ',',
            '-', '+', '*', '/', '=', '<', '>', '&', '|', '!'));

    private boolean findNumber() {
        int currentPos = pos;
        StringBuilder numberBuilder = new StringBuilder(); // Собираем число
        boolean isFloat = false;
        while (currentPos < string.length()) {
            char currentChar = string.charAt(currentPos);
            if (currentChar >= '0' && currentChar <= '9') { // is digit
                numberBuilder.append(currentChar);
            } else if (currentChar == '.') {
                if (isFloat) {
                    error = true;
                    // Вывод ошибки о том, что должна быть одна точка
                    System.err.printf("Error: Invalid numeric literal '.' (extra dot) at line %d, column %d\n", line, currentPos+1);
                    break;
                } else {
                    isFloat = true;
                    numberBuilder.append(currentChar);
                }
            } else {
                if (numberBuilder.isEmpty()) return false;
                if (ignoreCharacter.contains(currentChar)) break;

                // Вывод ошибки о том, что должен быть разделитель
                if (isValidCharacter(currentChar)) {
                    error = true;
                    System.err.printf("Error: Invalid numeric literal at line %d, column %d. Expected separator before `%c`\n", line, currentPos+1, currentChar);
                    break;
                }
                error = true;
                // Вывод ошибки неизвестный символ
                System.err.printf("Error: Unknown character `%c` at line %d, column %d\n", currentChar, line, currentPos+1);
                pos++;
                break;
            }
            currentPos++;
        }

        if (isFloat) tokens.add(new Token(TokenType.FLOAT_NUMBER, numberBuilder.toString(), line, pos));
        else tokens.add(new Token(TokenType.NUMBER, numberBuilder.toString(), line, pos));

        pos += numberBuilder.length();
        return true;
    }

    private boolean findIdentify() {
        int currentPos = pos;
        StringBuilder identifyBuilder = new StringBuilder();
        while (currentPos < string.length()) {
            char currentChar = string.charAt(currentPos);
            if (identifyBuilder.isEmpty()) {
                if (isValidCharacter(currentChar)) {
                    identifyBuilder.append(currentChar);
                    currentPos++;
                    continue;
                }
            } else {
                if (isValidCharacterOrNumber(currentChar)) {
                    identifyBuilder.append(currentChar);
                    currentPos++;
                    continue;
                }
                if (ignoreCharacter.contains(currentChar)) break;
            }
            error = true;
            // Ошибка неизвестный символ
            System.err.printf("Error: Unknown character `%c` at line %d, column %d\n", currentChar, line, currentPos+1);
            pos++;
            break;
        }
        if (identifyBuilder.isEmpty()) return false;
        tokens.add(new Token(TokenType.IDENTIFIER, identifyBuilder.toString(), line, pos));
        pos += identifyBuilder.length();
        return true;
    }
    private boolean isValidCharacter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }
    private boolean isValidCharacterOrNumber(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private boolean findString() {
        if (string.charAt(pos) != '"') return false;
        int currentPos = pos+1;
        StringBuilder stringBuilder = new StringBuilder();
        boolean end = false;
        while (currentPos < string.length()) {
            char currentChar = string.charAt(currentPos++);
            if (currentChar == '"') {
                if (string.charAt(currentPos - 2) == '\\') { // Предыдущий символ
                    stringBuilder.deleteCharAt(stringBuilder.length()-1);
                    stringBuilder.append(currentChar);
                }
                else {
                    end = true;
                    break;
                }
            }
            else stringBuilder.append(currentChar);
        }
        if (!end) {
            error = true;
            System.err.printf("Unterminated string literal at line %d, column %d.\n", line, currentPos+1);
        } else {
            tokens.add(new Token(TokenType.STRING, stringBuilder.toString(), line, pos));
        }
        pos = currentPos;
        return true;
    }
}