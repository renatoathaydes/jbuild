package jbuild.classes.parser;

import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.MethodParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * MethodParameters attribute parser.
 * <pre>
 * MethodParameters_attribute {
 *   u2 attribute_name_index;
 *   u4 attribute_length;
 *   u1 parameters_count;
 *   {   u2 name_index;
 *       u2 access_flags;
 *   } parameters[parameters_count];
 * }
 * </pre>
 */
final class MethodParametersParser extends AbstractAttributeParser {

    public MethodParametersParser(ClassFile classFile) {
        super(classFile);
    }

    public List<MethodParameter> parseMethodParameters(byte[] attributes) {
        var scanner = new ByteScanner(attributes);
        var parameterCount = scanner.nextByte();
        var result = new ArrayList<MethodParameter>(parameterCount);
        for (var i = 0; i < parameterCount; i++) {
            // The value of the name_index item must either be zero or a valid index into the constant_pool table.
            var nameIndex = scanner.nextShort();
            var name = nameIndex == 0 ? "" : constUtf8(nameIndex);
            result.add(new MethodParameter(scanner.nextShort(), name));
        }
        return result;
    }
}
