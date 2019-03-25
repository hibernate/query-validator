package org.hibernate.query.validator;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.function.NoArgSQLFunction;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.DateType;
import org.hibernate.type.StringType;
import org.hibernate.type.TimeType;
import org.hibernate.type.TimestampType;

class GenericDialect extends Dialect {

    GenericDialect() {
        super.registerFunction("current_date", new NoArgSQLFunction("current_date", DateType.INSTANCE));
        super.registerFunction("current_time", new NoArgSQLFunction("current_time", TimeType.INSTANCE));
        super.registerFunction("current_timestamp", new NoArgSQLFunction("current_timestamp", TimestampType.INSTANCE));

        super.registerFunction("concat", new StandardSQLFunction("concat", StringType.INSTANCE));
    }
}
