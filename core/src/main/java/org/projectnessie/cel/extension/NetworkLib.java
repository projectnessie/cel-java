/*
 * Copyright (C) 2026 The Authors of CEL-Java
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
package org.projectnessie.cel.extension;

import static java.util.Collections.singletonList;
import static org.projectnessie.cel.common.types.BoolT.False;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.newTypeConversionError;
import static org.projectnessie.cel.common.types.Err.noSuchOverload;
import static org.projectnessie.cel.common.types.IntT.intOf;
import static org.projectnessie.cel.common.types.StringT.StringType;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.TypeT.newObjectTypeValue;
import static org.projectnessie.cel.common.types.Types.boolOf;

import com.google.api.expr.v1alpha1.Type;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.projectnessie.cel.EnvOption;
import org.projectnessie.cel.Library;
import org.projectnessie.cel.ProgramOption;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.types.ref.BaseVal;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Receiver;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.functions.Overload;

/** CEL standard network extension library for IP addresses and CIDR prefixes. */
public final class NetworkLib implements Library {
  private static final String IP = "ip";
  private static final String CIDR = "cidr";
  private static final String IS_IP = "isIP";
  private static final String IP_IS_CANONICAL = "ip.isCanonical";
  private static final String NET_IP = "net.IP";
  private static final String NET_CIDR = "net.CIDR";

  private static final Type IP_TYPE = Decls.newObjectType(NET_IP);
  private static final Type CIDR_TYPE = Decls.newObjectType(NET_CIDR);

  private static final org.projectnessie.cel.common.types.ref.Type IP_TYPE_VALUE =
      newObjectTypeValue(NET_IP, Trait.ReceiverType);
  private static final org.projectnessie.cel.common.types.ref.Type CIDR_TYPE_VALUE =
      newObjectTypeValue(NET_CIDR, Trait.ReceiverType);

  private NetworkLib() {}

  /**
   * Returns an environment option installing the network types, declarations, and implementations.
   */
  public static EnvOption network() {
    return Library.Lib(new NetworkLib());
  }

  @Override
  public List<EnvOption> getCompileOptions() {
    return List.of(
        EnvOption.types(IP_TYPE_VALUE, CIDR_TYPE_VALUE),
        EnvOption.declarations(
            Decls.newVar(NET_IP, Decls.newTypeType(IP_TYPE)),
            Decls.newVar(NET_CIDR, Decls.newTypeType(CIDR_TYPE)),
            Decls.newFunction(
                IP, Decls.newOverload("ip_string", singletonList(Decls.String), IP_TYPE)),
            Decls.newFunction(
                CIDR, Decls.newOverload("cidr_string", singletonList(Decls.String), CIDR_TYPE)),
            Decls.newFunction(
                IS_IP, Decls.newOverload("is_ip_string", singletonList(Decls.String), Decls.Bool)),
            Decls.newFunction(
                IP_IS_CANONICAL,
                Decls.newOverload(
                    "ip_is_canonical_string", singletonList(Decls.String), Decls.Bool)),
            Decls.newFunction(
                "string",
                Decls.newOverload("string_ip", singletonList(IP_TYPE), Decls.String),
                Decls.newOverload("string_cidr", singletonList(CIDR_TYPE), Decls.String)),
            Decls.newFunction(
                "family",
                Decls.newInstanceOverload("ip_family", singletonList(IP_TYPE), Decls.Int)),
            Decls.newFunction(
                "isUnspecified",
                Decls.newInstanceOverload("ip_is_unspecified", singletonList(IP_TYPE), Decls.Bool)),
            Decls.newFunction(
                "isLoopback",
                Decls.newInstanceOverload("ip_is_loopback", singletonList(IP_TYPE), Decls.Bool)),
            Decls.newFunction(
                "isGlobalUnicast",
                Decls.newInstanceOverload(
                    "ip_is_global_unicast", singletonList(IP_TYPE), Decls.Bool)),
            Decls.newFunction(
                "isLinkLocalMulticast",
                Decls.newInstanceOverload(
                    "ip_is_link_local_multicast", singletonList(IP_TYPE), Decls.Bool)),
            Decls.newFunction(
                "isLinkLocalUnicast",
                Decls.newInstanceOverload(
                    "ip_is_link_local_unicast", singletonList(IP_TYPE), Decls.Bool)),
            Decls.newFunction(
                "containsIP",
                Decls.newInstanceOverload(
                    "cidr_contains_ip", List.of(CIDR_TYPE, IP_TYPE), Decls.Bool),
                Decls.newInstanceOverload(
                    "cidr_contains_ip_string", List.of(CIDR_TYPE, Decls.String), Decls.Bool)),
            Decls.newFunction(
                "containsCIDR",
                Decls.newInstanceOverload(
                    "cidr_contains_cidr", List.of(CIDR_TYPE, CIDR_TYPE), Decls.Bool),
                Decls.newInstanceOverload(
                    "cidr_contains_cidr_string", List.of(CIDR_TYPE, Decls.String), Decls.Bool)),
            Decls.newFunction(
                "ip", Decls.newInstanceOverload("cidr_ip", singletonList(CIDR_TYPE), IP_TYPE)),
            Decls.newFunction(
                "masked",
                Decls.newInstanceOverload("cidr_masked", singletonList(CIDR_TYPE), CIDR_TYPE)),
            Decls.newFunction(
                "prefixLength",
                Decls.newInstanceOverload(
                    "cidr_prefix_length", singletonList(CIDR_TYPE), Decls.Int))));
  }

