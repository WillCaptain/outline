package org.twelve.outline;

public class OutlineParser {
    private Grammars grammars;
    private final static String TEST_PARSER_GRAMMAR = "outlineParser.gm";
    private final static String TEST_LEXER_GRAMMAR = "outlineLexer.gm";
    private MyParserBuilder builder;
    private ParserGrammarTree parserGrammarTree;
    private LexerRuleTree lexerRuleTree;
}
