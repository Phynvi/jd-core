/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.service.converter.classfiletojavasyntax.visitor;

import org.jd.core.v1.model.javasyntax.AbstractJavaSyntaxVisitor;
import org.jd.core.v1.model.javasyntax.declaration.*;
import org.jd.core.v1.model.javasyntax.expression.*;
import org.jd.core.v1.model.javasyntax.reference.InnerObjectReference;
import org.jd.core.v1.model.javasyntax.reference.ObjectReference;
import org.jd.core.v1.model.javasyntax.statement.*;
import org.jd.core.v1.model.javasyntax.type.*;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.expression.*;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.TypeMaker;
import org.jd.core.v1.util.DefaultList;

import static org.jd.core.v1.model.javasyntax.type.ObjectType.TYPE_OBJECT;

public class AddCastExpressionVisitor extends AbstractJavaSyntaxVisitor {
    protected TypeMaker typeMaker;
    protected Type returnedType;
    protected Type type;

    public AddCastExpressionVisitor(TypeMaker typeMaker) {
        this.typeMaker = typeMaker;
    }

    @Override
    public void visit(FieldDeclaration declaration) {
        Type t = type;

        type = declaration.getType();
        declaration.getFieldDeclarators().accept(this);
        type = t;
    }

    @Override
    public void visit(FieldDeclarator declarator) {
        VariableInitializer variableInitializer = declarator.getVariableInitializer();

        if (variableInitializer != null) {
            int extraDimension = declarator.getDimension();

            if (extraDimension == 0) {
                variableInitializer.accept(this);
            } else {
                Type t = type;

                type = type.createType(type.getDimension() + extraDimension);
                variableInitializer.accept(this);
                type = t;
            }
        }
    }

    @Override
    public void visit(ConstructorDeclaration declaration) {
        safeAccept(declaration.getStatements());
    }

    @Override
    public void visit(MethodDeclaration declaration) {
        BaseStatement statements = declaration.getStatements();

        if (statements != null) {
            Type t = returnedType;

            returnedType = declaration.getReturnedType();
            statements.accept(this);
            returnedType = t;
        }
    }

    @Override
    public void visit(LambdaIdentifiersExpression expression) {
        BaseStatement statements = expression.getStatements();

        if (statements != null) {
            Type t = returnedType;

            returnedType = expression.getReturnedType();
            statements.accept(this);
            returnedType = t;
        }
    }

    @Override public void visit(ReturnExpressionStatement statement) {
        statement.setExpression(updateExpression(returnedType, statement.getExpression()));
    }

    @Override
    public void visit(LocalVariableDeclaration declaration) {
        Type t = type;

        type = declaration.getType();
        declaration.getLocalVariableDeclarators().accept(this);
        type = t;
    }

    @Override
    public void visit(LocalVariableDeclarator declarator) {
        VariableInitializer variableInitializer = declarator.getVariableInitializer();

        if (variableInitializer != null) {
            int extraDimension = declarator.getDimension();

            if (extraDimension == 0) {
                variableInitializer.accept(this);
            } else {
                Type t = type;

                type = type.createType(type.getDimension() + extraDimension);
                variableInitializer.accept(this);
                type = t;
            }
        }
    }

    @Override
    public void visit(ArrayVariableInitializer declaration) {
        Type t = type;

        type = type.createType(type.getDimension() - 1);
        acceptListDeclaration(declaration);
        type = t;
    }

    @Override
    public void visit(ExpressionVariableInitializer declaration) {
        Expression expression = declaration.getExpression();

        if (expression.getClass() == NewInitializedArray.class) {
            ((NewInitializedArray)expression).getArrayInitializer().accept(this);
        } else {
            declaration.setExpression(updateExpression(type, expression));
        }
    }

    @Override
    public void visit(SuperConstructorInvocationExpression expression) {
        BaseExpression parameters = expression.getParameters();

        if (parameters != null) {
            expression.setParameters(updateExpressions(((ClassFileSuperConstructorInvocationExpression)expression).getParameterTypes(), parameters));
        }
    }

    @Override
    public void visit(ConstructorInvocationExpression expression) {
        BaseExpression parameters = expression.getParameters();

        if (parameters != null) {
            expression.setParameters(updateExpressions(((ClassFileConstructorInvocationExpression)expression).getParameterTypes(), parameters));
        }
    }

    @Override
    public void visit(MethodInvocationExpression expression) {
        BaseExpression parameters = expression.getParameters();

        if (parameters != null) {
            ClassFileMethodInvocationExpression mie = (ClassFileMethodInvocationExpression)expression;

            parameters.accept(this);
            expression.setParameters(updateExpressions(mie.getParameterTypes(), parameters));
        }

        expression.getExpression().accept(this);
    }

    @Override
    public void visit(NewExpression expression) {
        BaseExpression parameters = expression.getParameters();

        if (parameters != null) {
            expression.setParameters(updateExpressions(((ClassFileNewExpression)expression).getParameterTypes(), parameters));
        }
    }

    @Override
    public void visit(NewInitializedArray expression) {
        ArrayVariableInitializer arrayInitializer = expression.getArrayInitializer();

        if (arrayInitializer != null) {
            Type t = type;

            type = expression.getType();
            arrayInitializer.accept(this);
            type = t;
        }
    }

