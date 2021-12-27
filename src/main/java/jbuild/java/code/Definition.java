package jbuild.java.code;

public abstract class Definition {

    public final String name;
    public final String type;

    Definition(String name, String type) {
        this.name = name;
        this.type = type;
    }

}
