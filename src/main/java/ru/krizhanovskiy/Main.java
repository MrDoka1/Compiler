package ru.krizhanovskiy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new FileReader("input.txt"))
        ) {
            List<Token> tokens = new ArrayList<>();
            CharacterTable charTable = new CharacterTable();
            Lexer lexer = new Lexer(tokens);

            String line;

            while ((line = reader.readLine()) != null) {
                lexer.analiseLine(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}