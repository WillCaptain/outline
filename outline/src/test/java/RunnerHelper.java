import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.value.*;
import org.twelve.outline.OutlineParser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RunnerHelper {
    /** Parse code into a fresh single-module ASF and run it. */
    public static Value run(String code) {
        ASF asf = new ASF();
        new OutlineParser().parse(asf, code);
        return asf.interpret();
    }

    /** Run an already-constructed AST (ASF retrieved from ast.asf()). */
    public static Value run(AST ast) {
        return ast.asf().interpret();
    }

    /** Run against a whole ASF (e.g. from ASTHelper.educationAndHuman()). */
    public static Value run(ASF asf) {
        return asf.interpret();
    }

    /** Parse and run multiple modules sequentially, returning the value of the last one. */
    public static Value runMultiModule(String... modules) {
        ASF asf = new ASF();
        OutlineParser p = new OutlineParser();
        for (String code : modules) p.parse(asf, code);
        return asf.interpret();
    }

    public static long intVal(Value v) {
        assertThat(v).isInstanceOf(IntValue.class);
        return ((IntValue) v).value();
    }

    public static double floatVal(Value v) {
        assertThat(v).isInstanceOf(FloatValue.class);
        return ((FloatValue) v).value();
    }

    public static String strVal(Value v) {
        assertThat(v).isInstanceOf(StringValue.class);
        return ((StringValue) v).value();
    }

    public static boolean boolVal(Value v) {
        assertThat(v).isInstanceOf(BoolValue.class);
        return ((BoolValue) v).isTruthy();
    }

    @SuppressWarnings("unchecked")
    public static List<Value> arrVal(Value v) {
        assertThat(v).isInstanceOf(ArrayValue.class);
        return ((ArrayValue) v).elements();
    }
}
