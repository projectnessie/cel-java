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
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.cel.CEL.astToCheckedExpr;
import static org.projectnessie.cel.CEL.astToParsedExpr;
import static org.projectnessie.cel.CEL.astToString;
import static org.projectnessie.cel.CEL.attributePattern;
import static org.projectnessie.cel.CEL.checkedExprToAst;
import static org.projectnessie.cel.CEL.estimateCost;
import static org.projectnessie.cel.CEL.noVars;
import static org.projectnessie.cel.CEL.parsedExprToAst;
import static org.projectnessie.cel.CEL.partialVars;
import static org.projectnessie.cel.Env.newCustomEnv;
import static org.projectnessie.cel.Env.newEnv;
import static org.projectnessie.cel.EnvOption.abbrevs;
import static org.projectnessie.cel.EnvOption.container;
import static org.projectnessie.cel.EnvOption.customTypeAdapter;
import static org.projectnessie.cel.EnvOption.customTypeProvider;
import static org.projectnessie.cel.EnvOption.declarations;
import static org.projectnessie.cel.EnvOption.homogeneousAggregateLiterals;
import static org.projectnessie.cel.EnvOption.macros;
import static org.projectnessie.cel.EnvOption.types;
import static org.projectnessie.cel.EvalOption.OptExhaustiveEval;
import static org.projectnessie.cel.EvalOption.OptPartialEval;
import static org.projectnessie.cel.EvalOption.OptTrackState;
import static org.projectnessie.cel.Library.StdLib;
import static org.projectnessie.cel.ProgramOption.customDecorator;
import static org.projectnessie.cel.ProgramOption.evalOptions;
import static org.projectnessie.cel.ProgramOption.functions;
import static org.projectnessie.cel.ProgramOption.globals;
import static org.projectnessie.cel.Util.mapOf;
import static org.projectnessie.cel.common.types.BoolT.True;
import static org.projectnessie.cel.common.types.Err.isError;
import static org.projectnessie.cel.common.types.Err.newErr;
import static org.projectnessie.cel.common.types.Err.valOrErr;
import static org.projectnessie.cel.common.types.IntT.IntOne;
import static org.projectnessie.cel.common.types.StringT.stringOf;
import static org.projectnessie.cel.common.types.pb.ProtoTypeRegistry.newEmptyRegistry;
import static org.projectnessie.cel.interpreter.Activation.emptyActivation;
import static org.projectnessie.cel.interpreter.Interpretable.newConstValue;
import static org.projectnessie.cel.parser.Macro.newReceiverMacro;

import com.google.api.expr.v1alpha1.CheckedExpr;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.Expr.Call;
import com.google.api.expr.v1alpha1.Expr.Ident;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.projectnessie.cel.Env.AstIssuesTuple;
import org.projectnessie.cel.Program.EvalResult;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.common.operators.Operator;
import org.projectnessie.cel.common.types.BoolT;
import org.projectnessie.cel.common.types.IntT;
import org.projectnessie.cel.common.types.Overloads;
import org.projectnessie.cel.common.types.StringT;
import org.projectnessie.cel.common.types.UnknownT;
import org.projectnessie.cel.common.types.pb.DefaultTypeAdapter;
import org.projectnessie.cel.common.types.ref.TypeRegistry;
import org.projectnessie.cel.common.types.ref.Val;
import org.projectnessie.cel.common.types.traits.Container;
import org.projectnessie.cel.common.types.traits.Mapper;
import org.projectnessie.cel.common.types.traits.Trait;
import org.projectnessie.cel.interpreter.Activation.PartialActivation;
import org.projectnessie.cel.interpreter.AttributeFactory.NamespacedAttribute;
import org.projectnessie.cel.interpreter.Coster.Cost;
import org.projectnessie.cel.interpreter.EvalState;
import org.projectnessie.cel.interpreter.Interpretable;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableAttribute;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableCall;
import org.projectnessie.cel.interpreter.Interpretable.InterpretableConst;
import org.projectnessie.cel.interpreter.InterpretableDecorator;
import org.projectnessie.cel.interpreter.functions.Overload;
import org.projectnessie.cel.parser.Macro;

public class CELTest {

