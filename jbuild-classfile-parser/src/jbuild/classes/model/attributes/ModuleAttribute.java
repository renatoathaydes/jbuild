package jbuild.classes.model.attributes;

import java.util.List;
import java.util.Set;

/**
 * https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.7.24
 */
public final class ModuleAttribute {

    public static final String ATTRIBUTE_NAME = "Module";

    public static final class ModuleFlags {
        public static final short NONE = 0x00;
        public static final short ACC_OPEN = 0x0020;
        public static final short ACC_SYNTHETIC = 0x1000;
        public static final short ACC_MANDATED = (short) 0x8000;

        public static boolean isOpen(short accessFlags) {
            return (accessFlags & ACC_OPEN) != 0;
        }

        public static boolean isSynthetic(short accessFlags) {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }

        public static boolean isMandated(short accessFlags) {
            return (accessFlags & ACC_MANDATED) != 0;
        }
    }

    public static final class RequiresFlags {
        public static final short NONE = 0x00;
        public static final short ACC_TRANSITIVE = 0x0020;
        public static final short ACC_STATIC_PHASE = 0x0040;
        public static final short ACC_SYNTHETIC = 0x1000;
        public static final short ACC_MANDATED = (short) 0x8000;

        public static boolean isTransitive(short accessFlags) {
            return (accessFlags & ACC_TRANSITIVE) != 0;
        }

        public static boolean isStaticPhase(short accessFlags) {
            return (accessFlags & ACC_STATIC_PHASE) != 0;
        }

        public static boolean isSynthetic(short accessFlags) {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }

        public static boolean isMandated(short accessFlags) {
            return (accessFlags & ACC_MANDATED) != 0;
        }
    }

    public static final class ExportsFlags {
        public static final short NONE = 0x00;
        public static final short ACC_SYNTHETIC = 0x1000;
        public static final short ACC_MANDATED = (short) 0x8000;

        public static boolean isSynthetic(short accessFlags) {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }

        public static boolean isMandated(short accessFlags) {
            return (accessFlags & ACC_MANDATED) != 0;
        }
    }

    public static final class OpensFlags {
        public static final short NONE = 0x00;
        public static final short ACC_SYNTHETIC = 0x1000;
        public static final short ACC_MANDATED = (short) 0x8000;

        public static boolean isSynthetic(short accessFlags) {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }

        public static boolean isMandated(short accessFlags) {
            return (accessFlags & ACC_MANDATED) != 0;
        }
    }

    public static final class Requires {
        public final String moduleName;
        public final short requiresFlags;

        /**
         * If the value is empty, then no version information about the dependence is present,
         * version of the module specified by {@link Requires#moduleName}.
         */
        public final String version;

        public Requires(String moduleName, short requiresFlags, String version) {
            this.moduleName = moduleName;
            this.requiresFlags = requiresFlags;
            this.version = version;
        }

        public boolean isTransitive() {
            return RequiresFlags.isTransitive(requiresFlags);
        }

        public boolean isStaticPhase() {
            return RequiresFlags.isStaticPhase(requiresFlags);
        }

        public boolean isSynthetic() {
            return RequiresFlags.isSynthetic(requiresFlags);
        }

        public boolean isMandated() {
            return RequiresFlags.isMandated(requiresFlags);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Requires requires = (Requires) o;
            return requiresFlags == requires.requiresFlags && moduleName.equals(requires.moduleName) &&
                    version.equals(requires.version);
        }

        @Override
        public int hashCode() {
            int result = moduleName.hashCode();
            result = 31 * result + requiresFlags;
            result = 31 * result + version.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Requires{" +
                    "moduleName='" + moduleName + '\'' +
                    ", requiresFlags=" + requiresFlags +
                    ", version='" + version + '\'' +
                    '}';
        }
    }

    public static final class Exports {
        public final String packageName;
        public final short exportsFlags;

        /**
         * Modules whose code can access the types and members in this exported package.
         */
        public final Set<String> exportsToModules;

        public Exports(String packageName, short exportsFlags, Set<String> exportsToModules) {
            this.packageName = packageName;
            this.exportsFlags = exportsFlags;
            this.exportsToModules = exportsToModules;
        }

        public boolean isSynthetic() {
            return ExportsFlags.isSynthetic(exportsFlags);
        }

        public boolean isMandated() {
            return ExportsFlags.isMandated(exportsFlags);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Exports exports = (Exports) o;
            return exportsFlags == exports.exportsFlags && packageName.equals(exports.packageName) &&
                    exportsToModules.equals(exports.exportsToModules);
        }

