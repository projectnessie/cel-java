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

import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.indexOutOfBoundsException;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.maybeNoSuchOverloadErr;
import static org.projectnessie.cel.common.types.Err.noSuchAttributeException;
import static org.projectnessie.cel.common.types.Err.noSuchKey;
import static org.projectnessie.cel.common.types.Err.noSuchKeyException;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.Err.throwErrorAsIllegalStateException;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.Types.boolOf;
import static org.projectnessie.cel.common.types.UintT.uintOf;
import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.interpreter.Coster.costOf;

import com.google.api.expr.v1alpha1.Type;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.NullT;
import org.projectnessie.cel.common.types.ref.FieldType;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Indexer;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.interpreter.AttributePattern.QualifierValueEquator;
import org.projectnessie.cel.shaded.org.antlr.v4.runtime.misc.Pair;

/** AttributeFactory provides methods creating Attribute and Qualifier values. */
public interface AttributeFactory {
  /**
   * AbsoluteAttribute creates an attribute that refers to a top-level variable name.
   *
   * <p>Checked expressions generate absolute attribute with a single name. Parse-only expressions
   * may have more than one possible absolute identifier when the expression is created within a
   * container, e.g. package or namespace.
   *
   * <p>When there is more than one name supplied to the AbsoluteAttribute call, the names must be
   * in CEL's namespace resolution order. The name arguments provided here are returned in the same
   * order as they were provided by the NamespacedAttribute CandidateVariableNames method.
   */
  NamespacedAttribute absoluteAttribute(long id, String... names);

  /**
   * ConditionalAttribute creates an attribute with two Attribute branches, where the Attribute that
   * is resolved depends on the boolean evaluation of the input 'expr'.
   */
  Attribute conditionalAttribute(long id, Interpretable expr, Attribute t, Attribute f);

  /**
   * MaybeAttribute creates an attribute that refers to either a field selection or a namespaced
   * variable name.
   *
   * <p>Only expressions which have not been type-checked may generate oneof attributes.
   */
  Attribute maybeAttribute(long id, String name);

  /**
   * RelativeAttribute creates an attribute whose value is a qualification of a dynamic computation
   * rather than a static variable reference.
   */
  Attribute relativeAttribute(long id, Interpretable operand);

  /**
   * NewQualifier creates a qualifier on the target object with a given value.
   *
   * <p>The 'val' may be an Attribute or any proto-supported map key type: bool, int, string, uint.
   *
   * <p>The qualifier may consider the object type being qualified, if present. If absent, the
   * qualification should be considered dynamic and the qualification should still work, though it
   * may be sub-optimal.
   */
  Qualifier newQualifier(Type objType, long qualID, Object val);

  /**
   * Qualifier marker interface for designating different qualifier values and where they appear
   * within field selections and index call expressions (`_[_]`).
   */
  interface Qualifier {
    /** ID where the qualifier appears within an expression. */
    long id();

    /**
     * Qualify performs a qualification, e.g. field selection, on the input object and returns the
     * value or error that results.
     */
    Object qualify(Activation vars, Object obj);
  }

  /**
   * ConstantQualifier interface embeds the Qualifier interface and provides an option to inspect
   * the qualifier's constant value.
   *
   * <p>Non-constant qualifiers are of Attribute type.
   */
  interface ConstantQualifier extends Qualifier {
    Val value();
  }

  interface ConstantQualifierEquator extends QualifierValueEquator, ConstantQualifier {}

  /**
   * Attribute values are a variable or value with an optional set of qualifiers, such as field,
   * key, or index accesses.
   */
  interface Attribute extends Qualifier {
    /**
     * AddQualifier adds a qualifier on the Attribute or error if the qualification is not a valid
     * qualifier type.
     */
    Attribute addQualifier(Qualifier q);

    /** Resolve returns the value of the Attribute given the current Activation. */
    Object resolve(Activation a);
  }

  /**
   * NamespacedAttribute values are a variable within a namespace, and an optional set of qualifiers
   * such as field, key, or index accesses.
   */
  interface NamespacedAttribute extends Attribute {
    /**
     * CandidateVariableNames returns the possible namespaced variable names for this Attribute in
     * the CEL namespace resolution order.
     */
    String[] candidateVariableNames();

