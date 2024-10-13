package com.barray.docgen;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class ClassDocumentation {
  private final String name;
  private String classJavaDoc;
  private List<String> extendedClasses = new ArrayList<>();
  private List<MethodDocumentation> methods = new ArrayList<>();

  public void addExtends(String name) {
    extendedClasses.add(name);
  }

  public void addMethod(MethodDocumentation method) {
    this.methods.add(method);
  }

  @JsonIgnore
  public MethodDocumentation getCurrentMethod() {
    return methods.isEmpty() ? null : methods.get(methods.size() - 1);
  }

}
