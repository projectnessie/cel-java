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
package org.projectnessie.cel.common.debug;

import org.projectnessie.cel.pb.Constant;
import org.projectnessie.cel.pb.Constant.BoolValue;
import org.projectnessie.cel.pb.Constant.BytesValue;
import org.projectnessie.cel.pb.Constant.DoubleValue;
import org.projectnessie.cel.pb.Constant.Int64Value;
import org.projectnessie.cel.pb.Constant.NullValue;
import org.projectnessie.cel.pb.Constant.StringValue;
import org.projectnessie.cel.pb.Constant.Uint64Value;
import org.projectnessie.cel.pb.Expr;
import org.projectnessie.cel.pb.Expr.CallExpr;
import org.projectnessie.cel.pb.Expr.ComprehensionExpr;
import org.projectnessie.cel.pb.Expr.IdentExpr;
import org.projectnessie.cel.pb.Expr.ListExpr;
import org.projectnessie.cel.pb.Expr.SelectExpr;
import org.projectnessie.cel.pb.Expr.StructExpr;
import org.projectnessie.cel.pb.Expr.StructExpr.Entry;
import org.projectnessie.cel.pb.Expr.StructExpr.FieldKey;
import org.projectnessie.cel.pb.Expr.StructExpr.MapKey;

public class Debug {
  /** ToDebugString gives the unadorned string representation of the Expr. */
  public String toDebugString(Expr e) {
    return toAdornedDebugString(e, new EmptyDebugAdorner());
  }

  /** ToAdornedDebugString gives the adorned string representation of the Expr. */
  public static String toAdornedDebugString(Expr e, Adorner adorner) {
    DebugWriter w = new DebugWriter(adorner);
    w.buffer(e);
    return w.toString();
  }

  public static String formatLiteral(Constant c) {
    if (c instanceof Constant.BoolValue) {
      return Boolean.toString(((BoolValue) c).value);
    } else if (c instanceof BytesValue) {
      StringBuilder sb = new StringBuilder();
      sb.append("b\"");
      byte[] bytes = ((BytesValue) c).value;
      for (byte b : bytes) {
        int i = b & 0xff;
        if (i >= 32 && i <= 127 && i != 34) {
          sb.append((char) i);
        } else {
          switch (i) {
            case 7:
              sb.append("\\a");
              break;
            case 8:
              sb.append("\\b");
              break;
            case 9:
              sb.append("\\t");
              break;
            case 10:
              sb.append("\\n");
              break;
            case 11:
              sb.append("\\v");
              break;
            case 12:
              sb.append("\\f");
              break;
            case 13:
              sb.append("\\r");
              break;
            case '"':
              sb.append("\\\"");
              break;
            default:
              sb.append(String.format("\\x%02x", i));
              break;
          }
        }
      }
      sb.append("\"");
      return sb.toString();
    } else if (c instanceof DoubleValue) {
      String s = Double.toString(((DoubleValue) c).value);
      if (s.endsWith(".0")) {
        return s.substring(0, s.length() - 2);
      }
      return s;
    } else if (c instanceof Int64Value) {
      return Long.toString(((Int64Value) c).value);
    } else if (c instanceof StringValue) {
      StringBuilder sb = new StringBuilder();
      sb.append('\"');
      String s = ((StringValue) c).value;
      for (int i = 0; i < s.length(); i++) {
        char ch = s.charAt(i);
        switch (ch) {
          case 7: // BEL
            sb.append("\\a");
            break;
          case 11: // VT
            sb.append("\\v");
            break;
          case '\t':
            sb.append("\\t");
            break;
          case '\b':
            sb.append("\\b");
            break;
          case '\n':
            sb.append("\\n");
            break;
          case '\r':
            sb.append("\\r");
            break;
          case '\f':
            sb.append("\\f");
            break;
          case '\'':
            sb.append("'");
            break;
          case '\"':
            sb.append("\\\"");
            break;
          case '\\':
            sb.append("\\\\");
            break;
          default:
            if (Character.isDefined(ch)) {
              sb.append(ch);
            } else {
              sb.append(String.format("\\u%04x", ((int) ch) & 0xffff));
            }
            break;
        }
      }
      sb.append('\"');
      return sb.toString();
    } else if (c instanceof Uint64Value) {
      return Long.toUnsignedString(((Uint64Value) c).value) + "u";
    } else if (c instanceof NullValue) {
      return null;
    } else {
      throw new IllegalArgumentException("" + c);
    }
  }

  /**
   * Adorner returns debug metadata that will be tacked on to the string representation of an
   * expression.#
   */
  public interface Adorner {
    /** GetMetadata for the input context. */
    String getMetadata(Object ctx);
  }

  static class EmptyDebugAdorner implements Adorner {
    public String getMetadata(Object e) {
      return "";
    }
  }

