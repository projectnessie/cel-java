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

import org.projectnessie.cel.common.ErrorWithLocation;
import org.projectnessie.cel.pb.Expr;

/**
 * MacroExpander converts the target and args of a function call that matches a Macro.
 *
 * <p>Note: when the Macros.IsReceiverStyle() is true, the target argument will be nil.
 */
@FunctionalInterface
public interface MacroExpander {
  Expr func(ExprHelper eh, Expr target, Expr... args) throws ErrorWithLocation;
}