    /** Qualifiers returns the list of qualifiers associated with the Attribute.s */
    List<Qualifier> qualifiers();

    /**
     * TryResolve attempts to return the value of the attribute given the current Activation. If an
     * error is encountered during attribute resolution, it will be returned immediately. If the
     * attribute cannot be resolved within the Activation, the result must be: `nil`, `false`,
     * `nil`.
     */
    Pair<Object, Boolean> tryResolve(Activation a);
  }

  /**
   * NewAttributeFactory returns a default AttributeFactory which is produces Attribute values
   * capable of resolving types by simple names and qualify the values using the supported qualifier
   * types: bool, int, string, and uint.
   */
  static AttributeFactory newAttributeFactory(Container cont, TypeAdapter a, TypeProvider p) {
    return new AttrFactory(cont, a, p);
  }

  final class AttrFactory implements AttributeFactory {
    private final Container container;
    private final TypeAdapter adapter;
    private final TypeProvider provider;

    AttrFactory(Container container, TypeAdapter adapter, TypeProvider provider) {
      this.container = container;
      this.adapter = adapter;
      this.provider = provider;
    }

    /**
     * AbsoluteAttribute refers to a variable value and an optional qualifier path.
     *
     * <p>The namespaceNames represent the names the variable could have based on namespace
     * resolution rules.
     */
    @Override
    public NamespacedAttribute absoluteAttribute(long id, String... names) {
      return new AbsoluteAttribute(id, names, new ArrayList<>(), adapter, provider, this);
    }

    /**
     * ConditionalAttribute supports the case where an attribute selection may occur on a
     * conditional expression, e.g. (cond ? a : b).c
     */
    @Override
    public AttributeFactory.Attribute conditionalAttribute(
        long id, Interpretable expr, AttributeFactory.Attribute t, AttributeFactory.Attribute f) {
      return new ConditionalAttribute(id, expr, t, f, adapter, this);
    }

    /**
     * MaybeAttribute collects variants of unchecked AbsoluteAttribute values which could either be
     * direct variable accesses or some combination of variable access with qualification.
     */
    @Override
    public Attribute maybeAttribute(long id, String name) {
      List<NamespacedAttribute> attrs = new ArrayList<>();
      attrs.add(absoluteAttribute(id, container.resolveCandidateNames(name)));
      return new MaybeAttribute(id, attrs, adapter, provider, this);
    }

    /** RelativeAttribute refers to an expression and an optional qualifier path. */
    @Override
    public Attribute relativeAttribute(long id, Interpretable operand) {
      return new RelativeAttribute(id, operand, new ArrayList<>(), adapter, this);
    }

    /** NewQualifier is an implementation of the AttributeFactory interface. */
    @Override
    public AttributeFactory.Qualifier newQualifier(Type objType, long qualID, Object val) {
      // Before creating a new qualifier check to see if this is a protobuf message field access.
      // If so, use the precomputed GetFrom qualification method rather than the standard
      // stringQualifier.
      if (val instanceof String) {
        String str = (String) val;
        if (objType != null && !objType.getMessageType().isEmpty()) {
          FieldType ft = provider.findFieldType(objType.getMessageType(), str);
          if (ft != null && ft.isSet != null && ft.getFrom != null) {
            return new FieldQualifier(qualID, str, ft, adapter);
          }
        }
      }
      return newQualifierStatic(adapter, qualID, val);
    }

    @Override
    public String toString() {
      return "AttrFactory{"
          + "container="
          + container
          + ", adapter="
          + adapter
          + ", provider="
          + provider
          + '}';
    }
  }

  final class AbsoluteAttribute implements Qualifier, NamespacedAttribute, Coster {
    final long id;
    /**
     * namespaceNames represent the names the variable could have based on declared container
     * (package) of the expression.
     */
    final String[] namespaceNames;

    final List<Qualifier> qualifiers;
    final TypeAdapter adapter;
    final TypeProvider provider;
    final AttributeFactory fac;

