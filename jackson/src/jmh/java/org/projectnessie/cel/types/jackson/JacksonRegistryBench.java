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
package org.projectnessie.cel.types.jackson;

import static org.projectnessie.cel.common.types.StringT.stringOf;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(iterations = 1, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class JacksonRegistryBench {

  @State(Scope.Benchmark)
  public static class ReadState {
    JacksonRegistry registry;
    JacksonObjectT value;

    @Setup
    public void init() {
      registry = (JacksonRegistry) JacksonRegistry.newRegistry();
      registry.typeDescription(Policy.class);
      registry.enumDescription(Status.class);
      value =
          JacksonObjectT.newObject(
              registry,
              new Policy("policy-1", 7, new Principal("alice@example.com"), Status.ACTIVE),
              registry.typeDescription(Policy.class));
    }
  }

  /** Supplies a fresh registry to each single-shot cold-registration benchmark iteration. */
  @State(Scope.Thread)
  public static class RegistrationState {
    JacksonRegistry registry;

    @Setup(Level.Iteration)
    public void init() {
      registry = (JacksonRegistry) JacksonRegistry.newRegistry();
    }
  }

  @Benchmark
  public void registryCreation(Blackhole blackhole) {
    blackhole.consume(JacksonRegistry.newRegistry());
  }

  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  public void registerType(RegistrationState state, Blackhole blackhole) {
    blackhole.consume(state.registry.typeDescription(Policy.class));
  }

  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  public void registerEnum(RegistrationState state, Blackhole blackhole) {
    blackhole.consume(state.registry.enumDescription(Status.class));
  }

  @Benchmark
  public void propertyRead(ReadState state, Blackhole blackhole) {
    blackhole.consume(state.value.get(stringOf("owner")));
  }

  @Benchmark
  public void enumConversion(ReadState state, Blackhole blackhole) {
    blackhole.consume(state.registry.nativeToValue(Status.ACTIVE));
  }

  public enum Status {
    ACTIVE,
    DISABLED
  }

  public static final class Principal {
    private final String email;

    Principal(String email) {
      this.email = email;
    }

    public String getEmail() {
      return email;
    }
  }

  public static final class Policy {
    private final String name;
    private final int priority;
    private final Principal owner;
    private final Status status;

    Policy(String name, int priority, Principal owner, Status status) {
      this.name = name;
      this.priority = priority;
      this.owner = owner;
      this.status = status;
    }

    public String getName() {
      return name;
    }

    public int getPriority() {
      return priority;
    }

    public Principal getOwner() {
      return owner;
    }

    public Status getStatus() {
      return status;
    }
  }
}