  @Override
  public List<ProgramOption> getProgramOptions() {
    return List.of(
        ProgramOption.functions(
            Overload.unary(IP, NetworkLib::ip),
            Overload.unary("ip_string", NetworkLib::ip),
            Overload.unary(CIDR, NetworkLib::cidr),
            Overload.unary("cidr_string", NetworkLib::cidr),
            Overload.unary(IS_IP, NetworkLib::isIp),
            Overload.unary("is_ip_string", NetworkLib::isIp),
            Overload.unary(IP_IS_CANONICAL, NetworkLib::ipIsCanonical),
            Overload.unary("ip_is_canonical_string", NetworkLib::ipIsCanonical),
            Overload.unary("string_ip", value -> value.convertToType(StringType)),
            Overload.unary("string_cidr", value -> value.convertToType(StringType)),
            Overload.unary("cidr_ip", value -> ((CidrT) value).receive("ip", "cidr_ip"))),
        ProgramOption.globals(Map.of(NET_IP, IP_TYPE_VALUE, NET_CIDR, CIDR_TYPE_VALUE)));
  }

  private static Val ip(Val value) {
    if (value instanceof CidrT cidr) {
      return cidr.ip;
    }
    if (!isString(value)) {
      return noSuchOverload(null, IP, value);
    }
    return parseIp(value.value().toString());
  }

  private static Val cidr(Val value) {
    if (!isString(value)) {
      return noSuchOverload(null, CIDR, value);
    }
    return parseCidr(value.value().toString());
  }

  private static Val isIp(Val value) {
    if (!isString(value)) {
      return noSuchOverload(null, IS_IP, value);
    }
    return parseIp(value.value().toString()) instanceof IpT ? True : False;
  }

  private static Val ipIsCanonical(Val value) {
    if (!isString(value)) {
      return noSuchOverload(null, IP_IS_CANONICAL, value);
    }
    Val parsed = parseIp(value.value().toString());
    if (!(parsed instanceof IpT ip)) {
      return parsed;
    }
    return boolOf(value.value().toString().equals(ip.canonical));
  }

  private static boolean isString(Val value) {
    return value.type().typeEnum() == org.projectnessie.cel.common.types.ref.TypeEnum.String;
  }

