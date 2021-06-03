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

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Comprehension;
import com.google.api.expr.v1alpha1.Expr.CreateList;
import com.google.api.expr.v1alpha1.Expr.CreateStruct;
import com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry;
import com.google.api.expr.v1alpha1.Expr.Select;

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
    switch (c.getConstantKindCase()) {
      case BOOL_VALUE:
        return Boolean.toString(c.getBoolValue());
      case BYTES_VALUE:
        StringBuilder sb = new StringBuilder();
        sb.append("b\"");
        byte[] bytes = c.getBytesValue().toByteArray();
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
      case DOUBLE_VALUE:
        String s = Double.toString(c.getDoubleValue());
        if (s.endsWith(".0")) {
          return s.substring(0, s.length() - 2);
        }
        return s;
      case INT64_VALUE:
        return Long.toString(c.getInt64Value());
      case STRING_VALUE:
        sb = new StringBuilder();
        sb.append('\"');
        s = c.getStringValue();
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
      case UINT64_VALUE:
        return Long.toUnsignedString(c.getUint64Value()) + "u";
      case NULL_VALUE:
        return null;
      default:
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
      switch (e.getExprKindCase()) {
        case CONST_EXPR:
          append(formatLiteral(e.getConstExpr()));
          break;
        case IDENT_EXPR:
          append(e.getIdentExpr().getName());
          break;
        case SELECT_EXPR:
          appendSelect(e.getSelectExpr());
          break;
        case CALL_EXPR:
          appendCall(e.getCallExpr());
          break;
        case LIST_EXPR:
          appendList(e.getListExpr());
          break;
        case STRUCT_EXPR:
          appendStruct(e.getStructExpr());
          break;
        case COMPREHENSION_EXPR:
          appendComprehension(e.getComprehensionExpr());
          break;
        case EXPRKIND_NOT_SET:
          throw new IllegalStateException("Expr w/o kind");
      }
      adorn(e);
    }

    void appendSelect(Select sel) {
      buffer(sel.getOperand());
      append(".");
      append(sel.getField());
      if (sel.getTestOnly()) {
        append("~test-only~");
      }
    }

    void appendCall(Call call) {
      if (call.hasTarget()) {
        buffer(call.getTarget());
        append(".");
      }
      append(call.getFunction());
      append("(");
      if (call.getArgsCount() > 0) {
        addIndent();
        appendLine();
        for (int i = 0; i < call.getArgsCount(); i++) {
          if (i > 0) {
            append(",");
            appendLine();
          }
          buffer(call.getArgs(i));
        }
        removeIndent();
        appendLine();
      }
      append(")");
    }

    void appendList(CreateList list) {
      append("[");
      if (list.getElementsCount() > 0) {
        appendLine();
        addIndent();
        for (int i = 0; i < list.getElementsCount(); i++) {

          if (i > 0) {
            append(",");
            appendLine();
          }
          buffer(list.getElements(i));
        }
        removeIndent();
        appendLine();
      }
      append("]");
    }

    void appendStruct(CreateStruct obj) {
      if (!obj.getMessageName().isEmpty()) {
        appendObject(obj);
      } else {
        appendMap(obj);
      }
    }

    void appendObject(CreateStruct obj) {
      append(obj.getMessageName());
      append("{");
      if (obj.getEntriesCount() > 0) {
        appendLine();
        addIndent();
        for (int i = 0; i < obj.getEntriesCount(); i++) {
          if (i > 0) {
            append(",");
            appendLine();
          }
          Entry entry = obj.getEntries(i);
          append(entry.getFieldKey());
          append(":");
          buffer(entry.getValue());
          adorn(entry);
        }
        removeIndent();
        appendLine();
      }
      append("}");
    }

    void appendMap(CreateStruct obj) {
      append("{");
      if (obj.getEntriesCount() > 0) {
        appendLine();
        addIndent();
        for (int i = 0; i < obj.getEntriesCount(); i++) {
          if (i > 0) {
            append(",");
            appendLine();
          }
          Entry entry = obj.getEntries(i);
          buffer(entry.getMapKey());
          append(":");
          buffer(entry.getValue());
          adorn(entry);
        }
        removeIndent();
        appendLine();
      }
      append("}");
    }

    void appendComprehension(Comprehension comprehension) {
      append("__comprehension__(");
      addIndent();
      appendLine();
      append("// Variable");
      appendLine();
      append(comprehension.getIterVar());
      append(",");
      appendLine();
      append("// Target");
      appendLine();
      buffer(comprehension.getIterRange());
      append(",");
      appendLine();
      append("// Accumulator");
      appendLine();
      append(comprehension.getAccuVar());
      append(",");
      appendLine();
      append("// Init");
      appendLine();
      buffer(comprehension.getAccuInit());
      append(",");
      appendLine();
      append("// LoopCondition");
      appendLine();
      buffer(comprehension.getLoopCondition());
      append(",");
      appendLine();
      append("// LoopStep");
      appendLine();
      buffer(comprehension.getLoopStep());
      append(",");
      appendLine();
      append("// Result");
      appendLine();
      buffer(comprehension.getResult());
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
