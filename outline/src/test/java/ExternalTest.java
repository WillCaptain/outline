import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.gcp.outline.Outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 所有__**__的符号都是外部构造器ID
 * interpreter即使到外部构造器ID后检索ID对应的构造器
 * __external_builder__是默认外部构造器，在GCP Interpreter里有标准实现：内存里新建一个<Outine>里Outline的instance，如果有参数，则根据参数复制对应的field
 * 其他构造器由插件提供，GCP启动便加载本目录里所有以ext_builder_开头的jar包，jar包里有注册好的key:builder对应，根据对应，gcp解释该语句
 * todo:请重新修饰该注释
 *
 */
public class ExternalTest {
    @Test
    void test_external_builder_inference() {
        AST ast = ASTHelper.mockExternalBuilder();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(String,Integer,String,Integer)", outline.toString());
    }
    @Test
    void test_external_builder_interpret() {
        AST ast = ASTHelper.mockExternalBuilder();
        Value result = ast.asf().interpret();
        assertEquals("(\"\",0,\"Will\",40)", result.toString());
    }
}
