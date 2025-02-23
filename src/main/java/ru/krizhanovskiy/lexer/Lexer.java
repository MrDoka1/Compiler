package ru.krizhanovskiy.lexer;

import ru.krizhanovskiy.lexer.token.Token;
import ru.krizhanovskiy.lexer.token.TokenType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class Lexer {
    private List<Token> tokens;

    private int pos = 0;
    private int line = 0;

    private String string;

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

            if (find(findPunctuation, TokenType.MAX_LENGTH_PUNCTUATION, TokenType.CHECK_FURTHER_PUNCTUATION)) continue;
            if (find(findOperation, TokenType.MAX_LENGTH_OPERATION, TokenType.CHECK_FURTHER_OPERATION)) continue;
            if (find(findKeyword, TokenType.MAX_LENGTH_KEYWORD, TokenType.CHECK_FURTHER_KEYWORD)) continue;
            if (findNumber()) continue;
            findIdentify();
        }
    }

    private boolean find(Function<String, TokenType> function, int maxLength, boolean checkFurther) {
        TokenType currentTokenType = null;
        int currentStringLength = 0;
        maxLength = Math.min(maxLength, string.length() - pos);

        for (int i = 1; i <= maxLength; i++) {
            String str = string.substring(pos, pos + i);
            TokenType type = function.apply(str);
            if (type != null) {
                currentTokenType = type;
                currentStringLength = i;
                if (!checkFurther) break;
            }
        }

        if (currentTokenType == null) return false;

        /**
         * В ситуации if1 - надётся токен IF, но это идентификатор, поэтому проверяем сдедующий символ
         */
        if (function.getClass() == FindKeyword.class) {
            if (!(pos + currentStringLength == string.length()
                    || ignoreCharacter.contains(string.charAt(pos + currentStringLength )))) return false;
        }

        tokens.add(new Token(currentTokenType, null, line, pos));
        pos += currentStringLength;
        return true;
    }

    Function<String, TokenType> findPunctuation = new FindPunctuation();
    class FindPunctuation implements Function<String, TokenType> {
        @Override
        public TokenType apply(String string) {
            return TokenType.getPunctuation(string);
        }
    }
    Function<String, TokenType> findKeyword = new FindKeyword();
    class FindKeyword implements Function<String, TokenType> {
        @Override
        public TokenType apply(String string) {
            return TokenType.getKeyword(string);
        }
    }
    Function<String, TokenType> findOperation = new FindOperation();
    class FindOperation implements Function<String, TokenType> {
        @Override
        public TokenType apply(String string) {
            return TokenType.getOperation(string);
        }
    }

    private Set<Character> ignoreCharacter = new HashSet<>(List.of(' ', '(', ')', '{', '}', ';', ',',
            '-', '+', '*', '/', '=', '<', '>', '&', '|', '!'));

    private boolean findNumber() {
        int currentPos = pos;
        StringBuilder numberBuilder = new StringBuilder(); // Собираем число
        boolean isFloat = false;
        while (currentPos < string.length()) {
            char currentChar = string.charAt(currentPos);
            if (Character.isDigit(currentChar)) {
                numberBuilder.append(currentChar);
            } else if (currentChar == '.') {
                if (isFloat) {
                    // TODO: Вывод ошибки о том, что должна быть одна точка
                } else {
                    isFloat = true;
                    numberBuilder.append(currentChar);
                }
            } else {
                if (numberBuilder.isEmpty()) return false;
                if (ignoreCharacter.contains(currentChar)) break;

                // TODO: Вывод ошибки неизвестный символ
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
                // TODO: Ошибка неверный символ
            } else {
                if (isValidCharacterOrNumber(currentChar)) {
                    identifyBuilder.append(currentChar);
                    currentPos++;
                    continue;
                }
                if (ignoreCharacter.contains(currentChar)) break;
                // TODO: Ошибка неверный символ
            }
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
}