package jbuild.classes.model.attributes;

import jbuild.classes.TypeGroup;

/**
 * The Signature attribute is a fixed-length attribute in the attributes table of a ClassFile, field_info, method_info,
 * or record_component_info structure (§4.1, §4.5, §4.6, §4.7.30).
 * <p>
 * A Signature attribute stores a signature (§4.7.9.1) for a class, interface, constructor, method, field, or record
 * component whose declaration in the Java programming language uses type variables or parameterized types.
 * <p>
 * See Class file spec,
 * <a href="https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-4.html#jvms-4.7.9">Section 4.7.9</a>.
 *
 * <pre>
 * Signature_attribute {
 *     u2 attribute_name_index;
 *     u4 attribute_length;
 *     u2 signature_index;
 * }
 * </pre>
 */
public abstract class SignatureAttribute implements TypeGroup {
    public static final String ATTRIBUTE_NAME = "Signature";

    public final String signature;

    public SignatureAttribute(String signature) {
        this.signature = signature;
    }
}
