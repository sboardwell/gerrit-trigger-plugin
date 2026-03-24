/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.cluster;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Hazelcast Compact Serializer for {@link MemoryImprintData}.
 * <p>
 * Compact Serialization is schema-based and doesn't require class definitions
 * on the Hazelcast server (sidecar container). This enables cross-JVM serialization
 * without classloading issues.
 *
 * @author CloudBees, Inc.
 */
public class MemoryImprintDataSerializer implements CompactSerializer<MemoryImprintData> {

    /**
     * Type name for schema registration.
     * Must be unique across all compact serialized types.
     */
    private static final String TYPE_NAME = "MemoryImprintData";

    @Override
    @NonNull
    public MemoryImprintData read(@NonNull CompactReader reader) {
        String eventJson = reader.readString("eventJson");

        // Read number of entries
        int entryCount = reader.readInt32("entryCount");
        List<EntryData> entries = new ArrayList<>(entryCount);

        // Read each entry
        for (int i = 0; i < entryCount; i++) {
            EntryData entry = new EntryData();
            entry.setProjectFullName(reader.readString("entry_" + i + "_projectFullName"));
            entry.setBuildId(reader.readString("entry_" + i + "_buildId"));
            entry.setBuildCompleted(reader.readBoolean("entry_" + i + "_buildCompleted"));
            entry.setCancelled(reader.readBoolean("entry_" + i + "_cancelled"));
            entry.setCustomUrl(reader.readString("entry_" + i + "_customUrl"));
            entry.setUnsuccessfulMessage(reader.readString("entry_" + i + "_unsuccessfulMessage"));
            entry.setTriggeredTimestamp(reader.readInt64("entry_" + i + "_triggeredTimestamp"));
            entry.setCompletedTimestamp(reader.readNullableInt64("entry_" + i + "_completedTimestamp"));
            entry.setStartedTimestamp(reader.readNullableInt64("entry_" + i + "_startedTimestamp"));

            entries.add(entry);
        }

        return new MemoryImprintData(eventJson, entries);
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull MemoryImprintData data) {
        writer.writeString("eventJson", data.getEventJson());

        // Write number of entries
        List<EntryData> entries = data.getEntries();
        int entryCount = 0;
        if (entries != null) {
            entryCount = entries.size();
        }
        writer.writeInt32("entryCount", entryCount);

        // Write each entry
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                EntryData entry = entries.get(i);
                writer.writeString("entry_" + i + "_projectFullName", entry.getProjectFullName());
                writer.writeString("entry_" + i + "_buildId", entry.getBuildId());
                writer.writeBoolean("entry_" + i + "_buildCompleted", entry.isBuildCompleted());
                writer.writeBoolean("entry_" + i + "_cancelled", entry.isCancelled());
                writer.writeString("entry_" + i + "_customUrl", entry.getCustomUrl());
                writer.writeString("entry_" + i + "_unsuccessfulMessage", entry.getUnsuccessfulMessage());
                writer.writeInt64("entry_" + i + "_triggeredTimestamp", entry.getTriggeredTimestamp());
                writer.writeNullableInt64("entry_" + i + "_completedTimestamp", entry.getCompletedTimestamp());
                writer.writeNullableInt64("entry_" + i + "_startedTimestamp", entry.getStartedTimestamp());
            }
        }
    }

    @Override
    @NonNull
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    @NonNull
    public Class<MemoryImprintData> getCompactClass() {
        return MemoryImprintData.class;
    }
}
