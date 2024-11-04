package com.barray.docgen;

import com.berray.GameObject;
import com.berray.components.core.Action;
import com.berray.components.core.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.TypeSolverBuilder;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class BerrayDoc {

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.out.println("Usage: BerrayDoc <path to berray source folder>");
    }
    SourceRoot sourceRoot = new SourceRoot(Path.of(args[0]));
    sourceRoot.getParserConfiguration().setSymbolResolver(
        new JavaSymbolSolver(
            new TypeSolverBuilder()
                .withCurrentJRE()
                .withCurrentClassloader()
                .withCurrentClassloader().build()
        ));
    List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();

    List<ClassDocumentation> classes = new ArrayList<>();

    for (ParseResult<CompilationUnit> parseResult : parseResults) {
      FindJavaDocVisitor visitor = new FindJavaDocVisitor();
      parseResult.getResult().ifPresent(result -> {
        visitor.visit(result, "");
        visitor.finishedClasses
            .stream().filter(c -> isInterestedClass(c))
            .forEach(classes::add);
      });
    }
    Path docPath = Path.of("./doc");
    Files.createDirectories(docPath);
    Files.writeString(docPath.resolve("doc.json"), new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(classes));

  }

  private static boolean isInterestedClass(ClassDocumentation c) {
    List<String> extendedClasses = c.getExtendedClasses();
    return extendedClasses.contains(Component.class.getName())
        || extendedClasses.contains(Action.class.getName())
        || extendedClasses.contains(GameObject.class.getName());
  }

  private static class FindJavaDocVisitor extends BerrayVisitorAdapter<Boolean, String> {
    private Map<String, String> imports = new HashMap<>();
    private Deque<ClassDocumentation> classStack = new ArrayDeque<>();
    private String packageName;
    private List<ClassDocumentation> finishedClasses = new ArrayList<>();

    @Override
    public Boolean visit(PackageDeclaration packageDeclaration, String arg) {
      this.packageName = packageDeclaration.getName().asString();
      return super.visit(packageDeclaration, arg);
    }

    @Override
    public Boolean visit(ImportDeclaration importDeclaration, String arg) {
      Name name = importDeclaration.getName();
      name.getQualifier().ifPresent(qualifier -> imports.put(name.getIdentifier(), qualifier.asString()));
      return super.visit(importDeclaration, arg);
    }

    @Override
    public Boolean visit(ClassOrInterfaceDeclaration n, String arg) {
      // create new class documentation holder and place them on the stack
      String className = getClassName(n.getName().getIdentifier());
      ClassDocumentation documentation = new ClassDocumentation(className);
      classStack.push(documentation);
      n.getComment().ifPresent(
          comment -> documentation.setClassJavaDoc(comment.getContent())
      );
      try {
        return super.visit(n, arg);
      } finally {
        finishedClasses.add(classStack.pop());
      }
    }

    private String getClassName(String thisClassName) {
      if (classStack.isEmpty()) {
        return thisClassName;
      }

      // The stack contains outer classes. append the outer classnames to this class name
      return classStack.stream()
          .map(ClassDocumentation::getName)
          .collect(Collectors.joining("."))
          + "." + thisClassName;
    }

    @Override
    public Boolean visitExtendsType(ClassOrInterfaceType n, String arg) {
      var resolved = n.resolve();
      var referenceType = resolved.asReferenceType();
      var allSuperTypes = referenceType.getAllAncestors();
      ClassDocumentation currentClass = classStack.peek();
      currentClass.addExtends(referenceType.getQualifiedName());
      allSuperTypes.stream()
          .map(ResolvedReferenceType::getQualifiedName)
          .filter(name -> !name.equals("java.lang.Object"))
          .forEach(currentClass::addExtends);
      return super.visitExtendsType(n, arg);
    }

    @Override
    public Boolean visit(MethodDeclaration n, String arg) {
      if (!classStack.isEmpty()) {
        MethodDocumentation method = new MethodDocumentation(n.getName().asString());

        n.getComment()
            .filter(Comment::isJavadocComment)
            .map(Comment::asJavadocComment)
            .map(JavadocComment::parse)
            .ifPresent(javadoc -> {
              javadoc.getBlockTags().stream()
                  .filter(tag -> tag.getTagName().equals("type"))
                  .map(tag -> tag.getContent().toText())
                  .findFirst()
                  .ifPresent(method::setType);

              method.setDocumentation(javadoc.getDescription().toText());
            });

        List<String> modifiers = n.getModifiers()
            .stream()
            .map(Modifier::getKeyword)
            .map(Modifier.Keyword::asString)
            .collect(Collectors.toList());

        method.setModifiers(modifiers);
        classStack.peek().addMethod(method);
      }
      return super.visit(n, arg);
    }

    @Override
    public Boolean visit(Parameter n, String arg) {
      if (!classStack.isEmpty()) {
        MethodDocumentation method = classStack.peek().getCurrentMethod();
        if (method == null) {
          System.out.println("in class " + classStack.peek().getName() + ": no current method for parameter " + n);
        } else {
          String type;
          Type parameterType = n.getType();
          if (parameterType.isUnknownType()) {
            type = null;
          } else {
            var resolved = parameterType.resolve();
            if (resolved.isPrimitive()) {
              type = resolved.asPrimitive().getBoxTypeQName();
            } else if (resolved.isReferenceType()) {
              type = resolved.asReferenceType().getQualifiedName();
            } else {
              type = "unknown: " + resolved.toString();
            }
          }
          method.addParameter(n.getName().asString(), type);
        }
      }
      return super.visit(n, arg);
    }
  }


}
