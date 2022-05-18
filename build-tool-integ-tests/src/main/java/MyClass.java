/*
 * Copyright (C) 2022 The Authors of CEL-Java
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

import com.google.api.expr.v1alpha1.ConformanceServiceGrpc;
import com.google.api.expr.v1alpha1.Decl;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.ScriptHost;
import org.projectnessie.cel.types.jackson.JacksonRegistry;

public class MyClass {}