package org.twelve.outline;

import org.twelve.gcp.ast.AST;
import org.twelve.msll.grammar.Grammars;
import org.twelve.msll.parser.MyParser;
import org.twelve.msll.parserbuilder.MyParserBuilder;
import org.twelve.msll.parsetree.LexerRuleTree;
import org.twelve.msll.parsetree.ParserGrammarTree;
import org.twelve.msll.parsetree.ParserTree;

import java.io.IOException;

public class OutlineParser {
    private final Grammars grammars;
    private final static String TEST_PARSER_GRAMMAR = "outlineParser.gm";
    private final static String TEST_LEXER_GRAMMAR = "outlineLexer.gm";
    private final MyParserBuilder builder;
    private final ParserGrammarTree parserGrammarTree;
    private final LexerRuleTree lexerRuleTree;
    private final GCPConverter converter;

    public OutlineParser(GCPConverter converter) throws IOException {
        this.builder = new MyParserBuilder(TEST_PARSER_GRAMMAR, TEST_LEXER_GRAMMAR);
        this.parserGrammarTree = builder.parserGrammarTree();
        this.lexerRuleTree = builder.lexerGrammarTree();
        this.grammars = builder.grammars();
        this.converter = converter;
    }

    public OutlineParser()throws IOException{
        this(new GCPConverter());
    }

    public AST parse(String code) {
        MyParser parser = builder.createParser(code);
        return this.converter.convert(parser.parse());
    }
}
