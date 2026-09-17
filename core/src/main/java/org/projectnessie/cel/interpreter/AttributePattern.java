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

import static org.projectnessie.cel.common.types.UnknownT.isUnknown;
import static org.projectnessie.cel.common.types.UnknownT.unknownOf;
import static org.projectnessie.cel.interpreter.AttributeFactory.newAttributeFactory;

import com.google.api.expr.v1alpha1.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.IntHashSet.IntIterator;
import org.projectnessie.cel.common.ULong;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributeFactory.Attribute;
import org.projectnessie.cel.interpreter.AttributeFactory.NamespacedAttribute;
import org.projectnessie.cel.interpreter.AttributeFactory.Qualifier;

/**
 * Mutable description of an attribute path that should evaluate to a CEL unknown value.
 *
 * <p>Pass completed patterns to {@link Activation#newPartialActivation(Object,
 * AttributePattern...)}. A pattern consists of a fully qualified top-level variable and zero or
 * more field, index, or wildcard qualifiers. Matching is overlap-based: if a pattern is a prefix of
 * an accessed attribute, or the accessed attribute is a prefix of a pattern, the last matched
 * expression component produces an unknown result.
 *
 * <p>When an expression uses a CEL container, the variable name must match the qualified name
 * produced during namespace resolution. For example, variable {@code c} in container {@code a.b} is
 * represented by pattern variable {@code a.b.c}.
 *
 * <p>The qualifier patterns for attribute matching must be one of the following:
 *
 * <ul>
 *   <li>valid map key type: string, int, uint, bool
 *   <li>wildcard (*)
 * </ul>
 *
 * <p>Example paths:
 *
 * <ol>
 *   <li>{@code ns.myvar["complex-value"]}
 *   <li>{@code ns.myvar["complex-value"][0]}
 *   <li>{@code ns.myvar["complex-value"].*.name}
 * </ol>
 *
 * <p>The first example is simple: match an attribute where the variable is 'ns.myvar' with a field
 * access on 'complex-value'. The second example expands the match to indicate that only a specific
 * index {@code 0} should match. The third example matches any indexed access that later selects the
 * {@code name} field.
 *
 * <p>Instances are mutable and not thread-safe. Complete qualifier construction before passing a
 * pattern to an activation or otherwise sharing it.
 */
public final class AttributePattern {
  private final String variable;
  private final List<AttributeQualifierPattern> qualifierPatterns;

  AttributePattern(String variable, List<AttributeQualifierPattern> qualifierPatterns) {
    this.variable = variable;
    this.qualifierPatterns = qualifierPatterns;
  }

  /**
   * Creates a mutable pattern for a fully qualified variable.
   *
   * @param variable non-null, fully qualified CEL variable name
   * @return a new pattern with no qualifier restrictions
   */
  public static AttributePattern newAttributePattern(String variable) {
    return new AttributePattern(variable, new ArrayList<>());
  }

  /**
   * Appends a string field or map-key qualifier.
   *
   * @param pattern non-null field name or string map key, including the empty string
   * @return this mutable pattern
   */
  public AttributePattern qualString(String pattern) {
    qualifierPatterns.add(AttributeQualifierPattern.forValue(pattern));
    return this;
  }

  /**
   * Appends a CEL int map key or list index qualifier.
   *
   * @param pattern signed CEL int key or list index
   * @return this mutable pattern
   */
  public AttributePattern qualInt(long pattern) {
    qualifierPatterns.add(AttributeQualifierPattern.forValue(pattern));
    return this;
  }

  /**
   * Appends a CEL uint map-key qualifier.
   *
   * <p>{@code pattern} contains the raw bits of the unsigned value.
   *
   * @param pattern unsigned key bits
   * @return this mutable pattern
   */
  public AttributePattern qualUint(long pattern) {
    qualifierPatterns.add(AttributeQualifierPattern.forValue(ULong.valueOf(pattern)));
    return this;
  }

  /**
   * Appends a CEL boolean map-key qualifier.
   *
   * @param pattern boolean key
   * @return this mutable pattern
   */
  public AttributePattern qualBool(boolean pattern) {
    qualifierPatterns.add(AttributeQualifierPattern.forValue(pattern));
    return this;
  }

