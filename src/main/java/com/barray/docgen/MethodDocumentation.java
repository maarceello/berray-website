package com.barray.docgen;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
public class MethodDocumentation {
  private final String name;
  private String documentation;
  private String type;
  private List<String> modifiers;
  private List<ParameterDocumentation> parameters = new ArrayList<>();

  public void addParameter(String name, String type) {
    this.parameters.add(new ParameterDocumentation(name, type));
  }
}
