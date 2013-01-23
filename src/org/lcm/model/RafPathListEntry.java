package org.lcm.model;

import java.nio.ByteBuffer;

public class RafPathListEntry {
	// offset from the path list
	int pathOffset;
	int length;
	public RafPathListEntry(ByteBuffer data) {
		pathOffset = data.getInt();
		length = data.getInt();
	}
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RafPathListEntry [pathOffset=");
		builder.append(pathOffset);
		builder.append(", length=");
		builder.append(length);
		builder.append("]");
		return builder.toString();
	}
}