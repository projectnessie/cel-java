/*
 * Copyright (C) 2021 The Authors of CEL-Java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.cel.common;

import com.google.api.expr.v1alpha1.SourceInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.agrona.collections.IntArrayList;

/**
 * Source text and location metadata used when parsing expressions and reporting diagnostics.
 *
 * <p>Locations use one-based line numbers and zero-based columns. Source offsets are nonnegative
 * integer positions in the coordinate system used by the source.
 */
public interface Source {
  /**
   * Creates a source for the given text with the default {@code <input>} description.
   *
   * @param text source text
   * @return a text-backed source
   */
  static Source newTextSource(String text) {
    return newStringSource(text, "<input>");
  }

  /**
   * Creates a source for the given text and description.
   *
   * @param contents source text
   * @param description brief source description, such as a file name
   * @return a text-backed source
   */
  static Source newStringSource(String contents, String description) {
    // Compute line offsets up front as they are referred to frequently.
    IntArrayList offsets = new IntArrayList();
    for (int i = 0; i <= contents.length(); ) {
      if (i > 0) {
        // don't add '0' for the first line, it's implicit
        offsets.add(i);
      }
      int nl = contents.indexOf('\n', i);
      if (nl == -1) {
        offsets.add(contents.length() + 1);
        break;
      } else {
        i = nl + 1;
      }
    }

    return new SourceImpl(contents, description, offsets);
  }

  /**
   * Creates a metadata-only source from the given source information.
   *
   * <p>The returned source has no source text and therefore cannot validate coordinates against an
   * exact EOF. It retains the supplied line-offset representation for compatibility.
   *
   * @param info source metadata
   * @return a metadata-only source
   * @throws NullPointerException if {@code info} is {@code null}
   */
  static Source newInfoSource(SourceInfo info) {
    return SourceImpl.fromSourceInfo(info);
  }

  /**
   * Returns the source content, or an empty string when the source was created from metadata
   * without its original text.
   *
   * @return source text, possibly empty
   */
  String content();

  /**
   * Returns a brief description of the source, such as a file name or UI element.
   *
   * @return source description
   */
  String description();

  /**
   * Returns immutable line-offset metadata in this source's coordinate system.
   *
   * <p>For a text-backed source, entries identify the start of each line after the first and the
   * final entry is a synthetic {@code content().length() + 1} terminal sentinel. For a
   * metadata-only source, the supplied entries are retained and interpreted as starts of lines
   * after the first; no exact EOF sentinel can be inferred.
   *
   * @return immutable line-offset metadata
   */
  List<Integer> lineOffsets();

  /**
   * Translates a location to a source offset.
   *
   * <p>End-of-line positions and, for text-backed sources, the EOF position are valid. Text-backed
   * sources validate the complete location against their content. Metadata-only sources validate
   * every available line boundary, but the final line remains unbounded because its EOF is unknown.
   *
   * @param location one-based line and zero-based column
   * @return the nonnegative source offset, or {@code -1} if the location is invalid or outside a
   *     known bound
   * @throws NullPointerException if {@code location} is {@code null}
   */
  int locationOffset(Location location);

  /**
   * Translates a source offset to a location.
   *
   * <p>For a text-backed source, offsets from zero through EOF are valid. A metadata-only source
   * accepts every nonnegative offset because its exact EOF is unknown.
   *
   * @param offset nonnegative source offset
   * @return the corresponding location, or {@link Location#NoLocation} if the offset is invalid or
   *     outside a known bound
   */
  Location offsetLocation(int offset);

  /**
   * Creates a location from the given line and column.
   *
   * <p>This method does not validate the location against the source. Use {@link
   * #locationOffset(Location)} to validate and translate it.
   *
   * @param line one-based line number
   * @param col zero-based column number
   * @return the created location
   */
  Location newLocation(int line, int col);

  /**
   * Returns the content of the requested one-based line.
   *
   * @param line one-based line number
   * @return line content without the terminating newline, or {@code null} if the line is
   *     unavailable
   */
  String snippet(int line);
}

final class SourceImpl implements Source {

  private static final int UNKNOWN_CONTENT_LENGTH = -1;

