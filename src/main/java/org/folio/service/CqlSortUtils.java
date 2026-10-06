package org.folio.service;

import java.io.IOException;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.z3950.zing.cql.CQLDefaultNodeVisitor;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.ModifierSet;

@Log4j2
public final class CqlSortUtils {

  private static final String ALL_RECORDS_QUERY = "cql.allRecords=1";
  private static final String ORDER_ATTRIBUTE = "order/number";

  private CqlSortUtils() {}

  /**
   * Adds "sortby order" part to cqlQuery, if query already has "sortby" part, then "order"
   * is added as second sort attribute
   *
   * @param cqlQuery initial query
   * @return query with "sortby order"
   */
  public static String withSortByOrder(String cqlQuery) {
    log.debug("withSortByOrder:: Attempts to sort query by order.");

    try {
      final CQLParser parser = new CQLParser(CQLParser.V1POINT2);
      CQLNode node = parser.parse(!StringUtils.isBlank(cqlQuery) ? cqlQuery : ALL_RECORDS_QUERY);
      SortVisitor visitor = new SortVisitor();
      node.traverse(visitor);
      CQLSortNode foundSortNode = visitor.getCqlSortNode();
      if (foundSortNode != null) {
        foundSortNode.addSortIndex(new ModifierSet(ORDER_ATTRIBUTE));
        return node.toCQL();
      } else {
        CQLSortNode newSortNode = new CQLSortNode(node);
        newSortNode.addSortIndex(new ModifierSet(ORDER_ATTRIBUTE));
        return newSortNode.toCQL();
      }
    } catch (CQLParseException | IOException e) {
      log.warn("Unsupported Query Format : Search query is in an unsupported format.");
      throw new IllegalArgumentException(e);
    }
  }

  private static class SortVisitor extends CQLDefaultNodeVisitor {

    private CQLSortNode cqlSortNode;

    @Override
    public void onSortNode(CQLSortNode cqlSortNode) {
      this.cqlSortNode = cqlSortNode;
    }

    public CQLSortNode getCqlSortNode() {
      return cqlSortNode;
    }
  }
}