        @Override
        public int hashCode() {
            int result = packageName.hashCode();
            result = 31 * result + exportsFlags;
            result = 31 * result + exportsToModules.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Exports{" +
                    "packageName='" + packageName + '\'' +
                    ", exportsFlags=" + exportsFlags +
                    ", exportsToModules=" + exportsToModules +
                    '}';
        }
    }

    public static final class Opens {
        public final String packageName;
        public final short opensFlags;

        /**
         * Modules whose code can access the types and members in this opened package.
         */
        public final Set<String> opensToModules;

        public Opens(String packageName, short opensFlags, Set<String> opensToModules) {
            this.packageName = packageName;
            this.opensFlags = opensFlags;
            this.opensToModules = opensToModules;
        }

        public boolean isSynthetic() {
            return OpensFlags.isSynthetic(opensFlags);
        }

        public boolean isMandated() {
            return OpensFlags.isMandated(opensFlags);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Opens opens = (Opens) o;
            return opensFlags == opens.opensFlags && packageName.equals(opens.packageName) &&
                    opensToModules.equals(opens.opensToModules);
        }

        @Override
        public int hashCode() {
            int result = packageName.hashCode();
            result = 31 * result + opensFlags;
            result = 31 * result + opensToModules.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Opens{" +
                    "packageName='" + packageName + '\'' +
                    ", opensFlags=" + opensFlags +
                    ", opensToModules=" + opensToModules +
                    '}';
        }
    }

    public static final class Provides {
        /**
         * A service interface for which the current module provides a service implementation.
         */
        public final String serviceName;

        /**
         * Service implementations for the service interface specified by {@link Provides#serviceName}.
         */
        public final Set<String> providesWith;

        public Provides(String serviceName, Set<String> providesWith) {
            this.serviceName = serviceName;
            this.providesWith = providesWith;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Provides provides = (Provides) o;
            return serviceName.equals(provides.serviceName) && providesWith.equals(provides.providesWith);
        }

        @Override
        public int hashCode() {
            int result = serviceName.hashCode();
            result = 31 * result + providesWith.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Provides{" +
                    "serviceName='" + serviceName + '\'' +
                    ", providesWith=" + providesWith +
                    '}';
        }
    }

    public final String moduleName;
    public final short moduleFlags;
    public final String moduleVersion;
    public final List<Requires> requires;
    public final List<Exports> exports;
    public final List<Opens> opens;
    public final Set<String> uses;
    public final List<Provides> provides;

    public ModuleAttribute(String moduleName,
                           short moduleFlags,
                           String moduleVersion,
                           List<Requires> requires,
                           List<Exports> exports,
                           List<Opens> opens,
                           Set<String> uses,
                           List<Provides> provides) {
        this.moduleName = moduleName;
        this.moduleFlags = moduleFlags;
        this.moduleVersion = moduleVersion;
        this.requires = requires;
        this.exports = exports;
        this.opens = opens;
        this.uses = uses;
        this.provides = provides;
    }

    public boolean isOpen() {
        return ModuleFlags.isOpen(moduleFlags);
    }

    public boolean isSynthetic() {
        return ModuleFlags.isSynthetic(moduleFlags);
    }

    public boolean isMandated() {
        return ModuleFlags.isMandated(moduleFlags);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        ModuleAttribute that = (ModuleAttribute) o;
        return moduleFlags == that.moduleFlags && moduleName.equals(that.moduleName) &&
                moduleVersion.equals(that.moduleVersion) && requires.equals(that.requires) &&
                exports.equals(that.exports) && opens.equals(that.opens) && uses.equals(that.uses) &&
                provides.equals(that.provides);
    }

    @Override
    public int hashCode() {
        int result = moduleName.hashCode();
        result = 31 * result + moduleFlags;
        result = 31 * result + moduleVersion.hashCode();
        result = 31 * result + requires.hashCode();
        result = 31 * result + exports.hashCode();
        result = 31 * result + opens.hashCode();
        result = 31 * result + uses.hashCode();
        result = 31 * result + provides.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ModuleAttribute{" +
                "name='" + moduleName + '\'' +
                ", flags=" + moduleFlags +
                ", version='" + moduleVersion + '\'' +
                ", requires=" + requires +
                ", exports=" + exports +
                ", opens=" + opens +
                ", uses=" + uses +
                ", provides=" + provides +
                '}';
    }
}