    @Override
    public void visit(BinaryOperatorExpression expression) {
        expression.getLeftExpression().accept(this);

        Expression rightExpression = expression.getRightExpression();

        if (expression.getOperator().equals("=")) {
            if (rightExpression.getClass() == ClassFileMethodInvocationExpression.class) {
                ClassFileMethodInvocationExpression mie = (ClassFileMethodInvocationExpression)rightExpression;

                if (mie.getTypeParameters() != null) {
                    // Do not add cast expression if method contains type parameters
                    rightExpression.accept(this);
                    return;
                }
            }

            expression.setRightExpression(updateExpression(expression.getLeftExpression().getType(), rightExpression));
            return;
        }

        rightExpression.accept(this);
    }

    @Override
    public void visit(TernaryOperatorExpression expression) {
        Type expressionType = expression.getType();

        expression.getCondition().accept(this);

        expression.setExpressionTrue(updateExpression(expressionType, expression.getExpressionTrue()));
        expression.setExpressionFalse(updateExpression(expressionType, expression.getExpressionFalse()));
    }

    @SuppressWarnings("unchecked")
    protected BaseExpression updateExpressions(BaseType types, BaseExpression expressions) {
        if (expressions != null) {
            if (expressions.isList()) {
                DefaultList<Type> t = types.getList();
                DefaultList<Expression> e = expressions.getList();

                for (int i = e.size() - 1; i >= 0; i--) {
                    e.set(i, updateExpression(t.get(i), e.get(i)));
                }
            } else {
                expressions = updateExpression(types.getFirst(), (Expression) expressions);
            }
        }

        return expressions;
    }

    private Expression updateExpression(Type type, Expression expression) {
        if (match(expression)) {
            Type expressionType = expression.getType();

            if (!expressionType.equals(type) && !TYPE_OBJECT.equals(type)) {
                if (type.isObject()) {
                    if (expressionType.isObject()) {
                        ObjectType objectType = (ObjectType) type;
                        ObjectType expressionObjectType = (ObjectType) expressionType;

                        if (!typeMaker.isAssignable(objectType, expressionObjectType)) {
                            BaseTypeArgument ta1 = objectType.getTypeArguments();
                            BaseTypeArgument ta2 = expressionObjectType.getTypeArguments();
                            Type t = type;

                            if ((ta1 != null) && (ta2 != null) && !ta1.isTypeArgumentAssignableFrom(ta2)) {
                                // Incompatible typeArgument arguments => Uses raw typeArgument
                                t = objectType.createType(null);
                            }
                            expression = addCastExpression(t, expression);
                        }
                    } else if (expressionType.isGeneric()) {
                        expression = addCastExpression(type, expression);
                    }
                } else if (type.isGeneric()) {
                    if (expressionType.isObject() || expressionType.isGeneric()) {
                        expression = addCastExpression(type, expression);
                    }
                }
            }
        }

        if (expression.getClass() == CastExpression.class) {
            CastExpression ce = (CastExpression)expression;
            Type ceExpressionType = ce.getExpression().getType();

            if (type.isObject() && ceExpressionType.isObject() && typeMaker.isAssignable((ObjectType)type, (ObjectType)ceExpressionType)) {
                // Remove cast expression
                expression = ce.getExpression();
            }
        }

        expression.accept(this);

        return expression;
    }

    private static final boolean match(Expression expression) {
        Class expressionClass = expression.getClass();

        if (expressionClass == NullExpression.class) {
            // Do not add a cast before a null value
            return false;
        }

        return true;
    }

    private static final Expression addCastExpression(Type type, Expression expression) {
        if (expression.getClass() == CastExpression.class) {
            CastExpression ce = (CastExpression)expression;

            if (type.equals(ce.getExpression().getType())) {
                return ce.getExpression();
            } else {
                ce.setType(type);
                return ce;
            }
        } else {
            return new CastExpression(expression.getLineNumber(), type, expression);
        }
    }

    @Override public void visit(FloatConstantExpression expression) {}
    @Override public void visit(IntegerConstantExpression expression) {}
    @Override public void visit(ConstructorReferenceExpression expression) {}
    @Override public void visit(DoubleConstantExpression expression) {}
    @Override public void visit(EnumConstantReferenceExpression expression) {}
    @Override public void visit(LocalVariableReferenceExpression expression) {}
    @Override public void visit(LongConstantExpression expression) {}
    @Override public void visit(BreakStatement statement) {}
    @Override public void visit(ByteCodeStatement statement) {}
    @Override public void visit(ContinueStatement statement) {}
    @Override public void visit(NullExpression expression) {}
    @Override public void visit(ObjectTypeReferenceExpression expression) {}
    @Override public void visit(SuperExpression expression) {}
    @Override public void visit(ThisExpression expression) {}
    @Override public void visit(TypeReferenceDotClassExpression expression) {}
    @Override public void visit(ObjectReference reference) {}
    @Override public void visit(InnerObjectReference reference) {}
    @Override public void visit(TypeArguments type) {}
    @Override public void visit(WildcardExtendsTypeArgument type) {}
    @Override public void visit(ObjectType type) {}
    @Override public void visit(InnerObjectType type) {}
    @Override public void visit(WildcardSuperTypeArgument type) {}
    @Override public void visit(Types list) {}
    @Override public void visit(TypeParameterWithTypeBounds type) {}
}