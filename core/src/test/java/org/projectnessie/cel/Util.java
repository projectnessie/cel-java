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
package org.projectnessie.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.common.types.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.Val;

public class Util {

  @SuppressWarnings({"unchecked"})
  public static <K, V> Map<K, V> mapOf(K k, V v, Object... kvPairs) {
    Map<K, V> map = new HashMap<>();
    assertThat(kvPairs.length % 2).isEqualTo(0);
    map.put(k, v);
    for (int i = 0; i < kvPairs.length; i += 2) {
      map.put((K) kvPairs[i], (V) kvPairs[i + 1]);
    }
    return map;
  }

  public static <K, V> Map<K, V> mapOf() {
    return new HashMap<>();
  }

  @SuppressWarnings("rawtypes")
  public static void deepEquals(String context, Object a, Object b) {
    if (a == null && b == null) {
      return;
    }
    if (a == null) {
      fail(String.format("deepEquals(%s), a==null, b!=null", context));
    }
    if (b == null) {
      fail(String.format("deepEquals(%s), a!=null, b==null", context));
    }
    if (!a.getClass().isAssignableFrom(b.getClass())) {
      if (a instanceof Val && !(b instanceof Val)) {
        deepEquals(context, ((Val) a).value(), b);
        return;
      }
      if (!(a instanceof Val) && b instanceof Val) {
        deepEquals(context, a, ((Val) b).value());
        return;
      }
      fail(
          String.format(
              "deepEquals(%s), a.class(%s) is not assignable from b.class(%s)",
              context, a.getClass().getName(), b.getClass().getName()));
    }
    if (a.getClass().isArray()) {
      int al = Array.getLength(a);
      int bl = Array.getLength(b);
      if (al != bl) {
        fail(
            String.format(
                "deepEquals(%s), %s.length(%d) != %s.length(%d)",
                context, a.getClass().getName(), al, b.getClass().getName(), bl));
      }
      for (int i = 0; i < al; i++) {
        Object av = Array.get(a, i);
        Object bv = Array.get(b, i);
        deepEquals(context + '[' + i + ']', av, bv);
      }
    } else if (a instanceof List) {
      List al = (List) a;
      List bl = (List) b;
      int as = al.size();
      int bs = bl.size();
      if (as != bs) {
        fail(
            String.format(
                "deepEquals(%s), %s.size(%d) != %s.size(%d)",
                context, a.getClass().getName(), as, b.getClass().getName(), bs));
      }
      for (int i = 0; i < as; i++) {
        deepEquals(context + '[' + i + ']', al.get(i), bl.get(i));
      }
    } else if (a instanceof Map) {
      Map am = (Map) a;
      Map bm = (Map) b;
      int as = am.size();
      int bs = bm.size();
      if (as != bs) {
        fail(
            String.format(
                "deepEquals(%s), %s.size(%d) != %s.size(%d)",
                context, a.getClass().getName(), as, b.getClass().getName(), bs));
      }
      for (Object ak : am.keySet()) {
        Object av = am.get(ak);
        if (!bm.containsKey(ak)) {
          boolean f = true;
          if (ak instanceof Val) {
            Object an = ((Val) ak).value();
            if (bm.containsKey(an)) {
              ak = an;
              f = false;
            }
          } else {
            Val aval = DefaultTypeAdapter.Instance.nativeToValue(ak);
            if (bm.containsKey(aval)) {
              ak = aval;
              f = false;
            }
          }
          if (f) {
            fail(
                String.format(
                    "deepEquals(%s), %s(%s) contains %s, but %s(%s) does not",
                    context, a.getClass().getName(), as, ak, b.getClass().getName(), bs));
          }
        }
        Object bv = bm.get(ak);
        deepEquals(context + '[' + ak + ']', av, bv);
      }
    } else if (!a.equals(b)) {
      fail(
          String.format(
              "deepEquals(%s), %s(%s) != %s(%s)",
              context, a.getClass().getName(), a, b.getClass().getName(), b));
    }
  }
}
