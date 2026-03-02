import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.config.GCPConfig;
import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.EntityValue;
import org.twelve.gcp.interpreter.value.StringValue;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.gcp.outline.Outline;
import org.twelve.outline.OutlineParser;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 所有__**__的符号都是外部构造器ID。
 * Interpreter 遇到外部构造器 ID 后，从注册表中检索对应的 SymbolConstructor 插件。
 *
 * <p>__external_builder__ 是唯一的内置外部构造器，直接在 GCP Interpreter 中实现：
 * 在内存里新建一个指定 Outline 类型的实例，并用可选参数（{field=value,...}）覆盖对应字段。
 *
 * <p>其他构造器由插件（Plugin）提供。GCP 启动时从 /plugin 目录加载所有以 ext_builder_ 开头
 * 的 JAR 包，每个 JAR 通过 manifest 注册 key→SymbolConstructor 映射；
 * 解释器根据 key 找到插件并委托构造。
 *
 * <p>插件注册方式（Java API）：
 * <pre>
 *   interpreter.registerConstructor("my_builder", (name, typeArgs, valueArgs) -> { ... });
 * </pre>
 */
public class ExternalTest {

    /**
     * Inference: the result tuple (name,age,gender,name,age,gender) must resolve to
     * (String, Integer, Male, String, Integer, Male).
     * gender field is typed as #Male (literal symbol type) whose outline string is "Male".
     */
    @Test
    void test_external_builder_inference() {
        AST ast = ASTHelper.mockExternalBuilder();
        ast.asf().infer();
        assertTrue(ast.inferred());
        assertTrue(ast.errors().isEmpty());
        Outline outline = ast.program().body().statements().getLast().outline();
        assertEquals("(String,Integer,Male,String,Integer,Male)", outline.toString());
    }

    /**
     * Interpretation:
     * <ul>
     *   <li>person_1 = __external_builder__&lt;Human&gt; — all fields default:
     *       name="", age=0, gender=Male (literal)</li>
     *   <li>person_2 = __external_builder__&lt;Human&gt;{age=40,name="Will"} —
     *       name and age overridden; gender remains Male (from base default)</li>
     * </ul>
     * result = (person_1.name, person_1.age, person_1.gender,
     *           person_2.name, person_2.age, person_2.gender)
     *        = ("", 0, Male{}, "Will", 40, Male{})
     *
     * <p>Plugin extension point: other builders registered via
     * {@code interpreter.registerConstructor("my_id", ...)} follow the same contract.
     */
    @Test
    void test_external_builder_interpret() {
        AST ast = ASTHelper.mockExternalBuilder();
        Value result = ast.asf().interpret();
        // Strings are quoted, elements joined by "," (code-style TupleValue.toString)
        assertEquals("(\"\",0,Male{},\"Will\",40,Male{})", result.toString());
        // gender must be a symbol entity tagged "Male" with no own fields
        org.twelve.gcp.interpreter.value.TupleValue tv =
                (org.twelve.gcp.interpreter.value.TupleValue) result;
        EntityValue gender1 = (EntityValue) tv.get(2);
        EntityValue gender2 = (EntityValue) tv.get(5);
        assertEquals("Male", gender1.symbolTag());
        assertTrue(gender1.ownFields().isEmpty());
        assertEquals("Male", gender2.symbolTag());
        assertTrue(gender2.ownFields().isEmpty());
    }

    /**
     * Plugin loading and execution test.
     *
     * <p>Verifies the full plugin lifecycle:
     * <ol>
     *   <li>The {@code ext_builder_test.jar} in {@code target/test-plugins/} is discovered by
     *       {@link OutlineInterpreter#loadPlugins}.</li>
     *   <li>The {@code __test__} constructor is registered automatically.</li>
     *   <li>GCP code using {@code __test__&lt;Human&gt;} is interpreted successfully.</li>
     *   <li>The returned {@link EntityValue} has the fields set by the plugin.</li>
     * </ol>
     *
     * <p>The plugin JAR is copied to {@code target/test-plugins/ext_builder_test.jar}
     * by {@code maven-dependency-plugin} during the {@code test-compile} phase.
     */
    @Test
    void test_plugin_load_and_execute() {
        var pluginsDir = Paths.get("target/test-plugins");
        assertTrue(Files.isDirectory(pluginsDir),
            "target/test-plugins/ must exist — run 'mvn test-compile' to copy the plugin JAR");

        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), """
                outline Human = { name: String, age: Int };
                __test__<Human>
                """);

        GCPConfig config = GCPConfig.load().with("plugin_dir", pluginsDir.toString());
        OutlineInterpreter interp = new OutlineInterpreter(config);
        Value result = interp.run(ast.asf());

        assertInstanceOf(EntityValue.class, result,
            "__test__ plugin should return an EntityValue");
        EntityValue ev = (EntityValue) result;

        // plugin field: identifies which plugin handled the call
        assertInstanceOf(StringValue.class, ev.get("plugin"));
        assertEquals("test-plugin", ((StringValue) ev.get("plugin")).value());

        // type field: the type argument passed to the builder
        assertInstanceOf(StringValue.class, ev.get("type"));
        assertEquals("Human", ((StringValue) ev.get("type")).value());
    }
}
