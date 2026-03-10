import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.node.function.FunctionCallNode;
import org.twelve.gcp.node.expression.accessor.MemberAccessor;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.adt.Array;
import org.twelve.gcp.outline.adt.Entity;
import org.twelve.gcp.outline.decorators.This;
import org.twelve.gcp.outline.projectable.*;
import org.twelve.outline.OutlineParser;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

public class NestedVSTest {
    private final OutlineParser parser = new OutlineParser();
    private static final String SCHOOL_PREAMBLE = OntologyFixtures.SYSTEM_OUTLINES + """
            outline Student = { id: 0, name: String, age: Int };
            outline Students = VirtualSet<Student>{};
            outline School = { id: 0, name: String, students: Unit -> Students };
            outline Schools = VirtualSet<School>{};
            """;

    private static Node findMemberCallNode(Node node, String memberName) {
        if (node == null) return null;
        if (node instanceof FunctionCallNode callNode) {
            if (callNode.function() instanceof MemberAccessor ma) {
                if (memberName.equals(ma.member().name())) return callNode;
            }
        }
        for (Node child : node.nodes()) {
            Node found = findMemberCallNode(child, memberName);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    void debugThisOriginAfterFilter() {
        // Check: what does calling filter return as its outline?
        String code = "module org.test.debug\n" + SCHOOL_PREAMBLE + """
                let f = (students: Students) -> students.filter(t->t.age>18).to_list();
                let x = 0;
                export f, x;
                """;
        AST ast = parser.parse(code);
        ast.asf().infer();
        
        Node filterCall = findMemberCallNode(ast.program(), "filter");
        Outline filterResult = filterCall.outline(); 
        System.out.println("filter result type: " + filterResult.getClass().getSimpleName() + " = " + filterResult);
        
        if (filterResult instanceof This thisType) {
            Outline origin = thisType.eventual();
            System.out.println("This.origin (eventual): " + origin.getClass().getSimpleName());
            System.out.println("Has <a> references: " + origin.toString().contains("<a>"));
            System.out.println("origin.id: " + origin.id());
            
            // Check if Students is in scope
            var studentsSymbol = ast.symbolEnv().lookupAll("Students");
            if (studentsSymbol != null) {
                Outline studentsOutline = studentsSymbol.outline();
                System.out.println("Students outline id: " + studentsOutline.id());
                System.out.println("Students is same as origin? " + (origin.id() == studentsOutline.id()));
            }
            
            // Check the filter function's return type directly from VirtualSet<Student>
            var studentsEntity = ast.symbolEnv().lookupAll("Students");
            if (studentsEntity != null && studentsEntity.outline() instanceof Entity e) {
                var filterMember = e.getMember("filter");
                if (filterMember.isPresent()) {
                    var filterType = filterMember.get().outline();
                    System.out.println("filter member type: " + filterType.getClass().getSimpleName());
                    if (filterType instanceof FirstOrderFunction fof) {
                        var ret = fof.returns();
                        var supposed = ret.supposedToBe();
                        System.out.println("filter returns.supposedToBe: " + supposed + " class=" + supposed.getClass().getSimpleName());
                        if (supposed instanceof This t2) {
                            System.out.println("This in filter member origin: " + t2.eventual().getClass().getSimpleName() + " id=" + t2.eventual().id());
                        }
                    }
                }
            }
        }
    }
}
