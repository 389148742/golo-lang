/*
 * Copyright 2012-2013 Institut National des Sciences Appliquées de Lyon (INSA-Lyon)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.insalyon.citi.golo.runtime.adapters;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.*;

import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.reflect.Modifier.*;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

public final class ClassGenerationDefinition {

  private final ClassLoader classLoader;
  private final String name;
  private final String parent;
  private final TreeSet<String> interfaces = new TreeSet<>();
  private final LinkedHashMap<String, MethodHandle> implementations = new LinkedHashMap<>();
  private final LinkedHashMap<String, MethodHandle> overrides = new LinkedHashMap<>();

  public ClassGenerationDefinition(ClassLoader classLoader, String name, String parent) {
    this.classLoader = classLoader;
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public String getParent() {
    return parent;
  }

  public Set<String> getInterfaces() {
    return unmodifiableSet(interfaces);
  }

  public Map<String, MethodHandle> getImplementations() {
    return unmodifiableMap(implementations);
  }

  public Map<String, MethodHandle> getOverrides() {
    return unmodifiableMap(overrides);
  }

  public ClassGenerationDefinition implementsInterface(String iface) {
    interfaces.add(iface);
    return this;
  }

  public ClassGenerationDefinition implementsMethod(String name, MethodHandle target) throws ClassGenerationDefinitionProblem {
    checkForImplementation(target);
    checkStarImplementationType(name, target);
    implementations.put(name, target);
    return this;
  }

  public ClassGenerationDefinition overridesMethod(String name, MethodHandle target) throws ClassGenerationDefinitionProblem {
    checkForOverriding(target);
    checkStarOverrideType(name, target);
    overrides.put(name, target);
    return this;
  }

  public boolean hasStarImplementation() {
    return implementations.containsKey("*");
  }

  public boolean hasStarOverride() {
    return overrides.containsKey("*");
  }

  public ClassGenerationDefinition validate() throws ClassGenerationDefinitionProblem {
    checkSuperTypesExistence();
    checkStarConflict();
    checkMethodsToBeImplemented();
    checkAllOverridesExist();
    return this;
  }

  private void checkStarImplementationType(String name, MethodHandle target) {
    if ("*".equals(name) && !target.type().equals(genericMethodType(0, true))) {
      throw new ClassGenerationDefinitionProblem("A * implementation must be of type (Object...)Object: " + target);
    }
  }

  private void checkStarOverrideType(String name, MethodHandle target) {
    if ("*".equals(name) && !target.type().equals(genericMethodType(1, true))) {
      throw new ClassGenerationDefinitionProblem("A * override must be of type (Object, Object...)Object: " + target);
    }
  }

  private void checkAllOverridesExist() {
    try {
      Class<?> parentClass = Class.forName(parent, true, classLoader);
      HashSet<String> canBeOverridden = new HashSet<>();
      for (Method method : parentClass.getMethods()) {
        canBeOverridden.add(method.getName());
      }
      for (Method method : parentClass.getDeclaredMethods()) {
        if (isPublic(method.getModifiers()) || isProtected(method.getModifiers())) {
          canBeOverridden.add(method.getName());
        }
      }
      for (String key : overrides.keySet()) {
        if (!"*".equals(key) && !canBeOverridden.contains(key)) {
          throw new ClassGenerationDefinitionProblem("There is no method named " + key + " to be overriden in parent class " + parentClass);
        }
      }
    } catch (ClassNotFoundException e) {
      throw new ClassGenerationDefinitionProblem(e);
    }
  }

  private Set<Method> abstractMethodsIn(Class<?> klass) {
    LinkedHashSet<Method> abstractMethods = new LinkedHashSet<>();
    for (Method method : klass.getMethods()) {
      if (isAbstract(method.getModifiers())) {
        abstractMethods.add(method);
      }
    }
    for (Method method : klass.getDeclaredMethods()) {
      if (isAbstract(method.getModifiers())) {
        abstractMethods.add(method);
      }
    }
    return abstractMethods;
  }

  private void checkMethodsToBeImplemented() {
    try {
      LinkedHashSet<Method> abstractMethods = new LinkedHashSet<>();
      abstractMethods.addAll(abstractMethodsIn(Class.forName(parent, true, classLoader)));
      for (String iface : interfaces) {
        abstractMethods.addAll(abstractMethodsIn(Class.forName(iface, true, classLoader)));
      }
      for (Method abstractMethod : abstractMethods) {
        String name = abstractMethod.getName();
        if (!implementations.containsKey(name) && !hasStarImplementation()) {
          throw new ClassGenerationDefinitionProblem("There is no implementation or override for: " + abstractMethod);
        }
        if (implementations.containsKey(name)) {
          MethodHandle target = implementations.get(name);
          if (argsDifferForImplementation(abstractMethod, target) || varargsMismatch(abstractMethod, target)) {
            throw new ClassGenerationDefinitionProblem("Types do not match to implement " + abstractMethod + " with " + target);
          }
        }
        if (overrides.containsKey(name)) {
          MethodHandle target = overrides.get(name);
          if (argsDifferForOverride(abstractMethod, target) || varargsMismatch(abstractMethod, target)) {
            throw new ClassGenerationDefinitionProblem("Types do not match to implement " + abstractMethod + " with " + target);
          }
        }
      }
    } catch (ClassNotFoundException e) {
      throw new ClassGenerationDefinitionProblem(e);
    }
  }

  private boolean varargsMismatch(Method abstractMethod, MethodHandle target) {
    return abstractMethod.isVarArgs() != target.isVarargsCollector();
  }

  private boolean argsDifferForImplementation(Method abstractMethod, MethodHandle target) {
    return (target.type().parameterCount() - 1 != abstractMethod.getParameterTypes().length);
  }

  private boolean argsDifferForOverride(Method abstractMethod, MethodHandle target) {
    return (target.type().parameterCount() - 2 != abstractMethod.getParameterTypes().length);
  }

  private void checkStarConflict() {
    if (hasStarImplementation() && hasStarOverride()) {
      throw new ClassGenerationDefinitionProblem("Having both a star implementation and a star override is forbidden.");
    }
  }

  private void checkSuperTypesExistence() {
    try {
      Class.forName(parent, true, classLoader);
      for (String iface : interfaces) {
        Class.forName(iface, true, classLoader);
      }
    } catch (ClassNotFoundException e) {
      throw new ClassGenerationDefinitionProblem(e);
    }
  }

  private void checkForImplementation(MethodHandle target) throws ClassGenerationDefinitionProblem {
    if (target.type().parameterCount() < 1) {
      throw new ClassGenerationDefinitionProblem("An implemented method target must take at least 1 argument (the receiver): " + target);
    }
  }

  private void checkForOverriding(MethodHandle target) throws ClassGenerationDefinitionProblem {
    if (target.type().parameterCount() < 2) {
      throw new ClassGenerationDefinitionProblem("An overriden method target must take at least 2 arguments (the 'super' method handle followed by the receiver): " + target);
    }
  }
}
