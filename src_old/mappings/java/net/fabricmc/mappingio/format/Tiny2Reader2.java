/*
 * Copyright (c) 2021 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.mappingio.format;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingFlag;
import net.fabricmc.mappingio.MappingVisitor;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public final class Tiny2Reader2 {
    @SuppressWarnings("unused")
    public static List<String> getNamespaces(Reader reader) throws IOException {
        return getNamespaces(new ColumnFileReader(reader, '\t'));
    }

    private static List<String> getNamespaces(ColumnFileReader reader) throws IOException {
        if (!reader.nextCol("tiny") // magic
            || reader.nextIntCol() != 2 // major version
            || reader.nextIntCol() < 0) { // minor version
            throw new IOException("invalid/unsupported tiny file: no tiny 2 header");
        }

        List<String> ret = new ArrayList<>();
        String ns;

        while ((ns = reader.nextCol()) != null) {
            ret.add(ns);
        }

        return ret;
    }

    public static void read(Reader reader, MappingVisitor visitor) throws IOException {
        read(new ColumnFileReader(reader, '\t'), visitor);
    }

    private static void read(ColumnFileReader reader, MappingVisitor visitor) throws IOException {
        if (!reader.nextCol("tiny") // magic
            || reader.nextIntCol() != 2 // major version
            || reader.nextIntCol() < 0) { // minor version
            throw new IOException("invalid/unsupported tiny file: no tiny 2 header");
        }

        String srcNamespace = reader.nextCol();
        List<String> dstNamespaces = new ArrayList<>();
        String dstNamespace;

        while ((dstNamespace = reader.nextCol()) != null) {
            dstNamespaces.add(dstNamespace);
        }

        int dstNsCount = dstNamespaces.size();

        if (visitor.getFlags().contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
            reader.mark();
        }

        boolean firstIteration = true;
        boolean escapeNames = false;

        for (; ; ) {
            boolean visitHeader = visitor.visitHeader();

            if (visitHeader) {
                visitor.visitNamespaces(srcNamespace, dstNamespaces);
            }

            if (visitHeader || firstIteration) {
                while (reader.nextLine(1)) {
                    if (!visitHeader) {
                        if (!escapeNames && reader.nextCol(Tiny2Util.escapedNamesProperty)) {
                            escapeNames = true;
                        }
                    } else {
                        String key = reader.nextCol();
                        if (key == null) {
                            throw new IOException("missing property key in line " + reader.getLineNumber());
                        }
                        String value = reader.nextEscapedCol(); // may be missing -> null

                        if (key.equals(Tiny2Util.escapedNamesProperty)) {
                            escapeNames = true;
                        }

                        visitor.visitMetadata(key, value);
                    }
                }
            }

            if (visitor.visitContent()) {
                while (reader.nextLine(0)) {
                    if (reader.nextCol("c")) { // class: c <names>...
                        String srcName = reader.nextCol(escapeNames);
                        if (srcName == null || srcName.isEmpty()) {
                            throw new IOException("missing class-name-a in line " + reader.getLineNumber());
                        }

                        if (visitor.visitClass(srcName)) {
                            readClass(reader, dstNsCount, escapeNames, visitor);
                        }
                    }
                }
            }

            if (visitor.visitEnd()) {
                break;
            }

            reader.reset();
            firstIteration = false;
        }
    }

    private static void readClass(ColumnFileReader reader, int dstNsCount, boolean escapeNames, MappingVisitor visitor) throws IOException {
        readDstNames(reader, MappedElementKind.CLASS, dstNsCount, escapeNames, visitor);
        if (!visitor.visitElementContent(MappedElementKind.CLASS)) {
            return;
        }

        while (reader.nextLine(1)) {
            if (reader.nextCol("f")) { // field: f <descA> <names>...
                String srcDesc = reader.nextCol(escapeNames);
                //				if (srcDesc == null || srcDesc.isEmpty()) throw new IOException("missing field-desc-a in line "+reader.getLineNumber());
                String srcName = reader.nextCol(escapeNames);
                if (srcName == null || srcName.isEmpty()) {
                    throw new IOException("missing field-name-a in line " + reader.getLineNumber());
                }

                if (visitor.visitField(srcName, srcDesc)) {
                    readElement(reader, MappedElementKind.FIELD, dstNsCount, escapeNames, visitor);
                }
            } else if (reader.nextCol("m")) { // method: m <descA> <names>...
                String srcDesc = reader.nextCol(escapeNames);
                if (srcDesc == null || srcDesc.isEmpty()) {
                    throw new IOException("missing method-desc-a in line " + reader.getLineNumber());
                }
                String srcName = reader.nextCol(escapeNames);
                if (srcName == null || srcName.isEmpty()) {
                    throw new IOException("missing method-name-a in line " + reader.getLineNumber());
                }

                if (visitor.visitMethod(srcName, srcDesc)) {
                    readMethod(reader, dstNsCount, escapeNames, visitor);
                }
            } else if (reader.nextCol("c")) { // comment: c <comment>
                readComment(reader, MappedElementKind.CLASS, visitor);
            }
        }
    }

    private static void readMethod(ColumnFileReader reader, int dstNsCount, boolean escapeNames, MappingVisitor visitor) throws IOException {
        readDstNames(reader, MappedElementKind.METHOD, dstNsCount, escapeNames, visitor);
        if (!visitor.visitElementContent(MappedElementKind.METHOD)) {
            return;
        }

        while (reader.nextLine(2)) {
            if (reader.nextCol("p")) { // method parameter: p <lv-index> <names>...
                int lvIndex = reader.nextIntCol();
                //				if (lvIndex < 0) throw new IOException("missing/invalid parameter lv-index in line "+reader.getLineNumber());
                String srcName = reader.nextCol(escapeNames);
                if (srcName == null) {
                    throw new IOException("missing var-name-a column in line " + reader.getLineNumber());
                }
                if (srcName.isEmpty()) {
                    srcName = null;
                }

                if (visitor.visitMethodArg(-1, lvIndex, srcName)) {
                    readElement(reader, MappedElementKind.METHOD_ARG, dstNsCount, escapeNames, visitor);
                }
            } else if (reader.nextCol("v")) { // method variable: v <lv-index> <lv-start-offset> <optional-lvt-index> <names>...
                int lvIndex = reader.nextIntCol();
                if (lvIndex < 0) {
                    throw new IOException("missing/invalid variable lv-index in line " + reader.getLineNumber());
                }
                int startOpIdx = reader.nextIntCol();
                if (startOpIdx < 0) {
                    throw new IOException("missing/invalid variable lv-start-offset in line " + reader.getLineNumber());
                }
                int lvtRowIndex = reader.nextIntCol();
                String srcName = reader.nextCol(escapeNames);
                if (srcName == null) {
                    throw new IOException("missing var-name-a column in line " + reader.getLineNumber());
                }
                if (srcName.isEmpty()) {
                    srcName = null;
                }

                if (visitor.visitMethodVar(lvtRowIndex, lvIndex, startOpIdx, srcName)) {
                    readElement(reader, MappedElementKind.METHOD_VAR, dstNsCount, escapeNames, visitor);
                }
            } else if (reader.nextCol("c")) { // comment: c <comment>
                readComment(reader, MappedElementKind.METHOD, visitor);
            }
        }
    }

    private static void readElement(ColumnFileReader reader, MappedElementKind kind, int dstNsCount, boolean escapeNames, MappingVisitor visitor) throws IOException {
        readDstNames(reader, kind, dstNsCount, escapeNames, visitor);
        if (!visitor.visitElementContent(kind)) {
            return;
        }

        while (reader.nextLine(kind.level + 1)) {
            if (reader.nextCol("c")) { // comment: c <comment>
                readComment(reader, kind, visitor);
            }
        }
    }

    private static void readComment(ColumnFileReader reader, MappedElementKind subjectKind, MappingVisitor visitor) throws IOException {
        String comment = reader.nextEscapedCol();
        if (comment == null) {
            throw new IOException("missing comment in line " + reader.getLineNumber());
        }

        visitor.visitComment(subjectKind, comment);
    }

    private static void readDstNames(ColumnFileReader reader, MappedElementKind subjectKind, int dstNsCount, boolean escapeNames, MappingVisitor visitor) throws IOException {
        for (int dstNs = 0; dstNs < dstNsCount; dstNs++) {
            String name = reader.nextCol(escapeNames);
            if (name == null) {
                throw new IOException("missing name columns in line " + reader.getLineNumber());
            }

            if (!name.isEmpty()) {
                visitor.visitDstName(subjectKind, dstNs, name);
            }
        }
    }

}