  /** debugWriter is used to print out pretty-printed debug strings. */
  public static class DebugWriter {
    Adorner adorner;
    StringBuilder buffer;
    int indent;
    boolean lineStart;

    public DebugWriter(Adorner a) {
      this.adorner = a;
      this.buffer = new StringBuilder();
      this.indent = 0;
      this.lineStart = true;
    }

    void buffer(Expr e) {
      if (e == null) {
        return;
      }
      if (e instanceof Expr.Const) {
        append(formatLiteral(((Expr.Const) e).value));
      } else if (e instanceof IdentExpr) {
        append(((IdentExpr) e).name);
      } else if (e instanceof SelectExpr) {
        appendSelect(((SelectExpr) e));
      } else if (e instanceof CallExpr) {
        appendCall(((CallExpr) e));
      } else if (e instanceof ListExpr) {
        appendList(((ListExpr) e));
      } else if (e instanceof StructExpr) {
        appendStruct(((StructExpr) e));
      } else if (e instanceof ComprehensionExpr) {
        appendComprehension(((ComprehensionExpr) e));
      }
      adorn(e);
    }

    void appendSelect(SelectExpr sel) {
      buffer(sel.operand);
      append(".");
      append(sel.field);
      if (sel.testOnly) {
        append("~test-only~");
      }
    }

    void appendCall(CallExpr call) {
      if (call.target != null) {
        buffer(call.target);
        append(".");
      }
      append(call.function);
      append("(");
      if (call.args.length > 0) {
        addIndent();
        appendLine();
        for (int i = 0; i < call.args.length; i++) {
          if (i > 0) {
            append(",");
            appendLine();
          }
          buffer(call.args[i]);
        }
        removeIndent();
        appendLine();
      }
      append(")");
    }

    void appendList(ListExpr list) {
      append("[");
      if (list.elements.length > 0) {
        appendLine();
        addIndent();
        for (int i = 0; i < list.elements.length; i++) {

          if (i > 0) {
            append(",");
            appendLine();
          }
          buffer(list.elements[i]);
        }
        removeIndent();
        appendLine();
      }
      append("]");
    }

    void appendStruct(StructExpr obj) {
      if (obj.messageName != null) {
        appendObject(obj);
      } else {
        appendMap(obj);
      }
    }

    void appendObject(StructExpr obj) {
      append(obj.messageName);
      append("{");
      if (obj.entries.length > 0) {
        appendLine();
        addIndent();
        for (int i = 0; i < obj.entries.length; i++) {
          if (i > 0) {
            append(",");
            appendLine();
          }
          Entry entry = obj.entries[i];
          append(((FieldKey) entry.key).field);
          append(":");
          buffer(entry.value);
          adorn(entry);
        }
        removeIndent();
        appendLine();
      }
      append("}");
    }

    void appendMap(StructExpr obj) {
      append("{");
      if (obj.entries.length > 0) {
        appendLine();
        addIndent();
        for (int i = 0; i < obj.entries.length; i++) {
          if (i > 0) {
            append(",");
            appendLine();
          }
          Entry entry = obj.entries[i];
          buffer(((MapKey) entry.key).mapKey);
          append(":");
          buffer(entry.value);
          adorn(entry);
        }
        removeIndent();
        appendLine();
      }
      append("}");
    }

    void appendComprehension(ComprehensionExpr comprehension) {
      append("__comprehension__(");
      addIndent();
      appendLine();
      append("// Variable");
      appendLine();
      append(comprehension.iterVar);
      append(",");
      appendLine();
      append("// Target");
      appendLine();
      buffer(comprehension.iterRange);
      append(",");
      appendLine();
      append("// Accumulator");
      appendLine();
      append(comprehension.accuVar);
      append(",");
      appendLine();
      append("// Init");
      appendLine();
      buffer(comprehension.accuInit);
      append(",");
      appendLine();
      append("// LoopCondition");
      appendLine();
      buffer(comprehension.loopCondition);
      append(",");
      appendLine();
      append("// LoopStep");
      appendLine();
      buffer(comprehension.loopStep);
      append(",");
      appendLine();
      append("// Result");
      appendLine();
      buffer(comprehension.result);
      append(")");
      removeIndent();
    }

    void append(String s) {
      doIndent();
      buffer.append(s);
    }

    void appendFormat(String f, Object... args) {
      append(String.format(f, args));
    }

    void doIndent() {
      if (lineStart) {
        lineStart = false;
        for (int i = 0; i < indent; i++) {
          buffer.append("  ");
        }
      }
    }

    void adorn(Object e) {
      append(adorner.getMetadata(e));
    }

    void appendLine() {
      buffer.append("\n");
      lineStart = true;
    }

    void addIndent() {
      indent++;
    }

    void removeIndent() {
      indent--;
      if (indent < 0) {
        throw new IllegalStateException("negative indent");
      }
    }

    public String toString() {
      return buffer.toString();
    }
  }
}
