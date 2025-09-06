package jbuild.classes.parser;

import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.AttributeInfo;
import jbuild.classes.model.attributes.ModuleAttribute;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModuleAttributeParser extends AbstractAttributeParser {
    public ModuleAttributeParser(ClassFile classFile) {
        super(classFile);
    }

    public ModuleAttribute parseModuleAttribute(AttributeInfo attribute) {
        var scanner = new ByteScanner(attribute.attributes);
        var moduleName = nextConstModule(scanner);
        var flags = scanner.nextShort();
        var versionIndex = scanner.nextShort();
        var version = versionIndex == 0 ? "" : constUtf8(versionIndex);
        var requires = parseRequires(scanner);
        var exports = parseExports(scanner);
        var opens = parseOpens(scanner);
        var uses = parseUses(scanner);
        var provides = parseProvides(scanner);
        return new ModuleAttribute(moduleName, flags, version, requires, exports, opens, uses, provides);
    }

    private List<ModuleAttribute.Requires> parseRequires(ByteScanner scanner) {
        var requiresCount = scanner.nextShort();
        var requires = new ArrayList<ModuleAttribute.Requires>(requiresCount);
        for (int i = 0; i < requiresCount; i++) {
            var moduleName = nextConstModule(scanner);
            var flags = scanner.nextShort();
            var versionIndex = scanner.nextShort();
            var version = versionIndex == 0 ? "" : constUtf8(versionIndex);
            requires.add(new ModuleAttribute.Requires(moduleName, flags, version));
        }
        return requires;
    }

    private List<ModuleAttribute.Exports> parseExports(ByteScanner scanner) {
        var exportsCount = scanner.nextShort();
        var exports = new ArrayList<ModuleAttribute.Exports>(exportsCount);
        for (int i = 0; i < exportsCount; i++) {
            var packageName = nextConstPackage(scanner);
            var flags = scanner.nextShort();
            var exportsTo = parseModuleInfos(scanner);
            exports.add(new ModuleAttribute.Exports(packageName, flags, exportsTo));
        }
        return exports;
    }

    private List<ModuleAttribute.Opens> parseOpens(ByteScanner scanner) {
        var opensCount = scanner.nextShort();
        var opens = new ArrayList<ModuleAttribute.Opens>(opensCount);
        for (int i = 0; i < opensCount; i++) {
            var packageName = nextConstPackage(scanner);
            var flags = scanner.nextShort();
            var opensTo = parseModuleInfos(scanner);
            opens.add(new ModuleAttribute.Opens(packageName, flags, opensTo));
        }
        return opens;
    }

    private Set<String> parseUses(ByteScanner scanner) {
        return parseClasses(scanner);
    }

    private List<ModuleAttribute.Provides> parseProvides(ByteScanner scanner) {
        var providesCount = scanner.nextShort();
        var provides = new ArrayList<ModuleAttribute.Provides>(providesCount);
        for (int i = 0; i < providesCount; i++) {
            var className = nextConstClass(scanner);
            var providesWith = parseClasses(scanner);
            provides.add(new ModuleAttribute.Provides(className, providesWith));
        }
        return provides;
    }

    private Set<String> parseModuleInfos(ByteScanner scanner) {
        var count = scanner.nextShort();
        var result = new LinkedHashSet<String>(count);
        for (int i = 0; i < count; i++) {
            var moduleName = nextConstModule(scanner);
            result.add(moduleName);
        }
        return result;
    }

    private Set<String> parseClasses(ByteScanner scanner) {
        var count = scanner.nextShort();
        var result = new LinkedHashSet<String>(count);
        for (int i = 0; i < count; i++) {
            var className = nextConstClass(scanner);
            result.add(className);
        }
        return result;
    }
}
