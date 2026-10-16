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
package org.projectnessie.cel.interpreter;

import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Util.isUnknownOrError;
import static org.projectnessie.cel.interpreter.Interpretable.sumOfCost;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.projectnessie.cel.OperationAbortedException.Phase;
import org.projectnessie.cel.common.types.MapT;
import org.projectnessie.cel.common.types.OptionalT;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;

/** Package-private established construction nodes. */
final class EvalMap extends AbstractEval implements Coster {
  final Interpretable[] keys;
  final Interpretable[] vals;
  final boolean[] optionalEntries;
  private final TypeAdapter adapter;

  EvalMap(long id, Interpretable[] keys, Interpretable[] vals, TypeAdapter adapter) {
    this(id, keys, vals, new boolean[keys.length], adapter);
  }

  EvalMap(
      long id,
      Interpretable[] keys,
      Interpretable[] vals,
      boolean[] optionalEntries,
      TypeAdapter adapter) {
    super(id);
    this.keys = keys;
    this.vals = vals;
    this.optionalEntries = optionalEntries;
    this.adapter = adapter;
  }

  /** Eval implements the Interpretable interface method. */
  @Override
  public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
    var controller = ActivationControls.controller(ctx);
    Map<Val, Val> entries = new HashMap<>(keys.length * 4 / 3 + 1);
    // If any argument is unknown or error early terminate.
    for (int i = 0; i < keys.length; i++) {
      controller.checkpoint(Phase.EVALUATE);
      Interpretable key = keys[i];
      Val keyVal = key.eval(ctx);
      if (isUnknownOrError(keyVal)) {
        return keyVal;
      }
      if (!MapT.isSupportedLiteralKeyType(keyVal)) {
        return newErr("unsupported key type");
      }
      Val valVal = vals[i].eval(ctx);
      if (isUnknownOrError(valVal)) {
        return valVal;
      }
      if (optionalEntries[i]) {
        if (!(valVal instanceof OptionalT optional)) {
          return newErr("optional map entry is not optional");
        }
        if (!optional.hasValue()) {
          continue;
        }
        valVal = optional.getValue();
      }
      if (entries.putIfAbsent(keyVal, valVal) != null) {
        // Prevent duplicate keys, error out.
        return newErr("Failed with repeated key");
      }
    }
    return MapT.newWrappedMap(adapter, entries);
  }

  /** Cost implements the Coster interface method. */
  @Override
  public Cost cost() {
    Cost k = sumOfCost(keys);
    Cost v = sumOfCost(vals);
    return k.add(v);
  }

  @Override
  public String toString() {
    return "EvalMap{"
        + "id="
        + id
        + ", keys="
        + Arrays.toString(keys)
        + ", vals="
        + Arrays.toString(vals)
        + '}';
  }
}

final class EvalObj extends AbstractEval implements Coster {
  private final String typeName;
  private final String[] fields;
  private final Interpretable[] vals;
  private final boolean[] optionalEntries;
  private final TypeProvider provider;

  EvalObj(
      long id,
      String typeName,
      String[] fields,
      Interpretable[] vals,
      boolean[] optionalEntries,
      TypeProvider provider) {
    super(id);
    this.typeName = Objects.requireNonNull(typeName);
    this.fields = Objects.requireNonNull(fields);
    this.vals = Objects.requireNonNull(vals);
    this.optionalEntries = optionalEntries;
    this.provider = Objects.requireNonNull(provider);
  }

  /** Eval implements the Interpretable interface method. */
  @Override
  public Val eval(org.projectnessie.cel.interpreter.Activation ctx) {
    var controller = ActivationControls.controller(ctx);
    Map<String, Val> fieldVals = new HashMap<>();
    // If any argument is unknown or error early terminate.
    for (int i = 0; i < fields.length; i++) {
      controller.checkpoint(Phase.EVALUATE);
      String field = fields[i];
      Val val = vals[i].eval(ctx);
      if (isUnknownOrError(val)) {
        return val;
      }
      if (optionalEntries[i]) {
        if (!(val instanceof OptionalT optional)) {
          return newErr("optional message field is not optional");
        }
        if (!optional.hasValue()) {
          continue;
        }
        val = optional.getValue();
      }
      fieldVals.put(field, val);
    }
    return provider.newValue(typeName, fieldVals);
  }

  /** Cost implements the Coster interface method. */
  @Override
  public Cost cost() {
    return sumOfCost(vals);
  }

  @Override
  public String toString() {
    return "EvalObj{"
        + "id="
        + id
        + ", typeName='"
        + typeName
        + '\''
        + ", fields="
        + Arrays.toString(fields)
        + ", vals="
        + Arrays.toString(vals)
        + ", provider="
        + provider
        + '}';
  }
}