  @Test
  void AstToProto() {
    Env stdEnv = newEnv(declarations(Decls.newVar("a", Decls.Dyn), Decls.newVar("b", Decls.Dyn)));
    AstIssuesTuple astIss = stdEnv.parse("a + b");
    assertThat(astIss.hasIssues()).isFalse();
    ParsedExpr parsed = astToParsedExpr(astIss.getAst());
    Ast ast2 = parsedExprToAst(parsed);
    assertThat(ast2.getExpr()).isEqualTo(astIss.getAst().getExpr());

    assertThatThrownBy(() -> astToCheckedExpr(astIss.getAst()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot convert unchecked ast");
    AstIssuesTuple astIss2 = stdEnv.check(astIss.getAst());
    assertThat(astIss2.hasIssues()).isFalse();
    // assertThat(astIss.hasIssues()).isFalse();
    CheckedExpr checked = astToCheckedExpr(astIss2.getAst());
    Ast ast3 = checkedExprToAst(checked);
    assertThat(ast3.getExpr()).isEqualTo(astIss2.getAst().getExpr());
  }

  @Test
  void AstToString() {
    Env stdEnv = newEnv();
    String in = "a + b - (c ? (-d + 4) : e)";
    AstIssuesTuple astIss = stdEnv.parse(in);
    assertThat(astIss.hasIssues()).isFalse();
    String expr = astToString(astIss.getAst());
    assertThat(expr).isEqualTo(in);
  }

  @Test
  void CheckedExprToAst_ConstantExpr() {
    Env stdEnv = newEnv();
    String in = "10";
    AstIssuesTuple astIss = stdEnv.compile(in);
    assertThat(astIss.hasIssues()).isFalse();
    CheckedExpr expr = astToCheckedExpr(astIss.getAst());
    Ast ast2 = checkedExprToAst(expr);
    assertThat(ast2.getExpr()).isEqualTo(astIss.getAst().getExpr());
  }

  @Test
  void exampleWithBuiltins() {
    // Variables used within this expression environment.
    EnvOption decls =
        declarations(Decls.newVar("i", Decls.String), Decls.newVar("you", Decls.String));
    Env env = newEnv(decls);

    // Compile the expression.
    AstIssuesTuple astIss = env.compile("\"Hello \" + you + \"! I'm \" + i + \".\"");
    assertThat(astIss.hasIssues()).isFalse();

    // Create the program, and evaluate it against some input.
    Program prg = env.program(astIss.getAst());

    // If the Eval() call were provided with cel.evalOptions(OptTrackState) the details response
    // (2nd return) would be non-nil.
    EvalResult out =
        prg.eval(
            mapOf(
                "i", "CEL",
                "you", "world"));

    assertThat(out.getVal().equal(stringOf("Hello world! I'm CEL."))).isSameAs(True);
  }

  @Test
  void Abbrevs_Compiled() {
    // Test whether abbreviations successfully resolve at type-check time (compile time).
    Env env =
        newEnv(
            abbrevs("qualified.identifier.name"),
            declarations(Decls.newVar("qualified.identifier.name.first", Decls.String)));
    AstIssuesTuple astIss = env.compile("\"hello \"+ name.first"); // abbreviation resolved here.
    assertThat(astIss.hasIssues()).isFalse();
    Program prg = env.program(astIss.getAst());
    EvalResult out = prg.eval(mapOf("qualified.identifier.name.first", "Jim"));
    assertThat(out.getVal().value()).isEqualTo("hello Jim");
  }

  @Test
  void Abbrevs_Parsed() {
    // Test whether abbreviations are resolved properly at evaluation time.
    Env env = newEnv(abbrevs("qualified.identifier.name"));
    AstIssuesTuple astIss = env.parse("\"hello \" + name.first");
    assertThat(astIss.hasIssues()).isFalse();
    Program prg = env.program(astIss.getAst()); // abbreviation resolved here.
    EvalResult out = prg.eval(mapOf("qualified.identifier.name", mapOf("first", "Jim")));
    assertThat(out.getVal().value()).isEqualTo("hello Jim");
  }

  @Test
  void Abbrevs_Disambiguation() {
    Env env =
        newEnv(
            abbrevs("external.Expr"),
            container("google.api.expr.v1alpha1"),
            types(Expr.getDefaultInstance()),
            declarations(
                Decls.newVar("test", Decls.Bool), Decls.newVar("external.Expr", Decls.String)));
    // This expression will return either a string or a protobuf Expr value depending on the value
    // of the 'test' argument. The fully qualified type name is used indicate that the protobuf
    // typed 'Expr' should be used rather than the abbreviatation for 'external.Expr'.
    AstIssuesTuple astIss = env.compile("test ? dyn(Expr) : google.api.expr.v1alpha1.Expr{id: 1}");
    assertThat(astIss.hasIssues()).isFalse();
    Program prg = env.program(astIss.getAst());
    EvalResult out = prg.eval(mapOf("test", true, "external.Expr", "string expr"));
    assertThat(out.getVal().value()).isEqualTo("string expr");
    out = prg.eval(mapOf("test", false, "external.Expr", "wrong expr"));
    Expr want = Expr.newBuilder().setId(1).build();
    Expr got = out.getVal().convertToNative(Expr.class);
    assertThat(got).isEqualTo(want);
  }

  @Test
  void CustomEnvError() {
    Env e = newCustomEnv(StdLib(), StdLib());
    AstIssuesTuple xIss = e.compile("a.b.c == true");
    assertThat(xIss.hasIssues()).isTrue();
  }

  @Test
  void CustomEnv() {
    Env e = newCustomEnv(declarations(Decls.newVar("a.b.c", Decls.Bool)));

    // t.Run("err", func(t *testing.T) {
    AstIssuesTuple xIss = e.compile("a.b.c == true");
    assertThat(xIss.hasIssues()).isTrue();

    // t.Run("ok", func(t *testing.T) {
    AstIssuesTuple astIss = e.compile("a.b.c");
    assertThat(astIss.hasIssues()).isFalse();
    Program prg = e.program(astIss.getAst());
    EvalResult out = prg.eval(mapOf("a.b.c", true));
    assertThat(out.getVal()).isSameAs(True);
  }

  @Test
  void HomogeneousAggregateLiterals() {
    Env e =
        newCustomEnv(
            declarations(
                Decls.newVar("name", Decls.String),
                Decls.newFunction(
                    Operator.In.id,
                    Decls.newOverload(
                        Overloads.InList,
                        asList(Decls.String, Decls.newListType(Decls.String)),
                        Decls.Bool),
                    Decls.newOverload(
                        Overloads.InMap,
                        asList(Decls.String, Decls.newMapType(Decls.String, Decls.Bool)),
                        Decls.Bool))),
            homogeneousAggregateLiterals());

    // t.Run("err_list", func(t *testing.T) {
    AstIssuesTuple xIss = e.compile("name in ['hello', 0]");
    assertThat(xIss.getIssues()).isNotNull();
    assertThat(xIss.hasIssues()).isTrue();
    // })
    // t.Run("err_map_key", func(t *testing.T) {
    xIss = e.compile("name in {'hello':'world', 1:'!'}");
    assertThat(xIss.getIssues()).isNotNull();
    assertThat(xIss.hasIssues()).isTrue();
    // })
    // t.Run("err_map_val", func(t *testing.T) {
    xIss = e.compile("name in {'hello':'world', 'goodbye':true}");
    assertThat(xIss.getIssues()).isNotNull();
    assertThat(xIss.hasIssues()).isTrue();
    // })

    ProgramOption funcs =
        functions(
            Overload.binary(
                Operator.In.id,
                (lhs, rhs) -> {
                  if (rhs.type().hasTrait(Trait.ContainerType)) {
                    return ((Container) rhs).contains(lhs);
                  }
                  return valOrErr(rhs, "no such overload");
                }));
    // t.Run("ok_list", func(t *testing.T) {
    AstIssuesTuple astIss = e.compile("name in ['hello', 'world']");
    assertThat(astIss.hasIssues()).isFalse();
    Program prg = e.program(astIss.getAst(), funcs);
    EvalResult out = prg.eval(mapOf("name", "world"));
    assertThat(out.getVal()).isSameAs(True);
    // })
    // t.Run("ok_map", func(t *testing.T) {
    astIss = e.compile("name in {'hello': false, 'world': true}");
    assertThat(astIss.hasIssues()).isFalse();
    prg = e.program(astIss.getAst(), funcs);
    out = prg.eval(mapOf("name", "world"));
    assertThat(out.getVal()).isSameAs(True);
    // })
  }

  @Test
  void Customtypes() {
    Type exprType = Decls.newObjectType("google.api.expr.v1alpha1.Expr");
    TypeRegistry reg = newEmptyRegistry();
    Env e =
        newEnv(
            customTypeAdapter(reg),
            customTypeProvider(reg),
            container("google.api.expr.v1alpha1"),
            types(Expr.getDefaultInstance(), BoolT.BoolType, IntT.IntType, StringT.StringType),
            declarations(Decls.newVar("expr", exprType)));

    AstIssuesTuple astIss =
        e.compile(
            "expr == Expr{id: 2,\n"
                + "\t\t\tcall_expr: Expr.Call{\n"
                + "\t\t\t\tfunction: \"_==_\",\n"
                + "\t\t\t\targs: [\n"
                + "\t\t\t\t\tExpr{id: 1, ident_expr: Expr.Ident{ name: \"a\" }},\n"
                + "\t\t\t\t\tExpr{id: 3, ident_expr: Expr.Ident{ name: \"b\" }}]\n"
                + "\t\t\t}}");
    assertThat(astIss.getAst().getResultType()).isEqualTo(Decls.Bool);
    Program prg = e.program(astIss.getAst());
    Object vars =
        mapOf(
            "expr",
            Expr.newBuilder()
                .setId(2)
                .setCallExpr(
                    Call.newBuilder()
                        .setFunction("_==_")
                        .addAllArgs(
                            asList(
                                Expr.newBuilder()
                                    .setId(1)
                                    .setIdentExpr(Ident.newBuilder().setName("a"))
                                    .build(),
                                Expr.newBuilder()
                                    .setId(3)
                                    .setIdentExpr(Ident.newBuilder().setName("b"))
                                    .build())))
                .build());
    EvalResult out = prg.eval(vars);
    assertThat(out.getVal()).isSameAs(True);
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void TypeIsolation() {
    //	b = ioutil.ReadFile("testdata/team.fds")
    //	var fds descpb.FileDescriptorSet
    //	if err = proto.Unmarshal(b, &fds); err != nil {
    //		t.Fatal("can't unmarshal descriptor data: ", err)
    //	}
    //
    //	Env e = newEnv(
    //		typeDescs(&fds),
    //		declarations(
    //			Decls.newVar("myteam",
    //				Decls.newObjectType("cel.testdata.Team"))));
    //
    //	String src = "myteam.members[0].name == 'Cyclops'";
    //	AstIssuesTuple xIss = e.compile(src)
    //	assertThat(xIss.getIssues().err()).isNull();
    //
    //	// Ensure that isolated types don't leak through.
    //	Env e2 = newEnv(
    //		declarations(
    //			Decls.newVar("myteam",
    //				Decls.newObjectType("cel.testdata.Team"))))
    //	xIss = e2.compile(src)
    //	if iss == nil || iss.Err() == nil {
    //		t.Errorf("wanted compile failure for unknown message.")
    //	}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void DynamicProto() {
    //	b = ioutil.ReadFile("testdata/team.fds");
    //	var fds descpb.FileDescriptorSet
    //	if err = proto.Unmarshal(b, &fds); err != nil {
    //		t.Fatalf("proto.Unmarshal() failed: %v", err)
    //	}
    //	files = (&fds).GetFile()
    //	fileCopy = make([]interface{}, len(files))
    //	for i = 0; i < len(files); i++ {
    //		fileCopy[i] = files[i]
    //	}
    //	pbFiles = protodesc.NewFiles(&fds);
    //	Env e = newEnv(
    //		container("cel"),
    //		// The following is identical to registering the FileDescriptorSet;
    //		// however, it tests a different code path which aggregates individual
    //		// FileDescriptorProto values together.
    //		typeDescs(fileCopy...),
    //		// Additionally, demonstrate that double registration of files doesn't
    //		// cause any problems.
    //		typeDescs(pbFiles),
    //	);
    //	src = `testdata.Team{name: 'X-Men', members: [
    //		testdata.Mutant{name: 'Jean Grey', level: 20},
    //		testdata.Mutant{name: 'Cyclops', level: 7},
    //		testdata.Mutant{name: 'Storm', level: 7},
    //		testdata.Mutant{name: 'Wolverine', level: 11}
    //	]}`
    //	AstIssuesTuple astIss = e.compile(src)
    //	assertThat(astIss.hasIssues()).isFalse();
    //  Program  prg = e.program(astIss.getAst(), evalOptions(OptOptimize));
    //  EvalResult out = prg.eval(noVars);
    //	obj, ok = out.(Trait.Indexer)
    //	if !ok {
    //		t.Fatalf("unable to convert output to object: %v", out)
    //	}
    //	if obj.Get(types.String("name")).equal(types.String("X-Men")) == types.False {
    //		t.Fatalf("got field 'name' %v, wanted X-Men", obj.Get(types.String("name")))
    //	}
  }

  @Test
  @Disabled("IMPLEMENT ME")
  void DynamicProto_Input() {
    //	b = ioutil.ReadFile("testdata/team.fds");
    //	var fds descpb.FileDescriptorSet
    //	if err = proto.Unmarshal(b, &fds); err != nil {
    //		t.Fatalf("proto.Unmarshal() failed: %v", err)
    //	}
    //	files = (&fds).GetFile()
    //	fileCopy = make([]interface{}, len(files))
    //	for i = 0; i < len(files); i++ {
    //		fileCopy[i] = files[i]
    //	}
    //	pbFiles = protodesc.NewFiles(&fds);
    //	desc = pbFiles.FindDescriptorByName("cel.testdata.Mutant");
    //	msgDesc, ok = desc.(protoreflect.MessageDescriptor)
    //	if !ok {
    //		t.Fatalf("desc not convertible to MessageDescriptor: %T", desc)
    //	}
    //	wolverine = dynamicpb.NewMessage(msgDesc)
    //	wolverine.ProtoReflect().Set(msgDesc.Fields().ByName("name"),
    // protoreflect.ValueOfString("Wolverine"))
    //	Env e = newEnv(
    //		// The following is identical to registering the FileDescriptorSet;
    //		// however, it tests a different code path which aggregates individual
    //		// FileDescriptorProto values together.
    //		typeDescs(fileCopy...),
    //		declarations(Decls.newVar("mutant", Decls.newObjectType("cel.testdata.Mutant"))),
    //	);
    //	src = `has(mutant.name) && mutant.name == 'Wolverine'`
    //	AstIssuesTuple astIss = e.compile(src);
    //	assertThat(astIss.hasIssues()).isFalse();
    //  Program  prg = e.program(astIss.getAst(), evalOptions(OptOptimize));
    //  EvalResult out = prg.eval(map[string]interface{}{
    //		"mutant": wolverine,
    //	});
    //	obj, ok = out.(types.Bool)
    //	if !ok {
    //		t.Fatalf("unable to convert output to object: %v", out)
    //	}
    //	if obj != types.True {
    //		t.Errorf("got %v, wanted true", out)
    //	}
  }

  @Test
  void GlobalVars() {
    Type mapStrDyn = Decls.newMapType(Decls.String, Decls.Dyn);
    Env e =
        newEnv(
            declarations(
                Decls.newVar("attrs", mapStrDyn),
                Decls.newVar("default", Decls.Dyn),
                Decls.newFunction(
                    "get",
                    Decls.newInstanceOverload(
                        "get_map", asList(mapStrDyn, Decls.String, Decls.Dyn), Decls.Dyn))));
    AstIssuesTuple astIss = e.compile("attrs.get(\"first\", attrs.get(\"second\", default))");

    // Create the program.
    ProgramOption funcs =
        functions(
            Overload.function(
                "get",
                args -> {
                  if (args.length != 3) {
                    return newErr("invalid arguments to 'get'");
                  }
                  if (!(args[0] instanceof Mapper)) {
                    return newErr(
                        "invalid operand of type '%s' to obj.get(key, def)", args[0].type());
                  }
                  Mapper attrs = (Mapper) args[0];
                  if (!(args[1] instanceof StringT)) {
                    return newErr("invalid key of type '%s' to obj.get(key, def)", args[1].type());
                  }
                  StringT key = (StringT) args[1];
                  Val defVal = args[2];
                  if (attrs.contains(key) == True) {
                    return attrs.get(key);
                  }
                  return defVal;
                }));

    // Global variables can be configured as a ProgramOption and optionally overridden on Eval.
    Program prg = e.program(astIss.getAst(), funcs, globals(mapOf("default", "third")));

    // t.Run("global_default", func(t *testing.T) {
    Object vars = mapOf("attrs", mapOf());
    EvalResult out = prg.eval(vars);
    assertThat(out.getVal().equal(stringOf("third"))).isSameAs(True);
    // })

    // t.Run("attrs_alt", func(t *testing.T) {
    vars = mapOf("attrs", mapOf("second", "yep"));
    out = prg.eval(vars);
    assertThat(out.getVal().equal(stringOf("yep"))).isSameAs(True);
    // })

    // t.Run("local_default", func(t *testing.T) {
    vars = mapOf("attrs", mapOf(), "default", "fourth");
    out = prg.eval(vars);
    assertThat(out.getVal().equal(stringOf("fourth"))).isSameAs(True);
    // })
  }

  @Test
  void CustomMacro() {
    Macro joinMacro =
        newReceiverMacro(
            "join",
            1,
            (eh, target, args) -> {
              Expr delim = args.get(0);
              Expr iterIdent = eh.ident("__iter__");
              Expr accuIdent = eh.ident("__result__");
              Expr init = eh.literalString("");
              Expr condition = eh.literalBool(true);
              Expr step =
                  eh.globalCall(
                      Operator.Conditional.id,
                      eh.globalCall(
                          Operator.Greater.id,
                          eh.receiverCall("size", accuIdent, emptyList()),
                          eh.literalInt(0)),
                      eh.globalCall(
                          Operator.Add.id,
                          eh.globalCall(Operator.Add.id, accuIdent, delim),
                          iterIdent),
                      iterIdent);
              return eh.fold("__iter__", target, "__result__", init, condition, step, accuIdent);
            });
    Env e = newEnv(macros(joinMacro));
    AstIssuesTuple astIss = e.compile("['hello', 'cel', 'friend'].join(',')");
    assertThat(astIss.hasIssues()).isFalse();
    Program prg = e.program(astIss.getAst(), evalOptions(OptExhaustiveEval));
    EvalResult out = prg.eval(noVars());
    assertThat(out.getVal().equal(stringOf("hello,cel,friend"))).isSameAs(True);
  }

  @Test
  void AstIsChecked() {
    Env e = newEnv();
    AstIssuesTuple astIss = e.compile("true");
    assertThat(astIss.hasIssues()).isFalse();
    assertThat(astIss.getAst()).extracting(Ast::isChecked).isEqualTo(true);
    CheckedExpr ce = astToCheckedExpr(astIss.getAst());
    Ast ast2 = checkedExprToAst(ce);
    assertThat(ast2).extracting(Ast::isChecked).isEqualTo(true);
    assertThat(astIss.getAst().getExpr()).isEqualTo(ast2.getExpr());
  }

  @Test
  void EvalOptions() {
    Env e = newEnv(declarations(Decls.newVar("k", Decls.String), Decls.newVar("v", Decls.Bool)));
    AstIssuesTuple astIss = e.compile("{k: true}[k] || v != false");

    Program prg = e.program(astIss.getAst(), evalOptions(OptExhaustiveEval));
    EvalResult outDetails = prg.eval(mapOf("k", "key", "v", true));
    assertThat(outDetails.getVal()).isSameAs(True);

    // Test to see whether 'v != false' was resolved to a value.
    // With short-circuiting it normally wouldn't be.
    EvalState s = outDetails.getEvalDetails().getState();
    Val lhsVal = s.value(astIss.getAst().getExpr().getCallExpr().getArgs(0).getId());
    assertThat(lhsVal).isSameAs(True);
    Val rhsVal = s.value(astIss.getAst().getExpr().getCallExpr().getArgs(1).getId());
    assertThat(rhsVal).isSameAs(True);
  }

  @Test
  void EvalRecover() {
    Env e =
        newEnv(
            declarations(
                Decls.newFunction(
                    "panic",
                    Decls.newOverload(
                        "panic", singletonList(Type.getDefaultInstance()), Decls.Bool))));
    ProgramOption funcs =
        functions(
            Overload.function(
                "panic",
                args -> {
                  throw new RuntimeException("watch me recover");
                }));
    // Test standard evaluation.
    AstIssuesTuple pAst = e.parse("panic()");
    Program prgm1 = e.program(pAst.getAst(), funcs);
    assertThatThrownBy(() -> prgm1.eval(emptyMap()))
        .isExactlyInstanceOf(RuntimeException.class)
        .hasMessage("internal error: watch me recover");
    // Test the factory-based evaluation.
    Program prgm2 = e.program(pAst.getAst(), funcs, evalOptions(OptTrackState));
    assertThatThrownBy(() -> prgm2.eval(emptyMap()))
        .isExactlyInstanceOf(RuntimeException.class)
        .hasMessage("internal error: watch me recover");
  }

  @Test
  void ResidualAst() {
    Env e = newEnv(declarations(Decls.newVar("x", Decls.Int), Decls.newVar("y", Decls.Int)));
    PartialActivation unkVars = e.getUnknownVars();
    AstIssuesTuple astIss = e.parse("x < 10 && (y == 0 || 'hello' != 'goodbye')");
    Program prg = e.program(astIss.getAst(), evalOptions(OptTrackState, OptPartialEval));
    EvalResult outDet = prg.eval(unkVars);
    assertThat(outDet.getVal()).matches(UnknownT::isUnknown);
    Ast residual = e.residualAst(astIss.getAst(), outDet.getEvalDetails());
    String expr = astToString(residual);
    assertThat(expr).isEqualTo("x < 10");
  }

  @Test
  void ResidualAst_Complex() {
    Env e =
        newEnv(
            declarations(
                Decls.newVar("resource.name", Decls.String),
                Decls.newVar("request.time", Decls.Timestamp),
                Decls.newVar("request.auth.claims", Decls.newMapType(Decls.String, Decls.String))));
    PartialActivation unkVars =
        partialVars(
            mapOf(
                "resource.name",
                "bucket/my-bucket/objects/private",
                "request.auth.claims",
                mapOf("email_verified", "true")),
            attributePattern("request.auth.claims").qualString("email"));
    AstIssuesTuple astIss =
        e.compile(
            "resource.name.startsWith(\"bucket/my-bucket\") &&\n"
                + "\t\t bool(request.auth.claims.email_verified) == true &&\n"
                + "\t\t request.auth.claims.email == \"wiley@acme.co\"");
    assertThat(astIss.hasIssues()).isFalse();
    Program prg = e.program(astIss.getAst(), evalOptions(OptTrackState, OptPartialEval));
    EvalResult outDet = prg.eval(unkVars);
    assertThat(outDet.getVal()).matches(UnknownT::isUnknown);
    Ast residual = e.residualAst(astIss.getAst(), outDet.getEvalDetails());
    String expr = astToString(residual);
    assertThat(expr).isEqualTo("request.auth.claims.email == \"wiley@acme.co\"");
  }

  @Test
  void EnvExtension() {
    Env e =
        newEnv(
            container("google.api.expr.v1alpha1"),
            types(Expr.getDefaultInstance()),
            declarations(
                Decls.newVar("expr", Decls.newObjectType("google.api.expr.v1alpha1.Expr"))));
    Env e2 =
        e.extend(
            customTypeAdapter(DefaultTypeAdapter.Instance),
            types(
                com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                    .getDefaultInstance()));
    assertThat(e).isNotEqualTo(e2);
    assertThat(e.getTypeAdapter()).isNotEqualTo(e2.getTypeAdapter());
    assertThat(e.getTypeProvider()).isNotEqualTo(e2.getTypeProvider());
    Env e3 = e2.extend();
    assertThat(e2.getTypeAdapter()).isEqualTo(e3.getTypeAdapter());
    assertThat(e2.getTypeProvider()).isEqualTo(e3.getTypeProvider());
  }

  @Test
  void EnvExtensionIsolation() {
    Env baseEnv =
        newEnv(
            container("google.api.expr.test.v1"),
            declarations(
                Decls.newVar("age", Decls.Int),
                Decls.newVar("gender", Decls.String),
                Decls.newVar("country", Decls.String)));
    Env env1 =
        baseEnv.extend(
            types(
                com.google.api.expr.test.v1.proto2.TestAllTypesProto.TestAllTypes
                    .getDefaultInstance()),
            declarations(Decls.newVar("name", Decls.String)));
    Env env2 =
        baseEnv.extend(
            types(
                com.google.api.expr.test.v1.proto3.TestAllTypesProto.TestAllTypes
                    .getDefaultInstance()),
            declarations(Decls.newVar("group", Decls.String)));
    AstIssuesTuple astIss =
        env2.compile("size(group) > 10 && !has(proto3.TestAllTypes{}.single_int32)");
    assertThat(astIss.hasIssues()).isFalse();
    astIss = env2.compile("size(name) > 10");
    assertThat(astIss.getIssues().err())
        .withFailMessage("env2 contains 'name', but should not")
        .isNotNull();
    astIss = env2.compile("!has(proto2.TestAllTypes{}.single_int32)");
    assertThat(astIss.hasIssues()).isTrue();

    astIss = env1.compile("size(name) > 10 && !has(proto2.TestAllTypes{}.single_int32)");
    assertThat(astIss.hasIssues()).isFalse();
    astIss = env1.compile("size(group) > 10");
    assertThat(astIss.hasIssues()).isTrue();
    astIss = env1.compile("!has(proto3.TestAllTypes{}.single_int32)");
    assertThat(astIss.hasIssues()).isTrue();
  }

  @SuppressWarnings("rawtypes")
  @Test
  void ParseAndCheckConcurrently() throws Exception {
    Env e =
        newEnv(
            container("google.api.expr.v1alpha1"),
            types(Expr.getDefaultInstance()),
            declarations(
                Decls.newVar("expr", Decls.newObjectType("google.api.expr.v1alpha1.Expr"))));

    Consumer<String> parseAndCheck =
        expr -> {
          AstIssuesTuple xIss = e.compile(expr);
          assertThat(xIss.hasIssues()).isFalse();
        };

    int concurrency = 10;
    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    try {
      CompletableFuture[] futures =
          IntStream.range(0, concurrency)
              .mapToObj(
                  i ->
                      CompletableFuture.runAsync(
                          () -> parseAndCheck.accept(String.format("expr.id + %d", i)), executor))
              .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void CustomInterpreterDecorator() {
    AtomicReference<Interpretable> lastInstruction = new AtomicReference<>();
    InterpretableDecorator optimizeArith =
        i -> {
          lastInstruction.set(i);
          // Only optimize the instruction if it is a call.
          if (!(i instanceof InterpretableCall)) {
            return i;
          }
          InterpretableCall call = (InterpretableCall) i;
          // Only optimize the math functions when they have constant arguments.
          switch (call.function()) {
            case "_+_":
            case "_-_":
            case "_*_":
            case "_/_":
              // These are all binary operators so they should have to arguments
              Interpretable[] args = call.args();
              // When the values are constant then the call can be evaluated with
              // an empty activation and the value returns as a constant.
              if (!(args[0] instanceof InterpretableConst)
                  || !(args[1] instanceof InterpretableConst)) {
                return i;
              }
              Val val = call.eval(emptyActivation());
              if (isError(val)) {
                throw new RuntimeException(val.toString());
              }
              return newConstValue(call.id(), val);
            default:
              return i;
          }
        };

    Env env = newEnv(declarations(Decls.newVar("foo", Decls.Int)));
    AstIssuesTuple astIss = env.compile("foo == -1 + 2 * 3 / 3");
    env.program(astIss.getAst(), evalOptions(OptPartialEval), customDecorator(optimizeArith));
    assertThat(lastInstruction.get()).isInstanceOf(InterpretableCall.class);
    InterpretableCall call = (InterpretableCall) lastInstruction.get();
    Interpretable[] args = call.args();
    Interpretable lhs = args[0];
    assertThat(lhs).isInstanceOf(InterpretableAttribute.class);
    InterpretableAttribute lastAttr = (InterpretableAttribute) lhs;
    NamespacedAttribute absAttr = (NamespacedAttribute) lastAttr.attr();
    String[] varNames = absAttr.candidateVariableNames();
    assertThat(varNames).containsExactly("foo");
    Interpretable rhs = args[1];
    assertThat(rhs).isInstanceOf(InterpretableConst.class);
    InterpretableConst lastConst = (InterpretableConst) rhs;
    // This is the last number produced by the optimization.
    assertThat(lastConst.value()).isSameAs(IntOne);
  }

  @Test
  void Cost() {
    Env e = newEnv();
    AstIssuesTuple astIss = e.compile("\"Hello, World!\"");
    assertThat(astIss.hasIssues()).isFalse();

    Cost wantedCost = Cost.None;

    // Test standard evaluation cost.
    Program prg = e.program(astIss.getAst());
    Cost c = estimateCost(prg);
    assertThat(c).isEqualTo(wantedCost);

    // Test the factory-based evaluation cost.
    prg = e.program(astIss.getAst(), evalOptions(OptExhaustiveEval));
    c = estimateCost(prg);
    assertThat(c).isEqualTo(wantedCost);
  }

  @Test
  void ResidualAst_AttributeQualifiers() {
    Env e =
        newEnv(
            declarations(
                Decls.newVar("x", Decls.newMapType(Decls.String, Decls.Dyn)),
                Decls.newVar("y", Decls.newListType(Decls.Int)),
                Decls.newVar("u", Decls.Int)));
    AstIssuesTuple astIss =
        e.parse(
            "x.abc == u && x[\"abc\"] == u && x[x.string] == u && y[0] == u && y[x.zero] == u && (true ? x : y).abc == u && (false ? y : x).abc == u");
    Program prg = e.program(astIss.getAst(), evalOptions(OptTrackState, OptPartialEval));
    PartialActivation vars =
        partialVars(
            mapOf(
                "x",
                    mapOf(
                        "zero", 0,
                        "abc", 123,
                        "string", "abc"),
                "y", singletonList(123)),
            attributePattern("u"));
    EvalResult outDet = prg.eval(vars);
    assertThat(outDet.getVal()).matches(UnknownT::isUnknown);
    Ast residual = e.residualAst(astIss.getAst(), outDet.getEvalDetails());
    String expr = astToString(residual);
    assertThat(expr)
        .isEqualTo(
            "123 == u && 123 == u && 123 == u && 123 == u && 123 == u && 123 == u && 123 == u");
  }

  @Test
  void residualAst_Modified() {
    Env e =
        newEnv(
            declarations(
                Decls.newVar("x", Decls.newMapType(Decls.String, Decls.Int)),
                Decls.newVar("y", Decls.Int)));
    AstIssuesTuple astIss = e.parse("x == y");
    Program prg = e.program(astIss.getAst(), evalOptions(OptTrackState, OptPartialEval));
    for (int x = 123; x < 456; x++) {
      PartialActivation vars = partialVars(mapOf("x", x), attributePattern("y"));
      EvalResult outDet = prg.eval(vars);
      assertThat(outDet.getVal()).matches(UnknownT::isUnknown);
      Ast residual = e.residualAst(astIss.getAst(), outDet.getEvalDetails());
      String orig = astToString(astIss.getAst());
      assertThat(orig).isEqualTo("x == y");
      String expr = astToString(residual);
      String want = String.format("%d == y", x);
      assertThat(expr).isEqualTo(want);
    }
  }

  //  @SuppressWarnings("rawtypes")
  //  static void Example() {
  //    // Create the CEL environment with declarations for the input attributes and
  //    // the desired extension functions. In many cases the desired functionality will
  //    // be present in a built-in function.
  //    EnvOption decls =
  //        declarations(
  //            // Identifiers used within this expression.
  //            Decls.newVar("i", Decls.String),
  //            Decls.newVar("you", Decls.String),
  //            // Function to generate a greeting from one person to another.
  //            //    i.greet(you)
  //            Decls.newFunction(
  //                "greet",
  //                Decls.newInstanceOverload(
  //                    "string_greet_string", asList(Decls.String, Decls.String), Decls.String)));
  //    Env e = newEnv(decls);
  //
  //    // Compile the expression.
  //    AstIssuesTuple astIss = e.compile("i.greet(you)");
  //    assertThat(astIss.hasIssues()).isFalse();
  //
  //    // Create the program.
  //    ProgramOption funcs =
  //        functions(
  //            Overload.binary(
  //                "string_greet_string",
  //                (lhs, rhs) ->
  //                    stringOf(String.format("Hello %s! Nice to meet you, I'm %s.\n", rhs,
  // lhs))));
  //    Program prg = e.program(astIss.getAst(), funcs);
  //
  //    // Evaluate the program against some inputs. Note: the details return is not used.
  //    EvalResult out =
  //        prg.eval(
  //            mapOf(
  //                // Native values are converted to CEL values under the covers.
  //                "i",
  //                "CEL",
  //                // Values may also be lazily supplied.
  //                "you",
  //                (Supplier) () -> stringOf("world")));
  //
  //    System.out.println(out);
  //    // Output:Hello world! Nice to meet you, I'm CEL.
  //  }

  //  // ExampleGlobalOverload demonstrates how to define global overload function.
  //  @SuppressWarnings("rawtypes")
  //  static void Example_globalOverload() {
  //    // Create the CEL environment with declarations for the input attributes and
  //    // the desired extension functions. In many cases the desired functionality will
  //    // be present in a built-in function.
  //    EnvOption decls =
  //        declarations(
  //            // Identifiers used within this expression.
  //            Decls.newVar("i", Decls.String),
  //            Decls.newVar("you", Decls.String),
  //            // Function to generate shake_hands between two people.
  //            //    shake_hands(i,you)
  //            Decls.newFunction(
  //                "shake_hands",
  //                Decls.newOverload(
  //                    "shake_hands_string_string",
  //                    asList(Decls.String, Decls.String),
  //                    Decls.String)));
  //    Env e = newEnv(decls);
  //
  //    // Compile the expression.
  //    AstIssuesTuple astIss = e.compile("shake_hands(i,you)");
  //    assertThat(astIss.hasIssues()).isFalse();
  //
  //    // Create the program.
  //    ProgramOption funcs =
  //        functions(
  //            Overload.binary(
  //                "shake_hands_string_string",
  //                (lhs, rhs) -> {
  //                  if (!(lhs instanceof StringT)) {
  //                    return valOrErr(lhs, "unexpected type '%s' passed to shake_hands",
  // lhs.type());
  //                  }
  //                  if (!(rhs instanceof StringT)) {
  //                    return valOrErr(rhs, "unexpected type '%s' passed to shake_hands",
  // rhs.type());
  //                  }
  //                  StringT s1 = (StringT) lhs;
  //                  StringT s2 = (StringT) rhs;
  //                  return stringOf(String.format("%s and %s are shaking hands.\n", s1, s2));
  //                }));
  //    Program prg = e.program(astIss.getAst(), funcs);
  //
  //    // Evaluate the program against some inputs. Note: the details return is not used.
  //    EvalResult out = prg.eval(mapOf("i", "CEL", "you", (Supplier) () -> stringOf("world")));
  //
  //    System.out.println(out);
  //    // Output:CEL and world are shaking hands.
  //  }
}
