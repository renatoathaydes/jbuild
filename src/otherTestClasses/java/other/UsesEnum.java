package other;

import foo.SomeEnum;

public class UsesEnum {
    SomeEnum someEnum;

    void checkEnum() {
        switch (someEnum) {
            case SOMETHING:
                System.out.println("something");
                break;
            case NOTHING:
                System.out.println("nothing");
                break;
        }
    }
}
