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

import static java.util.Arrays.asList;
import static org.projectnessie.cel.EnvOption.EnvFeature.FeatureDisableDynamicAggregateLiterals;
import static org.projectnessie.cel.common.containers.Container.name;

import com.google.api.expr.v1alpha1.Decl;
import java.util.List;
import org.projectnessie.cel.common.containers.Container;
import org.projectnessie.cel.common.types.ref.TypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeProvider;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.parser.Macro;

/**
 * Configuration token applied while creating or extending an {@link Env}.
 *
 * <p>Built-in options must be applied while an {@link Env} is being created or extended and before
 * its first check. Applying a built-in option to an environment after its first check throws {@link
 * IllegalStateException}. Options are order-sensitive where documented.
 *
 * <p>The static factories are the supported way to configure built-in environment state. Custom
 * implementations must return the environment to use for subsequent options and must not return
 * {@code null}.
 */
@FunctionalInterface
public interface EnvOption {
  /**
   * Applies this configuration token.
   *
   * @param e environment being configured
   * @return environment to receive subsequent options
   */
  Env apply(Env e);

  // These constants beginning with "Feature" enable optional behavior in
  // the library.  See the documentation for each constant to see its
  // effects, compatibility restrictions, and standard conformance.

  enum EnvFeature {
    /**
     * Reject heterogeneous list and map literals during type checking.
     *
     * <p>This does not prevent heterogeneous aggregates supplied as variables, produced from
     * dynamic values, or used by unchecked expressions.
     */
    FeatureDisableDynamicAggregateLiterals
  }

  /**
   * Removes all macros configured before this option is applied.
   *
   * <p>This removes standard comprehension macro syntax such as {@code all} and {@code exists} when
   * used after the standard library option. It does not impose an evaluation resource bound.
   *
   * @return option that clears configured macros
   */
  static EnvOption clearMacros() {
    return e -> e.applyConfiguration(env -> env.macros.clear());
  }

  /**
   * Replaces the environment's type adapter.
   *
   * <p>The adapter is retained, not copied; complete mutable configuration before concurrent
   * environment use.
   *
   * @param adapter adapter for Java-to-CEL value conversion
   * @return option that installs {@code adapter}
   */
  static EnvOption customTypeAdapter(TypeAdapter adapter) {
    return e -> e.applyConfiguration(env -> env.adapter = adapter);
  }

  /**
   * Replaces the environment's type provider.
   *
   * <p>Apply this option before {@link #types(List)} when both are used. The provider is retained,
   * not copied; complete mutable configuration before concurrent environment use.
   *
   * @param provider provider for CEL type and field lookup
   * @return option that installs {@code provider}
   */
  static EnvOption customTypeProvider(TypeProvider provider) {
    return e -> e.applyConfiguration(env -> env.provider = provider);
  }

  /**
   * Appends variable and function declarations.
   *
   * <p>{@link Env#newEnv(EnvOption...)} installs standard declarations before applying caller
   * options. Use {@link Env#newCustomEnv(EnvOption...)} for a declaration set that does not
   * automatically include the standard library. The list is read when this option is applied.
   *
   * @param decls declarations to append
   * @return option that appends {@code decls}
   */
  static EnvOption declarations(List<Decl> decls) {
    // TODO: provide an alternative means of specifying declarations that doesn't refer
    //  to the underlying proto implementations.
    return e -> e.applyConfiguration(env -> env.declarations.addAll(decls));
  }

  /**
   * Appends variable and function declarations.
   *
   * @param decls declarations to append
   * @return option that appends {@code decls}
   * @see #declarations(List)
   */
  static EnvOption declarations(Decl... decls) {
    return declarations(asList(decls));
  }

  /**
   * Enables environment features.
   *
   * @param flags features to enable
   * @return option that enables {@code flags}
   */
  static EnvOption features(EnvFeature... flags) {
    return e -> {
      e.setFeatures(flags);
      return e;
    };
  }

