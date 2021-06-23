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
import org.agrona.collections.IntArrayList;

/** Source interface for filter source contents. */
public interface Source {
  /** NewTextSource creates a new Source from the input text string. */
  static Source newTextSource(String text) {
    return newStringSource(text, "<input>");
  }

  /** NewStringSource creates a new Source from the given contents and description. */
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

  /** NewInfoSource creates a new Source from a SourceInfo. */
  static Source newInfoSource(SourceInfo info) {
    return new SourceImpl(
        "", info.getLocation(), info.getLineOffsetsList(), info.getPositionsMap());
  }

  /**
   * Content returns the source content represented as a string. Examples contents are the single
   * file contents, textbox field, or url parameter.
   */
  String content();

  /**
   * Description gives a brief description of the source. Example descriptions are a file name or ui
   * element.
   */
  String description();

  /**
   * LineOffsets gives the character offsets at which lines occur. The zero-th entry should refer to
   * the break between the first and second line, or EOF if there is only one line of source.
   */
  List<Integer> lineOffsets();

  /**
   * LocationOffset translates a Location to an offset. Given the line and column of the Location
   * returns the Location's character offset in the Source, and a bool indicating whether the
   * Location was found.
   */
  int locationOffset(Location location);

  /**
   * OffsetLocation translates a character offset to a Location, or false if the conversion was not
   * feasible.
   */
  Location offsetLocation(int offset);

  /**
   * NewLocation takes an input line and column and produces a Location. The default behavior is to
   * treat the line and column as absolute, but concrete derivations may use this method to convert
   * a relative line and column position into an absolute location.
   */
  Location newLocation(int line, int col);

  /** Snippet returns a line of content and whether the line was found. */
  String snippet(int line);
}

final class SourceImpl implements Source {

  private final String content;
  private final String description;
  private final List<Integer> lineOffsets;
  private final Map<Long, Integer> idOffsets;

  SourceImpl(String content, String description, List<Integer> lineOffsets) {
    this(content, description, lineOffsets, new HashMap<>());
  }

  SourceImpl(
      String content, String description, List<Integer> lineOffsets, Map<Long, Integer> idOffsets) {
    this.content = content;
    this.description = description;
    this.lineOffsets = lineOffsets;
    this.idOffsets = idOffsets;
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
    return findLineOffset(location.line()) + location.column();
  }

  @Override
  public Location newLocation(int line, int col) {
    return Location.newLocation(line, col);
  }

  @Override
  public Location offsetLocation(int offset) {
    // findLine finds the line that contains the given character offset and
    // returns the line number and offset of the beginning of that line.
    // Note that the last line is treated as if it contains all offsets
    // beyond the end of the actual source.
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
    int charStart = findLineOffset(line);
    if (charStart < 0) {
      return null;
    }
    int charEnd = findLineOffset(line + 1);
    if (charEnd >= 0) {
      return content.substring(charStart, charEnd - 1);
    }
    return content.substring(charStart);
  }

  /**
   * findLineOffset returns the offset where the (1-indexed) line begins, or false if line doesn't
   * exist.
   */
  private int findLineOffset(int line) {
    if (line == 1) {
      return 0;
    }
    if (line > 1 && line <= lineOffsets.size()) {
      return lineOffsets.get(line - 2);
    }
    return -1;
  }
}
