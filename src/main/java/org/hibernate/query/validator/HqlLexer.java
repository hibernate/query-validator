package org.hibernate.query.validator;

import antlr.Token;
import java.io.Reader;
import org.hibernate.QueryException;
import org.hibernate.hql.internal.antlr.HqlBaseLexer;

class HqlLexer extends HqlBaseLexer {
    private boolean possibleID;

    HqlLexer(Reader in) {
        super(in);
    }

    public void setTokenObjectClass(String cl) {
        this.tokenObjectClass = HqlToken.class;
    }

    protected void setPossibleID(boolean possibleID) {
        this.possibleID = possibleID;
    }

    protected Token makeToken(int i) {
        HqlToken token = (HqlToken)super.makeToken(i);
        token.setPossibleID(this.possibleID);
        this.possibleID = false;
        return token;
    }

    public void panic() {
        this.panic("CharScanner: panic");
    }

    public void panic(String s) {
        throw new QueryException(s);
    }
}