  /**
   * Requires homogeneous list and map literal entries during type checking.
   *
   * <p>Heterogeneous aggregates can still enter through variables, dynamic values, or unchecked
   * expressions.
   *
   * @return option enabling {@link EnvFeature#FeatureDisableDynamicAggregateLiterals}
   */
  static EnvOption homogeneousAggregateLiterals() {
    return features(FeatureDisableDynamicAggregateLiterals);
  }

  /**
   * Appends parser macros.
   *
   * @param macros macros to append
   * @return option that appends {@code macros}
   * @see #macros(List)
   */
  static EnvOption macros(Macro... macros) {
    return macros(asList(macros));
  }

  /**
   * Appends parser macros.
   *
   * <p>Apply this option after {@link #clearMacros()} when replacing the configured macro set. The
   * list is read when this option is applied.
   *
   * @param macros macros to append
   * @return option that appends {@code macros}
   */
  static EnvOption macros(List<Macro> macros) {
    return e -> e.applyConfiguration(env -> env.macros.addAll(macros));
  }

  /**
   * Sets the CEL container used to resolve names.
   *
   * <p>The default container is empty. For example, container {@code google.type} permits {@code
   * Expr{expression: 'a < b'}} instead of {@code google.type.Expr{...}} when the type can be
   * resolved there.
   *
   * @param name CEL container name
   * @return option that extends the current container with {@code name}
   */
  static EnvOption container(String name) {
    return e -> e.applyConfiguration(env -> env.container = env.container.extend(name(name)));
  }

  /**
   * Configures simple-name abbreviations for fully qualified names.
   *
   * <p>An abbreviation (abbrev for short) is a simple name that expands to a fully-qualified name.
   * Abbreviations can be useful when working with variables, functions, and especially types from
   * multiple namespaces:
   *
   * <p>For example, the CEL expression {@code qual.pkg.version.ObjTypeName{field:
   * alt.container.ver.FieldTypeName{value: ...}}} contains two fully qualified type names.
   *
   * <p>Only one the qualified names above may be used as the CEL container, so at least one of
   * these references must be a long qualified name within an otherwise short CEL program. Using the
   * following abbreviations, the program becomes much simpler:
   *
   * <pre>{@code
   * EnvOption option =
   *     EnvOption.abbrevs(
   *         "qual.pkg.version.ObjTypeName",
   *         "alt.container.ver.FieldTypeName");
   * }</pre>
   *
   * <p>With that option, the CEL expression can use {@code ObjTypeName{field: FieldTypeName{value:
   * ...}}}.
   *
   * <p>There are a few rules for the qualified names and the simple abbreviations generated from
   * them:
   *
   * <ul>
   *   <li>Qualified names must be dot-delimited, for example {@code package.subpkg.name}.
   *   <li>The last element in the qualified name is the abbreviation.
   *   <li>Abbreviations must not collide with each other.
   *   <li>The abbreviation must not collide with unqualified names in use.
   * </ul>
   *
   * <p>Abbreviations are distinct from container-based references in the following important ways:
   *
   * <ul>
   *   <li>Abbreviations must expand to a fully-qualified name.
   *   <li>Expanded abbreviations do not participate in namespace resolution.
   *   <li>Abbreviation expansion is done instead of the container search for a matching identifier.
   *   <li>Containers follow C++ namespace resolution rules with searches from the most qualified
   *       name to the least qualified name.
   *   <li>Container references within the CEL program may be relative, and are resolved to fully
   *       qualified names at either type-check time or program plan time, whichever comes first.
   * </ul>
   *
   * <p>If there is ever a case where an identifier could be in both the container and as an
   * abbreviation, the abbreviation wins as this will ensure that the meaning of a program is
   * preserved between compilations even as the container evolves.
   *
   * @param qualifiedNames fully qualified names whose final components become abbreviations
   * @return option that adds the abbreviations
   */
  static EnvOption abbrevs(String... qualifiedNames) {
    return e ->
        e.applyConfiguration(
            env -> env.container = env.container.extend(Container.abbrevs(qualifiedNames)));
  }