  /**
   * Appends a wildcard that matches one field or index qualifier.
   *
   * @return this mutable pattern
   */
  public AttributePattern wildcard() {
    qualifierPatterns.add(AttributeQualifierPattern.wildcard());
    return this;
  }

  /**
   * Tests the fully qualified variable portion of this pattern.
   *
   * @param variable fully qualified candidate variable
   * @return whether the candidate equals this pattern's variable
   */
  public boolean variableMatches(String variable) {
    return this.variable.equals(variable);
  }

  /**
   * Returns the live qualifier list used by this pattern.
   *
   * <p>The list and its element type are interpreter implementation details. External callers
   * should build patterns through the qualifier methods instead of modifying the returned list. Any
   * direct structural modification affects this pattern and is not thread-safe.
   *
   * @return the live qualifier list
   */
  public List<AttributeQualifierPattern> qualifierPatterns() {
    return qualifierPatterns;
  }

  @Override
  public String toString() {
    return "AttributePattern{"
        + "variable='"
        + variable
        + '\''
        + ", qualifierPatterns="
        + qualifierPatterns.stream().map(Object::toString).collect(Collectors.joining(",\n    "))
        + '}';
  }

  /** Holds a wildcard or value-based qualifier pattern. */
  static final class AttributeQualifierPattern {
    private final boolean wildcard;
    private final Object value;

    private AttributeQualifierPattern(boolean wildcard, Object value) {
      this.wildcard = wildcard;
      this.value = value;
    }

    static AttributeQualifierPattern wildcard() {
      return new AttributeQualifierPattern(true, null);
    }

    static AttributeQualifierPattern forValue(Object value) {
      return new AttributeQualifierPattern(false, value);
    }

    /**
     * Matches returns true if the qualifier pattern is a wildcard, or the Qualifier implements the
     * qualifierValueEquator interface and its IsValueEqualTo returns true for the qualifier
     * pattern.
     */
    public boolean matches(Qualifier q) {
      if (wildcard) {
        return true;
      }
      if (q instanceof QualifierValueEquator qve) {
        return qve.qualifierValueEquals(value);
      }
      return false;
    }

    @Override
    public String toString() {
      return "AttributeQualifierPattern{" + "wildcard=" + wildcard + ", value=" + value + '}';
    }
  }

  /**
   * qualifierValueEquator defines an interface for determining if an input value, of valid map key
   * type, is equal to the value held in the Qualifier. This interface is used by the
   * AttributeQualifierPattern to determine pattern matches for non-wildcard qualifier patterns.
   *
   * <p>Note: Attribute values are also Qualifier values; however, attributes are resolved before
   * qualification happens. This is an implementation detail, but one relevant to why the Attribute
   * types do not surface in the list of implementations.
   *
   * <p>See: partialAttributeFactory.matchesUnknownPatterns for more details on how this interface
   * is used.
   */
  interface QualifierValueEquator {
    /**
     * QualifierValueEquals returns true if the input value is equal to the value held in the
     * Qualifier.
     */
    boolean qualifierValueEquals(Object value);
  }

  /**
   * Creates the low-level attribute factory used for partial evaluation.
   *
   * <p>The container, adapter, and provider must come from the same configured environment. The
   * returned factory recognizes {@link Activation.PartialActivation} inputs and produces unknown
   * values for matching patterns; ordinary activations retain normal attribute resolution.
   *
   * @param container namespace resolution configuration
   * @param adapter Java-to-CEL value adapter
   * @param provider type and field provider
   * @return an attribute factory that supports partial activations
   */
  public static AttributeFactory newPartialAttributeFactory(
      Container container, TypeAdapter adapter, TypeProvider provider) {
    AttributeFactory fac = newAttributeFactory(container, adapter, provider);
    return new PartialAttributeFactory(fac, container, adapter, provider);
  }

  static final class PartialAttributeFactory implements AttributeFactory {
    private final AttributeFactory fac;
    private final Container container;
    private final TypeAdapter adapter;
    private final TypeProvider provider;

    PartialAttributeFactory(
        AttributeFactory fac, Container container, TypeAdapter adapter, TypeProvider provider) {
      this.fac = fac;
      this.container = container;
      this.adapter = adapter;
      this.provider = provider;
    }

