/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.se;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.sonar.java.collections.PCollections;
import org.sonar.java.collections.PMap;
import org.sonar.java.se.constraint.BooleanConstraint;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.constraint.ConstraintManager;
import org.sonar.java.se.constraint.ObjectConstraint;
import org.sonar.java.se.symbolicvalues.BinaryRelation;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.VariableTree;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ProgramState {

  public static class Pop {

    public final ProgramState state;
    public final List<SymbolicValue> values;

    public Pop(ProgramState programState, List<SymbolicValue> result) {
      state = programState;
      values = result;
    }

  }

  private int hashCode;

  private final int constraintSize;
  public static final ProgramState EMPTY_STATE = new ProgramState(
    PCollections.emptyMap(),
    PCollections.<SymbolicValue, Constraint>emptyMap()
      .put(SymbolicValue.NULL_LITERAL, ObjectConstraint.nullConstraint())
      .put(SymbolicValue.TRUE_LITERAL, BooleanConstraint.TRUE)
      .put(SymbolicValue.FALSE_LITERAL, BooleanConstraint.FALSE),
    PCollections.emptyMap(),
    Lists.newLinkedList());

  private final PMap<ExplodedGraph.ProgramPoint, Integer> visitedPoints;

  private final Deque<SymbolicValue> stack;
  private final PMap<Symbol, SymbolicValue> values;
  private final PMap<SymbolicValue, Constraint> constraints;

  private ProgramState(PMap<Symbol, SymbolicValue> values, PMap<SymbolicValue, Constraint> constraints,
                       PMap<ExplodedGraph.ProgramPoint, Integer> visitedPoints, Deque<SymbolicValue> stack) {
    this.values = values;
    this.constraints = constraints;
    this.visitedPoints = visitedPoints;
    this.stack = stack;
    constraintSize = 3;
  }

  private ProgramState(ProgramState ps, Deque<SymbolicValue> newStack) {
    values = ps.values;
    constraints = ps.constraints;
    constraintSize = ps.constraintSize;
    visitedPoints = ps.visitedPoints;
    stack = newStack;
  }

  private ProgramState(ProgramState ps, PMap<SymbolicValue, Constraint> newConstraints) {
    values = ps.values;
    constraints = newConstraints;
    constraintSize = ps.constraintSize + 1;
    visitedPoints = ps.visitedPoints;
    this.stack = ps.stack;
  }

  ProgramState stackValue(SymbolicValue sv) {
    Deque<SymbolicValue> newStack = new LinkedList<>(stack);
    newStack.push(sv);
    return new ProgramState(this, newStack);
  }

  ProgramState clearStack() {
    return unstackValue(stack.size()).state;
  }

  public Pop unstackValue(int nbElements) {
    if (nbElements == 0) {
      return new Pop(this, Collections.<SymbolicValue>emptyList());
    }
    Preconditions.checkArgument(stack.size() >= nbElements, nbElements);
    Deque<SymbolicValue> newStack = new LinkedList<>(stack);
    List<SymbolicValue> result = Lists.newArrayList();
    for (int i = 0; i < nbElements; i++) {
      result.add(newStack.pop());
    }
    return new Pop(new ProgramState(this, newStack), result);
  }

  public SymbolicValue peekValue() {
    return stack.peek();
  }

  public List<SymbolicValue> peekValues(int n) {
    if (n > stack.size()) {
      throw new IllegalStateException("At least " + n + " values were expected on the stack!");
    }
    return ImmutableList.copyOf(stack).subList(0, n);
  }

  int numberOfTimeVisited(ExplodedGraph.ProgramPoint programPoint) {
    Integer count = visitedPoints.get(programPoint);
    return count == null ? 0 : count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProgramState that = (ProgramState) o;
    return Objects.equals(values, that.values) &&
      Objects.equals(constraints, that.constraints) &&
      Objects.equals(peekValue(), that.peekValue());
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = Objects.hash(values, constraints, peekValue());
    }
    return hashCode;
  }

  @Override
  public String toString() {
    return "{" + values.toString() + "}  {" + constraints.toString() + "}" + " { " + stack.toString() + " }";
  }

  public ProgramState addConstraint(SymbolicValue symbolicValue, Constraint constraint) {
    PMap<SymbolicValue, Constraint> newConstraints = constraints.put(symbolicValue, constraint);
    if (newConstraints != constraints) {
      return new ProgramState(this, newConstraints);
    }
    return this;
  }

  ProgramState put(Symbol symbol, SymbolicValue value) {
    if (symbol.isUnknown() || isVolatileField(symbol)) {
      return this;
    }
    SymbolicValue oldValue = values.get(symbol);
    if (oldValue == null || oldValue != value) {
      PMap<Symbol, SymbolicValue> newValues = values.put(symbol, value);
      return new ProgramState(newValues, constraints, visitedPoints, stack);
    }
    return this;
  }

  private static boolean isVolatileField(Symbol symbol) {
    return isField(symbol) && symbol.isVolatile();
  }

  private static boolean isDisposable(SymbolicValue symbolicValue, @Nullable Object constraint) {
    return SymbolicValue.isDisposable(symbolicValue) && (constraint == null || !(constraint instanceof ObjectConstraint) || ((ObjectConstraint) constraint).isDisposable());
  }

  private static boolean isLocalVariable(Symbol symbol) {
    return symbol.isVariableSymbol() && symbol.owner().isMethodSymbol();
  }

  public ProgramState purge(Set<Symbol> liveVariables) {
    final PMap<Symbol, SymbolicValue>[] newValues = new PMap[]{values};
    final PMap<SymbolicValue, Constraint>[] newConstraints = new PMap[]{constraints};
    final Set<SymbolicValue> tmpReferences = new HashSet<>();
    newValues[0].forEach((symbol, symbolicValue) -> {
      if (isLocalVariable(symbol) && !liveVariables.contains(symbol)) {
        newValues[0] = newValues[0].remove(symbol);
      } else {
        tmpReferences.add(symbolicValue);
        tmpReferences.addAll(symbolicValue.referenced());
      }
    });
    stack.forEach(symbolicValue -> {
      tmpReferences.add(symbolicValue);
      tmpReferences.addAll(symbolicValue.referenced());
    });
    newConstraints[0].forEach((symbolicValue, constraint) -> {
      if (!tmpReferences.contains(symbolicValue) && isDisposable(symbolicValue, constraint)) {
        newConstraints[0] = newConstraints[0].remove(symbolicValue);
      }
    });
    return new ProgramState(newValues[0], newConstraints[0], visitedPoints, stack);
  }

  public ProgramState resetFieldValues(ConstraintManager constraintManager) {
    final List<VariableTree> variableTrees = new ArrayList<>();
    values.forEach((symbol, symbolicValue) -> {
      if (isField(symbol)) {
        VariableTree variable = ((Symbol.VariableSymbol) symbol).declaration();
        if (variable != null) {
          variableTrees.add(variable);
        }
      }
    });
    if (variableTrees.isEmpty()) {
      return this;
    }
    PMap<Symbol, SymbolicValue> newValues = values;
    for (VariableTree variableTree : variableTrees) {
      SymbolicValue newValue = constraintManager.createSymbolicValue(variableTree);
      newValues = newValues.put(variableTree.symbol(), newValue);
    }
    return new ProgramState(newValues, constraints, visitedPoints, stack);
  }

  public static boolean isField(Symbol symbol) {
    return symbol.isVariableSymbol() && !symbol.owner().isMethodSymbol();
  }

  public boolean canReach(SymbolicValue symbolicValue) {
    final boolean[] canReach = {false};
    values.forEach((symbol, sv) -> {
      if (symbolicValue.equals(sv)) {
        canReach[0] = true;
      }
    });
    return canReach[0];
  }

  public ProgramState visitedPoint(ExplodedGraph.ProgramPoint programPoint, int nbOfVisit) {
    return new ProgramState(values, constraints, visitedPoints.put(programPoint, nbOfVisit), stack);
  }

  @CheckForNull
  public Constraint getConstraint(SymbolicValue sv) {
    return constraints.get(sv);
  }

  public int constraintsSize() {
    return constraintSize;
  }

  @CheckForNull
  public SymbolicValue getValue(Symbol symbol) {
    return values.get(symbol);
  }

  public Map<SymbolicValue, ObjectConstraint> getValuesWithConstraints(final Object state) {
    final Map<SymbolicValue, ObjectConstraint> result = new HashMap<>();
    constraints.forEach((symbolicValue, valueConstraint) -> {
      if (valueConstraint instanceof ObjectConstraint) {
        ObjectConstraint constraint = (ObjectConstraint) valueConstraint;
        if (constraint.hasStatus(state)) {
          result.put(symbolicValue, constraint);
        }
      }
    });
    return result;
  }

  public List<ObjectConstraint> getFieldConstraints(final Object state) {
    final Set<SymbolicValue> valuesAssignedToFields = getFieldValues();
    final List<ObjectConstraint> result = new ArrayList<>();
    constraints.forEach((symbolicValue, valueConstraint) -> {
      if (valueConstraint instanceof ObjectConstraint && !valuesAssignedToFields.contains(symbolicValue)) {
        ObjectConstraint constraint = (ObjectConstraint) valueConstraint;
        if (constraint.hasStatus(state)) {
          result.add(constraint);
        }
      }
    });
    return result;
  }

  public Set<SymbolicValue> getFieldValues() {
    final Set<SymbolicValue> fieldValues = new HashSet<>();
    values.forEach((symbol, symbolicValue) -> {
      if (isField(symbol)) {
        fieldValues.add(symbolicValue);
      }
    });
    return fieldValues;
  }

  public List<BinaryRelation> getKnownRelations() {
    final List<BinaryRelation> knownRelations = new ArrayList<>();
    constraints.forEach((symbolicValue, constraint) -> {
      BinaryRelation relation = symbolicValue.binaryRelation();
      if (relation != null) {
        if (BooleanConstraint.TRUE.equals(constraint)) {
          knownRelations.add(relation);
        } else if (BooleanConstraint.FALSE.equals(constraint)) {
          knownRelations.add(relation.inverse());
        }
      }
    });
    return knownRelations;
  }

  @CheckForNull
  public ObjectConstraint getConstraintWithStatus(SymbolicValue value, Object aState) {
    final Object constraint = getConstraint(value.wrappedValue());
    if (constraint instanceof ObjectConstraint) {
      ObjectConstraint oConstraint = (ObjectConstraint) constraint;
      if (oConstraint.hasStatus(aState)) {
        return oConstraint;
      }
    }
    return null;
  }
}
