package org.hibernate.query.validator;

import antlr.CommonToken;

public class HqlToken extends CommonToken {
    private boolean possibleID;
    private int tokenType;

    public HqlToken() {
    }

    public boolean isPossibleID() {
        return this.possibleID;
    }

    public void setType(int t) {
        this.tokenType = this.getType();
        super.setType(t);
    }

    private int getPreviousType() {
        return this.tokenType;
    }

    public void setPossibleID(boolean possibleID) {
        this.possibleID = possibleID;
    }

    public String toString() {
        return "[\"" + this.getText() + "\",<" + this.getType() + "> previously: <" + this.getPreviousType() + ">,line=" + this.line + ",col=" + this.col + ",possibleID=" + this.possibleID + "]";
    }
}
