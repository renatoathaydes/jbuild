package jbuild.classes.signature;

import jbuild.classes.model.attributes.SignatureAttribute;

import java.util.Set;

public final class TypeSignatureAttribute extends SignatureAttribute {

    public final JavaTypeSignature javaTypeSignature;

    public TypeSignatureAttribute(String signature, JavaTypeSignature typeSignature) {
        super(signature);
        this.javaTypeSignature = typeSignature;
    }

    @Override
    public Set<String> getAllTypes() {
        return javaTypeSignature.getAllTypes();
    }
}
