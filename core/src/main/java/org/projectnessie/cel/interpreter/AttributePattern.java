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
 * AttributePattern represents a top-level variable with an optional set of qualifier patterns.
 *
 * <p>When using a CEL expression within a container, e.g. a package or namespace, the variable name
 * in the pattern must match the qualified name produced during the variable namespace resolution.
 * For example, if variable `c` appears in an expression whose container is `a.b`, the variable name
 * supplied to the pattern must be `a.b.c`
 *
 * <p>The qualifier patterns for attribute matching must be one of the following:
 *
 * <ul>
 *   <li>valid map key type: string, int, uint, bool
 *   <li>wildcard (*)
 * </ul>
 *
 * <p>Examples:
 *
 * <ol>
 *   <li>ns.myvar["complex-value"]
 *   <li>ns.myvar["complex-value"][0]
 *   <li>ns.myvar["complex-value"].*.name
 * </ol>
 *
 * <p>The first example is simple: match an attribute where the variable is 'ns.myvar' with a field
 * access on 'complex-value'. The second example expands the match to indicate that only a specific
 * index `0` should match. And lastly, the third example matches any indexed access that later
 * selects the 'name' field.
 */
public final class AttributePattern {
  private final String variable;
  private final List<AttributeQualifierPattern> qualifierPatterns;

  AttributePattern(String variable, List<AttributeQualifierPattern> qualifierPatterns) {
    this.variable = variable;
    this.qualifierPatterns = qualifierPatterns;
  }

  /** NewAttributePattern produces a new mutable AttributePattern based on a variable name. */
  public static AttributePattern newAttributePattern(String variable) {
    return new AttributePattern(variable, new ArrayList<>());
  }

  /**
   * QualString adds a string qualifier pattern to the AttributePattern. The string may be a valid
   * identifier, or string map key including empty string.
   */
  public AttributePattern qualString(String pattern) {
    qualifierPatterns.add(AttributeQualifierPattern.forValue(pattern));
    return this;
  }

  /**
   * QualInt adds an int qualifier pattern to the AttributePattern. The index may be either a map or
   * list index.
   */
  public AttributePattern qualInt(long pattern) {
    qualifierPatterns.add(AttributeQualifierPattern.forValue(pattern));
    return this;
  }

  /** QualUint adds an uint qualifier pattern for a map index operation to the AttributePattern. */
  public AttributePattern qualUint(long pattern) {
    qualifierPatterns.add(AttributeQualifierPattern.forValue(ULong.valueOf(pattern)));
    return this;
  }

  /** QualBool adds a bool qualifier pattern for a map index operation to the AttributePattern. */
  public AttributePattern qualBool(boolean pattern) {
    qualifierPatterns.add(AttributeQualifierPattern.forValue(pattern));
    return this;
  }

  /** Wildcard adds a special sentinel qualifier pattern that will match any single qualifier. */
  public AttributePattern wildcard() {
    qualifierPatterns.add(AttributeQualifierPattern.wildcard());
    return this;
  }

  /**
   * VariableMatches returns true if the fully qualified variable matches the AttributePattern fully
   * qualified variable name.
   */
  public boolean variableMatches(String variable) {
    return this.variable.equals(variable);
  }

  /**
   * QualifierPatterns returns the set of AttributeQualifierPattern values on the AttributePattern.
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

  /** AttributeQualifierPattern holds a wilcard or valued qualifier pattern. */
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
      if (q instanceof QualifierValueEquator) {
        QualifierValueEquator qve = (QualifierValueEquator) q;
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
   * <p>Note: Attribute values are also Qualifier values; however, Attriutes are resolved before
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
   * NewPartialAttributeFactory returns an AttributeFactory implementation capable of performing
   * AttributePattern matches with PartialActivation inputs.
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
     * MaybeAttribute implementation of the AttributeFactory interface which ensure that the set of
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
     * <p>For example, in the expression `a.b`, the Attribute is composed of variable `a`, with
     * string qualifier `b`. When a PartialActivation is supplied, it indicates that some or all of
     * the data provided in the input is unknown by specifying unknown AttributePatterns. An
     * AttributePattern that refers to variable `a` with a string qualifier of `c` will not match
     * `a.b`; however, any of the following patterns will match Attribute `a.b`:
     *
     * <ul>
     *   <li>`AttributePattern("a")`
     *   <li>`AttributePattern("a").Wildcard()`
     *   <li>`AttributePattern("a").QualString("b")`
     *   <li>`AttributePattern("a").QualString("b").QualInt(0)`
     * </ul>
     *
     * <p>Any AttributePattern which overlaps an Attribute or vice-versa will produce an Unknown
     * result for the last pattern matched variable or qualifier in the Attribute. In the first
     * matching example, the expression id representing variable `a` would be listed in the Unknown
     * result, whereas in the other pattern examples, the qualifier `b` would be returned as the
     * Unknown.
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
      if (vars instanceof PartialActivation) {
        PartialActivation partial = (PartialActivation) vars;
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
