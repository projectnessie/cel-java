/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
package org.projectnessie.cel.types.jackson.types;

import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.types.ref.Val;

public class ArrayObject {
  public byte[] bytes;
  public int[] ints;
  public long[] longs;
  public double[] doubles;
  public String[] strings;
  public Integer[] boxedInts;
  public ULong[] uints;
  public AnEnum[] enums;
  public InnerType[] objects;
  public Object[] dynamic;
  public Val[] values;
  public int[][] nestedInts;
  public byte[][] nestedBytes;
}