  /**
   * Registers native type descriptions with the environment's type registry.
   *
   * <p>Supported descriptions depend on the configured {@link TypeRegistry}. The default Protobuf
   * registry accepts Protobuf message instances and CEL {@link
   * org.projectnessie.cel.common.types.ref.Type} values.
   *
   * <p>Well-known Protobuf types in {@code google.protobuf} are included in the default registry.
   * Apply {@link #customTypeProvider(TypeProvider)} before this option when using a custom
   * registry. The list is read and the registry is mutated when this option is applied.
   *
   * @param addTypes native type descriptions to register
   * @return option that registers {@code addTypes}
   * @throws RuntimeException when the configured provider is not a type registry or a type is not
   *     supported
   */
  static EnvOption types(List<Object> addTypes) {
    return e ->
        e.applyConfiguration(
            env -> {
              if (!(env.provider instanceof TypeRegistry reg)) {
                throw new RuntimeException(
                    String.format(
                        "custom types not supported by provider: %s",
                        env.provider.getClass().getName()));
              }
              for (Object t : addTypes) {
                reg.register(t);
              }
            });
  }

  /**
   * Registers native type descriptions with the environment's type registry.
   *
   * @param addTypes native type descriptions to register
   * @return option that registers {@code addTypes}
   * @see #types(List)
   */
  static EnvOption types(Object... addTypes) {
    return types(asList(addTypes));
  }

  //  /**
  //   * TypeDescs adds type declarations from any protoreflect.FileDescriptor, protoregistry.Files,
  //   * google.protobuf.FileDescriptorProto or google.protobuf.FileDescriptorSet provided.
  //   *
  //   * <p>Note that messages instantiated from these descriptors will be *dynamicpb.Message values
  //   * rather than the concrete message type.
  //   *
  //   * <p>TypeDescs are hermetic to a single Env object, but may be copied to other Env values via
  //   * extension or by re-using the same EnvOption with another NewEnv() call.
  //   */
  //  static EnvOption typeDescs(Object... descs) {
  //    return e -> {
  //      if (!(e.provider instanceof TypeRegistry)) {
  //        throw new RuntimeException(
  //            String.format(
  //                "custom types not supported by provider: %s", e.provider.getClass().getName()));
  //      }
  //      TypeRegistry reg = (TypeRegistry) e.provider;
  //      // Scan the input descriptors for FileDescriptorProto messages and accumulate them into a
  //      // synthetic FileDescriptorSet as the FileDescriptorProto messages may refer to each other
  //      // and will not resolve properly unless they are part of the same set.
  //      //		FileDescriptorSet fds = null;
  //      for (Object d : descs) {
  //        if (d instanceof FileDescriptorProto) {
  //          throw new RuntimeException(
  //              String.format("unsupported type descriptor: %s", d.getClass().getName()));
  //          //				if (fds == null) {
  //          //					fds = &descpb.FileDescriptorSet{
  //          //						File: []*descpb.FileDescriptorProto{},
  //          //					}
  //          //				}
  //          //				fds.File = append(fds.File, f)
  //        }
  //      }
  //      //		if (fds != null) {
  //      //			registerFileSet(reg, fds);
  //      //		}
  //      for (Object d : descs) {
  //        //			if (d instanceof protoregistry.Files) {
  //        //				if err := registerFiles(reg, f); err != nil {
  //        //					return nil, err
  //        //				}
  //        //			} else
  //        if (d instanceof FileDescriptor) {
  //          reg.registerDescriptor((FileDescriptor) d);
  //          //			} else if (d instanceof FileDescriptorSet) {
  //          //				registerFileSet(reg, (FileDescriptorSet) d);;
  //        } else if (d instanceof FileDescriptorProto) {
  //
  //        } else {
  //          throw new RuntimeException(
  //              String.format("unsupported type descriptor: %s", d.getClass().getName()));
  //        }
  //      }
  //      return e;
  //    };
  //  }

  //	static void registerFileSet(TypeRegistry ref, FileDescriptorSet fileSet) {
  //	files = protodesc.NewFiles(fileSet);
  //	return registerFiles(reg, files);
  // }

  // static void registerFiles(TypeRegistry ref, protoregistry.Files files) {
  //	var err error
  //	files.RangeFiles(func(fd protoreflect.FileDescriptor) bool {
  //		err = reg.RegisterDescriptor(fd)
  //		return err == nil
  //	})
  //	return err
  // }

}
