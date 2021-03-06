/**
 * Copyright (c) 2015, STARSCHEMA LTD.
 * All rights reserved.

 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.starschema.clouddb.jdbc.list;

import java.util.ArrayList;
import java.util.List;

import net.starschema.clouddb.jdbc.JdbcGrammarParser;
import net.starschema.clouddb.jdbc.antlr.sqlparse.ColumnCallException;
import net.starschema.clouddb.jdbc.antlr.sqlparse.TreeParsingException;

import org.antlr.runtime.tree.Tree;

public class Conjunction extends Node {

    public static Node buildFromConjunction(Tree t, TreeBuilder builder, Node mainnode,
                                            SelectStatement selectstatement) throws TreeParsingException {

        //TODO WHEN expression like a=b=c is parsed, we need to make it separate as a=b and b=c and a=c
        List<Disjunction> disjunctionContainer = new ArrayList<Disjunction>();
        List<Conjunction> conjunctionContainer = new ArrayList<Conjunction>();
        List<Negation> negationContainer = new ArrayList<Negation>();
        List<BooleanExpressionItem> booleanExpressionItemContainer = new ArrayList<BooleanExpressionItem>();

        for (int i = 0; i < t.getChildCount(); i++) {
            Tree child = t.getChild(i);
            switch (child.getType()) {
                case JdbcGrammarParser.DISJUNCTION:
                    Disjunction disjunction = new Disjunction(child, builder,
                            mainnode, selectstatement);
                    disjunctionContainer.add(disjunction);
                    break;
                case JdbcGrammarParser.CONJUNCTION:
                    Node built = Conjunction.buildFromConjunction(child, builder, mainnode, selectstatement);
                    if (built.getTokenType() == JdbcGrammarParser.CONJUNCTION) {
                        conjunctionContainer.add(Conjunction.class.cast(built));
                    } else {
                        disjunctionContainer.add(Disjunction.class.cast(built));
                    }
                    break;
                case JdbcGrammarParser.NEGATION:
                    negationContainer.add(new Negation(child, builder, mainnode,
                            selectstatement));
                    break;
                case JdbcGrammarParser.BOOLEANEXPRESSIONITEM:
                    booleanExpressionItemContainer.add(new BooleanExpressionItem(child,
                            builder, mainnode, selectstatement));
                    break;
                default:
                    break;
            }
        }

        //Now we handle Distribution
        if (disjunctionContainer.size() == 0) {
            //We do nothing
            return new Conjunction(conjunctionContainer, negationContainer, booleanExpressionItemContainer);
        } else {
            //We do nothing
            return new Disjunction(disjunctionContainer, conjunctionContainer, negationContainer, booleanExpressionItemContainer);
        }
    }

    public Conjunction(List<Conjunction> conjunctionContainer,
                       List<Negation> negationContainer,
                       List<BooleanExpressionItem> booleanExpressionItemContainer) {

        this.logger.debug("BUILDING " + "CONJUNCTION" + "NORMALLY");

        if (conjunctionContainer.size() != 0) {
            for (Conjunction conjunction : conjunctionContainer) {
                //We unite conjunctions
                for (Node item : conjunction.children) {
                    this.children.addLast(item);
                }
                this.logger.debug("ADDING CONJUNCTION TO CONJUNCTION");
            }
        }
        if (negationContainer.size() != 0) {
            for (Negation negation : negationContainer) {
                this.children.addLast(negation);
                this.logger.debug("ADDING NEGATION TO CONJUNCTION");
            }
        }
        if (booleanExpressionItemContainer.size() != 0) {
            for (BooleanExpressionItem booleanExpressionItem : booleanExpressionItemContainer) {
                this.children.addLast(booleanExpressionItem);
                this.logger.debug("ADDING BOOLEANEXPRITEM TO CONJUNCTION");
            }
        }
        this.tokenType = JdbcGrammarParser.CONJUNCTION;
        this.tokenName = JdbcGrammarParser.tokenNames[JdbcGrammarParser.CONJUNCTION];

    }

    public Conjunction(List<Node> nodelist) {
        this.tokenType = JdbcGrammarParser.CONJUNCTION;
        this.tokenName = JdbcGrammarParser.tokenNames[JdbcGrammarParser.CONJUNCTION];
        for (Node node : nodelist) {
            this.children.addLast(node);
            logger.debug("APPENDNIG CHILDNODE TO CONJUNCTION");
        }
    }

    /**
     * Constructor for building a new Conjunction from BooleanExpressionItems and research their pointednode in the selectStatements FromExpression
     *
     * @param nodelist
     * @param selectStatement
     */
    public Conjunction(List<Node> nodelist, SelectStatement selectStatement) {
        this.tokenType = JdbcGrammarParser.CONJUNCTION;
        this.tokenName = JdbcGrammarParser.tokenNames[JdbcGrammarParser.CONJUNCTION];
        for (Node node : nodelist) {
            this.children.addLast(node);
            logger.debug("APPENDNIG CHILDNODE TO CONJUNCTION");
        }
        //We must research for references
        for (Node node : this.children) {
            int tokenType2 = node.getTokenType();
            switch (tokenType2) {
                case JdbcGrammarParser.BOOLEANEXPRESSIONITEM:
                    BooleanExpressionItem cast = BooleanExpressionItem.class.cast(node);
                    List<SubQuery> subQueries = selectStatement.getFromExpression().getSubQueries();
                    List<JoinExpression> joinExpressions = selectStatement.getFromExpression().getJoinExpressions();
                    if (joinExpressions != null) {
                        for (JoinExpression joinExpression : joinExpressions) {
                            if (subQueries == null) {
                                subQueries = new ArrayList<SubQuery>();
                            }
                            subQueries.add(SubQuery.class.cast(joinExpression.getLeftItem()));
                            subQueries.add(SubQuery.class.cast(joinExpression.getRightItem()));
                        }
                    }


                    if (cast.left.tokenType == JdbcGrammarParser.COLUMN) {
                        ColumnReference leftColRef = ColumnReference.class.cast(cast.left);

                        if (subQueries != null) {
                            for (SubQuery subQuery : subQueries) {
                                try {
                                    UniQueIdContainer PointedNodeInSubQuery = leftColRef.searchPointedNodeInSubQuery(subQuery);
                                    if (PointedNodeInSubQuery != null) {
                                        leftColRef.setPointedNode(PointedNodeInSubQuery);
                                    }
                                } catch (ColumnCallException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                        }

                    }
                    if (cast.right.tokenType == JdbcGrammarParser.COLUMN) {
                        ColumnReference rightColRef = ColumnReference.class.cast(cast.right);
                        if (subQueries != null) {
                            for (SubQuery subQuery : subQueries) {
                                try {
                                    UniQueIdContainer PointedNodeInSubQuery = rightColRef.searchPointedNodeInSubQuery(subQuery);
                                    if (PointedNodeInSubQuery != null) {
                                        rightColRef.setPointedNode(PointedNodeInSubQuery);
                                    }
                                } catch (ColumnCallException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    break;

                default:
                    break;
            }
        }
    }

    @Override
    public String toPrettyString() {
        String result = "(";
        for (Node item : this.children) {
            result += item.toPrettyString() + " AND ";
        }
        result = result.substring(0, result.length() - 5) + ")";
        return result;
    }
}
