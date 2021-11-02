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
package org.projectnessie.cel.parser;

import org.projectnessie.cel.shaded.org.antlr.v4.runtime.CharStream;
import org.projectnessie.cel.shaded.org.antlr.v4.runtime.IntStream;
import org.projectnessie.cel.shaded.org.antlr.v4.runtime.misc.Interval;

public final class StringCharStream implements CharStream {

  private final String buf;
  private final String src;
  private int pos;

  public StringCharStream(String buf, String src) {
    this.buf = buf;
    this.src = src;
  }

  @Override
  public void consume() {
    if (pos >= buf.length()) {
      throw new RuntimeException("cannot consume EOF");
    }
    pos++;
  }

  @Override
  public int LA(int offset) {
    if (offset == 0) {
      return 0;
    }
    if (offset < 0) {
      offset++;
    }
    pos = pos + offset - 1;
    if (pos < 0 || pos >= buf.length()) {
      return IntStream.EOF;
    }
    return buf.charAt(pos);
  }

  @Override
  public int mark() {
    return -1;
  }

  @Override
  public void release(int marker) {}

  @Override
  public int index() {
    return pos;
  }

  @Override
  public void seek(int index) {
    if (index <= pos) {
      pos = index;
      return;
    }
    pos = Math.min(index, buf.length());
  }

  @Override
  public int size() {
    return buf.length();
  }

  @Override
  public String getSourceName() {
    return src;
  }

  @Override
  public String getText(Interval interval) {
    int start = interval.a;
    int stop = interval.b;
    if (stop >= buf.length()) {
      stop = buf.length() - 1;
    }
    if (start >= buf.length()) {
      return "";
    }
    return buf.substring(start, stop + 1);
  }

  @Override
  public String toString() {
    return buf;
  }
}