    @Override
    public Attribute conditionalAttribute(long id, Interpretable expr, Attribute t, Attribute f) {
      return fac.conditionalAttribute(id, expr, t, f);
    }

    @Override
    public Attribute relativeAttribute(long id, Interpretable operand) {
      return fac.relativeAttribute(id, operand);
    }

    @Override
    public Qualifier newQualifier(Type objType, long qualID, Object val) {
      return fac.newQualifier(objType, qualID, val);
    }

    /**
     * AbsoluteAttribute implementation of the AttributeFactory interface which wraps the
     * NamespacedAttribute resolution in an internal attributeMatcher object to dynamically match
     * unknown patterns from PartialActivation inputs if given.
     */
    @Override
    public NamespacedAttribute absoluteAttribute(long id, String... names) {
      NamespacedAttribute attr = fac.absoluteAttribute(id, names);
      return new AttributeMatcher(this, attr, new ArrayList<>());
    }

    /**
     * MaybeAttribute implementation of the AttributeFactory interface which ensures that the set of
     * 'maybe' NamespacedAttribute values are produced using the PartialAttributeFactory rather than
     * the base AttributeFactory implementation.
     */
    @Override
    public Attribute maybeAttribute(long id, String name) {
      List<NamespacedAttribute> attrs = new ArrayList<>();
      attrs.add(absoluteAttribute(id, container.resolveCandidateNames(name)));
      return new MaybeAttribute(id, attrs, adapter, provider, this);
    }

    /**
     * matchesUnknownPatterns returns true if the variable names and qualifiers for a given
     * Attribute value match any of the ActivationPattern objects in the set of unknown activation
     * patterns on the given PartialActivation.
     *
     * <p>For example, in expression {@code a.b}, the attribute is composed of variable {@code a}
     * and string qualifier {@code b}. A partial activation indicates that some or all input data is
     * unknown through its attribute patterns. A pattern for variable {@code a} with string
     * qualifier {@code c} does not match {@code a.b}; any of the following patterns do:
     *
     * <ul>
     *   <li>{@code newAttributePattern("a")}
     *   <li>{@code newAttributePattern("a").wildcard()}
     *   <li>{@code newAttributePattern("a").qualString("b")}
     *   <li>{@code newAttributePattern("a").qualString("b").qualInt(0)}
     * </ul>
     *
     * <p>Any AttributePattern which overlaps an Attribute or vice-versa will produce an Unknown
     * result for the last pattern matched variable or qualifier in the Attribute. In the first
     * matching example, the expression ID representing variable {@code a} is listed in the unknown
     * result, whereas in the other examples the qualifier {@code b} is returned as unknown.
     */
    Object matchesUnknownPatterns(
        PartialActivation vars, long attrID, String[] variableNames, List<Qualifier> qualifiers) {
      AttributePattern[] patterns = vars.unknownAttributePatterns();
      IntHashSet candidateIndices = new IntHashSet();
      for (String variable : variableNames) {
        for (int i = 0; i < patterns.length; i++) {
          AttributePattern pat = patterns[i];
          if (pat.variableMatches(variable)) {
            candidateIndices.add(i);
          }
        }
      }
      // Determine whether to return early if there are no candidate unknown patterns.
      if (candidateIndices.isEmpty()) {
        return null;
      }
      // Determine whether to return early if there are no qualifiers.
      if (qualifiers.isEmpty()) {
        return unknownOf(attrID);
      }
      // Resolve the attribute qualifiers into a static set. This prevents more dynamic
      // Attribute resolutions than necessary when there are multiple unknown patterns
      // that traverse the same Attribute-based qualifier field.
      Qualifier[] newQuals = new Qualifier[qualifiers.size()];
      for (int i = 0; i < qualifiers.size(); i++) {
        Qualifier qual = qualifiers.get(i);
        if (qual instanceof Attribute) {
          Object val = ((Attribute) qual).resolve(vars);
          if (isUnknown(val)) {
            return val;
          }
          // If this resolution behavior ever changes, new implementations of the
          // qualifierValueEquator may be required to handle proper resolution.
          qual = fac.newQualifier(null, qual.id(), val);
        }
        newQuals[i] = qual;
      }
      // Determine whether any of the unknown patterns match.
      for (IntIterator patIter = candidateIndices.iterator(); patIter.hasNext(); ) {
        int patIdx = patIter.nextValue();
        AttributePattern pat = patterns[patIdx];
        boolean isUnk = true;
        long matchExprID = attrID;
        List<AttributeQualifierPattern> qualPats = pat.qualifierPatterns();
        for (int i = 0; i < newQuals.length; i++) {
          Qualifier qual = newQuals[i];
          if (i >= qualPats.size()) {
            break;
          }
          matchExprID = qual.id();
          AttributeQualifierPattern qualPat = qualPats.get(i);
          // Note, the AttributeQualifierPattern relies on the input Qualifier not being an
          // Attribute, since there is no way to resolve the Attribute with the information
          // provided to the Matches call.
          if (!qualPat.matches(qual)) {
            isUnk = false;
            break;
          }
        }
        if (isUnk) {
          return unknownOf(matchExprID);
        }
      }
      return null;
    }
  }

