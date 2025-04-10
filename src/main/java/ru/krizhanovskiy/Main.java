package ru.krizhanovskiy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.krizhanovskiy.ast.NonTerminalNode;
import ru.krizhanovskiy.character_table.CharacterTable;
import ru.krizhanovskiy.lexer.Lexer;
import ru.krizhanovskiy.lexer.token.Token;
import ru.krizhanovskiy.parser.GrammarParser;
import ru.krizhanovskiy.parser.Parser;
import ru.krizhanovskiy.semantic_analyzer.SemanticAnalyzer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new FileReader("input2.txt"))
        ) {
            List<Token> tokens = new ArrayList<>();
            CharacterTable charTable = new CharacterTable();
            Lexer lexer = new Lexer(tokens);

            String line;

            while ((line = reader.readLine()) != null) {
                lexer.analiseLine(line);
            }
            if (tokens.isEmpty()) {
                System.err.println("No tokens found");
                return;
            }

//            tokens.forEach(System.out::println); // remove print
//            charTable.print(); // remove print
            System.out.println(lexer.error);

//            Map<String, List<List<String>>> grammar = GrammarParser.getGrammar();

            Parser parser = new Parser(tokens);
            parser.parse();

            NonTerminalNode ast = parser.getRootAST();

//            BytecodeGenerator generator = new BytecodeGenerator(ast);
//            generator.generate("GeneratedClass");

            serializeToJson(parser.getRootAST(), "ast.json");

            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(parser.getRootAST());
            semanticAnalyzer.analyze();

            serializeToJson(parser.getRootAST(), "ast-optimized.json");


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