  private final String content;
  private final String description;
  private final List<Integer> lineOffsets;
  private final Map<Long, Integer> idOffsets;
  private final int contentLength;

  SourceImpl(String content, String description, List<Integer> lineOffsets) {
    this(content, description, lineOffsets, new HashMap<>(), content.length());
  }

  static SourceImpl fromSourceInfo(SourceInfo info) {
    return new SourceImpl(
        "",
        info.getLocation(),
        info.getLineOffsetsList(),
        info.getPositionsMap(),
        UNKNOWN_CONTENT_LENGTH);
  }

  private SourceImpl(
      String content,
      String description,
      List<Integer> lineOffsets,
      Map<Long, Integer> idOffsets,
      int contentLength) {
    this.content = content;
    this.description = description;
    this.lineOffsets = List.copyOf(lineOffsets);
    this.idOffsets = idOffsets;
    this.contentLength = contentLength;
  }

  @Override
  public String content() {
    return content;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public List<Integer> lineOffsets() {
    return lineOffsets;
  }

  @Override
  public int locationOffset(Location location) {
    Objects.requireNonNull(location, "location");

    int column = location.column();
    if (column < 0) {
      return -1;
    }

    int lineStart = findLocationLineStart(location.line());
    if (lineStart < 0) {
      return -1;
    }

    int nextLineBoundary = findNextLineBoundary(location.line());
    long maximumColumn;
    if (nextLineBoundary >= 0) {
      maximumColumn = (long) nextLineBoundary - lineStart - 1;
    } else if (contentLength != UNKNOWN_CONTENT_LENGTH) {
      // Text-backed sources normally have a terminal sentinel. Retain an exact fallback for
      // directly constructed SourceImpl instances whose offsets omit it.
      maximumColumn = (long) contentLength - lineStart;
    } else {
      maximumColumn = Integer.MAX_VALUE;
    }

    long offset = (long) lineStart + column;
    if (column > maximumColumn || offset > Integer.MAX_VALUE) {
      return -1;
    }
    return (int) offset;
  }

  @Override
  public Location newLocation(int line, int col) {
    return Location.newLocation(line, col);
  }

  @Override
  public Location offsetLocation(int offset) {
    if (offset < 0 || (contentLength != UNKNOWN_CONTENT_LENGTH && offset > contentLength)) {
      return Location.NoLocation;
    }

    // findLine finds the line that contains the given character offset and
    // returns the line number and offset of the beginning of that line.
    // The final line is unbounded only when the original source text is unavailable.
    int line = 1;
    int lineOffset;
    for (int lo : lineOffsets) {
      if (lo > offset) {
        break;
      } else {
        line++;
      }
    }
    if (line == 1) {
      lineOffset = 0;
    } else {
      lineOffset = lineOffsets.get(line - 2);
    }

    return Location.newLocation(line, offset - lineOffset);
  }

  @Override
  public String snippet(int line) {
    int charStart = findSnippetLineOffset(line);
    if (charStart < 0) {
      return null;
    }
    int charEnd = findSnippetLineOffset(line + 1);
    if (charEnd >= 0) {
      return content.substring(charStart, charEnd - 1);
    }
    return content.substring(charStart);
  }

  private int findLocationLineStart(int line) {
    if (line == 1) {
      return 0;
    }
    if (line < 1) {
      return -1;
    }

    int lineStartCount =
        contentLength == UNKNOWN_CONTENT_LENGTH ? lineOffsets.size() : lineOffsets.size() - 1;
    int lineStartIndex = line - 2;
    if (lineStartIndex < lineStartCount) {
      return lineOffsets.get(lineStartIndex);
    }
    return -1;
  }

  private int findNextLineBoundary(int line) {
    int boundaryIndex = line - 1;
    if (boundaryIndex >= 0 && boundaryIndex < lineOffsets.size()) {
      return lineOffsets.get(boundaryIndex);
    }
    return -1;
  }

  private int findSnippetLineOffset(int line) {
    if (line == 1) {
      return 0;
    }
    if (line > 1 && line <= lineOffsets.size()) {
      return lineOffsets.get(line - 2);
    }
    return -1;
  }
}