    AbsoluteAttribute(
        long id,
        String[] namespaceNames,
        List<Qualifier> qualifiers,
        TypeAdapter adapter,
        TypeProvider provider,
        AttributeFactory fac) {
      this.id = id;
      this.namespaceNames = Objects.requireNonNull(namespaceNames);
      this.qualifiers = Objects.requireNonNull(qualifiers);
      this.adapter = Objects.requireNonNull(adapter);
      this.provider = Objects.requireNonNull(provider);
      this.fac = Objects.requireNonNull(fac);
    }

    /** ID implements the Attribute interface method. */
    @Override
    public long id() {
      return id;
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      long min = 0L;
      long max = 0L;
      for (Qualifier q : qualifiers) {
        Cost qc = Cost.estimateCost(q);
        min += qc.min;
        max += qc.max;
      }
      min++; // For object retrieval.
      max++;
      return costOf(min, max);
    }

    /** AddQualifier implements the Attribute interface method. */
    @Override
    public Attribute addQualifier(AttributeFactory.Qualifier q) {
      qualifiers.add(q);
      return this;
    }

    /** CandidateVariableNames implements the NamespaceAttribute interface method. */
    @Override
    public String[] candidateVariableNames() {
      return namespaceNames;
    }

    /**
     * Qualifiers returns the list of Qualifier instances associated with the namespaced attribute.
     */
    @Override
    public List<Qualifier> qualifiers() {
      return qualifiers;
    }

    /** Qualify is an implementation of the Qualifier interface method. */
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      Object val = resolve(vars);
      if (isUnknown(val)) {
        return val;
      }
      Qualifier qual = fac.newQualifier(null, id, val);
      return qual.qualify(vars, obj);
    }

    /**
     * Resolve returns the resolved Attribute value given the Activation, or error if the Attribute
     * variable is not found, or if its Qualifiers cannot be applied successfully.
     */
    @Override
    public Object resolve(org.projectnessie.cel.interpreter.Activation vars) {
      Pair<Object, Boolean> obj = tryResolve(vars);
      if (!obj.b) {
        throw noSuchAttributeException(this);
      }
      return obj.a;
    }

    /**
     * TryResolve iterates through the namespaced variable names until one is found within the
     * Activation or TypeProvider.
     *
     * <p>If the variable name cannot be found as an Activation variable or in the TypeProvider as a
     * type, then the result is `nil`, `false`, `nil` per the interface requirement.
     */
    @Override
    public Pair<Object, Boolean> tryResolve(org.projectnessie.cel.interpreter.Activation vars) {
      for (String nm : namespaceNames) {
        // If the variable is found, process it. Otherwise, wait until the checks to
        // determine whether the type is unknown before returning.
        Pair<Object, Boolean> obj = vars.resolveName(nm);
        if (obj.b) {
          Object op = obj.a;
          for (Qualifier qual : qualifiers) {
            Object op2 = qual.qualify(vars, op);
            if (op2 instanceof Err) {
              return new Pair<>(op2, true);
            }
            if (op2 == null) {
              break;
            }
            op = op2;
          }
          return new Pair<>(op, true);
        }
        // Attempt to resolve the qualified type name if the name is not a variable identifier.
        Val typ = provider.findIdent(nm);
        if (typ != null) {
          if (qualifiers.isEmpty()) {
            return new Pair<>(typ, true);
          }
          throw noSuchAttributeException(this);
        }
      }
      return new Pair<>(null, false);
    }