  private static Val parseIp(String text) {
    if (text.contains("%")) {
      return newErr("IP Address with zone value is not allowed");
    }
    if (text.indexOf(':') >= 0 && text.indexOf('.') >= 0) {
      return newErr("IPv4-mapped IPv6 address is not allowed");
    }
    try {
      if (text.indexOf(':') >= 0) {
        InetAddress address = InetAddress.getByName(text);
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
          return new IpT(bytes, 4);
        }
        if (bytes.length == 16) {
          return new IpT(bytes, 6);
        }
      } else {
        return new IpT(parseIpv4(text), 4);
      }
    } catch (IllegalArgumentException | UnknownHostException e) {
      // fall through
    }
    return newErr("IP Address '%s' parse error during conversion from string", text);
  }

  private static byte[] parseIpv4(String text) {
    String[] parts = text.split("\\.", -1);
    if (parts.length != 4) {
      throw new IllegalArgumentException("invalid IPv4 address");
    }
    byte[] bytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      String part = parts[i];
      if (part.isEmpty() || (part.length() > 1 && part.charAt(0) == '0')) {
        throw new IllegalArgumentException("invalid IPv4 address");
      }
      int value = Integer.parseInt(part);
      if (value < 0 || value > 255) {
        throw new IllegalArgumentException("invalid IPv4 address");
      }
      bytes[i] = (byte) value;
    }
    return bytes;
  }

  private static Val parseCidr(String text) {
    int slash = text.indexOf('/');
    if (slash < 0 || slash != text.lastIndexOf('/') || slash == text.length() - 1) {
      return newErr("network address parse error during conversion from string");
    }
    String ipText = text.substring(0, slash);
    String prefixText = text.substring(slash + 1);
    if (ipText.contains("%")) {
      return newErr("CIDR with zone value is not allowed");
    }
    Val parsedIp = parseIp(ipText);
    if (!(parsedIp instanceof IpT ip)) {
      return parsedIp;
    }
    try {
      int prefix = Integer.parseInt(prefixText);
      int bits = ip.family == 4 ? 32 : 128;
      if (prefix < 0 || prefix > bits) {
        return newErr("network address parse error during conversion from string");
      }
      return new CidrT(ip, prefix);
    } catch (NumberFormatException e) {
      return newErr("network address parse error during conversion from string");
    }
  }

  private static BigInteger unsigned(byte[] bytes) {
    return new BigInteger(1, bytes);
  }

  private static byte[] bytes(BigInteger value, int length) {
    byte[] source = value.toByteArray();
    byte[] target = new byte[length];
    int copy = Math.min(source.length, length);
    System.arraycopy(source, source.length - copy, target, length - copy, copy);
    return target;
  }

  private static byte[] mask(byte[] address, int prefix) {
    int bits = address.length * Byte.SIZE;
    if (prefix == bits) {
      return address.clone();
    }
    BigInteger value = unsigned(address);
    BigInteger mask = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
    mask = mask.xor(BigInteger.ONE.shiftLeft(bits - prefix).subtract(BigInteger.ONE));
    return bytes(value.and(mask), address.length);
  }

  private static String canonicalIpv4(byte[] bytes) {
    return (bytes[0] & 0xff)
        + "."
        + (bytes[1] & 0xff)
        + "."
        + (bytes[2] & 0xff)
        + "."
        + (bytes[3] & 0xff);
  }

  private static String canonicalIpv6(byte[] bytes) {
    int[] words = new int[8];
    for (int i = 0; i < words.length; i++) {
      words[i] = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
    }

    int bestStart = -1;
    int bestLength = 0;
    for (int i = 0; i < words.length; ) {
      if (words[i] != 0) {
        i++;
        continue;
      }
      int start = i;
      while (i < words.length && words[i] == 0) {
        i++;
      }
      int length = i - start;
      if (length > bestLength && length > 1) {
        bestStart = start;
        bestLength = length;
      }
    }

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      if (i == bestStart) {
        if (!result.isEmpty() && result.charAt(result.length() - 1) != ':') {
          result.append(':');
        }
        result.append(':');
        i += bestLength - 1;
        continue;
      }
      if (!result.isEmpty() && result.charAt(result.length() - 1) != ':') {
        result.append(':');
      }
      result.append(Integer.toHexString(words[i]));
    }
    return result.toString();
  }

  private static final class IpT extends BaseVal implements Receiver {
    private final byte[] bytes;
    private final int family;
    private final String canonical;

    private IpT(byte[] bytes, int family) {
      this.bytes = bytes.clone();
      this.family = family;
      this.canonical = family == 4 ? canonicalIpv4(bytes) : canonicalIpv6(bytes);
    }

    @Override
    @SuppressWarnings({"removal", "unchecked"})
    public <T> T convertToNative(Class<T> typeDesc) {
      if (typeDesc == String.class || typeDesc == Object.class) {
        return (T) canonical;
      }
      if (typeDesc == byte[].class) {
        return (T) bytes.clone();
      }
      throw new IllegalArgumentException(
          String.format("Unsupported conversion of '%s' to '%s'", NET_IP, typeDesc.getName()));
    }

    @Override
    public Val convertToType(org.projectnessie.cel.common.types.ref.Type typeVal) {
      if (typeVal == StringType) {
        return stringOf(canonical);
      }
      if (typeVal.typeName().equals(org.projectnessie.cel.common.types.TypeT.TypeType.typeName())) {
        return IP_TYPE_VALUE;
      }
      if (typeVal.typeName().equals(NET_IP)) {
        return this;
      }
      return newTypeConversionError(NET_IP, typeVal);
    }

    @Override
    public Val equal(Val other) {
      if (!(other instanceof IpT otherIp)) {
        return False;
      }
      return boolOf(Arrays.equals(bytes, otherIp.bytes));
    }

    @Override
    public Val receive(String function, String overload, Val... args) {
      if (args.length != 0) {
        return noSuchOverload(this, function, overload, args);
      }
      return switch (function) {
        case "family" -> intOf(family);
        case "isUnspecified" -> boolOf(unsigned(bytes).signum() == 0);
        case "isLoopback" ->
            boolOf(family == 4 ? (bytes[0] & 0xff) == 127 : unsigned(bytes).equals(BigInteger.ONE));
        case "isGlobalUnicast" ->
            boolOf(!isMulticast() && unsigned(bytes).signum() != 0 && !isBroadcast());
        case "isLinkLocalMulticast" ->
            boolOf(
                family == 4
                    ? canonical.startsWith("224.0.0.")
                    : (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0x02);
        case "isLinkLocalUnicast" ->
            boolOf(
                family == 4
                    ? (bytes[0] & 0xff) == 169 && (bytes[1] & 0xff) == 254
                    : (bytes[0] & 0xff) == 0xfe && ((bytes[1] & 0xc0) == 0x80));
        default -> noSuchOverload(this, function, overload, args);
      };
    }

    private boolean isMulticast() {
      return family == 4 ? (bytes[0] & 0xf0) == 0xe0 : (bytes[0] & 0xff) == 0xff;
    }

    private boolean isBroadcast() {
      return family == 4
          && unsigned(bytes).equals(BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE));
    }

    @Override
    public org.projectnessie.cel.common.types.ref.Type type() {
      return IP_TYPE_VALUE;
    }

    @Override
    public Object value() {
      return canonical;
    }
  }

  private static final class CidrT extends BaseVal implements Receiver {
    private final IpT ip;
    private final int prefix;
    private final String canonical;

    private CidrT(IpT ip, int prefix) {
      this.ip = ip;
      this.prefix = prefix;
      this.canonical = ip.canonical + "/" + prefix;
    }

    @Override
    @SuppressWarnings({"removal", "unchecked"})
    public <T> T convertToNative(Class<T> typeDesc) {
      if (typeDesc == String.class || typeDesc == Object.class) {
        return (T) canonical;
      }
      throw new IllegalArgumentException(
          String.format("Unsupported conversion of '%s' to '%s'", NET_CIDR, typeDesc.getName()));
    }

    @Override
    public Val convertToType(org.projectnessie.cel.common.types.ref.Type typeVal) {
      if (typeVal == StringType) {
        return stringOf(canonical);
      }
      if (typeVal.typeName().equals(org.projectnessie.cel.common.types.TypeT.TypeType.typeName())) {
        return CIDR_TYPE_VALUE;
      }
      if (typeVal.typeName().equals(NET_CIDR)) {
        return this;
      }
      return newTypeConversionError(NET_CIDR, typeVal);
    }

    @Override
    public Val equal(Val other) {
      if (!(other instanceof CidrT otherCidr)) {
        return False;
      }
      return boolOf(prefix == otherCidr.prefix && ip.equal(otherCidr.ip) == True);
    }

    @Override
    public Val receive(String function, String overload, Val... args) {
      return switch (function) {
        case "containsIP" -> containsIp(args);
        case "containsCIDR" -> containsCidr(args);
        case "ip" -> args.length == 0 ? ip : noSuchOverload(this, function, overload, args);
        case "masked" ->
            args.length == 0 ? masked() : noSuchOverload(this, function, overload, args);
        case "prefixLength" ->
            args.length == 0 ? intOf(prefix) : noSuchOverload(this, function, overload, args);
        default -> noSuchOverload(this, function, overload, args);
      };
    }

    private Val containsIp(Val[] args) {
      if (args.length != 1) {
        return noSuchOverload(this, "containsIP", "", args);
      }
      Val candidate =
          args[0] instanceof IpT
              ? args[0]
              : isString(args[0]) ? parseIp(args[0].value().toString()) : null;
      if (!(candidate instanceof IpT candidateIp)) {
        return candidate != null ? candidate : noSuchOverload(this, "containsIP", "", args);
      }
      if (candidateIp.family != ip.family) {
        return False;
      }
      return boolOf(Arrays.equals(mask(candidateIp.bytes, prefix), mask(ip.bytes, prefix)));
    }

    private Val containsCidr(Val[] args) {
      if (args.length != 1) {
        return noSuchOverload(this, "containsCIDR", "", args);
      }
      Val candidate =
          args[0] instanceof CidrT
              ? args[0]
              : isString(args[0]) ? parseCidr(args[0].value().toString()) : null;
      if (!(candidate instanceof CidrT candidateCidr)) {
        return candidate != null ? candidate : noSuchOverload(this, "containsCIDR", "", args);
      }
      if (candidateCidr.ip.family != ip.family || candidateCidr.prefix < prefix) {
        return False;
      }
      return boolOf(Arrays.equals(mask(candidateCidr.ip.bytes, prefix), mask(ip.bytes, prefix)));
    }

    private CidrT masked() {
      return new CidrT(new IpT(mask(ip.bytes, prefix), ip.family), prefix);
    }

    @Override
    public org.projectnessie.cel.common.types.ref.Type type() {
      return CIDR_TYPE_VALUE;
    }

    @Override
    public Object value() {
      return canonical;
    }
  }
}
