package jbuild.extension;

import java.util.function.Consumer;

interface JbManifestEntry {
    String getTaskName();

    String getClassName();

    void with(Consumer<JbManifestEntry.Parsed> whenParsed, Consumer<JbManifestEntry.Str> whenStr);

    final class Parsed implements JbManifestEntry {
        final String className;
        final String taskName;
        final String description;
        final String phaseName;
        final int phaseIndex;
        final ConfigObject.ConfigObjectDescriptor descriptor;

        Parsed(String className,
               String taskName,
               String description,
               String phaseName,
               int phaseIndex,
               ConfigObject.ConfigObjectDescriptor descriptor) {
            this.className = className;
            this.taskName = taskName;
            this.description = description;
            this.phaseName = phaseName;
            this.phaseIndex = phaseIndex;
            this.descriptor = descriptor;
        }

        @Override
        public String getTaskName() {
            return taskName;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public void with(Consumer<Parsed> whenParsed, Consumer<Str> whenStr) {
            whenParsed.accept(this);
        }

        @Override
        public String toString() {
            return "JbManifestEntry.Parsed{" +
                    "taskName='" + getTaskName() + '\'' +
                    ", className='" + getClassName() + '\'' +
                    '}';
        }
    }

    final class Str implements JbManifestEntry {
        private final String className;
        private final String taskName;
        final String yamlString;

        Str(String className, String taskName, String yamlString) {
            this.className = className;
            this.taskName = taskName;
            this.yamlString = yamlString;
        }

        @Override
        public String getTaskName() {
            return taskName;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public void with(Consumer<Parsed> whenParsed, Consumer<Str> whenStr) {
            whenStr.accept(this);
        }

        @Override
        public String toString() {
            return "JbManifestEntry.Str{" +
                    "taskName='" + getTaskName() + '\'' +
                    ", className='" + getClassName() + '\'' +
                    '}';
        }
    }

}