    /** String implements the Stringer interface method. */
    @Override
    public String toString() {
      return "id: " + id + ", names: " + Arrays.toString(namespaceNames);
    }
  }

  final class ConditionalAttribute implements Qualifier, Attribute, Coster {
    final long id;
    final Interpretable expr;
    final Attribute truthy;
    final Attribute falsy;
    final TypeAdapter adapter;
    final AttributeFactory fac;

    ConditionalAttribute(
        long id,
        Interpretable expr,
        Attribute truthy,
        Attribute falsy,
        TypeAdapter adapter,
        AttributeFactory fac) {
      this.id = id;
      this.expr = expr;
      this.truthy = truthy;
      this.falsy = falsy;
      this.adapter = adapter;
      this.fac = fac;
    }

    /** ID is an implementation of the Attribute interface method. */
    @Override
    public long id() {
      return id;
    }

    /**
     * Cost provides the heuristic cost of a ternary operation {@code &lt;expr&gt; ? &lt;t&gt; :
     * &lt;f&gt;}. The cost is computed as {@code cost(expr)} plus the min/max costs of evaluating
     * either `t` or `f`.
     */
    @Override
    public Cost cost() {
      Cost t = Cost.estimateCost(truthy);
      Cost f = Cost.estimateCost(falsy);
      Cost e = Cost.estimateCost(expr);
      return costOf(e.min + Math.min(t.min, f.min), e.max + Math.max(t.max, f.max));
    }

    /**
     * AddQualifier appends the same qualifier to both sides of the conditional, in effect managing
     * the qualification of alternate attributes.
     */
    @Override
    public Attribute addQualifier(AttributeFactory.Qualifier qual) {
      truthy.addQualifier(qual); // just do
      falsy.addQualifier(qual); // just do
      return this;
    }

    /** Qualify is an implementation of the Qualifier interface method. */
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      Object val = resolve(vars);
      if (isUnknown(val)) {
        return val;
      }
      Qualifier qual = fac.newQualifier(null, id, val);
      return qual.qualify(vars, obj);
    }

    /**
     * Resolve evaluates the condition, and then resolves the truthy or falsy branch accordingly.
     */
    @Override
    public Object resolve(org.projectnessie.cel.interpreter.Activation vars) {
      Val val = expr.eval(vars);
      if (val == null) {
        throw noSuchAttributeException(this);
      }
      if (isError(val)) {
        return null;
      }
      if (val == True) {
        return truthy.resolve(vars);
      }
      if (val == False) {
        return falsy.resolve(vars);
      }
      if (isUnknown(val)) {
        return val;
      }
      return maybeNoSuchOverloadErr(val);
    }

    /** String is an implementation of the Stringer interface method. */
    @Override
    public String toString() {
      return String.format("id: %d, truthy attribute: %s, falsy attribute: %s", id, truthy, falsy);
    }
  }

  final class MaybeAttribute implements Coster, Attribute, Qualifier {
    final long id;
    final List<NamespacedAttribute> attrs;
    final TypeAdapter adapter;
    final TypeProvider provider;
    final AttributeFactory fac;

    MaybeAttribute(
        long id,
        List<NamespacedAttribute> attrs,
        TypeAdapter adapter,
        TypeProvider provider,
        AttributeFactory fac) {
      this.id = id;
      this.attrs = attrs;
      this.adapter = adapter;
      this.provider = provider;
      this.fac = fac;
    }

    /** ID is an implementation of the Attribute interface method. */
    @Override
    public long id() {
      return id;
    }

    /**
     * Cost implements the Coster interface method. The min cost is computed as the minimal cost
     * among all the possible attributes, the max cost ditto.
     */
    @Override
    public Cost cost() {
      long min = Long.MAX_VALUE;
      long max = 0L;
      for (NamespacedAttribute a : attrs) {
        Cost ac = Cost.estimateCost(a);
        min = Long.min(min, ac.min);
        max = Long.max(max, ac.max);
      }
      return costOf(min, max);
    }

    /**
     * AddQualifier adds a qualifier to each possible attribute variant, and also creates a new
     * namespaced variable from the qualified value.
     *
     * <p>The algorithm for building the maybe attribute is as follows:
     *
     * <ol>
     *   <li>Create a maybe attribute from a simple identifier when it occurs in a parsed-only
     *       expression <br>
     *       <br>
     *       {@code mb = MaybeAttribute(&lt;id&gt;, "a")} <br>
     *       <br>
     *       Initializing the maybe attribute creates an absolute attribute internally which
     *       includes the possible namespaced names of the attribute. In this example, let's assume
     *       we are in namespace 'ns', then the maybe is either one of the following variable names:
     *       <br>
     *       <br>
     *       possible variables names -- ns.a, a
     *   <li>Adding a qualifier to the maybe means that the variable name could be a longer
     *       qualified name, or a field selection on one of the possible variable names produced
     *       earlier: <br>
     *       <br>
     *       {@code mb.AddQualifier("b")} <br>
     *       <br>
     *       possible variables names -- ns.a.b, a.b<br>
     *       possible field selection -- ns.a['b'], a['b']
     * </ol>
     *
     * If none of the attributes within the maybe resolves a value, the result is an error.
     */
    @Override
    public Attribute addQualifier(AttributeFactory.Qualifier qual) {
      String str = "";
      boolean isStr = false;
      if (qual instanceof ConstantQualifier) {
        ConstantQualifier cq = (ConstantQualifier) qual;
        Object cqv = cq.value().value();
        if (cqv instanceof String) {
          str = (String) cqv;
          isStr = true;
        }
      }
      String[] augmentedNames = new String[0];
      // First add the qualifier to all existing attributes in the oneof.
      for (NamespacedAttribute attr : attrs) {
        if (isStr && attr.qualifiers().isEmpty()) {
          String[] candidateVars = attr.candidateVariableNames();
          augmentedNames = new String[candidateVars.length];
          for (int i = 0; i < candidateVars.length; i++) {
            String name = candidateVars[i];
            augmentedNames[i] = String.format("%s.%s", name, str);
          }
        }
        attr.addQualifier(qual);
      }
      // Next, ensure the most specific variable / type reference is searched first.
      if (attrs.isEmpty()) {
        attrs.add(fac.absoluteAttribute(qual.id(), augmentedNames));
      } else {
        attrs.add(0, fac.absoluteAttribute(qual.id(), augmentedNames));
      }
      return this;
    }

    /** Qualify is an implementation of the Qualifier interface method. */
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      Object val = resolve(vars);
      if (isUnknown(val)) {
        return val;
      }
      Qualifier qual = fac.newQualifier(null, id, val);
      return qual.qualify(vars, obj);
    }

    /**
     * Resolve follows the variable resolution rules to determine whether the attribute is a
     * variable or a field selection.
     */
    @Override
    public Object resolve(org.projectnessie.cel.interpreter.Activation vars) {
      for (NamespacedAttribute attr : attrs) {
        Pair<Object, Boolean> obj = attr.tryResolve(vars);
        // If the object was found, return it.
        if (obj.b) {
          return obj.a;
        }
      }
      // Else, produce a no such attribute error.
      throw noSuchAttributeException(this);
    }

    /** String is an implementation of the Stringer interface method. */
    @Override
    public String toString() {
      return String.format("id: %s, attributes: %s", id, attrs);
    }
  }

  final class RelativeAttribute implements Coster, Qualifier, Attribute {
    final long id;
    final Interpretable operand;
    final List<Qualifier> qualifiers;
    final TypeAdapter adapter;
    final AttributeFactory fac;

    RelativeAttribute(
        long id,
        Interpretable operand,
        List<Qualifier> qualifiers,
        TypeAdapter adapter,
        AttributeFactory fac) {
      this.id = id;
      this.operand = operand;
      this.qualifiers = qualifiers;
      this.adapter = adapter;
      this.fac = fac;
    }

    /** ID is an implementation of the Attribute interface method. */
    @Override
    public long id() {
      return id;
    }

    /** Cost implements the Coster interface method. */
    @Override
    public Cost cost() {
      Cost c = Cost.estimateCost(operand);
      long min = c.min;
      long max = c.max;
      for (Qualifier qual : qualifiers) {
        Cost q = Cost.estimateCost(qual);
        min += q.min;
        max += q.max;
      }
      return costOf(min, max);
    }

    /** AddQualifier implements the Attribute interface method. */
    @Override
    public Attribute addQualifier(AttributeFactory.Qualifier qual) {
      qualifiers.add(qual);
      return this;
    }

    /** Qualify is an implementation of the Qualifier interface method. */
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      Object val = resolve(vars);
      if (isUnknown(val)) {
        return val;
      }
      Qualifier qual = fac.newQualifier(null, id, val);
      return qual.qualify(vars, obj);
    }

    /** Resolve expression value and qualifier relative to the expression result. */
    @Override
    public Object resolve(org.projectnessie.cel.interpreter.Activation vars) {
      // First, evaluate the operand.
      Val v = operand.eval(vars);
      if (isError(v)) {
        return null;
      }
      if (isUnknown(v)) {
        return v;
      }
      // Next, qualify it. Qualification handles unkonwns as well, so there's no need to recheck.
      Object obj = v;
      for (Qualifier qual : qualifiers) {
        if (obj == null) {
          throw noSuchAttributeException(this);
        }
        obj = qual.qualify(vars, obj);
        if (obj instanceof Err) {
          return obj;
        }
      }
      if (obj == null) {
        throw noSuchAttributeException(this);
      }
      return obj;
    }

    /** String is an implementation of the Stringer interface method. */
    @Override
    public String toString() {
      return String.format("id: %d, operand: %s", id, operand);
    }
  }

  static Qualifier newQualifierStatic(TypeAdapter adapter, long id, Object v) {
    if (v instanceof Attribute) {
      return new AttrQualifier(id, (Attribute) v);
    }

    Class<?> c = v.getClass();

    if (v instanceof Val) {
      Val val = (Val) v;
      switch (val.type().typeEnum()) {
        case String:
          return new StringQualifier(id, (String) val.value(), val, adapter);
        case Int:
          return new IntQualifier(id, val.intValue(), val, adapter);
        case Uint:
          return new UintQualifier(id, val.intValue(), val, adapter);
        case Bool:
          return new BoolQualifier(id, val.booleanValue(), val, adapter);
      }
    }

    if (c == String.class) {
      return new StringQualifier(id, (String) v, stringOf((String) v), adapter);
    }
    if (c == ULong.class) {
      long l = ((ULong) v).longValue();
      return new UintQualifier(id, l, uintOf(l), adapter);
    }
    if ((c == Byte.class) || (c == Short.class) || (c == Integer.class) || (c == Long.class)) {
      long i = ((Number) v).longValue();
      return new IntQualifier(id, i, intOf(i), adapter);
    }
    if (c == Boolean.class) {
      boolean b = (Boolean) v;
      return new BoolQualifier(id, b, boolOf(b), adapter);
    }

    throw new IllegalStateException(
        String.format("invalid qualifier type: %s", v.getClass().getName()));
  }

  final class AttrQualifier implements Coster, Attribute {
    final long id;
    final Attribute attribute;

    AttrQualifier(long id, Attribute attribute) {
      this.id = id;
      this.attribute = attribute;
    }

    @Override
    public long id() {
      return id;
    }

    /** Cost returns zero for constant field qualifiers */
    @Override
    public Cost cost() {
      return Cost.estimateCost(attribute);
    }

    @Override
    public Attribute addQualifier(Qualifier q) {
      return attribute.addQualifier(q);
    }

    @Override
    public Object resolve(Activation a) {
      return attribute.resolve(a);
    }

    @Override
    public Object qualify(Activation vars, Object obj) {
      return attribute.qualify(vars, obj);
    }

    @Override
    public String toString() {
      return "AttrQualifier{" + "id=" + id + ", attribute=" + attribute + '}';
    }
  }

  final class StringQualifier implements Coster, ConstantQualifierEquator, QualifierValueEquator {
    final long id;
    final String value;
    final Val celValue;
    final TypeAdapter adapter;

    StringQualifier(long id, String value, Val celValue, TypeAdapter adapter) {
      this.id = id;
      this.value = value;
      this.celValue = celValue;
      this.adapter = adapter;
    }

    /** ID is an implementation of the Qualifier interface method. */
    @Override
    public long id() {
      return id;
    }

    /** Qualify implements the Qualifier interface method. */
    @SuppressWarnings("rawtypes")
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      String s = value;
      if (obj instanceof Map) {
        Map m = (Map) obj;
        obj = m.get(s);
        if (obj == null) {
          if (m.containsKey(s)) {
            return NullT.NullValue;
          }
          throw noSuchKeyException(s);
        }
      } else if (isUnknown(obj)) {
        return obj;
      } else {
        return refResolve(adapter, celValue, obj);
      }
      return obj;
    }

    /** Value implements the ConstantQualifier interface */
    @Override
    public Val value() {
      return celValue;
    }

    /** Cost returns zero for constant field qualifiers */
    @Override
    public Cost cost() {
      return Cost.None;
    }

    @Override
    public boolean qualifierValueEquals(Object value) {
      if (value instanceof String) {
        return this.value.equals(value);
      }
      return false;
    }

    @Override
    public String toString() {
      return "StringQualifier{"
          + "id="
          + id
          + ", value='"
          + value
          + '\''
          + ", celValue="
          + celValue
          + ", adapter="
          + adapter
          + '}';
    }
  }

  final class IntQualifier implements Coster, ConstantQualifierEquator {
    final long id;
    final long value;
    final Val celValue;
    final TypeAdapter adapter;

    IntQualifier(long id, long value, Val celValue, TypeAdapter adapter) {
      this.id = id;
      this.value = value;
      this.celValue = celValue;
      this.adapter = adapter;
    }

    /** ID is an implementation of the Qualifier interface method. */
    @Override
    public long id() {
      return id;
    }

    /** Qualify implements the Qualifier interface method. */
    @SuppressWarnings("rawtypes")
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      long i = value;
      if (obj instanceof Map) {
        Map m = (Map) obj;
        obj = m.get(i);
        if (obj == null) {
          obj = m.get((int) i);
        }
        if (obj == null) {
          if (m.containsKey(i) || m.containsKey((int) i)) {
            return null;
          }
          throw noSuchKeyException(i);
        }
        return obj;
      }
      if (obj.getClass().isArray()) {
        int l = Array.getLength(obj);
        if (i < 0 || i >= l) {
          throw indexOutOfBoundsException(i);
        }
        obj = Array.get(obj, (int) i);
        return obj;
      }
      if (obj instanceof List) {
        List list = (List) obj;
        int l = list.size();
        if (i < 0 || i >= l) {
          throw indexOutOfBoundsException(i);
        }
        obj = list.get((int) i);
        return obj;
      }
      if (isUnknown(obj)) {
        return obj;
      }
      return refResolve(adapter, celValue, obj);
    }

    /** Value implements the ConstantQualifier interface */
    @Override
    public Val value() {
      return celValue;
    }

    /** Cost returns zero for constant field qualifiers */
    @Override
    public Cost cost() {
      return Cost.None;
    }

    @Override
    public boolean qualifierValueEquals(Object value) {
      if (value instanceof ULong) {
        return false;
      }
      if (value instanceof Number) {
        return this.value == ((Number) value).longValue();
      }
      return false;
    }

    @Override
    public String toString() {
      return "IntQualifier{"
          + "id="
          + id
          + ", value="
          + value
          + ", celValue="
          + celValue
          + ", adapter="
          + adapter
          + '}';
    }
  }

  final class UintQualifier implements Coster, ConstantQualifierEquator {
    final long id;
    final long value;
    final Val celValue;
    final TypeAdapter adapter;

    UintQualifier(long id, long value, Val celValue, TypeAdapter adapter) {
      this.id = id;
      this.value = value;
      this.celValue = celValue;
      this.adapter = adapter;
    }

    /** ID is an implementation of the Qualifier interface method. */
    @Override
    public long id() {
      return id;
    }

    /** Qualify implements the Qualifier interface method. */
    @SuppressWarnings("rawtypes")
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      long i = value;
      if (obj instanceof Map) {
        Map m = (Map) obj;
        obj = m.get(ULong.valueOf(i));
        if (obj == null) {
          throw noSuchKeyException(i);
        }
        return obj;
      }
      if (obj.getClass().isArray()) {
        int l = Array.getLength(obj);
        if (i < 0 && i >= l) {
          throw indexOutOfBoundsException(i);
        }
        obj = Array.get(obj, (int) i);
        return obj;
      }
      if (isUnknown(obj)) {
        return obj;
      }
      return refResolve(adapter, celValue, obj);
    }

    /** Value implements the ConstantQualifier interface */
    @Override
    public Val value() {
      return celValue;
    }

    /** Cost returns zero for constant field qualifiers */
    @Override
    public Cost cost() {
      return Cost.None;
    }

    @Override
    public boolean qualifierValueEquals(Object value) {
      if (value instanceof ULong) {
        return this.value == ((ULong) value).longValue();
      }
      return false;
    }

    @Override
    public String toString() {
      return "UintQualifier{"
          + "id="
          + id
          + ", value="
          + value
          + ", celValue="
          + celValue
          + ", adapter="
          + adapter
          + '}';
    }
  }

  final class BoolQualifier implements Coster, ConstantQualifierEquator {
    final long id;
    final boolean value;
    final Val celValue;
    final TypeAdapter adapter;

    BoolQualifier(long id, boolean value, Val celValue, TypeAdapter adapter) {
      this.id = id;
      this.value = value;
      this.celValue = celValue;
      this.adapter = adapter;
    }

    /** ID is an implementation of the Qualifier interface method. */
    @Override
    public long id() {
      return id;
    }

    /** Qualify implements the Qualifier interface method. */
    @SuppressWarnings("rawtypes")
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      boolean b = value;
      if (obj instanceof Map) {
        Map m = (Map) obj;
        obj = m.get(b);
        if (obj == null) {
          if (m.containsKey(b)) {
            return null;
          }
          throw noSuchKeyException(b);
        }
      } else if (isUnknown(obj)) {
        return obj;
      } else {
        return refResolve(adapter, celValue, obj);
      }
      return obj;
    }

    /** Value implements the ConstantQualifier interface */
    @Override
    public Val value() {
      return celValue;
    }

    /** Cost returns zero for constant field qualifiers */
    @Override
    public Cost cost() {
      return Cost.None;
    }

    @Override
    public boolean qualifierValueEquals(Object value) {
      if (value instanceof Boolean) {
        return this.value == (Boolean) value;
      }
      return false;
    }

    @Override
    public String toString() {
      return "BoolQualifier{"
          + "id="
          + id
          + ", value="
          + value
          + ", celValue="
          + celValue
          + ", adapter="
          + adapter
          + '}';
    }
  }

  /**
   * fieldQualifier indicates that the qualification is a well-defined field with a known field
   * type. When the field type is known this can be used to improve the speed and efficiency of
   * field resolution.
   */
  final class FieldQualifier implements Coster, ConstantQualifierEquator {
    final long id;
    final String name;
    final FieldType fieldType;
    final TypeAdapter adapter;

    FieldQualifier(long id, String name, FieldType fieldType, TypeAdapter adapter) {
      this.id = id;
      this.name = name;
      this.fieldType = fieldType;
      this.adapter = adapter;
    }

    /** ID is an implementation of the Qualifier interface method. */
    @Override
    public long id() {
      return id;
    }

    /** Qualify implements the Qualifier interface method. */
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      if (obj instanceof Val) {
        obj = ((Val) obj).value();
      }
      return fieldType.getFrom.getFrom(obj);
    }

    /** Value implements the ConstantQualifier interface */
    @Override
    public Val value() {
      return stringOf(name);
    }

    /** Cost returns zero for constant field qualifiers */
    @Override
    public Cost cost() {
      return Cost.None;
    }

    @Override
    public boolean qualifierValueEquals(Object value) {
      if (value instanceof String) {
        return this.name.equals(value);
      }
      return false;
    }

    @Override
    public String toString() {
      return "FieldQualifier{"
          + "id="
          + id
          + ", name='"
          + name
          + '\''
          + ", fieldType="
          + fieldType
          + ", adapter="
          + adapter
          + '}';
    }
  }

  /**
   * refResolve attempts to convert the value to a CEL value and then uses reflection methods to try
   * and resolve the qualifier.
   */
  static Val refResolve(TypeAdapter adapter, Val idx, Object obj) {
    Val celVal = adapter.nativeToValue(obj);
    if (celVal instanceof Mapper) {
      Mapper mapper = (Mapper) celVal;
      Val elem = mapper.find(idx);
      if (elem == null) {
        return noSuchKey(idx);
      }
      return elem;
    }
    if (celVal instanceof Indexer) {
      Indexer indexer = (Indexer) celVal;
      return indexer.get(idx);
    }
    if (isUnknown(celVal)) {
      return celVal;
    }
    // TODO: If the types.Err value contains more than just an error message at some point in the
    //  future, then it would be reasonable to return error values as ref.Val types rather than
    //  simple go error types.
    throwErrorAsIllegalStateException(celVal);
    return noSuchOverload(celVal, "ref-resolve", null);
  }
}
