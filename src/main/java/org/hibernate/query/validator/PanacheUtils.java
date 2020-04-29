package org.hibernate.query.validator;

import java.util.Set;

public class PanacheUtils {
    public static String panacheQlToHql(Validation.Handler handler, String targetType, String methodName, 
                                        String panacheQl, int[] offset, Set<Integer> setParameterLabels) {
        switch(methodName) {
        case "find":
        case "list":
        case "stream":
            return panacheFindQueryToHql(handler, targetType, panacheQl, offset, setParameterLabels);
        // FIXME: those throw:
            /*
warning: Method org/hibernate/query/validator/JavacSessionFactory$EntityPersister$ByteBuddy$BYYHBkRf.getDiscriminatorType()Lorg/hibernate/type/Type; is abstract
warning: java.lang.AbstractMethodError: Method org/hibernate/query/validator/JavacSessionFactory$EntityPersister$ByteBuddy$BYYHBkRf.getDiscriminatorType()Lorg/hibernate/type/Type; is abstract
at org.hibernate.query.validator.JavacSessionFactory$EntityPersister$ByteBuddy$BYYHBkRf.getDiscriminatorType(Unknown Source)
at org.hibernate.hql.internal.ast.HqlSqlWalker.postProcessDML(HqlSqlWalker.java:840)
at org.hibernate.hql.internal.ast.HqlSqlWalker.postProcessUpdate(HqlSqlWalker.java:855)
at org.hibernate.hql.internal.antlr.HqlSqlBaseWalker.updateStatement(HqlSqlBaseWalker.java:417)
at org.hibernate.hql.internal.antlr.HqlSqlBaseWalker.statement(HqlSqlBaseWalker.java:273)
at org.hibernate.query.validator.Validation.validate(Validation.java:77)
at org.hibernate.query.validator.JavacProcessor$1.checkPanacheQuery(JavacProcessor.java:97)
at org.hibernate.query.validator.JavacProcessor$1.visitApply(JavacProcessor.java:374)
             */
//        case "delete":
//            return panacheDeleteQueryToHql(handler, targetType, panacheQl, offset, setParameterLabels);
//        case "update":
//            return panacheUpdateQueryToHql(handler, targetType, panacheQl, offset, setParameterLabels);
        case "exists":
        case "count":
            return panacheCountQueryToHql(handler, targetType, panacheQl, offset, setParameterLabels);
        default:
            return null;    
        }
    }

    private static String panacheCountQueryToHql(Validation.Handler handler, String targetType, 
                                          String query, int[] offset, Set<Integer> setParameterLabels) {
        String countPrefix = "SELECT COUNT(*) ";
        String fromPrefix = "FROM "+targetType;
        if (query == null) {
            offset[0] = countPrefix.length()+fromPrefix.length();
            return countPrefix + fromPrefix;
        }

        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            offset[0] = countPrefix.length()+fromPrefix.length();
            return countPrefix + fromPrefix;
        }

        String trimmedLc = trimmed.toLowerCase();
        if (trimmedLc.startsWith("from ")) {
            offset[0] = countPrefix.length();
            return countPrefix + query;
        }
        if (trimmedLc.startsWith("order by ")) {
            // ignore it
            offset[0] = countPrefix.length()+fromPrefix.length();
            return countPrefix + fromPrefix;
        }
        if (trimmedLc.indexOf(' ') == -1 && trimmedLc.indexOf('=') == -1) {
            if(missingRequiredSingleParam(handler, query, setParameterLabels))
                return null;
            query += " = ?1";
        }
        offset[0] = countPrefix.length()+fromPrefix.length() + 7;
        return countPrefix + fromPrefix + " WHERE "+query;
    }

    private static String panacheUpdateQueryToHql(Validation.Handler handler, String targetType,
                                           String query, int[] offset, Set<Integer> setParameterLabels) {
        if (query == null) {
            return null;
        }

        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String trimmedLc = trimmed.toLowerCase();
        if (trimmedLc.startsWith("update ")) {
            offset[0] = 0;
            return query;
        }
        if (trimmedLc.startsWith("from ")) {
            offset[0] = 7;
            return "UPDATE " + query;
        }
        if (trimmedLc.indexOf(' ') == -1 && trimmedLc.indexOf('=') == -1) {
            if(missingRequiredSingleParam(handler, query, setParameterLabels))
                return null;
            query += " = ?1";
        }
        String fromPrefix = "UPDATE FROM " + targetType + " ";
        if (trimmedLc.startsWith("set ")) {
            offset[0] = fromPrefix.length();
            return fromPrefix + query;
        }
        offset[0] = fromPrefix.length() + 4;
        return fromPrefix + "SET " + query;
    }

    private static String panacheDeleteQueryToHql(Validation.Handler handler, String targetType,
                                           String query, int[] offset, Set<Integer> setParameterLabels) {
        String deletePrefix = "DELETE FROM " + targetType;
        if (query == null) {
            offset[0] = deletePrefix.length();
            return deletePrefix;
        }

        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            offset[0] = deletePrefix.length();
            return deletePrefix;
        }

        String trimmedLc = trimmed.toLowerCase();
        if (trimmedLc.startsWith("from ")) {
            offset[0] = 7;
            return "DELETE " + query;
        }
        if (trimmedLc.startsWith("order by ")) {
            // ignore it
            offset[0] = deletePrefix.length();
            return deletePrefix;
        }
        if (trimmedLc.indexOf(' ') == -1 && trimmedLc.indexOf('=') == -1) {
            if(missingRequiredSingleParam(handler, query, setParameterLabels))
                return null;
            query += " = ?1";
        }
        offset[0] = deletePrefix.length() + 7;
        return deletePrefix + " WHERE " + query;
    }

    private static String panacheFindQueryToHql(Validation.Handler handler, String targetType,
                                         String query, int[] offset, Set<Integer> setParameterLabels) {
        String fromPrefix = "FROM " + targetType;
        if (query == null) {
            offset[0] = fromPrefix.length();
            return fromPrefix;
        }

        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            offset[0] = fromPrefix.length();
            return fromPrefix;
        }

        // FIXME: support, by collecting and checking named queries
        if (isNamedQuery(query)) {
            return null;
        }

        String trimmedLc = trimmed.toLowerCase();
        if (trimmedLc.startsWith("from ") || trimmedLc.startsWith("select ")) {
            offset[0] = 0;
            return query;
        }
        if (trimmedLc.startsWith("order by ")) {
            offset[0] = fromPrefix.length()+1;
            return fromPrefix + " " + query;
        }
        if (trimmedLc.indexOf(' ') == -1 && trimmedLc.indexOf('=') == -1) {
            if(missingRequiredSingleParam(handler, query, setParameterLabels))
                return null;
            query += " = ?1";
        }
        offset[0] = fromPrefix.length() + 7;
        return fromPrefix + " WHERE " + query;
    }

    private static boolean isNamedQuery(String query) {
        return query != null && !query.isEmpty() && query.charAt(0) == '#';
    }

    private static boolean missingRequiredSingleParam(Validation.Handler handler, String query,
                                               Set<Integer> setParameterLabels) {
        if(setParameterLabels.size() < 1) {
            handler.warn(0, 0, "Missing required parameter for "+query);
            return true;
        } else if(setParameterLabels.size() > 1){
            handler.warn(0, 0, "Too many parameters for "+query);
            return true;
        }
        return false;
    }

}
