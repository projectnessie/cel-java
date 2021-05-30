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

import java.util.Arrays;
import java.util.Objects;

public abstract class Expr {
  public final long id;

  protected Expr(long id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Expr expr = (Expr) o;
    return id == expr.id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static Error error(long id) {
    return new Error(id);
  }

  // TODO rename this class!
  public static class Error extends Expr {
    public Error(long id) {
      super(id);
    }
  }

  public static Const constExpr(long id, Constant value) {
    return new Const(id, value);
  }

  public static class Const extends Expr {
    public final Constant value;

    public Const(long id, Constant value) {
      super(id);
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
      Const aConst = (Const) o;
      return Objects.equals(value, aConst.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), value);
    }
  }

  public static IdentExpr ident(long id, String name) {
    return new IdentExpr(id, name);
  }

  public static class IdentExpr extends Expr {
    public final String name;

    public IdentExpr(long id, String name) {
      super(id);
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
      IdentExpr identExpr = (IdentExpr) o;
      return Objects.equals(name, identExpr.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), name);
    }
  }

  public static ListExpr list(long id, Expr... elements) {
    return new ListExpr(id, elements);
  }

  public static class ListExpr extends Expr {
    public final Expr[] elements;

    public ListExpr(long id, Expr[] elements) {
      super(id);
      this.elements = elements;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
      ListExpr listExpr = (ListExpr) o;
      return Arrays.equals(elements, listExpr.elements);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Arrays.hashCode(elements);
      return result;
    }
  }

  public static SelectExpr select(long id, Expr operand, String field, boolean testOnly) {
    return new SelectExpr(id, operand, field, testOnly);
  }

  public static class SelectExpr extends Expr {
    public final Expr operand;
    public final String field;
    public final boolean testOnly;

    public SelectExpr(long id, Expr operand, String field, boolean testOnly) {
      super(id);
      this.operand = operand;
      this.field = field;
      this.testOnly = testOnly;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
      SelectExpr that = (SelectExpr) o;
      return testOnly == that.testOnly
          && Objects.equals(operand, that.operand)
          && Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), operand, field, testOnly);
    }
  }

  public static CallExpr call(long id, String function, Expr target, Expr... args) {
    return new CallExpr(id, function, target, args);
  }

  public static class CallExpr extends Expr {
    public final String function;
    public final Expr target;
    public final Expr[] args;

    public CallExpr(long id, String function, Expr target, Expr[] args) {
      super(id);
      this.function = function;
      this.target = target;
      this.args = args;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
      CallExpr callExpr = (CallExpr) o;
      return Objects.equals(function, callExpr.function)
          && Objects.equals(target, callExpr.target)
          && Arrays.equals(args, callExpr.args);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(super.hashCode(), function, target);
      result = 31 * result + Arrays.hashCode(args);
      return result;
    }
  }

  public static StructExpr structExpr(long id, String messageName, StructExpr.Entry... entries) {
    return new StructExpr(id, messageName, entries);
  }

  public static StructExpr.Entry createStructEntry(long entryID, StructExpr.Key key, Expr value) {
    return new StructExpr.Entry(entryID, key, value);
  }

  public static StructExpr.MapKey createStructMapKey(Expr mapKey) {
    return new StructExpr.MapKey(mapKey);
  }

  public static StructExpr.FieldKey createStructFieldKey(String field) {
    return new StructExpr.FieldKey(field);
  }

  public static class StructExpr extends Expr {
    public final String messageName;
    public final StructExpr.Entry[] entries;

    public StructExpr(long id, String messageName, StructExpr.Entry[] entries) {
      super(id);
      this.messageName = messageName;
      this.entries = entries;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
      StructExpr that = (StructExpr) o;
      return Objects.equals(messageName, that.messageName) && Arrays.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(super.hashCode(), messageName);
      result = 31 * result + Arrays.hashCode(entries);
      return result;
    }

    public static class Entry {
      public final long id;
      public final StructExpr.Key key;
      public final Expr value;

      public Entry(long id, Key key, Expr value) {
        this.id = id;
        this.key = key;
        this.value = value;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        Entry entry = (Entry) o;
        return id == entry.id
            && Objects.equals(key, entry.key)
            && Objects.equals(value, entry.value);
      }

      @Override
      public int hashCode() {
        return Objects.hash(id, key, value);
      }
    }

    public abstract static class Key {}

    public static class MapKey extends Key {
      public final Expr mapKey;

      public MapKey(Expr mapKey) {
        this.mapKey = mapKey;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        MapKey mapKey1 = (MapKey) o;
        return Objects.equals(mapKey, mapKey1.mapKey);
      }

      @Override
      public int hashCode() {
        return Objects.hash(mapKey);
      }
    }

    public static class FieldKey extends Key {
      public final String field;

      public FieldKey(String field) {
        this.field = field;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        FieldKey fieldKey = (FieldKey) o;
        return Objects.equals(field, fieldKey.field);
      }

      @Override
      public int hashCode() {
        return Objects.hash(field);
      }
    }
  }

  public static ComprehensionExpr comprehensionExpr(
      long id,
      String accuVar,
      Expr accuInit,
      String iterVar,
      Expr iterRange,
      Expr loopCondition,
      Expr loopStep,
      Expr result) {
    return new ComprehensionExpr(
        id, accuVar, accuInit, iterVar, iterRange, loopCondition, loopStep, result);
  }

  public static class ComprehensionExpr extends Expr {
    public final String accuVar;
    public final Expr accuInit;
    public final String iterVar;
    public final Expr iterRange;
    public final Expr loopCondition;
    public final Expr loopStep;
    public final Expr result;

    public ComprehensionExpr(
        long id,
        String accuVar,
        Expr accuInit,
        String iterVar,
        Expr iterRange,
        Expr loopCondition,
        Expr loopStep,
        Expr result) {
      super(id);
      this.accuVar = accuVar;
      this.accuInit = accuInit;
      this.iterVar = iterVar;
      this.iterRange = iterRange;
      this.loopCondition = loopCondition;
      this.loopStep = loopStep;
      this.result = result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
      ComprehensionExpr that = (ComprehensionExpr) o;
      return Objects.equals(accuVar, that.accuVar)
          && Objects.equals(accuInit, that.accuInit)
          && Objects.equals(iterVar, that.iterVar)
          && Objects.equals(iterRange, that.iterRange)
          && Objects.equals(loopCondition, that.loopCondition)
          && Objects.equals(loopStep, that.loopStep)
          && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          super.hashCode(), accuVar, accuInit, iterVar, iterRange, loopCondition, loopStep, result);
    }
  }
}
