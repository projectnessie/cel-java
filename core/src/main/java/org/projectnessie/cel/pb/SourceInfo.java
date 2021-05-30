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
package org.projectnessie.cel.pb;

import org.agrona.collections.Long2LongHashMap;

public class SourceInfo {
  private final String description;
  private final Long2LongHashMap positions;
  private final int[] lineOffsets;

  public SourceInfo(String description, Long2LongHashMap positions, int[] lineOffsets) {
    this.description = description;
    this.positions = positions;
    this.lineOffsets = lineOffsets;
  }

  public long getPosition(long exprID) {
    return positions.get(exprID);
  }

  public int[] getLineOffsets() {
    return lineOffsets;
  }

  public long getLineOffset(int i) {
    return lineOffsets[i];
  }
}