  /**
   * attributeMatcher embeds the NamespacedAttribute interface which allows it to participate in
   * AttributePattern matching against Attribute values without having to modify the code paths that
   * identify Attributes in expressions.
   */
  static final class AttributeMatcher implements NamespacedAttribute {

    private final NamespacedAttribute attr;
    private final PartialAttributeFactory fac;
    private final List<Qualifier> qualifiers;

    AttributeMatcher(
        PartialAttributeFactory fac, NamespacedAttribute attr, List<Qualifier> qualifiers) {
      this.fac = fac;
      this.attr = attr;
      this.qualifiers = qualifiers;
    }

    @Override
    public long id() {
      return attr.id();
    }

    @Override
    public String[] candidateVariableNames() {
      return attr.candidateVariableNames();
    }

    @Override
    public List<Qualifier> qualifiers() {
      return attr.qualifiers();
    }

    /** AddQualifier implements the Attribute interface method. */
    @Override
    public Attribute addQualifier(Qualifier qual) {
      // Add the qualifier to the embedded NamespacedAttribute. If the input to the Resolve
      // method is not a PartialActivation, or does not match an unknown attribute pattern, the
      // Resolve method is directly invoked on the underlying NamespacedAttribute.
      attr.addQualifier(qual);
      // The attributeMatcher overloads TryResolve and will attempt to match unknown patterns
      // against
      // the variable name and qualifier set contained within the Attribute. These values are not
      // directly inspectable on the top-level NamespacedAttribute interface and so are tracked
      // within
      // the attributeMatcher.
      qualifiers.add(qual);
      return this;
    }

    /**
     * Resolve is an implementation of the Attribute interface method which uses the
     * attributeMatcher TryResolve implementation rather than the embedded NamespacedAttribute
     * Resolve implementation.
     */
    @Override
    public Object resolve(org.projectnessie.cel.interpreter.Activation vars) {
      return tryResolve(vars);
    }

    /**
     * TryResolve is an implementation of the NamespacedAttribute interface method which tests for
     * matching unknown attribute patterns and returns types.Unknown if present. Otherwise, the
     * standard Resolve logic applies.
     */
    @Override
    public Object tryResolve(org.projectnessie.cel.interpreter.Activation vars) {
      long id = attr.id();
      if (vars instanceof PartialActivation partial) {
        Object unk = fac.matchesUnknownPatterns(partial, id, candidateVariableNames(), qualifiers);
        if (unk != null) {
          return unk;
        }
      }
      return attr.tryResolve(vars);
    }

    /** Qualify is an implementation of the Qualifier interface method. */
    @Override
    public Object qualify(org.projectnessie.cel.interpreter.Activation vars, Object obj) {
      Object val = resolve(vars);
      if (isUnknown(val)) {
        return val;
      }
      Qualifier qual = fac.newQualifier(null, id(), val);
      return qual.qualify(vars, obj);
    }

    @Override
    public String toString() {
      return "AttributeMatcher{"
          + "attr="
          + attr
          + ", fac="
          + fac
          + ", qualifiers="
          + qualifiers.stream().map(Object::toString).collect(Collectors.joining(",\n    "))
          + '}';
    }
  }
}
