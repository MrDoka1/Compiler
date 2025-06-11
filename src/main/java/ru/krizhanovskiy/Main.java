package ru.krizhanovskiy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.krizhanovskiy.ast.NonTerminalNode;
import ru.krizhanovskiy.lexer.Lexer;
import ru.krizhanovskiy.lexer.token.Token;
import ru.krizhanovskiy.parser.Parser;
import ru.krizhanovskiy.semantic_analyzer.SemanticAnalyzer;
import ru.krizhanovskiy.translation.Translator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Введите путь к файлу с кодом");
            return;
        }
        String filename = args[0];
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))
        ) {
            List<Token> tokens = new ArrayList<>();
            Lexer lexer = new Lexer(tokens);

            String line;

            while ((line = reader.readLine()) != null) {
                lexer.analiseLine(line);
            }
            if (tokens.isEmpty()) {
                System.err.println("No tokens found");
                return;
            }

            Parser parser = new Parser(tokens);
            parser.parse();

            NonTerminalNode ast = parser.getRootAST();


            serializeToJson(parser.getRootAST(), "ast.json");

            if (parser.error) return;


            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(parser.getRootAST());
            semanticAnalyzer.analyze();

            serializeToJson(parser.getRootAST(), "ast-optimized.json");

            if (semanticAnalyzer.errors) return;

            Translator translator = new Translator(ast, semanticAnalyzer.methods);
            translator.translate();
            System.out.println("Main.class generated successfully.");


        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void serializeToJson(NonTerminalNode root, String filename) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        mapper.writeValue(new File(filename), root);
    }
}