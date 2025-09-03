package org.twelve.outline.converter;

import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.msll.parsetree.ParseNode;

import java.util.Map;

public abstract class Converter {
    protected final Map<String, Converter> converters;

    public Converter(Map<String, Converter> converters){
        this.converters = converters;
    }
    public abstract Node convert(AST ast, ParseNode source, Node related);
    public  Node convert(AST ast, ParseNode source){
        return this.convert(ast,source,null);
    }

}
