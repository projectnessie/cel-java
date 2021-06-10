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
package org.projectnessie.cel.common.types;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.DurationT.DurationType;
import static org.projectnessie.cel.common.types.DurationT.durationOf;
import static org.projectnessie.cel.common.types.IntT.IntNegOne;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.IntT.IntType;
import static org.projectnessie.cel.common.types.IntT.IntZero;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.TypeT.TypeType;
import static org.projectnessie.cel.common.types.UintT.UintType;

import com.google.protobuf.Any;
import com.google.protobuf.Value;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.common.types.ref.Val;

public class DurationTest {

  @Test
  void durationAdd() {
    Duration dur = ofSeconds(7506);
    DurationT d = durationOf(dur);

    assertThat(d.add(d).equal(durationOf(ofSeconds(15012)))).isSameAs(True);

    assertThat(durationOf(ofSeconds(Long.MAX_VALUE)).add(durationOf(ofSeconds(1L))))
        .matches(Err::isError);

    assertThat(durationOf(ofSeconds(Long.MIN_VALUE)).add(durationOf(ofSeconds(-1L))))
        .matches(Err::isError);

    assertThat(
            durationOf(ofSeconds(Long.MAX_VALUE - 1))
                .add(durationOf(Duration.ofSeconds(1L)))
                .equal(durationOf(ofSeconds(Long.MAX_VALUE))))
        .isSameAs(True);

    assertThat(
            durationOf(ofSeconds(Long.MIN_VALUE + 1))
                .add(durationOf(ofSeconds(-1L)))
                .equal(durationOf(ofSeconds(Long.MIN_VALUE))))
        .isSameAs(True);
  }

  @Test
  void durationCompare() {
    DurationT d = durationOf(ofSeconds(7506));
    DurationT lt = durationOf(ofSeconds(-10));
    assertThat(d.compare(lt)).isSameAs(IntOne);
    assertThat(lt.compare(d)).isSameAs(IntNegOne);
    assertThat(d.compare(d)).isSameAs(IntZero);
    assertThat(d.compare(False)).matches(Err::isError);
  }

  @Test
  void durationConvertToNative() {
    DurationT dur = durationOf(Duration.ofSeconds(7506, 1000));

    com.google.protobuf.Duration val = dur.convertToNative(com.google.protobuf.Duration.class);
    assertThat(val)
        .isEqualTo(
            com.google.protobuf.Duration.newBuilder().setSeconds(7506).setNanos(1000).build());

    assertThat(val)
        .extracting(
            com.google.protobuf.Duration::getSeconds, com.google.protobuf.Duration::getNanos)
        .containsExactly(((Duration) dur.value()).getSeconds(), ((Duration) dur.value()).getNano());

    Duration valD = dur.convertToNative(Duration.class);
    assertThat(valD).isEqualTo(dur.value());
  }

  @Test
  void durationConvertToNative_Any() {
    DurationT d = durationOf(Duration.ofSeconds(7506, 1000));
    Any val = d.convertToNative(Any.class);
    Any want =
        Any.pack(com.google.protobuf.Duration.newBuilder().setSeconds(7506).setNanos(1000).build());
    assertThat(val).isEqualTo(want);
  }

  @Test
  void durationConvertToNative_Error() {
    Value val = durationOf(Duration.ofSeconds(7506, 1000)).convertToNative(Value.class);
    Value want = Value.newBuilder().setStringValue("7506.000001s").build();
    assertThat(val).isEqualTo(want);
  }

  @Test
  void durationConvertToNative_Json() {
    Value val = durationOf(Duration.ofSeconds(7506, 1000)).convertToNative(Value.class);
    Value want = Value.newBuilder().setStringValue("7506.000001s").build();
    assertThat(val).isEqualTo(want);
  }

  @Test
  void durationConvertToType_Identity() {
    DurationT d = durationOf(ofSeconds(7506, 1000));

    assertThat(d.convertToType(IntType).value()).isEqualTo(7506000001000L);
    assertThat(d.convertToType(DurationType).equal(d)).isSameAs(True);
    assertThat(d.convertToType(TypeType)).isSameAs(DurationType);
    assertThat(d.convertToType(UintType)).matches(Err::isError);
    assertThat(d.convertToType(StringType).value().toString()).isEqualTo("7506.000001s");
  }

  @Test
  void durationNegate() {
    Val neg = durationOf(ofSeconds(1234, 1)).negate();
    Duration want = ofSeconds(-1234, -1);
    assertThat(neg.value()).isEqualTo(want);
    assertThat(durationOf(ofSeconds(Long.MIN_VALUE)).negate()).matches(Err::isError);
    assertThat(
            durationOf(ofSeconds(Long.MAX_VALUE))
                .negate()
                .equal(durationOf(ofSeconds(Long.MIN_VALUE + 1))))
        .isSameAs(True);
  }

  @Test
  void durationGetHours() {
    DurationT d = durationOf(ofSeconds(7506, 0));
    Val hr = d.receive(Overloads.TimeGetHours, Overloads.DurationToHours);
    assertThat(hr.equal(intOf(2))).isSameAs(True);
  }

  @Test
  void durationGetMinutes() {
    DurationT d = durationOf(ofSeconds(7506, 0));
    Val min = d.receive(Overloads.TimeGetMinutes, Overloads.DurationToMinutes);
    assertThat(min.equal(intOf(125))).isSameAs(True);
  }

  @Test
  void durationGetSeconds() {
    DurationT d = durationOf(ofSeconds(7506, 0));
    Val sec = d.receive(Overloads.TimeGetSeconds, Overloads.DurationToSeconds);
    assertThat(sec.equal(intOf(7506))).isSameAs(True);
  }

  @Test
  void durationGetMilliseconds() {
    DurationT d = durationOf(ofSeconds(7506, 0));
    Val min = d.receive(Overloads.TimeGetMilliseconds, Overloads.DurationToMilliseconds);
    assertThat(min.equal(intOf(7506000))).isSameAs(True);
  }

  @Test
  void durationSubtract() {
    DurationT d = durationOf(ofSeconds(7506, 0));

    assertThat(d.subtract(d).convertToType(IntType).equal(IntZero)).isSameAs(True);

    assertThat(durationOf(ofSeconds(Long.MAX_VALUE)).subtract(durationOf(ofSeconds(-1L))))
        .matches(Err::isError);

    assertThat(durationOf(ofSeconds(Long.MIN_VALUE)).subtract(durationOf(ofSeconds(1L))))
        .matches(Err::isError);

    assertThat(
            durationOf(ofSeconds(Long.MAX_VALUE - 1))
                .subtract(durationOf(ofSeconds(-1L)))
                .equal(durationOf(ofSeconds(Long.MAX_VALUE))))
        .isSameAs(True);

    assertThat(
            durationOf(ofSeconds(Long.MIN_VALUE + 1))
                .subtract(durationOf(ofSeconds(1L)))
                .equal(durationOf(ofSeconds(Long.MIN_VALUE))))
        .isSameAs(True);
  }
}
