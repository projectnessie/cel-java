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

plugins {
  `java-platform`
  `maven-publish`
  signing
  `cel-conventions`
}

dependencies {
  constraints {
    api(project(":cel-core"))
    api(project(":cel-generated-antlr", "shadow"))
    api(project(":cel-generated-pb"))
    api(project(":cel-conformance"))
    api(project(":cel-jackson"))
    api(project(":cel-tools"))
    api(project(":cel-standalone"))
  }
}

javaPlatform { allowDependencies() }
